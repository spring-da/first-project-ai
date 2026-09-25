<script setup lang="ts">
import { ref } from 'vue'
import { ArrowDownToLine, ArrowUpToLine, ClipboardPaste, Copy, MousePointer2, PanelRightClose, PanelRightOpen, SlidersHorizontal, Trash2 } from 'lucide-vue-next'
import type { FlowEdge, FlowNode } from '../../types/flowcharts'

const props = defineProps<{ selected: FlowNode | FlowEdge | null; nodes: FlowNode[]; edges: FlowEdge[]; collapsed: boolean }>()
const emit = defineEmits<{ change: [cell: FlowNode | FlowEdge]; action: [name: 'copy' | 'paste' | 'delete' | 'front' | 'back']; select: [id: string | null]; toggle: [] }>()

const labelInput = ref<HTMLTextAreaElement>()

function update(key: string, value: unknown, style = false) {
  if (!props.selected) return
  const next = structuredClone(props.selected)
  if (style) Object.assign(next.style, { [key]: value })
  else Object.assign(next, { [key]: value })
  emit('change', next)
}

const value = (event: Event) => (event.target as HTMLInputElement).value
const checked = (event: Event) => (event.target as HTMLInputElement).checked
const selectionValue = (event: Event) => value(event) || null

defineExpose({ focusLabel: () => labelInput.value?.focus() })
</script>

<template>
  <aside class="inspector-panel" :class="{ 'inspector-panel--collapsed': collapsed }" aria-label="检查器">
    <button v-if="collapsed" type="button" class="inspector-panel__rail" aria-label="展开检查器" title="展开检查器" @click="emit('toggle')"><PanelRightOpen :size="17" /></button>
    <template v-else>
      <header class="inspector-panel__header">
        <SlidersHorizontal :size="16" aria-hidden="true" />
        <div><strong>检查器</strong><span>{{ selected ? ('width' in selected ? '图形属性' : '连线属性') : '画布与快捷帮助' }}</span></div>
        <button type="button" class="inspector-panel__toggle" aria-label="折叠检查器" title="折叠检查器" @click="emit('toggle')"><PanelRightClose :size="17" /></button>
      </header>

      <label class="inspector-field">
        <span>当前对象</span>
        <select :value="selected?.id ?? ''" @change="emit('select', selectionValue($event))">
          <option value="">画布（未选择对象）</option>
          <optgroup v-if="nodes.length" label="图形"><option v-for="node in nodes" :key="node.id" :value="node.id">{{ node.label || '无文字图形' }}</option></optgroup>
          <optgroup v-if="edges.length" label="连线"><option v-for="edge in edges" :key="edge.id" :value="edge.id">{{ edge.label || '未命名连线' }}</option></optgroup>
        </select>
      </label>

      <section v-if="!selected" class="inspector-help" aria-label="画布快捷帮助">
        <MousePointer2 :size="28" aria-hidden="true" />
        <h2>从这里开始</h2>
        <p>从左侧图形库单击添加，或拖到画布的目标位置。</p>
        <dl><div><dt>选择图形</dt><dd>单击对象，查看它的文字、样式和位置。</dd></div><div><dt>连接图形</dt><dd>从端口拖到另一个图形，创建正交连线。</dd></div><div><dt>快捷键</dt><dd><kbd>Delete</kbd> 删除，<kbd>⌘/Ctrl S</kbd> 保存，<kbd>Esc</kbd> 取消操作。</dd></div></dl>
      </section>

      <template v-else-if="'width' in selected">
        <label class="inspector-field"><span>文字</span><textarea ref="labelInput" :value="selected.label" maxlength="10000" rows="4" aria-label="图形文字" @input="update('label', value($event))" /></label>
        <div class="inspector-fields">
          <label class="inspector-field"><span>文字颜色</span><input type="color" :value="selected.style.textColor.slice(0, 7)" @input="update('textColor', value($event), true)" /></label>
          <label class="inspector-field"><span>字号</span><input type="number" :value="selected.style.fontSize" min="8" max="96" @change="update('fontSize', Number(value($event)), true)" /></label>
          <label class="inspector-field"><span>填充色</span><input type="color" :value="selected.style.fill === 'transparent' ? '#ffffff' : selected.style.fill.slice(0, 7)" @input="update('fill', value($event), true)" /></label>
          <label class="inspector-field"><span>文字对齐</span><select :value="selected.style.textAlign" @change="update('textAlign', value($event), true)"><option value="left">左对齐</option><option value="center">居中</option><option value="right">右对齐</option></select></label>
        </div>
        <label class="inspector-check"><input type="checkbox" :checked="selected.style.fill === 'transparent'" @change="update('fill', checked($event) ? 'transparent' : '#eef2ff', true)" />透明填充</label>
        <fieldset class="inspector-geometry"><legend>几何</legend><div class="inspector-fields"><label v-for="field in ['x', 'y', 'width', 'height'] as const" :key="field" class="inspector-field"><span>{{ { x: '横坐标', y: '纵坐标', width: '宽度', height: '高度' }[field] }}</span><input type="number" :value="Math.round(selected[field])" :min="field === 'width' || field === 'height' ? 10 : -1000000" :max="field === 'width' || field === 'height' ? 10000 : 1000000" @change="update(field, Number(value($event)))" /></label></div></fieldset>
        <div class="inspector-fields">
          <label class="inspector-field"><span>线条颜色</span><input type="color" :value="selected.style.stroke.slice(0, 7)" @input="update('stroke', value($event), true)" /></label>
          <label class="inspector-field"><span>线条粗细</span><input type="number" :value="selected.style.strokeWidth" min="0" max="12" step="0.5" @change="update('strokeWidth', Number(value($event)), true)" /></label>
        </div>
        <label class="inspector-check"><input type="checkbox" :checked="selected.style.dash" @change="update('dash', checked($event), true)" />虚线</label>
      </template>

      <template v-else>
        <label class="inspector-field"><span>连线文字</span><textarea ref="labelInput" :value="selected.label" maxlength="10000" rows="4" aria-label="连线文字" @input="update('label', value($event))" /></label>
        <div class="inspector-fields">
          <label class="inspector-field"><span>文字颜色</span><input type="color" :value="selected.style.textColor.slice(0, 7)" @input="update('textColor', value($event), true)" /></label>
          <label class="inspector-field"><span>字号</span><input type="number" :value="selected.style.fontSize" min="8" max="96" @change="update('fontSize', Number(value($event)), true)" /></label>
          <label class="inspector-field"><span>线条颜色</span><input type="color" :value="selected.style.stroke.slice(0, 7)" @input="update('stroke', value($event), true)" /></label>
          <label class="inspector-field"><span>线条粗细</span><input type="number" :value="selected.style.strokeWidth" min="0" max="12" step="0.5" @change="update('strokeWidth', Number(value($event)), true)" /></label>
        </div>
        <label class="inspector-check"><input type="checkbox" :checked="selected.style.dash" @change="update('dash', checked($event), true)" />虚线</label>
        <label class="inspector-field"><span>连线方式</span><select :value="selected.kind" @change="update('kind', value($event))"><option value="orthogonal">折线</option><option value="straight">直线</option></select></label>
        <div class="inspector-checks"><label class="inspector-check"><input type="checkbox" :checked="selected.sourceArrow" @change="update('sourceArrow', checked($event))" />起点箭头</label><label class="inspector-check"><input type="checkbox" :checked="selected.targetArrow" @change="update('targetArrow', checked($event))" />终点箭头</label></div>
      </template>

      <div v-if="selected" class="inspector-actions" aria-label="图层与编辑操作">
        <button type="button" @click="emit('action', 'front')"><ArrowUpToLine :size="15" aria-hidden="true" />置于顶层</button><button type="button" @click="emit('action', 'back')"><ArrowDownToLine :size="15" aria-hidden="true" />置于底层</button><button type="button" @click="emit('action', 'copy')"><Copy :size="15" aria-hidden="true" />复制</button><button type="button" @click="emit('action', 'paste')"><ClipboardPaste :size="15" aria-hidden="true" />粘贴</button>
        <button type="button" class="inspector-actions__delete" @click="emit('action', 'delete')"><Trash2 :size="15" aria-hidden="true" />删除选中</button>
      </div>
    </template>
  </aside>
