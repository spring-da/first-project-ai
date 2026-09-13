<script setup lang="ts">
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, onDeactivated, onMounted, ref, shallowRef, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import { ArrowLeft, Save, Share2, Download, History, Pencil, Workflow, Circle, Square, Diamond, Database, Type, RectangleHorizontal, ArrowRightLeft, LayoutTemplate, CloudCheck, LoaderCircle, AlertCircle, RotateCcw, FileJson, Smartphone, Heart, Search, ChevronDown } from 'lucide-vue-next'
import type { FlowchartDiagram, FlowchartDocument, FlowchartDraft, FlowNode, FlowEdge, FlowNodeKind, FlowchartSummary, FlowchartRevision, FlowchartRevisionDetail } from '../../types/flowcharts'
import type { KnowledgeDomain } from '../../types'
import { useAuthStore } from '../../stores/auth'
import { useNotificationStore } from '../../stores/notifications'
import { useConfirmationStore } from '../../stores/confirmation'
import { flowchartApi } from '../../services/flowcharts'
import { FlowchartSession } from '../../utils/flowchartSession'
import { createFlowchartDraftStorage } from '../../utils/flowchartDrafts'
import type { LocalFlowchartDraft } from '../../utils/flowchartDrafts'
import { createClientId } from '../../utils/clientId'
import { copyDraft, emptyDiagram, createTemplate, exportFlowchartSource, downloadBlob, validateDiagram } from '../../utils/flowcharts'
import { filterFlowchartShapes, type FlowchartShapeCategory } from '../../utils/flowchartEditor'
import FlowchartProperties from './FlowchartProperties.vue'
import AppModal from '../AppModal.vue'
const FlowchartCanvas = defineAsyncComponent(() => import('./FlowchartCanvas.vue'))
const ShareDialog = defineAsyncComponent(() => import('../ShareDialog.vue'))
const props = defineProps<{ documentId?: string; domainId: string | null; domains: KnowledgeDomain[]; initialDiagram?: FlowchartDiagram; initialTitle?: string; startEditing?: boolean }>()
const emit = defineEmits<{ close: []; saved: [document: FlowchartSummary] }>()
interface CanvasHandle { add: (kind: FlowNodeKind) => void; startDrag: (kind: FlowNodeKind, event: MouseEvent) => void; updateCell: (cell: FlowNode | FlowEdge) => void; selectId: (id: string) => void; action: (name: 'copy' | 'paste' | 'delete' | 'front' | 'back') => void; fit: () => void; keepSelectedInView: () => void; flush: () => void; exportImage: (kind: 'png' | 'svg', title: string) => Promise<void>; getDiagram: () => FlowchartDiagram }
const auth = useAuthStore(), notifications = useNotificationStore(), confirmation = useConfirmationStore()
const ownerKey = auth.workspaceKey ?? '', localId = createClientId(), lifetime = new AbortController()
const canvas = ref<CanvasHandle>(), properties = ref<InstanceType<typeof FlowchartProperties>>()
const session = shallowRef<FlowchartSession | null>(null), tick = ref(0), loading = ref(true), loadingError = ref(''), localError = ref(''), localSaved = ref('')
const editing = ref(!props.documentId || !!props.startEditing), smallScreen = ref(window.matchMedia('(max-width: 767px)').matches)
const showProperties = ref(false), propertiesPinned = ref(false), selected = shallowRef<FlowNode | FlowEdge | null>(null), showTemplates = ref(false), showExport = ref(false), exporting = ref(false), showShare = ref(false)
const drafts = ref<LocalFlowchartDraft[]>([]), showHistory = ref(false), revisions = ref<FlowchartRevision[]>([]), revisionPage = ref(0), revisionBusy = ref(false), revisionPreview = shallowRef<FlowchartRevisionDetail | null>(null)
let storage: ReturnType<typeof createFlowchartDraftStorage> | undefined
try { storage = createFlowchartDraftStorage() } catch { localError.value = '浏览器不支持本机草稿存储，请及时保存或导出源文件。' }
let active = true, idleTimer: ReturnType<typeof setTimeout> | undefined, maxTimer: ReturnType<typeof setTimeout> | undefined, localTimer: ReturnType<typeof setTimeout> | undefined, retryTimer: ReturnType<typeof setTimeout> | undefined
let writes = Promise.resolve(), failures = 0
let recoveredDraftId: string | null = null
const draft = computed(() => { void tick.value; return session.value?.draft ?? { title: '', domainId: props.domainId, favorite: false, diagram: emptyDiagram() } })
const dirty = computed(() => { void tick.value; return session.value?.dirty ?? false })
const status = computed(() => { void tick.value; return session.value?.status ?? 'idle' })
const error = computed(() => { void tick.value; return session.value?.error ?? '' })
const doc = computed(() => { void tick.value; return session.value?.document })
const readOnly = computed(() => !editing.value || smallScreen.value)
const shapeIcons = { terminal: Circle, rectangle: Square, rounded: RectangleHorizontal, diamond: Diamond, io: ArrowRightLeft, database: Database, text: Type }
const paletteQuery = ref(''), paletteCategory = ref<FlowchartShapeCategory>('all')
const paletteCategories: Array<{ key: FlowchartShapeCategory; label: string }> = [{ key: 'all', label: '全部' }, { key: 'flow', label: '流程' }, { key: 'basic', label: '基础' }, { key: 'data', label: '数据' }, { key: 'annotation', label: '标注' }]
const paletteShapes = computed(() => filterFlowchartShapes(paletteQuery.value, paletteCategory.value))
const statusLabel = computed(() => status.value === 'saving' ? '正在保存…' : status.value === 'conflict' ? '版本冲突' : status.value === 'error' ? '尚未保存到云端' : dirty.value ? '有未保存修改' : doc.value ? '所有修改已保存' : '新建画布')
function clearSaveTimers() { clearTimeout(idleTimer); clearTimeout(maxTimer); clearTimeout(retryTimer); idleTimer = maxTimer = retryTimer = undefined }
function install(d: FlowchartDraft, saved: FlowchartDocument | null, creationKey = createClientId()) {
  session.value?.dispose(); clearSaveTimers()
  session.value = new FlowchartSession(d, saved, creationKey, {
    create: input => flowchartApi.create(input, lifetime.signal), update: (id, input) => flowchartApi.update(id, input, lifetime.signal),
    changed: () => { if (active) tick.value++ },
    saved: document => { if (active) { emit('saved', document); void persist().catch(() => {}) } },
  })
  selected.value = null; showProperties.value = false; propertiesPinned.value = false; loadingError.value = ''; tick.value++
}

