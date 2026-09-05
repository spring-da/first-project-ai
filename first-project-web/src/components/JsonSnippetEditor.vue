<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { Braces, Code2, Columns2, GitBranch, Sparkles, Undo2, Redo2, Check, ChevronsDownUp, ChevronsUpDown, ScanSearch } from 'lucide-vue-next'
import CodeEditor from './CodeEditor.vue'
import JsonFieldNode from './JsonFieldNode.vue'
import { escapeJsonText, extractEmbeddedJson, formatJsonText, parseJsonSource, preflightJson, unescapeJsonText, type JsonSource } from '../utils/codeJson'
import { buildJsonTree, countJsonNodes, editJsonSource, type JsonEdit, type JsonNode } from '../utils/jsonStructure'
import { appendBoundedHistory, measureCode } from '../utils/codeEditing'
import { useNotificationStore } from '../stores/notifications'

const props = defineProps<{ modelValue: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const notifications = useNotificationStore()
const mode = ref<'code' | 'tree' | 'split'>('split')
const history = ref<string[]>([])
const future = ref<string[]>([])
const expansion = ref({ open: false, revision: 0 })
const parsed = shallowRef<JsonSource>(parseJsonSource(''))
const structure = shallowRef<{ root: JsonNode | null; error: string; nodeCount: number }>({ root: null, error: '', nodeCount: 0 })
const analyzing = ref(false)
const buildingTree = ref(false)
const validationPaused = ref(false)
const metrics = computed(() => measureCode(props.modelValue))
const largeSource = computed(() => metrics.value.large)
const canExpandAll = computed(() => structure.value.nodeCount <= 2_000)
let analyzedSource = ''
let analysisTimer: number | undefined
let treeTimer: number | undefined
let revision = 0
let wasLarge = false
const lastEscaped = ref(false)
const escaped = computed(() => parsed.value.valid ? parsed.value.encoding !== 'json' : lastEscaped.value)

function clearTimer(timer: number | undefined) {
  if (timer !== undefined) window.clearTimeout(timer)
}

function buildStructure(value: Extract<JsonSource, { valid: true }>, targetRevision: number) {
  clearTimer(treeTimer)
  buildingTree.value = true
  treeTimer = window.setTimeout(() => {
    if (targetRevision !== revision || analyzedSource !== props.modelValue || mode.value === 'code') return
    try {
      const root = buildJsonTree(value.text, true)
      structure.value = { root, error: '', nodeCount: countJsonNodes(root, 2_001) }
    } catch (error) {
      structure.value = { root: null, error: (error as Error).message, nodeCount: 0 }
    } finally {
      buildingTree.value = false
    }
  }, largeSource.value ? 32 : 0)
}

function analyze(value: string, force = false) {
  const targetRevision = ++revision
  clearTimer(analysisTimer)
  clearTimer(treeTimer)
  analyzedSource = ''
  structure.value = { root: null, error: '', nodeCount: 0 }
  buildingTree.value = false
  const preflight = preflightJson(value)
  if (preflight === 'empty') {
    validationPaused.value = false
    analyzing.value = false
    parsed.value = { valid: false, error: '输入 JSON，或创建一个空对象 / 数组。' }
    return
  }
  if (validationPaused.value && !force) {
    analyzing.value = false
    parsed.value = { valid: false, error: '自动解析已暂停。修正内容后点击“格式化”重新校验。' }
    structure.value = { root: null, error: parsed.value.error, nodeCount: 0 }
    return
  }
  if (preflight !== 'candidate') {
    validationPaused.value = true
    analyzing.value = false
    const error = preflight === 'incomplete'
      ? 'JSON 尚未闭合。为保持输入流畅，已暂停自动解析。'
      : '当前内容不是完整 JSON。为避免反复处理，已暂停自动解析。'
    parsed.value = { valid: false, error }
    structure.value = { root: null, error, nodeCount: 0 }
    return
  }
  analyzing.value = true
  analysisTimer = window.setTimeout(() => {
    if (targetRevision !== revision || value !== props.modelValue) return
    const result = parseJsonSource(value)
    parsed.value = result
    analyzedSource = value
    analyzing.value = false
    if (result.valid) {
      validationPaused.value = false
      lastEscaped.value = result.encoding !== 'json'
      if (mode.value !== 'code') buildStructure(result, targetRevision)
    } else {
      validationPaused.value = true
      structure.value = { root: null, error: result.error, nodeCount: 0 }
    }
  }, largeSource.value ? 320 : 100)
}

watch(() => props.modelValue, (value) => {
  const nextLarge = measureCode(value).large
  if (nextLarge && !wasLarge && mode.value !== 'code') mode.value = 'code'
  wasLarge = nextLarge
  analyze(value)
}, { immediate: true })

watch(mode, (value) => {
  if (value === 'code') {
    clearTimer(treeTimer)
    buildingTree.value = false
    return
  }
  if (analyzedSource === props.modelValue && parsed.value.valid) buildStructure(parsed.value, revision)
})

onBeforeUnmount(() => {
  clearTimer(analysisTimer)
  clearTimer(treeTimer)
})

function update(value: string) {
  if (value === props.modelValue) return
  if (value.length > 200000) { notifications.notify('代码内容不能超过 200000 个字符。', { type: 'warning' }); return }
  history.value = appendBoundedHistory(history.value, props.modelValue)
  future.value = []
  emit('update:modelValue', value)
}
function undo() {
  const value = history.value.pop()
  if (value === undefined) return
  future.value = appendBoundedHistory(future.value, props.modelValue)
  validationPaused.value = false
  emit('update:modelValue', value)
}
function redo() {
  const value = future.value.pop()
  if (value === undefined) return
  history.value = appendBoundedHistory(history.value, props.modelValue)
  validationPaused.value = false
  emit('update:modelValue', value)
}
function onKeydown(event: KeyboardEvent) {
  if (event.isComposing || !(event.ctrlKey || event.metaKey) || event.altKey) return
  if (event.key.toLowerCase() === 'z' || event.key.toLowerCase() === 'y') {
    event.preventDefault()
    event.stopPropagation()
    if (event.shiftKey || event.key.toLowerCase() === 'y') redo()
    else undo()
  }
}
function transform(action: 'format' | 'escape' | 'unescape') {
  try {
    const value = action === 'format' ? formatJsonText(props.modelValue) : action === 'escape' ? escapeJsonText(props.modelValue).value : unescapeJsonText(props.modelValue).value
    validationPaused.value = false
    if (value === props.modelValue) analyze(value, true)
    else update(value)
  } catch (error) { notifications.notify(`请先修正 JSON：${(error as Error).message}`, { type: 'warning' }) }
}
function extractJson() {
  const embedded = extractEmbeddedJson(props.modelValue)
  if (!embedded) {
    notifications.notify('没有找到可完整解析的 JSON 对象或数组。', { type: 'warning' })
    return
  }
  validationPaused.value = false
  update(embedded.value)
  notifications.notify('已从日志中提取完整 JSON，原始文本可通过撤销恢复。', { type: 'success' })
}
function expandAll() {
  if (!canExpandAll.value) {
    notifications.notify('结构字段较多，为避免页面卡顿，请按需展开节点。', { type: 'warning' })
    return
  }
  expansion.value = { open: true, revision: expansion.value.revision + 1 }
}
function edit(path: number[], action: JsonEdit) {
  try { update(editJsonSource(props.modelValue, path, action)) }
  catch (error) { notifications.notify((error as Error).message, { type: 'warning' }) }
}
</script>

<template>
  <section class="json-editor" aria-label="JSON 编辑区" @keydown.capture="onKeydown">
    <div class="json-editor__toolbar">
      <div class="json-editor__modes" role="group" aria-label="JSON 编辑视图">
        <button type="button" :aria-pressed="mode === 'code'" @click="mode = 'code'"><Code2 :size="14" />代码</button>
        <button type="button" :aria-pressed="mode === 'tree'" @click="mode = 'tree'"><GitBranch :size="14" />结构</button>
        <button type="button" :aria-pressed="mode === 'split'" @click="mode = 'split'"><Columns2 :size="14" />分栏</button>
      </div>
      <div class="json-editor__tools">
        <button type="button" aria-label="撤销 JSON 修改" title="撤销 (Ctrl / ⌘ Z)" :disabled="!history.length" @click="undo"><Undo2 :size="14" /></button>
        <button type="button" aria-label="重做 JSON 修改" title="重做" :disabled="!future.length" @click="redo"><Redo2 :size="14" /></button>
        <button v-if="validationPaused && modelValue.trim()" type="button" class="json-editor__extract" title="仅在点击后扫描并提取日志中的完整 JSON" @click="extractJson"><ScanSearch :size="14" />提取 JSON</button>
        <button type="button" :class="{ selected: escaped }" title="转义引号与反斜杠，保留换行和缩进" @click="transform('escape')"><Braces :size="14" />加转义</button>
        <button type="button" @click="transform('unescape')">去转义</button>
        <button class="json-editor__format" type="button" @click="transform('format')"><Sparkles :size="14" />格式化</button>
      </div>
    </div>
    <div class="json-editor__panes" :class="`mode-${mode}`">
      <div v-show="mode !== 'tree'" class="json-editor__code">
        <div class="json-editor__pane-label"><Code2 :size="13" />{{ escaped ? '转义文本' : 'JSON 源码' }}<small>{{ largeSource ? '大文本性能模式 · 已暂停高亮' : 'Enter 自动缩进 · Tab 缩进' }}</small></div>
        <CodeEditor :model-value="modelValue" language="JSON" :escaped="escaped" placeholder="粘贴 JSON，或输入 { 开始编辑…" :required="mode !== 'tree'" @update:model-value="update" />
      </div>
      <div v-if="mode !== 'code'" class="json-editor__structure">
        <div class="json-editor__pane-label"><GitBranch :size="13" />结构编辑<small>{{ structure.nodeCount > 2_000 ? '大型结构 · 请按需展开' : '直接修改字段、类型和值' }}</small><button type="button" aria-label="展开全部字段" :title="canExpandAll ? '展开全部' : '字段过多，请按需展开'" :disabled="validationPaused || buildingTree || !canExpandAll" @click="expandAll"><ChevronsDownUp :size="14" /></button><button type="button" aria-label="折叠全部字段" title="折叠全部" :disabled="validationPaused || buildingTree" @click="expansion = { open: false, revision: expansion.revision + 1 }"><ChevronsUpDown :size="14" /></button></div>
        <div v-if="buildingTree && !validationPaused" class="json-editor__empty json-editor__empty--progress"><GitBranch :size="28" /><strong>正在生成按需结构…</strong><p>大型内容不会自动展开全部字段。</p></div>
        <div v-else-if="structure.root && !validationPaused" class="json-editor__tree-scroll"><JsonFieldNode :node="structure.root" :path="[]" label="$" :expansion="expansion" @edit="edit" /></div>
        <div v-else class="json-editor__empty">
          <Braces :size="30" /><strong>{{ analyzing ? '正在检查 JSON…' : modelValue.trim() ? '完善 JSON 后即可编辑结构' : '从一个结构开始' }}</strong>
          <p>{{ analyzing ? '输入与粘贴不会被同步解析阻塞。' : validationPaused ? '源码保持原样，不再反复解析；修正后点击“格式化”重新校验。' : modelValue.trim() ? '源码会保留你的输入。' : '粘贴已有 JSON，或创建一个空对象、数组。' }}</p>
          <code v-if="modelValue.trim() && !analyzing">{{ structure.error }}</code>
          <button v-if="validationPaused && modelValue.trim()" type="button" @click="extractJson"><ScanSearch :size="14" />提取日志中的 JSON</button>
          <div v-if="!modelValue.trim()"><button type="button" @click="update('{}')">创建对象</button><button type="button" @click="update('[]')">创建数组</button></div>
          <button v-if="mode === 'tree'" type="button" @click="mode = 'code'">前往代码编辑</button>
        </div>
      </div>
    </div>
    <div class="json-editor__status" role="status"><span :class="{ valid: parsed.valid && !analyzing && !validationPaused, paused: validationPaused }"><Check v-if="parsed.valid && !analyzing && !validationPaused" :size="12" />{{ validationPaused ? '自动解析已暂停 · 修正后点格式化' : analyzing ? '正在校验（不阻塞输入）' : parsed.valid ? (escaped ? '已转义 · 保留结构' : 'JSON 格式有效') : 'JSON 待完善' }}</span><span>{{ metrics.lines }} 行 · {{ metrics.characters.toLocaleString() }} 字符</span><p v-if="largeSource">已启用大文本性能模式：源码使用原生文本渲染，结构树默认折叠并限制批量展开。</p><p v-else-if="validationPaused">无效内容只作为纯文本保留，不会扫描、格式化或生成结构树。</p><p v-else-if="escaped">转义文本保留实际换行；作为标准 JSON 使用前请点“去转义”。</p></div>
  </section>
</template>

<style scoped>
.json-editor { width: 100%; height: 100%; flex: 1; min-width: 0; min-height: 0; display: flex; flex-direction: column; overflow: hidden; background: var(--code-bg); }
.json-editor__toolbar { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px; padding: 9px 12px; border-bottom: 1px solid var(--border); background: var(--panel); }
.json-editor button { display: inline-flex; align-items: center; justify-content: center; gap: 5px; min-height: 29px; padding: 4px 8px; border: 1px solid transparent; border-radius: 6px; background: transparent; color: var(--subtle); cursor: pointer; font-size: 11px; white-space: nowrap; }
.json-editor button:hover:not(:disabled) { background: var(--accent-bg); color: var(--accent); }
.json-editor button:disabled { opacity: .35; cursor: default; }
.json-editor__modes { display: flex; gap: 2px; padding: 3px; border: 1px solid var(--border); border-radius: 8px; background: var(--surface-sunken); }
.json-editor__modes button[aria-pressed="true"] { background: var(--panel); color: var(--accent); box-shadow: 0 1px 5px rgba(0,0,0,.09); }
.json-editor__tools { display: flex; flex-wrap: wrap; gap: 4px; }
.json-editor__tools .selected, .json-editor__tools .json-editor__format, .json-editor__tools .json-editor__extract { color: var(--accent); border-color: var(--accent-border); background: var(--accent-bg); }
.json-editor__panes { display: flex; flex: 1; min-width: 0; min-height: 0; overflow: hidden; }
.json-editor__code, .json-editor__structure { flex: 1; width: 0; min-width: 0; min-height: 0; display: flex; flex-direction: column; overflow: hidden; }
.mode-split .json-editor__structure { border-left: 1px solid var(--border); }
.json-editor__pane-label { min-height: 35px; flex-shrink: 0; display: flex; align-items: center; gap: 6px; padding: 3px 12px; border-bottom: 1px solid var(--border); color: var(--subtle); background: color-mix(in srgb, var(--surface-sunken) 50%, var(--panel)); font-size: 11px; }
.json-editor__pane-label small { margin-left: auto; color: var(--muted); font-size: 10px; }
.json-editor__pane-label button { padding: 2px 4px; min-height: 22px; }
.json-editor__tree-scroll { flex: 1; overflow: auto; padding: 10px 8px 40px; }
.json-editor__empty { display: flex; flex: 1; min-height: 0; overflow: auto; flex-direction: column; align-items: center; justify-content: safe center; gap: 12px; padding: 24px; color: var(--muted); text-align: center; }
.json-editor__empty > svg { color: var(--accent); }
.json-editor__empty strong { color: var(--text); font-size: var(--font-sm); }
.json-editor__empty p { margin: 0; font-size: var(--font-xs); line-height: 1.7; }
.json-editor__empty code { max-width: 100%; overflow-wrap: anywhere; color: var(--danger); font-size: 11px; }
.json-editor__empty div { display: flex; gap: 8px; }
.json-editor__empty button { border-color: var(--accent-border); color: var(--accent); }
.json-editor__status { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 6px 12px; padding: 7px 12px; color: var(--muted); border-top: 1px solid var(--border); background: var(--panel); font-size: 10px; }
.json-editor__status > span { display: flex; align-items: center; gap: 4px; }
.json-editor__status .valid { color: var(--success); }
.json-editor__status .paused { color: var(--warning); }
.json-editor__status p { flex-basis: 100%; margin: 0; line-height: 1.5; }
@media (max-width: 1100px) {
  .mode-split { flex-direction: column; }
  .mode-split .json-editor__code, .mode-split .json-editor__structure { width: 100%; }
  .mode-split .json-editor__structure { border-left: 0; border-top: 1px solid var(--border); }
}
@media (max-width: 600px) {
  .json-editor__toolbar { padding: 6px; gap: 5px; }
  .json-editor__tools { gap: 0; }
  .json-editor__tools button { padding-inline: 6px; }
  .json-editor__pane-label small { display: none; }
  .json-editor__pane-label button:first-of-type { margin-left: auto; }
}
</style>