</template>

<style scoped>
.inspector-panel { position: relative; z-index: 1; width: 304px; flex: 0 0 304px; min-height: 0; overflow: auto; overscroll-behavior: contain; scrollbar-gutter: stable; padding: 16px 15px; border-left: 1px solid var(--flow-border, var(--border)); background: var(--flow-surface, var(--panel)); color: var(--flow-text, var(--text)); box-shadow: -8px 0 20px color-mix(in srgb, var(--flow-text, var(--text)) 8%, transparent); color-scheme: light; }
:global(:root[data-theme='dark'] .inspector-panel) { color-scheme: dark; }
.inspector-panel--collapsed { width: 44px; flex-basis: 44px; padding: 8px 6px; overflow: hidden; }
.inspector-panel__rail,.inspector-panel__toggle { display: grid; place-items: center; width: 32px; height: 32px; padding: 0; border: 1px solid transparent; border-radius: 7px; background: transparent; color: var(--flow-muted, var(--muted)); cursor: pointer; transition: color .16s ease, border-color .16s ease, background .16s ease; }
.inspector-panel__rail:hover,.inspector-panel__toggle:hover { color: var(--flow-accent, var(--accent)); border-color: var(--flow-border, var(--border)); background: var(--flow-surface-alt, var(--surface-sunken)); }.inspector-panel__rail:focus-visible,.inspector-panel__toggle:focus-visible,.inspector-panel button:focus-visible { outline: 2px solid var(--flow-accent, var(--accent)); outline-offset: 2px; }
.inspector-panel__header { position: sticky; top: -16px; z-index: 2; display: flex; align-items: center; gap: 8px; margin: -16px 0 18px; padding: 16px 0 12px; border-bottom: 1px solid var(--flow-border, var(--border)); background: var(--flow-surface, var(--panel)); }.inspector-panel__header > svg { color: var(--flow-accent, var(--accent)); }.inspector-panel__header div { display: grid; gap: 1px; min-width: 0; }.inspector-panel__header strong { font-size: 13px; }.inspector-panel__header span { overflow: hidden; color: var(--flow-muted, var(--muted)); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }.inspector-panel__toggle { margin-left: auto; flex: 0 0 auto; }
.inspector-field { display: flex; flex-direction: column; gap: 6px; margin-bottom: 13px; min-width: 0; color: var(--flow-muted, var(--subtle)); font-size: 12px; }.inspector-field input,.inspector-field select,.inspector-field textarea { min-width: 0; width: 100%; box-sizing: border-box; padding: 7px 8px; border: 1px solid var(--flow-border, var(--border)); border-radius: 6px; background: var(--flow-surface-alt, var(--surface-sunken)); color: var(--flow-text, var(--text)); font: inherit; transition: border-color .16s ease, box-shadow .16s ease; }.inspector-field input:focus,.inspector-field select:focus,.inspector-field textarea:focus { outline: none; border-color: var(--flow-accent, var(--accent)); box-shadow: 0 0 0 2px color-mix(in srgb, var(--flow-accent, var(--accent)) 18%, transparent); }.inspector-field input[type=color] { height: 34px; padding: 3px; cursor: pointer; }.inspector-field textarea { resize: vertical; line-height: 1.6; }
.inspector-fields { display: grid; grid-template-columns: 1fr 1fr; gap: 9px; }.inspector-checks { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }.inspector-check { display: flex; align-items: center; gap: 7px; margin-bottom: 15px; color: var(--flow-muted, var(--subtle)); font-size: 12px; }.inspector-check input { accent-color: var(--flow-accent, var(--accent)); }.inspector-geometry { margin: 0 0 14px; padding: 10px; border: 1px solid var(--flow-border, var(--border)); border-radius: 7px; }.inspector-geometry legend { padding: 0 4px; color: var(--flow-muted, var(--subtle)); font-size: 11px; }
.inspector-help { max-width: 244px; margin: 32px auto 0; color: var(--flow-muted, var(--muted)); text-align: center; line-height: 1.65; }.inspector-help > svg { color: var(--flow-accent, var(--accent)); }.inspector-help h2 { margin: 10px 0 4px; color: var(--flow-text, var(--text)); font-size: 14px; }.inspector-help p { margin: 0; font-size: 12px; }.inspector-help dl { display: grid; gap: 9px; margin: 20px 0; text-align: left; }.inspector-help dl div { padding: 9px; border: 1px solid var(--flow-border, var(--border)); border-radius: 7px; background: var(--flow-surface-alt, var(--surface-sunken)); }.inspector-help dt { color: var(--flow-text, var(--text)); font-size: 11px; font-weight: 600; }.inspector-help dd { margin: 2px 0 0; font-size: 11px; }.inspector-help kbd { padding: 1px 4px; border: 1px solid var(--flow-border, var(--border)); border-radius: 3px; color: var(--flow-text, var(--text)); font: 10px ui-monospace, monospace; }
.inspector-actions { display: grid; grid-template-columns: 1fr 1fr; gap: 7px; padding-top: 13px; border-top: 1px solid var(--flow-border, var(--border)); }.inspector-actions button { display: flex; align-items: center; justify-content: center; gap: 5px; min-height: 34px; padding: 7px 2px; border: 1px solid var(--flow-border, var(--border)); border-radius: 6px; background: var(--flow-surface, var(--panel)); color: var(--flow-muted, var(--subtle)); font-size: 11px; cursor: pointer; transition: color .16s ease, border-color .16s ease, background .16s ease; }.inspector-actions button:hover { color: var(--flow-accent, var(--accent)); border-color: var(--flow-accent, var(--accent)); background: var(--flow-accent-soft, var(--accent-bg)); }.inspector-actions__delete { grid-column: 1 / -1; color: var(--danger) !important; }.inspector-actions__delete:hover { border-color: var(--danger) !important; background: color-mix(in srgb, var(--danger) 10%, transparent) !important; }
@media (max-width: 1100px) { .inspector-panel { width: 264px; flex-basis: 264px; padding-inline: 12px; }.inspector-panel__header { top: -16px; }.inspector-panel--collapsed { width: 44px; flex-basis: 44px; padding-inline: 6px; } }
@media (prefers-reduced-motion: reduce) { .inspector-panel__rail,.inspector-panel__toggle,.inspector-field input,.inspector-field select,.inspector-field textarea,.inspector-actions button { transition-duration: .01ms; } }
</style>
