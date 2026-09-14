<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { ArrowLeft, CloudCheck, Download, FileJson, History, LayoutTemplate, LoaderCircle, Pencil, Save, Share2, Workflow } from 'lucide-vue-next'
import type { FlowchartDiagram, FlowchartDraft, FlowEdge, FlowNode, FlowNodeKind } from '../../types/flowcharts'
import type { FlowchartWorkbenchPreferences } from '../../utils/flowchartEditorState'
import NodeLibraryPanel from './NodeLibraryPanel.vue'
import FlowchartCanvas from './FlowchartCanvas.vue'
import InspectorPanel from './InspectorPanel.vue'

type ExportKind = 'source' | 'png' | 'svg'
type CanvasAction = 'copy' | 'paste' | 'delete' | 'front' | 'back'

const props = withDefaults(defineProps<{
  draft: FlowchartDraft
  statusLabel: string
  statusTone: 'default' | 'warning' | 'saving'
  selected: FlowNode | FlowEdge | null
  preferences: FlowchartWorkbenchPreferences
  readOnly: boolean
  viewportKey?: string
  exporting?: boolean
  historyAvailable?: boolean
  shareAvailable?: boolean
  canEdit?: boolean
  saveAvailable?: boolean
}>(), { exporting: false, historyAvailable: false, shareAvailable: false, canEdit: true, saveAvailable: true })

const emit = defineEmits<{
  title: [value: string]
  'toggle-library': []
  'toggle-inspector': []
  change: [diagram: FlowchartDiagram]
  selection: [cell: FlowNode | FlowEdge | null]
  'canvas-action': [name: CanvasAction]
  'library-create': [kind: FlowNodeKind]
  'library-drag-start': [kind: FlowNodeKind, event: DragEvent]
  'manual-save': []
  templates: []
  history: []
  export: [kind: ExportKind]
  share: []
  close: []
  error: [message: string]
}>()

const canvas = ref<InstanceType<typeof FlowchartCanvas>>()
const inspector = ref<InstanceType<typeof InspectorPanel>>()
const titleInput = ref<HTMLInputElement>()
const exportOpen = ref(false)
const editableTitle = computed(() => !props.readOnly)

function requestExport(kind: ExportKind) {
  exportOpen.value = false
  emit('export', kind)
}

function editSelected() {
  if (props.readOnly) return
  if (props.preferences.inspectorCollapsed) emit('toggle-inspector')
  void nextTick(() => inspector.value?.focusLabel())
}

function focusInspectorLabel() {
  if (props.preferences.inspectorCollapsed) emit('toggle-inspector')
  void nextTick(() => inspector.value?.focusLabel())
}

defineExpose({ canvas, focusTitle: () => titleInput.value?.focus(), focusInspectorLabel })
</script>