function openProperties(pin = false, focusLabel = false) {
  if (readOnly.value) return
  showProperties.value = true
  if (pin) propertiesPinned.value = true
  if (focusLabel) void nextTick(() => properties.value?.focusLabel())
}

function closeProperties() {
  showProperties.value = false
  propertiesPinned.value = false
}

function toggleProperties() {
  if (showProperties.value) closeProperties()
  else openProperties(true)
}

function selectionChanged(cell: FlowNode | FlowEdge | null) {
  selected.value = cell
  if (!cell && !propertiesPinned.value) showProperties.value = false
}

function editSelected() { openProperties(false, true) }
function record(): LocalFlowchartDraft | null {
  const s = session.value
  if (!s || !ownerKey) return null
  return { id: localId, ownerKey, documentId: s.document?.id ?? props.documentId ?? null, creationKey: s.creationKey, baseVersion: s.document?.version ?? null, savedAt: new Date().toISOString(), draft: copyDraft(s.draft) }
}
function persist() {
  const snapshot = record(), shouldWrite = !!session.value?.dirty
  const recoveredId = recoveredDraftId
  if (!snapshot) return Promise.resolve()
  const operation = writes.catch(() => {}).then(async () => {
    if (!storage) throw new Error('浏览器无法保存本机草稿，请先保存到云端或导出源文件。')
    if (shouldWrite) await storage.write(snapshot)
    else {
      await storage.remove(ownerKey, localId)
      if (recoveredId) {
        await storage.remove(ownerKey, recoveredId)
        if (recoveredDraftId === recoveredId) recoveredDraftId = null
      }
    }
    if (active) { localError.value = ''; localSaved.value = shouldWrite ? snapshot.savedAt : '' }
  })
  writes = operation
  return operation.catch(error => { if (active) localError.value = (error as Error).message; throw error })
}
function change(next: FlowchartDraft) {
  session.value?.edit(next)
  clearTimeout(localTimer); localTimer = setTimeout(() => { void persist().catch(() => {}) }, 500)
  if (!active || status.value === 'conflict' || readOnly.value) return
  clearTimeout(idleTimer); idleTimer = setTimeout(() => { void save('AUTO') }, 2000)
  maxTimer ??= setTimeout(() => { void save('AUTO') }, 15000)
}
function field(key: 'title' | 'domainId' | 'favorite', value: string | boolean | null) { change({ ...copyDraft(draft.value), [key]: value }) }
function diagramChanged(diagram: FlowchartDiagram) { change({ ...copyDraft(draft.value), diagram }) }
async function save(mode: 'AUTO' | 'MANUAL'): Promise<boolean> {
  clearSaveTimers(); canvas.value?.flush()
  if (!active || !session.value) return false
  try {
    validateDiagram(session.value.draft.diagram)
    await session.value.save(mode)
    failures = 0; await persist().catch(() => {})
    if (dirty.value && status.value !== 'conflict' && active) { idleTimer = setTimeout(() => { void save('AUTO') }, 2000); maxTimer = setTimeout(() => { void save('AUTO') }, 15000) }
    return !dirty.value
  } catch (cause) {
    if (!active) return false
    await persist().catch(() => {})
    const http = (cause as { status?: number }).status
    if (status.value !== 'conflict' && (!http || http === 429 || http >= 500) && draft.value.title.trim()) {
      failures++; retryTimer = setTimeout(() => { void save('AUTO') }, http === 429 ? 30000 : Math.min(30000, 5000 * failures))
    }
    if (mode === 'MANUAL') notifications.notify((cause as Error).message, { type: 'warning' })
    return false
  }
}
async function canLeave() {
  canvas.value?.flush()
  if (!dirty.value && status.value !== 'saving') return true
  if (await save('AUTO')) return true
  try { await persist(); return true } catch {
    return confirmation.ask({ title: '修改尚未备份', message: '云端保存和本机草稿都未成功。离开会丢失这些修改。', detail: '建议取消离开，先导出流程图源文件。', confirmText: '仍然离开', cancelText: '继续编辑', tone: 'danger', icon: 'leave' })
  }
}
async function close() { if (await canLeave()) emit('close') }
async function reloadCloud() {
  const id = doc.value?.id ?? props.documentId
  if (!id) return
  if (!await confirmation.ask({ title: '读取云端版本？', message: '当前编辑器将切换到云端内容，本机修改会留在草稿恢复列表中。', confirmText: '读取云端', icon: 'info' })) return
  try {
    const snapshot = record()
    if (snapshot && storage) await storage.write({ ...snapshot, id: createClientId() })
    const latest = await flowchartApi.get(id, lifetime.signal)
    if (!active) return
    recoveredDraftId = null
    install(copyDraft(latest), latest); await persist().catch(() => {}); await refreshDrafts()
  } catch (cause) { notifyError((cause as Error).message) }
}
async function saveCopy() { if (!session.value) return; const d = copyDraft(draft.value); d.title = `${d.title.slice(0, 195)} 副本`; install(d, null); editing.value = true; await save('MANUAL') }
async function refreshDrafts() { if (storage) { try { const records = await storage.list(ownerKey); if (active) drafts.value = records.filter(r => r.id !== localId && r.documentId === (doc.value?.id ?? props.documentId ?? null)) } catch (cause) { if (active) localError.value = (cause as Error).message } } }
async function recover(record: LocalFlowchartDraft) {
  if (dirty.value) {
    try {
      await persist()
      const current = session.value
      if (current && storage) await storage.write({ id: createClientId(), ownerKey, documentId: current.document?.id ?? props.documentId ?? null, creationKey: current.creationKey, baseVersion: current.document?.version ?? null, savedAt: new Date().toISOString(), draft: copyDraft(current.draft) })
    } catch (cause) { notifyError(`当前修改未能备份，请先保存或导出再恢复：${(cause as Error).message}`); return }
  }
  const latest = doc.value
  const base = latest && record.documentId === latest.id ? { ...latest, version: record.baseVersion ?? latest.version } : null
  install(record.draft, base, record.creationKey)
  recoveredDraftId = record.id
  if (latest && record.baseVersion !== latest.version && session.value) { session.value.status = 'conflict'; session.value.error = '云端版本已更新，请另存本机版本或读取云端。'; tick.value++ }
  editing.value = true; await persist().catch(() => {}); drafts.value = []
  if (status.value !== 'conflict') change(copyDraft(draft.value))
}
async function discard(record: LocalFlowchartDraft) {
  if (!await confirmation.ask({ title: '删除这份本机草稿？', message: `${record.draft.title || '未命名流程图'}的这份本机草稿将永久删除。`, confirmText: '删除草稿', tone: 'danger', icon: 'delete' })) return
  try { await storage?.remove(ownerKey, record.id); await refreshDrafts() } catch (cause) { notifyError((cause as Error).message) }
}
async function applyTemplate(kind: 'sequence' | 'decision' | 'architecture') {
  if (draft.value.diagram.nodes.length && !await confirmation.ask({ title: '用模板替换当前画布？', message: '画布中的现有图形将被替换。可先导出源文件保留副本。', confirmText: '替换画布', tone: 'warning' })) return
  change({ ...copyDraft(draft.value), diagram: createTemplate(kind) }); showTemplates.value = false; await nextTick(); canvas.value?.fit()
}
function notifyError(message: string) { notifications.notify(message, { type: 'warning' }) }
async function exportFile(kind: 'source' | 'png' | 'svg') {
  if (exporting.value) return
  showExport.value = false; exporting.value = true
  try {
    canvas.value?.flush()
    if (kind === 'source') downloadBlob(new Blob([exportFlowchartSource(draft.value.title, draft.value.diagram)], { type: 'application/json' }), `${draft.value.title || '流程图'}.flowchart.json`)
    else await canvas.value?.exportImage(kind, draft.value.title || '流程图')
  } catch (cause) { notifyError(`导出失败：${(cause as Error).message}`) } finally { exporting.value = false }
}
async function share() { if (dirty.value && !await save('MANUAL')) return; if (doc.value) showShare.value = true }
async function history(page = 0) {
  if (!doc.value || revisionBusy.value) return
  showHistory.value = true; revisionBusy.value = true; revisionPreview.value = null
  try { revisions.value = await flowchartApi.revisions(doc.value.id, page); revisionPage.value = page } catch (cause) { notifyError((cause as Error).message) } finally { revisionBusy.value = false }
}
async function previewRevision(id: string) { if (!doc.value) return; revisionBusy.value = true; try { revisionPreview.value = await flowchartApi.revision(doc.value.id, id) } catch (cause) { notifyError((cause as Error).message) } finally { revisionBusy.value = false } }
async function restoreRevision() {
  if (!doc.value || !revisionPreview.value) return
  if (!await confirmation.ask({ title: '恢复这个历史版本？', message: '恢复将产生新的云端版本，并更新已分享的流程图。', confirmText: '恢复版本', tone: 'warning' })) return
  if (dirty.value && !await save('MANUAL')) return
  clearSaveTimers(); revisionBusy.value = true
  try { const restored = await flowchartApi.restoreRevision(doc.value.id, revisionPreview.value.id, doc.value.version); if (!active) return; install(copyDraft(restored), restored); emit('saved', restored); showHistory.value = false; await persist().catch(() => {}) }
  catch (cause) { notifyError((cause as Error).message) } finally { revisionBusy.value = false }
}
function shortcut(event: KeyboardEvent) {
  if (!readOnly.value && (event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's' && !showHistory.value && !showShare.value && !confirmation.current && !document.body.classList.contains('modal-open')) { event.preventDefault(); event.stopPropagation(); void save('MANUAL') }
}
function beforeUnload(event: BeforeUnloadEvent) { if (dirty.value || status.value === 'saving') { void persist().catch(() => {}); event.preventDefault(); event.returnValue = '' } }
function online() { if (dirty.value && status.value !== 'conflict') void save('AUTO') }
const media = window.matchMedia('(max-width: 767px)')
function mediaChange() { smallScreen.value = media.matches }
onMounted(async () => {
  window.addEventListener('beforeunload', beforeUnload); window.addEventListener('online', online); media.addEventListener('change', mediaChange)
  await refreshDrafts()
  try {
    if (props.documentId) { const existing = await flowchartApi.get(props.documentId, lifetime.signal); if (active) install(copyDraft(existing), existing) }
    else if (active) install({ title: props.initialTitle || '未命名流程图', domainId: props.domainId, favorite: false, diagram: props.initialDiagram ?? emptyDiagram() }, null)
  } catch (cause) { if (active) loadingError.value = (cause as Error).message }
  finally { if (active) loading.value = false }
})
function dispose() { if (!active) return; canvas.value?.flush(); void persist().catch(() => {}); active = false; session.value?.dispose(); clearSaveTimers(); clearTimeout(localTimer); lifetime.abort(); window.removeEventListener('beforeunload', beforeUnload); window.removeEventListener('online', online); media.removeEventListener('change', mediaChange); void writes.finally(() => storage?.close()).catch(() => {}) }
watch(() => auth.workspaceKey, key => { if (key !== ownerKey) dispose() }, { flush: 'sync' })
watch(showProperties, (open, wasOpen) => {
  if (open && !wasOpen) void nextTick(() => canvas.value?.keepSelectedInView())
})
onBeforeRouteLeave(canLeave)
onBeforeRouteUpdate((to, from) => to.query.focus !== from.query.focus || to.query.create !== from.query.create || to.query.tab !== from.query.tab ? canLeave() : true)
onDeactivated(dispose); onBeforeUnmount(dispose)
defineExpose({ canLeave })
</script>

<template>
  <section class="flow-workspace" aria-label="流程图工作区" @keydown.capture="shortcut">
    <header class="flow-header">
      <button type="button" class="flow-back" aria-label="返回知识列表" @click="close"><ArrowLeft :size="18" /></button>
      <span class="flow-header__icon"><Workflow :size="22" /></span>
      <div class="flow-header__title"><input v-if="!readOnly && session" :value="draft.title" maxlength="200" aria-label="流程图标题" placeholder="输入流程图标题" @input="field('title', ($event.target as HTMLInputElement).value)" /><h1 v-else>{{ draft.title || '流程图' }}</h1><span role="status" :class="{ warning: status === 'error' || status === 'conflict' }"><LoaderCircle v-if="status === 'saving'" :size="12" class="spin" /><CloudCheck v-else :size="12" />{{ statusLabel }}</span></div>
      <div class="flow-header__actions">
        <button v-if="!readOnly" type="button" class="button button-ghost" @click="showTemplates = true"><LayoutTemplate :size="15" /><span>模板</span></button>
        <button v-if="doc" type="button" class="button button-ghost" @click="history()"><History :size="15" /><span>历史</span></button>
        <div class="flow-export"><button type="button" class="button button-ghost" :disabled="exporting || !session" :aria-expanded="showExport" @click="showExport = !showExport"><Download :size="15" /><span>{{ exporting ? '导出中' : '导出' }}</span></button><div v-if="showExport" class="flow-export__menu"><button type="button" @click="exportFile('png')">PNG 图片</button><button type="button" @click="exportFile('svg')">SVG 矢量图</button><button type="button" @click="exportFile('source')"><FileJson :size="14" />可编辑源文件</button></div></div>
        <button type="button" class="button button-ghost" :disabled="!session || status === 'conflict'" @click="share"><Share2 :size="15" /><span>分享</span></button>
        <button v-if="readOnly && !smallScreen && session" type="button" class="button button-primary" @click="editing = true"><Pencil :size="15" />编辑流程图</button>
        <button v-if="!readOnly" type="button" class="button button-primary" :disabled="!session || status === 'saving' || status === 'conflict'" @click="save('MANUAL')"><Save :size="15" />保存</button>
      </div>
    </header>
    <div v-if="session" class="flow-meta"><label><span>目录</span><select :value="draft.domainId ?? ''" :disabled="readOnly" @change="field('domainId', ($event.target as HTMLSelectElement).value || null)"><option value="">未分类</option><option v-for="domain in domains" :key="domain.id" :value="domain.id">{{ domain.name }}</option></select></label><button type="button" :disabled="readOnly" :class="{ active: draft.favorite }" @click="field('favorite', !draft.favorite)"><Heart :size="13" :fill="draft.favorite ? 'currentColor' : 'none'" />{{ draft.favorite ? '已收藏' : '收藏' }}</button><span>{{ draft.diagram.nodes.length }} 个图形 · {{ draft.diagram.edges.length }} 条连线</span><small v-if="localSaved">本机草稿已备份</small></div>
    <div v-if="smallScreen" class="flow-notice"><Smartphone :size="15" />手机支持查看和缩放，请在电脑上编辑图形。</div>
    <div v-if="error || localError" class="flow-notice warning" role="alert"><AlertCircle :size="16" /><span>{{ error || localError }}<small v-if="error && localError">{{ localError }}</small></span><template v-if="status === 'conflict'"><button type="button" @click="reloadCloud">读取云端版本</button><button type="button" @click="saveCopy">另存本机版本</button></template><button v-else-if="error" type="button" @click="save('MANUAL')">重试保存</button><button type="button" @click="exportFile('source')">导出备份</button></div>
    <div v-if="drafts.length" class="flow-drafts"><strong><RotateCcw :size="15" />发现本机草稿</strong><div v-for="record in drafts" :key="record.id"><span>{{ record.draft.title }} · {{ new Date(record.savedAt).toLocaleString() }}</span><button type="button" @click="recover(record)">恢复编辑</button><button type="button" @click="discard(record)">删除草稿</button></div></div>
    <div v-if="loading" class="flow-loading"><LoaderCircle :size="24" class="spin" />正在打开画布…</div>
    <div v-else-if="loadingError" class="flow-loading" role="alert"><AlertCircle :size="24" />{{ loadingError }}</div>
    <div v-else-if="session" class="flow-body">
      <aside v-if="!readOnly" class="flow-palette" aria-label="图形库">
        <div class="flow-palette__heading"><div><h2>图形库</h2><p>拖入画布，或点击添加</p></div><ChevronDown :size="15" aria-hidden="true" /></div>
        <label class="flow-palette__search"><Search :size="14" aria-hidden="true" /><input v-model="paletteQuery" type="search" placeholder="搜索图形" aria-label="搜索图形" /></label>
        <div class="flow-palette__tabs" role="tablist" aria-label="图形分类"><button v-for="category in paletteCategories" :key="category.key" type="button" role="tab" :aria-selected="paletteCategory === category.key" :class="{ active: paletteCategory === category.key }" @click="paletteCategory = category.key">{{ category.label }}</button></div>
        <div v-if="paletteShapes.length" class="flow-palette__grid">
          <button v-for="shape in paletteShapes" :key="shape.kind" type="button" :aria-label="`添加${shape.label}`" :title="shape.hint" @mousedown.prevent="canvas?.startDrag(shape.kind, $event)" @click="canvas?.add(shape.kind)"><component :is="shapeIcons[shape.kind]" :size="25" :stroke-width="1.4" /><span>{{ shape.label }}</span></button>
        </div>
        <p v-else class="flow-palette__empty">没有匹配的图形</p>
        <button type="button" class="flow-palette__add" @click="canvas?.add('rectangle')"><Square :size="14" />添加处理步骤</button>
        <small>双击图形编辑文字<br />从连接点拖出连线</small>
      </aside>
      <FlowchartCanvas :key="String(readOnly)" ref="canvas" class="flow-workspace__canvas" :diagram="draft.diagram" :readonly="readOnly" :viewport-key="`${ownerKey}:${doc?.id || localId}`" :properties-open="showProperties" @change="diagramChanged" @select="selectionChanged" @edit-label="editSelected" @toggle-properties="toggleProperties" @error="notifyError" />
      <Transition name="flow-properties">
        <FlowchartProperties v-if="!readOnly && showProperties" id="flow-properties-panel" ref="properties" :selected="selected" :nodes="draft.diagram.nodes" :edges="draft.diagram.edges" @change="canvas?.updateCell($event)" @action="canvas?.action($event)" @select="canvas?.selectId($event)" @close="closeProperties" />
      </Transition>
    </div>
    <AppModal v-if="showTemplates" title="从模板开始" description="选择常用结构，再根据你的流程调整。" @close="showTemplates = false"><div class="flow-template-list"><button type="button" @click="applyTemplate('sequence')"><Workflow :size="32" /><strong>顺序流程</strong><span>开始 → 处理 → 结束</span></button><button type="button" @click="applyTemplate('decision')"><Diamond :size="32" /><strong>条件分支</strong><span>判断条件，分为两条路径</span></button><button type="button" @click="applyTemplate('architecture')"><Database :size="32" /><strong>系统结构</strong><span>前端 → 后端 → 数据库</span></button></div></AppModal>
    <AppModal v-if="showHistory" title="历史版本" description="自动检查点间隔 5 分钟，手动保存即时生成；保留最近 100 条。" wide @close="showHistory = false"><div v-if="revisionBusy" role="status">正在读取历史…</div><div class="flow-history"><div class="flow-history__list"><button v-for="revision in revisions" :key="revision.id" type="button" :class="{ active: revisionPreview?.id === revision.id }" @click="previewRevision(revision.id)"><strong>{{ revision.title }}</strong><small>{{ new Date(revision.createdAt).toLocaleString() }} · v{{ revision.documentVersion }}</small></button><p v-if="!revisions.length">暂无历史版本。</p><div class="flow-history__pages"><button type="button" :disabled="revisionPage === 0 || revisionBusy" @click="history(revisionPage - 1)">上一页</button><span>{{ revisionPage + 1 }}</span><button type="button" :disabled="revisions.length < 20 || revisionBusy" @click="history(revisionPage + 1)">下一页</button></div></div><div v-if="revisionPreview" class="flow-history__preview"><FlowchartCanvas :key="revisionPreview.id" :diagram="revisionPreview.diagram" readonly /><button type="button" class="button button-primary" :disabled="revisionBusy" @click="restoreRevision">恢复此版本</button></div></div></AppModal>
    <ShareDialog v-if="showShare && doc" :items="[{ type: 'FLOWCHART', id: doc.id, title: draft.title }]" @close="showShare = false" />
  </section>
</template>

<style scoped>
.flow-workspace { --flow-bg: #f4f7fb; --flow-surface: #fff; --flow-surface-alt: #f8fafc; --flow-border: #dbe3ee; --flow-border-strong: #c5d0df; --flow-text: #25364d; --flow-muted: #6b7b91; --flow-accent: #1677ff; --flow-accent-soft: #eaf3ff; min-width: 0; width: 100%; height: 100%; min-height: 0; display: flex; flex-direction: column; border: 1px solid var(--flow-border); border-radius: 10px; background: var(--flow-bg); overflow: hidden; box-shadow: 0 8px 28px rgba(39, 70, 110, .08); }
:global(:root:not([data-theme='light'])) .flow-workspace { --flow-bg: #151a23; --flow-surface: #1d2430; --flow-surface-alt: #202936; --flow-border: #303b4b; --flow-border-strong: #465468; --flow-text: #edf2fb; --flow-muted: #9aa8ba; --flow-accent: #70a4ff; --flow-accent-soft: #243a5e; box-shadow: 0 8px 28px rgba(0, 0, 0, .24); }
.flow-header { display: flex; align-items: center; gap: 10px; min-height: 58px; flex: 0 0 auto; padding: 10px 14px; border-bottom: 1px solid var(--flow-border); background: var(--flow-surface); }.flow-back { width: 34px; height: 34px; display: grid; place-items: center; flex: 0 0 auto; border: 0; background: transparent; color: var(--flow-muted); border-radius: 6px; cursor: pointer; transition: color .15s ease, background .15s ease; }.flow-back:hover { color: var(--flow-text); background: var(--flow-surface-alt); }.flow-back:focus-visible { outline: 2px solid var(--flow-accent); outline-offset: 2px; }.flow-header__icon { display: grid; place-items: center; flex: 0 0 auto; color: var(--flow-accent); width: 34px; height: 34px; border-radius: 7px; background: var(--flow-accent-soft); }.flow-header__title { flex: 1 1 auto; min-width: 0; overflow: hidden; }.flow-header__title input,.flow-header__title h1 { display: block; width: 100%; margin: 0; padding: 0; overflow: hidden; border: 0; background: transparent; color: var(--flow-text); font: 600 15px/1.5 inherit; text-overflow: ellipsis; white-space: nowrap; }.flow-header__title input:focus { outline: none; }.flow-header__title span { display: flex; align-items: center; gap: 4px; max-width: 100%; margin-top: 2px; overflow: hidden; font-size: 11px; color: var(--flow-muted); text-overflow: ellipsis; white-space: nowrap; }.flow-header__actions { display: flex; align-items: center; flex: 0 1 auto; min-width: 0; max-width: 100%; gap: 3px; overflow-x: auto; flex-wrap: nowrap; scrollbar-width: none; }.flow-header__actions::-webkit-scrollbar { display: none; }.flow-header__actions .button { min-height: 32px; flex: 0 0 auto; padding: 6px 9px; color: var(--flow-muted); border-radius: 6px; font-size: 12px; }.flow-header__actions .button:hover { color: var(--flow-text); background: var(--flow-surface-alt); }.flow-header__actions .button:focus-visible { outline: 2px solid var(--flow-accent); outline-offset: 1px; }.flow-header__actions .button-primary { color: #fff; background: var(--flow-accent); }.flow-meta { display: flex; align-items: center; flex: 0 0 auto; flex-wrap: wrap; gap: 12px; min-height: 34px; padding: 6px 14px; overflow: hidden; font-size: 11px; color: var(--flow-muted); border-bottom: 1px solid var(--flow-border); background: var(--flow-surface); }.flow-meta label { display: flex; align-items: center; flex: 0 0 auto; gap: 7px; }.flow-meta select { max-width: 180px; min-width: 0; border: 0; color: var(--flow-text); font: inherit; background: transparent; }.flow-meta > span { flex: 0 0 auto; white-space: nowrap; }.flow-meta button { display: flex; gap: 5px; align-items: center; flex: 0 0 auto; border: 0; font: inherit; color: var(--flow-muted); background: transparent; cursor: pointer; }.flow-meta button.active { color: var(--flow-accent); }.flow-meta small { max-width: 35%; margin-left: auto; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.flow-body { position: relative; display: flex; flex: 1 1 auto; height: auto; min-height: 0; min-width: 0; overflow: hidden; }.flow-workspace__canvas { flex: 1 1 auto; min-width: 0; min-height: 0; }.flow-palette { width: 176px; flex: 0 0 176px; min-height: 0; padding: 16px 12px; overflow: auto; overscroll-behavior: contain; scrollbar-gutter: stable; border-right: 1px solid var(--flow-border); background: var(--flow-surface); }.flow-palette h2 { font-size: 12px; margin: 0 0 4px; color: var(--flow-text); }.flow-palette p { font-size: 10px; color: var(--flow-muted); margin: 0 0 14px; }.flow-palette button { display: flex; flex-direction: column; align-items: center; justify-content: center; width: 100%; gap: 6px; min-height: 62px; padding: 9px 6px; margin-bottom: 7px; border: 1px solid var(--flow-border); border-radius: 7px; background: var(--flow-surface); color: var(--flow-muted); cursor: grab; font-size: 11px; transition: border-color .15s ease, color .15s ease, background .15s ease, box-shadow .15s ease; }.flow-palette button:hover { color: var(--flow-accent); border-color: #8bbcff; background: var(--flow-accent-soft); box-shadow: 0 2px 8px rgba(22,119,255,.12); }.flow-palette button:focus-visible { outline: 2px solid var(--flow-accent); outline-offset: 2px; }.flow-palette small { display: block; font-size: 10px; line-height: 1.8; color: var(--flow-muted); padding-top: 7px; }.flow-palette .flow-palette__add { flex-direction: row; min-height: 36px; font-size: 10px; cursor: pointer; }
.flow-properties-enter-active, .flow-properties-leave-active { overflow: hidden; transition: opacity .18s ease, transform .18s ease, width .22s ease, flex-basis .22s ease, padding .22s ease, border-color .22s ease; }
.flow-properties-enter-from, .flow-properties-leave-to { width: 0 !important; flex-basis: 0 !important; padding-right: 0 !important; padding-left: 0 !important; border-left-color: transparent !important; opacity: 0; transform: translateX(12px); }
.flow-export { position: relative; }.flow-export__menu { position: absolute; top: calc(100% + 6px); right: 0; width: 160px; padding: 5px; z-index: 10; border: 1px solid var(--flow-border); border-radius: 7px; box-shadow: 0 8px 22px rgba(32,55,88,.16); background: var(--flow-surface); }.flow-export__menu button { width: 100%; padding: 9px; display: flex; gap: 6px; border: 0; background: transparent; color: var(--flow-text); text-align: left; font-size: 12px; cursor: pointer; border-radius: 4px; }.flow-export__menu button:hover { background: var(--flow-accent-soft); }
.flow-notice { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; padding: 10px 18px; font-size: 12px; color: var(--flow-text); background: var(--flow-surface-alt); border-bottom: 1px solid var(--flow-border); }.warning { color: var(--danger) !important; }.flow-notice span { flex: 1; }.flow-notice small { display: block; margin-top: 5px; }.flow-notice button,.flow-drafts button { background: var(--flow-surface); border: 1px solid var(--flow-border); border-radius: 5px; padding: 6px 9px; color: var(--flow-text); cursor: pointer; font: inherit; }
.flow-drafts { padding: 12px 18px; font-size: 12px; background: var(--flow-accent-soft); }.flow-drafts strong { display: flex; gap: 6px; align-items: center; color: var(--flow-accent); }.flow-drafts>div { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-top: 9px; }.flow-drafts span { flex: 1; min-width: 160px; color: var(--flow-text); }.flow-loading { flex: 1; min-height: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 14px; color: var(--flow-muted); }
.flow-template-list { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }.flow-template-list button { padding: 28px 12px; display: flex; flex-direction: column; align-items: center; gap: 16px; background: var(--panel); border: 1px solid var(--border); border-radius: 12px; color: var(--accent); cursor: pointer; }.flow-template-list strong { color: var(--text); font-size: 14px; }.flow-template-list span { color: var(--muted); font-size: 11px; }.flow-history { display: flex; gap: 18px; }.flow-history__list { width: 220px; flex: 0 0 220px; max-height: 470px; overflow: auto; }.flow-history__list>button { display: flex; flex-direction: column; gap: 6px; width: 100%; padding: 10px; margin-bottom: 6px; border: 1px solid var(--border); border-radius: 6px; background: var(--panel); color: var(--text); text-align: left; cursor: pointer; }.flow-history__list>button.active { border-color: var(--accent); }.flow-history__list small { font-size: 10px; color: var(--muted); }.flow-history__preview { flex: 1; min-width: 0; }.flow-history__preview>.button { margin-top: 14px; }.flow-history__pages { display: flex; gap: 8px; align-items: center; justify-content: space-between; font-size: 11px; }.flow-history__pages button { padding: 6px; background: var(--panel); color: var(--text); border: 1px solid var(--border); border-radius: 4px; }
@media (max-width: 1100px) { .flow-header { flex-wrap: nowrap; }.flow-header__actions { margin-left: auto; }.flow-header__actions .button { padding-inline: 7px; }.flow-palette { width: 124px; flex-basis: 124px; padding: 14px 10px; }.flow-palette button { min-height: 54px; } }
@media (max-width: 767px) { .flow-header { padding: 12px; gap: 8px; }.flow-header__actions { width: 100%; margin-top: 4px; justify-content: flex-end; }.flow-header__actions .button { min-height: 38px; }.flow-body { height: auto; min-height: 440px; }.flow-meta { gap: 10px; padding: 10px 14px; }.flow-header__icon { width: 30px; height: 30px; }.flow-template-list { grid-template-columns: 1fr; }.flow-history { flex-direction: column; }.flow-history__list { width: 100%; flex-basis: auto; max-height: 200px; }.flow-history__preview { width: 100%; }.flow-notice { align-items: flex-start; } }
.flow-palette__heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; margin-bottom: 12px; }
.flow-palette__heading h2 { margin-bottom: 3px; }
.flow-palette__heading > svg { color: var(--flow-muted); margin-top: 2px; }
.flow-palette__search { display: flex; align-items: center; gap: 7px; height: 32px; margin-bottom: 10px; padding: 0 9px; border: 1px solid var(--flow-border); border-radius: 6px; background: var(--flow-surface-alt); color: var(--flow-muted); }
.flow-palette__search:focus-within { border-color: var(--flow-accent); box-shadow: 0 0 0 2px color-mix(in srgb, var(--flow-accent) 14%, transparent); }
.flow-palette__search input { width: 100%; min-width: 0; border: 0; outline: 0; background: transparent; color: var(--flow-text); font: inherit; font-size: 11px; }
.flow-palette__tabs { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 2px; margin-bottom: 12px; padding: 2px; border-radius: 6px; background: var(--flow-surface-alt); }
.flow-palette__tabs button { min-height: 28px; margin: 0; padding: 4px 2px; border: 0; border-radius: 4px; background: transparent; color: var(--flow-muted); font-size: 10px; cursor: pointer; }
.flow-palette__tabs button.active { background: var(--flow-surface); color: var(--flow-accent); box-shadow: 0 1px 3px color-mix(in srgb, var(--flow-text) 10%, transparent); }
.flow-palette__grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 6px; }
.flow-palette__grid button { min-height: 72px; margin: 0; padding: 7px 3px; gap: 5px; border-radius: 6px; font-size: 10px; }
.flow-palette__grid button svg { flex: none; }
.flow-palette__empty { margin: 24px 0; color: var(--flow-muted); font-size: 11px; text-align: center; }
@media (max-width: 1100px) { .flow-palette__tabs { grid-template-columns: repeat(3, minmax(0, 1fr)); }.flow-palette__grid { grid-template-columns: 1fr; } }
.flow-history__preview { min-height: 360px; }
.flow-history__preview :deep(.flow-canvas) { min-height: 360px; height: 360px; }
:global(.writing-mode) .flow-workspace { border: 0; border-radius: 0; box-shadow: none; }
@media (prefers-reduced-motion: reduce) {
  .flow-properties-enter-active, .flow-properties-leave-active { transition-duration: 0.01ms; }
}
.flow-header__actions .button span { white-space: nowrap; }
.flow-header__actions { overflow: visible; }
@media (min-width: 768px) and (max-width: 900px) {
  .flow-header { flex-wrap: wrap; }
  .flow-header__title { flex-basis: 180px; }
  .flow-header__actions { width: 100%; justify-content: flex-end; flex-wrap: wrap; }
}
</style>