<template>
  <section class="flowchart-editor-shell" :class="{ 'flowchart-editor-shell--library-collapsed': preferences.libraryCollapsed, 'flowchart-editor-shell--inspector-collapsed': preferences.inspectorCollapsed }" aria-label="流程图编辑器">
    <header class="flowchart-editor-shell__header">
      <button type="button" class="flowchart-editor-shell__back" aria-label="返回知识列表" title="返回知识列表" @click="emit('close')"><ArrowLeft :size="18" /></button>
      <span class="flowchart-editor-shell__mark" aria-hidden="true"><Workflow :size="20" /></span>
      <div class="flowchart-editor-shell__identity">
        <input v-if="editableTitle" ref="titleInput" :value="draft.title" maxlength="200" aria-label="流程图标题" placeholder="输入流程图标题" @input="emit('title', ($event.target as HTMLInputElement).value)" />
        <h1 v-else>{{ draft.title || '流程图' }}</h1>
        <span class="flowchart-editor-shell__status" :class="`is-${statusTone}`" role="status" aria-live="polite"><LoaderCircle v-if="statusTone === 'saving'" :size="13" class="flowchart-editor-shell__spin" /><CloudCheck v-else :size="13" />{{ statusLabel }}</span>
      </div>
      <nav class="flowchart-editor-shell__actions" aria-label="流程图文档操作">
        <button v-if="!readOnly" type="button" class="button button-ghost" @click="emit('templates')"><LayoutTemplate :size="15" aria-hidden="true" />模板</button>
        <button v-if="historyAvailable" type="button" class="button button-ghost" @click="emit('history')"><History :size="15" aria-hidden="true" />历史</button>
        <div class="flowchart-editor-shell__export">
          <button type="button" class="button button-ghost" :disabled="exporting" :aria-expanded="exportOpen" aria-controls="flowchart-export-menu" @click="exportOpen = !exportOpen"><Download :size="15" aria-hidden="true" />{{ exporting ? '导出中' : '导出' }}</button>
          <div v-if="exportOpen" id="flowchart-export-menu" class="flowchart-editor-shell__export-menu" role="menu" @keydown.escape="exportOpen = false">
            <button type="button" role="menuitem" @click="requestExport('png')">PNG 图片</button>
            <button type="button" role="menuitem" @click="requestExport('svg')">SVG 矢量图</button>
            <button type="button" role="menuitem" @click="requestExport('source')"><FileJson :size="14" aria-hidden="true" />可编辑源文件</button>
          </div>
        </div>
        <button type="button" class="button button-ghost" :disabled="!shareAvailable" @click="emit('share')"><Share2 :size="15" aria-hidden="true" />分享</button>
        <button v-if="!readOnly" type="button" class="button button-primary" :disabled="statusTone === 'saving' || !saveAvailable" @click="emit('manual-save')"><Save :size="15" aria-hidden="true" />保存</button>
        <button v-else-if="canEdit" type="button" class="button button-primary" @click="emit('manual-save')"><Pencil :size="15" aria-hidden="true" />编辑流程图</button>
      </nav>
    </header>

    <main class="flowchart-editor-shell__body" :class="{ 'flowchart-editor-shell__body--readonly': readOnly }">
      <NodeLibraryPanel v-if="!readOnly" :collapsed="preferences.libraryCollapsed" :recent-kinds="preferences.recentKinds" @create="emit('library-create', $event)" @drag-start="(kind, event) => emit('library-drag-start', kind, event)" @toggle="emit('toggle-library')" />
      <FlowchartCanvas ref="canvas" :diagram="draft.diagram" :readonly="readOnly" :viewport-key="viewportKey" :properties-open="!preferences.inspectorCollapsed" :panel-collapse-state="preferences" @change="emit('change', $event)" @select="emit('selection', $event)" @edit-label="editSelected" @toggle-properties="emit('toggle-inspector')" @error="emit('error', $event)" />
      <InspectorPanel v-if="!readOnly" ref="inspector" :selected="selected" :nodes="draft.diagram.nodes" :edges="draft.diagram.edges" :collapsed="preferences.inspectorCollapsed" @change="canvas?.updateCell($event)" @action="emit('canvas-action', $event)" @select="canvas?.selectId($event)" @toggle="emit('toggle-inspector')" />
    </main>
  </section>
</template>

<style scoped>
.flowchart-editor-shell { --library-width: 264px; --inspector-width: 320px; display: flex; flex: 1 1 auto; min-width: 0; min-height: 0; height: 100%; flex-direction: column; background: var(--flow-bg); color: var(--flow-text); }
.flowchart-editor-shell--library-collapsed { --library-width: 44px; }.flowchart-editor-shell--inspector-collapsed { --inspector-width: 44px; }
.flowchart-editor-shell__header { display: flex; align-items: center; gap: 10px; min-height: 60px; padding: 10px 14px; border-bottom: 1px solid var(--flow-border); background: var(--flow-surface); }
.flowchart-editor-shell__back,.flowchart-editor-shell__mark { display: grid; place-items: center; width: 34px; height: 34px; flex: 0 0 auto; border-radius: 8px; }.flowchart-editor-shell__back { border: 1px solid transparent; background: transparent; color: var(--flow-muted); cursor: pointer; transition: color .16s ease, background .16s ease, border-color .16s ease; }.flowchart-editor-shell__back:hover { color: var(--flow-text); border-color: var(--flow-border); background: var(--flow-surface-alt); }.flowchart-editor-shell__back:focus-visible,.flowchart-editor-shell button:focus-visible { outline: 2px solid var(--flow-accent); outline-offset: 2px; }.flowchart-editor-shell__mark { color: var(--flow-accent); background: var(--flow-accent-soft); }
.flowchart-editor-shell__identity { min-width: 0; flex: 1 1 auto; }.flowchart-editor-shell__identity input,.flowchart-editor-shell__identity h1 { display: block; width: 100%; margin: 0; padding: 0; overflow: hidden; border: 0; background: transparent; color: var(--flow-text); font: 650 15px/1.4 inherit; text-overflow: ellipsis; white-space: nowrap; }.flowchart-editor-shell__identity input:focus,.flowchart-editor-shell__identity input:focus-visible { outline: 2px solid var(--flow-accent); outline-offset: 2px; border-radius: 4px; }.flowchart-editor-shell__status { display: inline-flex; align-items: center; gap: 5px; margin-top: 2px; color: var(--flow-muted); font-size: 11px; line-height: 1.4; }.flowchart-editor-shell__status.is-warning { color: var(--danger); }.flowchart-editor-shell__spin { animation: flowchart-editor-spin .8s linear infinite; }
.flowchart-editor-shell__actions { display: flex; align-items: center; gap: 4px; flex: 0 0 auto; }.flowchart-editor-shell__actions .button { display: inline-flex; align-items: center; gap: 5px; min-height: 32px; padding: 6px 9px; border-radius: 7px; color: var(--flow-muted); font-size: 12px; }.flowchart-editor-shell__actions .button:hover:not(:disabled) { color: var(--flow-text); background: var(--flow-surface-alt); }.flowchart-editor-shell__actions .button-primary { color: #fff; background: var(--flow-accent); }.flowchart-editor-shell__actions .button-primary:hover:not(:disabled) { background: var(--flow-accent); filter: brightness(1.04); }.flowchart-editor-shell__actions .button:disabled { cursor: not-allowed; opacity: .52; }
.flowchart-editor-shell__export { position: relative; }.flowchart-editor-shell__export-menu { position: absolute; z-index: 8; top: calc(100% + 7px); right: 0; display: grid; width: 164px; padding: 5px; border: 1px solid var(--flow-border); border-radius: 8px; background: var(--flow-surface); box-shadow: 0 10px 26px color-mix(in srgb, var(--flow-text) 16%, transparent); }.flowchart-editor-shell__export-menu button { display: flex; align-items: center; gap: 6px; min-height: 32px; border: 0; border-radius: 5px; background: transparent; color: var(--flow-text); font: inherit; font-size: 12px; text-align: left; cursor: pointer; }.flowchart-editor-shell__export-menu button:hover { background: var(--flow-accent-soft); }
.flowchart-editor-shell__body { display: grid; grid-template-columns: var(--library-width) minmax(0, 1fr) var(--inspector-width); flex: 1 1 auto; min-width: 0; min-height: 0; overflow: hidden; transition: grid-template-columns .18s ease; }.flowchart-editor-shell__body--readonly { grid-template-columns: minmax(0, 1fr); }.flowchart-editor-shell__body > :deep(.node-library),.flowchart-editor-shell__body > :deep(.inspector-panel) { box-sizing: border-box; width: auto; min-width: 0; flex: 0 1 auto; }.flowchart-editor-shell__body > :deep(.flow-canvas) { min-width: 0; min-height: 0; }
@keyframes flowchart-editor-spin { to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) { .flowchart-editor-shell__body,.flowchart-editor-shell__back { transition-duration: .01ms; }.flowchart-editor-shell__spin { animation: none; } }
</style>
