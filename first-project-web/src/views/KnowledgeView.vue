<script setup lang="ts">
import { computed, nextTick, onActivated, onDeactivated, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, BookOpen, Braces, Check, CheckSquare, ChevronDown, Clipboard, Code2, Download, Edit3, FileText, Filter, Folder, Heart, LoaderCircle, MoreHorizontal, Pin, Plus, RotateCcw, Save, Sparkles, Square, Trash2, Upload } from 'lucide-vue-next'
import AppModal from '../components/AppModal.vue'
import CodeEditor from '../components/CodeEditor.vue'
import EmptyState from '../components/EmptyState.vue'
import JsonTree from '../components/JsonTree.vue'
import JsonSnippetEditor from '../components/JsonSnippetEditor.vue'
import KnowledgeDirectories from '../components/KnowledgeDirectories.vue'
import LanguageSelect from '../components/LanguageSelect.vue'
import MarkdownWorkspace from '../components/MarkdownWorkspace.vue'
import WorkspaceModuleState from '../components/WorkspaceModuleState.vue'
import { useWorkspaceStore } from '../stores/workspace'
import { useSearchStore } from '../stores/search'
import { useNotificationStore } from '../stores/notifications'
import { useConfirmationStore } from '../stores/confirmation'
import { CODE_LANGUAGES, getLanguage, languageBadgeStyle, languageHue, normalizeLanguageLabel } from '../utils/codeLanguages'
import { formatCode } from '../utils/codeFormat'
import { highlightCode } from '../utils/codeHighlight'
import { parseJsonSource } from '../utils/codeJson'
import { buildUnifiedKnowledgeItems, countKnowledgeInDomain, filterUnifiedKnowledgeItems } from '../utils/knowledgeItems'
import type { KnowledgeItemType, KnowledgeTypeFilter, UnifiedKnowledgeItem } from '../utils/knowledgeItems'
import { handleSaveShortcut } from '../utils/markdownEditing'
import type { CodeSnippet, DevLogEntry, DomainDraft, KnowledgeBulkMoveItem, KnowledgeDomain, LogCategory, LogDraft, MarkdownDocument, SnippetDraft } from '../types'

const ALL_DOMAINS = 'ALL'
const UNASSIGNED = 'UNASSIGNED'
const PAGE_SIZE_OPTIONS = [12, 24, 48] as const
type KnowledgeItemView = 'list' | 'preview' | 'edit'

interface MarkdownWorkspaceHandle {
  openCreate: () => Promise<void>
  openDocument: (document: MarkdownDocument, view?: 'preview' | 'edit') => Promise<void>
  canLeaveEditor: () => Promise<boolean>
  toggleFavorite: (document: MarkdownDocument) => Promise<void>
  requestDelete: (document: MarkdownDocument) => void
  openTrash: () => void
  triggerImport: () => void
  importFiles: (files: File[]) => Promise<void>
  exportDocuments: (ids?: string[]) => Promise<void>
}

const route = useRoute()
const router = useRouter()
const workspace = useWorkspaceStore()
const notifications = useNotificationStore()
const confirmation = useConfirmationStore()
const searchState = useSearchStore()
const emit = defineEmits<{ 'editing-change': [editing: boolean] }>()
const markdownWorkspace = ref<MarkdownWorkspaceHandle | null>(null)
const documentEditing = ref(false)
const search = computed(() => searchState.pageQuery)
const typeFilter = ref<KnowledgeTypeFilter>('all')
const favoriteOnly = ref(false)
const categoryFilter = ref<'ALL' | LogCategory>('ALL')
const selectedDomain = ref(ALL_DOMAINS)
const currentPage = ref(1)
const pageSize = ref<number>(24)
const itemView = ref<KnowledgeItemView>('list')
const activeItemType = ref<Exclude<KnowledgeItemType, 'documents'> | null>(null)
const activeSnippetId = ref('')
const activeLogId = ref('')
const showCreateMenu = ref(false)
const showDomainManager = ref(false)
const editingSnippet = ref<CodeSnippet | null>(null)
const editingLog = ref<DevLogEntry | null>(null)
const editingDomain = ref<KnowledgeDomain | null>(null)
const copiedId = ref('')
const tagInput = ref('')
const selectedKnowledgeKeys = ref(new Set<string>())
const bulkDomainId = ref('')
const showTypeMenu = ref(false)
const showBulkDomainMenu = ref(false)
const rowActionMenu = ref<{ item: UnifiedKnowledgeItem; x: number; y: number } | null>(null)
const snippetDraft = reactive<SnippetDraft>({ title: '', language: '', code: '', favorite: false, domainId: null })
const logDraft = reactive<LogDraft>({ title: '', content: '', category: 'LEARNING', tags: [], pinned: false, domainId: null })
const domainDraft = reactive<DomainDraft>({ name: '', description: '', sortOrder: 0 })

// 目录删除（带数据处置选项）
const showDomainDelete = ref(false)
const domainDeleteTarget = ref<KnowledgeDomain | null>(null)
const domainDeleteMode = ref<'unassigned' | 'move' | 'deleteAll'>('unassigned')
const domainMoveTarget = ref('')
const domainDeleteBusy = ref(false)

// 统一回收站（文章 / 代码片段 / 开发日志）
const showTrash = ref(false)
const trashTab = ref<'documents' | 'snippets' | 'logs'>('snippets')
const documentTrash = ref<MarkdownDocument[]>([])
const trashLoading = ref(false)
const trashBusy = ref(false)

const categoryMeta: Record<LogCategory, { label: string; color: string }> = {
  PROBLEM: { label: '问题解决', color: 'rose' },
  DECISION: { label: '技术决策', color: 'violet' },
  LEARNING: { label: '学习记录', color: 'blue' },
  IDEA: { label: '灵感想法', color: 'amber' },
}

const typeMeta: Record<KnowledgeItemType, { label: string; shortLabel: string }> = {
  documents: { label: '文章', shortLabel: '文章' },
  snippets: { label: '代码片段', shortLabel: '代码' },
  logs: { label: '开发日志', shortLabel: '日志' },
}

const allKnowledgeItems = computed(() => buildUnifiedKnowledgeItems(
  workspace.markdownDocuments,
  workspace.snippets,
  workspace.logs,
))
const filteredKnowledgeItems = computed(() => filterUnifiedKnowledgeItems(allKnowledgeItems.value, {
  type: typeFilter.value,
  domain: selectedDomain.value,
  allDomains: ALL_DOMAINS,
  unassigned: UNASSIGNED,
  query: search.value,
  featuredOnly: favoriteOnly.value,
  logCategory: categoryFilter.value,
}))
const pageItems = computed(() => filteredKnowledgeItems.value.slice((currentPage.value - 1) * pageSize.value, currentPage.value * pageSize.value))
const totalKnowledgeItems = computed(() => allKnowledgeItems.value.length)
const directoryCounts = computed(() => Object.fromEntries([ALL_DOMAINS, ...workspace.domains.map(domain => domain.id), UNASSIGNED].map(id => [id, domainCount(id)])))
const totalPages = computed(() => Math.max(1, Math.ceil(filteredKnowledgeItems.value.length / pageSize.value)))
const pageNumbers = computed(() => {
  const start = Math.max(1, Math.min(currentPage.value - 2, totalPages.value - 4))
  return Array.from({ length: Math.min(5, totalPages.value) }, (_, index) => start + index)
})
const activeSnippet = computed(() => workspace.snippets.find((item) => item.id === activeSnippetId.value) ?? null)
const activeLog = computed(() => workspace.logs.find((item) => item.id === activeLogId.value) ?? null)
const isWriting = computed(() => documentEditing.value || itemView.value !== 'list')
const selectedDomainName = computed(() => {
  if (selectedDomain.value === ALL_DOMAINS) return '全部知识'
  if (selectedDomain.value === UNASSIGNED) return '未分类'
  return workspace.domains.find((item) => item.id === selectedDomain.value)?.name ?? '全部知识'
})
const contextLabel = computed(() => {
  return selectedDomainName.value
})
const languageOptions = CODE_LANGUAGES.map((item) => ({ id: item.id, label: item.label }))
const highlightedSnippet = computed(() => activeSnippet.value ? highlightCode(activeSnippet.value.code, activeSnippet.value.language) : '')
const selectedKnowledgeItems = computed(() => allKnowledgeItems.value.filter((item) => selectedKnowledgeKeys.value.has(knowledgeItemKey(item))))
const selectedDocuments = computed(() => selectedKnowledgeItems.value.filter((item) => item.type === 'documents'))
const allPageItemsSelected = computed(() => pageItems.value.length > 0
  && pageItems.value.every((item) => selectedKnowledgeKeys.value.has(knowledgeItemKey(item))))
const typeFilterOptions = computed<Array<{ value: KnowledgeTypeFilter, label: string, count: number }>>(() => [
  { value: 'all', label: '全部', count: typeCount('all') },
  { value: 'documents', label: '文章', count: typeCount('documents') },
  { value: 'snippets', label: '代码片段', count: typeCount('snippets') },
  { value: 'logs', label: '开发日志', count: typeCount('logs') },
])
const selectedTypeOption = computed(() => typeFilterOptions.value.find((item) => item.value === typeFilter.value) ?? typeFilterOptions.value[0]!)
const bulkDomainLabel = computed(() => {
  if (!bulkDomainId.value) return '选择目标目录'
  if (bulkDomainId.value === UNASSIGNED) return '未分类'
  return workspace.domains.find((item) => item.id === bulkDomainId.value)?.name ?? '选择目标目录'
})

watch(isWriting, (editing) => emit('editing-change', editing), { immediate: true })

function domainName(id: string | null) {
  if (!id) return '未分类'
  return workspace.domains.find((item) => item.id === id)?.name ?? '已删除目录'
}

function domainCount(id: string) {
  return countKnowledgeInDomain(allKnowledgeItems.value, id, ALL_DOMAINS, UNASSIGNED)
}

function typeCount(type: KnowledgeTypeFilter) {
  return type === 'all' ? totalKnowledgeItems.value : allKnowledgeItems.value.filter((item) => item.type === type).length
}

function selectType(next: KnowledgeTypeFilter) {
  typeFilter.value = next
  categoryFilter.value = 'ALL'
  void router.replace({ query: next === 'all' ? {} : { type: next } })
}

function selectTypeOption(value: KnowledgeTypeFilter) {
  selectType(value)
  showTypeMenu.value = false
}

function selectBulkDomain(value: string) {
  bulkDomainId.value = value
  showBulkDomainMenu.value = false
}

function onTypeMenuFocusout(event: FocusEvent) {
  const container = event.currentTarget as HTMLElement
  const next = event.relatedTarget
  if (!(next instanceof Node) || !container.contains(next)) showTypeMenu.value = false
}

function onBulkDomainMenuFocusout(event: FocusEvent) {
  const container = event.currentTarget as HTMLElement
  const next = event.relatedTarget
  if (!(next instanceof Node) || !container.contains(next)) showBulkDomainMenu.value = false
}

function openCreate(type: KnowledgeItemType) {
  showCreateMenu.value = false
  if (type === 'documents') void markdownWorkspace.value?.openCreate()
  else if (type === 'snippets') openSnippetEditor()
  else openLogEditor()
}

function onCreateMenuFocusout(event: FocusEvent) {
  const container = event.currentTarget as HTMLElement
  const next = event.relatedTarget
  if (!(next instanceof Node) || !container.contains(next)) showCreateMenu.value = false
}

function openSnippetEditor(snippet?: CodeSnippet) {
  activeItemType.value = 'snippets'
  editingSnippet.value = snippet ?? null
  Object.assign(snippetDraft, snippet ? {
    title: snippet.title, language: normalizeLanguageLabel(snippet.language), code: snippet.code,
    favorite: snippet.favorite, domainId: snippet.domainId,
  } : {
    title: '', language: '', code: '', favorite: false,
    domainId: selectedDomain.value === ALL_DOMAINS || selectedDomain.value === UNASSIGNED ? null : selectedDomain.value,
  })
  activeSnippetId.value = snippet?.id ?? ''
  activeLogId.value = ''
  itemView.value = 'edit'
}

function openLogEditor(entry?: DevLogEntry) {
  activeItemType.value = 'logs'
  editingLog.value = entry ?? null
  Object.assign(logDraft, entry ? {
    title: entry.title, content: entry.content, category: entry.category,
    tags: [...entry.tags], pinned: entry.pinned, domainId: entry.domainId,
  } : {
    title: '', content: '', category: 'LEARNING', tags: [], pinned: false,
    domainId: selectedDomain.value === ALL_DOMAINS || selectedDomain.value === UNASSIGNED ? null : selectedDomain.value,
  })
  tagInput.value = logDraft.tags.join(', ')
  activeLogId.value = entry?.id ?? ''
  activeSnippetId.value = ''
  itemView.value = 'edit'
}

function routeQueryWithFocus(id: string) {
  return typeFilter.value === 'all' ? { focus: id } : { type: typeFilter.value, focus: id }
}

function openSnippetPreview(snippet: CodeSnippet, syncRoute = true) {
  activeItemType.value = 'snippets'
  activeSnippetId.value = snippet.id
  activeLogId.value = ''
  editingSnippet.value = null
  itemView.value = 'preview'
  if (syncRoute) void router.replace({ query: routeQueryWithFocus(snippet.id) })
}

function openLogPreview(entry: DevLogEntry, syncRoute = true) {
  activeItemType.value = 'logs'
  activeLogId.value = entry.id
  activeSnippetId.value = ''
  editingLog.value = null
  itemView.value = 'preview'
  if (syncRoute) void router.replace({ query: routeQueryWithFocus(entry.id) })
}

async function openKnowledgeItem(item: UnifiedKnowledgeItem) {
  if (item.type === 'documents') await markdownWorkspace.value?.openDocument(item.source)
  else if (item.type === 'snippets') openSnippetPreview(item.source)
  else openLogPreview(item.source)
}

function openDomainManager(domain?: KnowledgeDomain) {
  showDomainManager.value = true
  startDomainEdit(domain)
}

function startDomainEdit(domain?: KnowledgeDomain) {
  editingDomain.value = domain ?? null
  Object.assign(domainDraft, domain ? {
    name: domain.name, description: domain.description, sortOrder: domain.sortOrder,
  } : {
    name: '', description: '',
    sortOrder: workspace.domains.length ? Math.max(...workspace.domains.map((item) => item.sortOrder)) + 1 : 0,
  })
}

function closeItemWorkspace(syncRoute = true) {
  itemView.value = 'list'
  activeItemType.value = null
  activeSnippetId.value = ''
  activeLogId.value = ''
  editingSnippet.value = null
  editingLog.value = null
  if (syncRoute && (route.query.create || route.query.focus)) {
    void router.replace({ query: typeFilter.value === 'all' ? {} : { type: typeFilter.value } })
  }
}

const shortcutSaving = ref(false)
const snippetSaving = ref(false)

function snippetUnchanged() {
  const current = editingSnippet.value
  return Boolean(current
    && current.title === snippetDraft.title.trim()
    && current.language === snippetDraft.language.trim()
    && current.code === snippetDraft.code
    && current.favorite === snippetDraft.favorite
    && current.domainId === snippetDraft.domainId)
}

async function saveSnippet(stayOpen = false) {
  if (snippetSaving.value) return
  if (!snippetDraft.code.trim()) { notifications.notify('请输入代码内容。', { type: 'warning' }); return }
  if (snippetUnchanged()) {
    if (stayOpen) notifications.notify('没有新的修改，无需重复保存。', { type: 'info' })
    else closeItemWorkspace()
    return
  }
  snippetSaving.value = true
  try {
    const saved = await workspace.saveSnippet({ ...snippetDraft }, editingSnippet.value?.id)
    editingSnippet.value = saved
    activeSnippetId.value = saved.id
    if (stayOpen) notifications.notify('片段已保存，可继续编辑。', { type: 'success' })
    else closeItemWorkspace()
  } finally {
    snippetSaving.value = false
  }
}

async function saveLog(stayOpen = false) {
  logDraft.tags = tagInput.value.split(/[,，\s]+/).map((item) => item.replace(/^#/, '').trim()).filter((item, index, list) => item && list.indexOf(item) === index)
  await workspace.saveLog({ ...logDraft, tags: [...logDraft.tags] }, editingLog.value?.id)
  if (stayOpen) notifications.notify('日志已保存，可继续编辑。', { type: 'success' })
  else closeItemWorkspace()
}

function onSnippetSubmit() {
  const stayOpen = shortcutSaving.value
  shortcutSaving.value = false
  void saveSnippet(stayOpen)
}

function onLogSubmit() {
  const stayOpen = shortcutSaving.value
  shortcutSaving.value = false
  void saveLog(stayOpen)
}

function onItemEditorSaveKeydown(event: KeyboardEvent) {
  handleSaveShortcut(event, itemView.value === 'edit', () => {
    if (workspace.mutating || snippetSaving.value) return
    shortcutSaving.value = true
    ;(event.currentTarget as HTMLFormElement).requestSubmit()
    shortcutSaving.value = false
  })
}

function formatSnippetCode() {
  const language = normalizeLanguageLabel(snippetDraft.language)
  snippetDraft.code = formatCode(snippetDraft.code, language)
  notifications.notify(`已按 ${language} 语法格式化代码`, { type: 'success' })
}

function isJsonLanguage(language: string | null | undefined) {
  return getLanguage(language).id === 'json'
}

const isJsonDraft = computed(() => isJsonLanguage(snippetDraft.language))
const isJsonPreview = computed(() => activeSnippet.value !== null && isJsonLanguage(activeSnippet.value!.language))
const jsonPreviewValid = computed(() => {
  if (!activeSnippet.value || !isJsonLanguage(activeSnippet.value.language)) return false
  return parseJsonSource(activeSnippet.value.code).valid
})

async function saveDomain() {
  await workspace.saveDomain({ ...domainDraft }, editingDomain.value?.id)
  startDomainEdit()
}

const domainDeleteAffected = computed(() => domainDeleteTarget.value ? domainCount(domainDeleteTarget.value.id) : 0)
const domainDeleteName = computed(() => domainDeleteTarget.value?.name ?? '')

function requestRemoveDomain(domain: KnowledgeDomain) {
  domainDeleteTarget.value = domain
  domainDeleteMode.value = 'unassigned'
  domainMoveTarget.value = ''
  showDomainDelete.value = true
}

async function confirmRemoveDomain() {
  const domain = domainDeleteTarget.value
  if (!domain || domainDeleteBusy.value) return
  const affected = domainCount(domain.id)
  const items: KnowledgeBulkMoveItem[] = allKnowledgeItems.value
    .filter((item) => item.domainId === domain.id)
    .map((item) => ({
      type: item.type === 'documents' ? 'DOCUMENT' as const : item.type === 'snippets' ? 'SNIPPET' as const : 'LOG' as const,
      id: item.id,
      ...(item.type === 'documents' ? { expectedVersion: item.source.version! } : {}),
    }))

  if (affected > 0 && domainDeleteMode.value !== 'deleteAll'
    && items.some((item) => item.type === 'DOCUMENT' && item.expectedVersion === undefined)) {
    notifications.notify('部分文章版本信息不完整，请刷新页面后重试。', { type: 'warning' })
    return
  }
  if (domainDeleteMode.value === 'move' && !domainMoveTarget.value) {
    notifications.notify('请先选择要把数据移动到的目录。', { type: 'warning' })
    return
  }

  domainDeleteBusy.value = true
  try {
    if (affected > 0 && domainDeleteMode.value !== 'deleteAll') {
      const target = domainDeleteMode.value === 'move' ? domainMoveTarget.value : null
      await workspace.moveKnowledgeItemsToDomain(items, target)
      notifications.notify(`已把 ${items.length} 条知识${target ? '' : '移入未分类'}。`, { type: 'success' })
    } else if (affected > 0) {
      // 目录内全部数据分别移入各自回收站（文章、片段、日志都可恢复）
      for (const item of items) {
        if (item.type === 'DOCUMENT') await workspace.deleteMarkdownDocument(item.id)
        else if (item.type === 'SNIPPET') await workspace.deleteSnippet(item.id)
        else await workspace.deleteLog(item.id)
      }
      notifications.notify(`已把目录中的 ${items.length} 条内容移入回收站。`, { type: 'success' })
    }
    await workspace.deleteDomain(domain.id)
    if (selectedDomain.value === domain.id) selectedDomain.value = ALL_DOMAINS
    if (editingDomain.value?.id === domain.id) startDomainEdit()
    notifications.notify(`目录“${domain.name}”已删除。`, { type: 'success' })
    showDomainDelete.value = false
    domainDeleteTarget.value = null
  } catch {
    // The global notification host reports API failures; keep the dialog open for retry.
  } finally {
    domainDeleteBusy.value = false
  }
}

async function openTrash() {
  showTrash.value = true
  trashTab.value = 'snippets'
  await reloadTrash()
}

async function reloadTrash() {
  if (trashLoading.value) return
  trashLoading.value = true
  try {
    const [articles] = await Promise.all([
      workspace.loadMarkdownTrash(),
      workspace.loadItemTrash(),
    ])
    documentTrash.value = articles
  } catch {
    // The global notification host reports API failures.
  } finally {
    trashLoading.value = false
  }
}

async function restoreTrashItem(kind: 'documents' | 'snippets' | 'logs', id: string, title: string) {
  if (trashBusy.value) return
  trashBusy.value = true
  try {
    if (kind === 'documents') {
      await workspace.restoreMarkdownDocument(id)
      documentTrash.value = documentTrash.value.filter((item) => item.id !== id)
    } else if (kind === 'snippets') {
      await workspace.restoreSnippetFromTrash(id)
    } else {
      await workspace.restoreLogFromTrash(id)
    }
    notifications.notify(`“${title}”已恢复到列表。`, { type: 'success' })
  } catch {
    // The global notification host reports API failures.
  } finally {
    trashBusy.value = false
  }
}

async function purgeTrashItem(kind: 'documents' | 'snippets' | 'logs', id: string, title: string) {
  if (trashBusy.value) return
  const noun = kind === 'documents' ? '文章' : kind === 'snippets' ? '代码片段' : '开发日志'
  if (!await confirmation.ask({
    title: `彻底删除${noun}？`, message: `“${title}”将被永久删除，无法恢复。`,
    detail: '建议先确认已保留需要的内容。', confirmText: '彻底删除', tone: 'danger', icon: 'delete',
  })) return
  trashBusy.value = true
  try {
    if (kind === 'documents') {
      await workspace.purgeMarkdownDocument(id)
      documentTrash.value = documentTrash.value.filter((item) => item.id !== id)
    } else if (kind === 'snippets') {
      await workspace.purgeSnippetFromTrash(id)
    } else {
      await workspace.purgeLogFromTrash(id)
    }
    notifications.notify('已彻底删除。', { type: 'success' })
  } catch {
    // The global notification host reports API failures.
  } finally {
    trashBusy.value = false
  }
}

async function removeDomain(domain: KnowledgeDomain) {
  requestRemoveDomain(domain)
}

async function toggleFavorite(snippet: CodeSnippet) {
  await workspace.saveSnippet({ title: snippet.title, language: snippet.language, code: snippet.code, favorite: !snippet.favorite, domainId: snippet.domainId }, snippet.id)
}

async function togglePinned(entry: DevLogEntry) {
  await workspace.saveLog({ title: entry.title, content: entry.content, category: entry.category, tags: [...entry.tags], pinned: !entry.pinned, domainId: entry.domainId }, entry.id)
}

async function toggleKnowledgeFeatured(item: UnifiedKnowledgeItem) {
  try {
    if (item.type === 'documents') await markdownWorkspace.value?.toggleFavorite(item.source)
    else if (item.type === 'snippets') await toggleFavorite(item.source)
    else await togglePinned(item.source)
    const isLog = item.type === 'logs'
    notifications.notify(
      item.featured
        ? (isLog ? '已取消置顶。' : '已取消收藏。')
        : (isLog ? '已置顶并移到列表前方。' : '已收藏并移到列表前方。'),
      { type: 'success' },
    )
  } finally {
    rowActionMenu.value = null
  }
}

async function writeClipboardText(value: string) {
  if (window.isSecureContext && navigator.clipboard?.writeText) {
    try { await navigator.clipboard.writeText(value); return }
    catch { /* Fall back for browsers that expose but reject the Clipboard API. */ }
  }
  const textarea = document.createElement('textarea')
  const activeElement = document.activeElement instanceof HTMLElement ? document.activeElement : null
  textarea.value = value
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.inset = '0 auto auto -9999px'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.focus()
  textarea.select()
  textarea.setSelectionRange(0, value.length)
  try { if (!document.execCommand('copy')) throw new Error('Browser rejected the copy command') }
  finally { textarea.remove(); activeElement?.focus({ preventScroll: true }) }
}

async function copyCode(snippet: CodeSnippet) {
  try {
    await writeClipboardText(snippet.code)
    copiedId.value = snippet.id
    window.setTimeout(() => copiedId.value = '', 1500)
  } catch {
    openSnippetEditor(snippet)
    notifications.notify('自动复制失败，已打开代码编辑窗口，请手动选中复制。', { type: 'warning' })
  }
}

async function removeSnippet(snippet: CodeSnippet) {
  if (!await confirmation.ask({
    title: '删除这个代码片段？', message: `“${snippet.title}”将移入回收站。`, detail: '可在回收站恢复；彻底删除前内容不会丢失。',
    confirmText: '移入回收站', tone: 'danger', icon: 'delete',
  })) return
  try { await workspace.deleteSnippet(snippet.id) }
  catch { /* The global notification host reports API failures. */ }
}

async function removeLog(entry: DevLogEntry) {
  if (!await confirmation.ask({
    title: '删除这篇开发日志？', message: `“${entry.title || '未命名日志'}”将移入回收站。`, detail: '可在回收站恢复；彻底删除前内容不会丢失。',
    confirmText: '移入回收站', tone: 'danger', icon: 'delete',
  })) return
  try { await workspace.deleteLog(entry.id) }
  catch { /* The global notification host reports API failures. */ }
}

function removeKnowledgeItem(item: UnifiedKnowledgeItem) {
  rowActionMenu.value = null
  if (item.type === 'documents') markdownWorkspace.value?.requestDelete(item.source)
  else if (item.type === 'snippets') void removeSnippet(item.source)
  else void removeLog(item.source)
}

function editKnowledgeItem(item: UnifiedKnowledgeItem) {
  rowActionMenu.value = null
  if (item.type === 'documents') void markdownWorkspace.value?.openDocument(item.source, 'edit')
  else if (item.type === 'snippets') openSnippetEditor(item.source)
  else openLogEditor(item.source)
}

function openRowActionMenu(event: MouseEvent, item: UnifiedKnowledgeItem) {
  const menuWidth = 210
  const menuHeight = item.type === 'snippets' ? 210 : 170
  rowActionMenu.value = {
    item,
    x: Math.max(12, Math.min(event.clientX, window.innerWidth - menuWidth - 12)),
    y: Math.max(12, Math.min(event.clientY, window.innerHeight - menuHeight - 12)),
  }
}

function openRowActionMenuFromButton(event: MouseEvent, item: UnifiedKnowledgeItem) {
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  openRowActionMenu(new MouseEvent('contextmenu', { clientX: rect.right - 210, clientY: rect.bottom + 5 }), item)
}

function knowledgeItemKey(item: Pick<UnifiedKnowledgeItem, 'type' | 'id'>) {
  return `${item.type}:${item.id}`
}

function toggleKnowledgeSelection(item: UnifiedKnowledgeItem) {
  showTypeMenu.value = false
  showBulkDomainMenu.value = false
  const key = knowledgeItemKey(item)
  const next = new Set(selectedKnowledgeKeys.value)
  next.has(key) ? next.delete(key) : next.add(key)
  selectedKnowledgeKeys.value = next
}

function toggleAllPageItems() {
  showTypeMenu.value = false
  showBulkDomainMenu.value = false
  const next = new Set(selectedKnowledgeKeys.value)
  if (allPageItemsSelected.value) pageItems.value.forEach((item) => next.delete(knowledgeItemKey(item)))
  else pageItems.value.forEach((item) => next.add(knowledgeItemKey(item)))
  selectedKnowledgeKeys.value = next
}

function clearKnowledgeSelection() {
  selectedKnowledgeKeys.value = new Set()
  bulkDomainId.value = ''
  showBulkDomainMenu.value = false
}

async function moveSelectedKnowledge() {
  if (!bulkDomainId.value) {
    notifications.notify('请先选择目标目录。', { type: 'warning' })
    return
  }
  if (selectedDocuments.value.some((item) => item.source.version === undefined)) {
    notifications.notify('文章版本信息不完整，请刷新页面后重试。', { type: 'warning' })
    return
  }
  const items: KnowledgeBulkMoveItem[] = selectedKnowledgeItems.value.map((item) => ({
    type: item.type === 'documents' ? 'DOCUMENT' : item.type === 'snippets' ? 'SNIPPET' : 'LOG',
    id: item.id,
    ...(item.type === 'documents' ? { expectedVersion: item.source.version! } : {}),
  }))
  try {
    await workspace.moveKnowledgeItemsToDomain(items, bulkDomainId.value === UNASSIGNED ? null : bulkDomainId.value)
    notifications.notify(`已移动 ${items.length} 条知识。`, { type: 'success' })
    clearKnowledgeSelection()
  } catch {
    // The global notification host reports API failures; keep the selection so the user can retry.
  }
}

async function deleteSelectedKnowledge() {
  const items = [...selectedKnowledgeItems.value]
  if (!items.length) return
  const counts = {
    documents: items.filter((item) => item.type === 'documents').length,
    snippets: items.filter((item) => item.type === 'snippets').length,
    logs: items.filter((item) => item.type === 'logs').length,
  }
  const detail = [counts.documents && `${counts.documents} 篇文章`, counts.snippets && `${counts.snippets} 个代码片段`, counts.logs && `${counts.logs} 篇日志`].filter(Boolean).join('、')
  if (!await confirmation.ask({
    title: `删除选中的 ${items.length} 条知识？`,
    message: `${detail}将统一移入回收站。`,
    detail: '这些内容仍可从回收站恢复。', confirmText: '批量移入回收站', tone: 'danger', icon: 'delete',
  })) return
  let deleted = 0
  for (const item of items) {
    try {
      if (item.type === 'documents') await workspace.deleteMarkdownDocument(item.id)
      else if (item.type === 'snippets') await workspace.deleteSnippet(item.id)
      else await workspace.deleteLog(item.id)
      const next = new Set(selectedKnowledgeKeys.value)
      next.delete(knowledgeItemKey(item))
      selectedKnowledgeKeys.value = next
      deleted++
    } catch {
      // Keep failed items selected so the user can retry without rebuilding the selection.
    }
  }
  if (deleted === items.length) {
    notifications.notify(`已将 ${deleted} 条知识移入回收站。`, { type: 'success' })
    clearKnowledgeSelection()
  } else if (deleted) {
    notifications.notify(`已删除 ${deleted} 条，另有 ${items.length - deleted} 条未完成并保持选中。`, { type: 'warning' })
  }
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(value))
}

function formatTrashTime(value: string) {
  return new Date(value).toLocaleString('zh-CN', {
    year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  })
}

function onKnowledgeDrop(event: DragEvent) {
  if (event.dataTransfer?.files.length) void markdownWorkspace.value?.importFiles([...event.dataTransfer.files])
}

async function locateKnowledgeItem(id: string) {
  const snippet = workspace.snippets.find((item) => item.id === id)
  const entry = workspace.logs.find((item) => item.id === id)
  const markdownDocument = workspace.markdownDocuments.find((item) => item.id === id)
  if (!snippet && !entry && !markdownDocument) return
  favoriteOnly.value = false
  categoryFilter.value = 'ALL'
  searchState.clear()
  await nextTick()
  if (markdownDocument) await markdownWorkspace.value?.openDocument(markdownDocument)
  else if (snippet) openSnippetPreview(snippet, false)
  else if (entry) openLogPreview(entry, false)
}

watch([search, favoriteOnly, categoryFilter, selectedDomain, typeFilter, pageSize], () => currentPage.value = 1)
watch(totalPages, (value) => { if (currentPage.value > value) currentPage.value = value })
watch(() => route.query, async (query) => {
  const requestedType = query.type ?? query.tab
  typeFilter.value = requestedType === 'documents' || requestedType === 'snippets' || requestedType === 'logs' ? requestedType : 'all'
  if (query.create === '1') {
    const createType = query.tab === 'snippets' || query.tab === 'logs' ? query.tab : 'documents'
    await nextTick(() => openCreate(createType))
  } else if (typeof query.focus === 'string') await locateKnowledgeItem(query.focus)
  else if (itemView.value !== 'list') closeItemWorkspace(false)
}, { immediate: true })
watch([() => workspace.snippets.length, () => workspace.logs.length, () => workspace.markdownDocuments.length], () => {
  if (typeof route.query.focus === 'string') void locateKnowledgeItem(route.query.focus)
})
onActivated(() => {
  emit('editing-change', isWriting.value)
  if (typeof route.query.focus === 'string') void locateKnowledgeItem(route.query.focus)
})
onDeactivated(() => emit('editing-change', false))
</script>

<template>
  <div class="page-content knowledge-page" :class="{ 'writing-mode': isWriting }">
    <div class="knowledge-workspace">
      <section id="knowledge-panel" class="knowledge-results">
      <Transition name="reveal">
      <header v-if="!isWriting" class="knowledge-header">
        <div class="knowledge-overview">
          <div class="knowledge-overview__copy">
            <span class="knowledge-overview__icon"><BookOpen :size="20" /></span>
            <div><strong>我的知识库</strong><span>{{ totalKnowledgeItems }} 条内容 · {{ workspace.domains.length }} 个目录</span></div>
          </div>
          <div class="create-knowledge" @focusout="onCreateMenuFocusout" @keydown.esc="showCreateMenu = false">
            <button class="button button-primary create-knowledge__button" type="button" aria-haspopup="menu" :aria-expanded="showCreateMenu" @click="showCreateMenu = !showCreateMenu"><Plus :size="18" />新增知识<ChevronDown :size="15" /></button>
            <Transition name="reveal">
              <div v-if="showCreateMenu" class="create-menu" role="menu">
                <button type="button" role="menuitem" @click="openCreate('documents')"><span class="create-menu__icon document"><FileText :size="18" /></span><span><strong>写文章</strong><small>适合完整笔记与长内容</small></span></button>
                <button type="button" role="menuitem" @click="openCreate('snippets')"><span class="create-menu__icon snippet"><Code2 :size="18" /></span><span><strong>存代码片段</strong><small>记录可复用的代码</small></span></button>
                <button type="button" role="menuitem" @click="openCreate('logs')"><span class="create-menu__icon log"><BookOpen :size="18" /></span><span><strong>记开发日志</strong><small>保留问题、决策与灵感</small></span></button>
              </div>
            </Transition>
          </div>
        </div>
        <KnowledgeDirectories v-model="selectedDomain" :domains="workspace.domains" :counts="directoryCounts" @create="openDomainManager()" @edit="openDomainManager" @remove="requestRemoveDomain" />
        <WorkspaceModuleState class="domain-module-state" module="domains" title="知识目录" :has-data="Boolean(workspace.domains.length)" compact />
      </header>
      </Transition>
      <div class="knowledge-content" :class="{ 'document-writing': documentEditing }" @dragover.prevent @drop.prevent="onKnowledgeDrop">
        <MarkdownWorkspace ref="markdownWorkspace" class="markdown-host" :selected-domain="selectedDomain" :selected-domain-name="selectedDomainName" headless-list @editing-change="documentEditing = $event" />
        <Transition name="content-swap" mode="out-in">
        <div v-if="!documentEditing" :key="itemView" class="knowledge-results__inner">
          <section v-if="itemView === 'preview' && activeItemType === 'snippets' && activeSnippet" class="knowledge-reader" aria-label="代码片段预览">
            <header class="knowledge-reader__bar">
              <button class="detail-back" type="button" aria-label="返回代码片段列表" @click="closeItemWorkspace()"><ArrowLeft :size="18" /><span>返回片段</span></button>
              <div class="detail-actions">
                <button class="detail-toolbar-button" type="button" :aria-label="copiedId === activeSnippet.id ? '代码已复制' : '复制代码'" @click="copyCode(activeSnippet)"><Check v-if="copiedId === activeSnippet.id" :size="16" /><Clipboard v-else :size="16" /><span>{{ copiedId === activeSnippet.id ? '已复制' : '复制代码' }}</span></button>
                <button class="detail-toolbar-button" type="button" :aria-label="activeSnippet.favorite ? '取消收藏片段' : '收藏片段'" @click="toggleFavorite(activeSnippet)"><Heart :size="16" :fill="activeSnippet.favorite ? 'currentColor' : 'none'" /><span>{{ activeSnippet.favorite ? '取消收藏' : '收藏' }}</span></button>
                <button class="detail-toolbar-button" type="button" aria-label="编辑片段" @click="openSnippetEditor(activeSnippet)"><Edit3 :size="16" /><span>编辑片段</span></button>
              </div>
            </header>
            <article class="knowledge-reader__paper">
              <header class="knowledge-reader__headline">
                <span><Code2 :size="16" />代码片段</span>
                <h1>{{ activeSnippet.title }}</h1>
                <div><span class="domain-badge"><Folder :size="13" />{{ domainName(activeSnippet.domainId) }}</span><span class="language-badge" :style="languageBadgeStyle(activeSnippet.language)">{{ activeSnippet.language }}</span><time>更新于 {{ formatDate(activeSnippet.updatedAt) }}</time></div>
              </header>
              <JsonTree v-if="isJsonPreview && jsonPreviewValid" :source="activeSnippet.code" />
              <pre v-else class="snippet-reader-code"><code v-html="highlightedSnippet"></code></pre>
            </article>
          </section>

          <section v-else-if="itemView === 'preview' && activeItemType === 'logs' && activeLog" class="knowledge-reader" aria-label="开发日志预览">
            <header class="knowledge-reader__bar">
              <button class="detail-back" type="button" aria-label="返回开发日志列表" @click="closeItemWorkspace()"><ArrowLeft :size="18" /><span>返回日志</span></button>
              <div class="detail-actions">
                <button class="detail-toolbar-button" type="button" :aria-label="activeLog.pinned ? '取消置顶日志' : '置顶日志'" @click="togglePinned(activeLog)"><Pin :size="16" /><span>{{ activeLog.pinned ? '取消置顶' : '置顶' }}</span></button>
                <button class="detail-toolbar-button" type="button" aria-label="编辑日志" @click="openLogEditor(activeLog)"><Edit3 :size="16" /><span>编辑日志</span></button>
              </div>
            </header>
            <article class="knowledge-reader__paper">
              <header class="knowledge-reader__headline">
                <span><BookOpen :size="16" />{{ categoryMeta[activeLog.category].label }}</span>
                <h1>{{ activeLog.title || activeLog.content.slice(0, 34) }}</h1>
                <div><span class="domain-badge"><Folder :size="13" />{{ domainName(activeLog.domainId) }}</span><span v-if="activeLog.pinned" class="pin-label"><Pin :size="12" />置顶</span><time>更新于 {{ formatDate(activeLog.updatedAt) }}</time></div>
                <div v-if="activeLog.tags.length" class="reader-tags"><span v-for="tag in activeLog.tags" :key="tag">#{{ tag }}</span></div>
              </header>
              <div class="log-reader-content">{{ activeLog.content }}</div>
            </article>
          </section>

          <form v-else-if="itemView === 'edit' && activeItemType === 'snippets'" class="knowledge-inline-editor" aria-label="代码片段编辑器" aria-keyshortcuts="Control+S Meta+S" @keydown.capture="onItemEditorSaveKeydown" @submit.prevent="onSnippetSubmit">
            <header class="inline-editor-header">
              <button class="detail-back" type="button" aria-label="返回代码片段列表" @click="closeItemWorkspace()"><ArrowLeft :size="18" /><span>返回片段</span></button>
              <div class="inline-editor-title"><input v-model.trim="snippetDraft.title" autofocus required maxlength="200" aria-label="片段标题" placeholder="输入片段标题" /><span><i></i>{{ editingSnippet ? '编辑代码片段' : '新建代码片段' }}</span></div>
              <button class="button button-primary inline-save-button" type="submit" :aria-label="editingSnippet ? '保存代码片段修改' : '保存代码片段'" :disabled="workspace.mutating || snippetSaving"><LoaderCircle v-if="snippetSaving" class="spin" :size="16" /><Save v-else :size="16" /><span>{{ snippetSaving ? '正在保存' : editingSnippet ? '保存修改' : '保存片段' }}</span></button>
            </header>
            <div class="inline-editor-meta">
              <label><span>语言</span><LanguageSelect v-model="snippetDraft.language" :options="languageOptions" placeholder="选择或输入语言" required /></label>
              <label><span>目录</span><select v-model="snippetDraft.domainId"><option :value="null">未分类</option><option v-for="domain in workspace.domains" :key="domain.id" :value="domain.id">{{ domain.name }}</option></select></label>
              <label class="inline-toggle"><input v-model="snippetDraft.favorite" type="checkbox" /><Heart :size="15" :fill="snippetDraft.favorite ? 'currentColor' : 'none'" /><span>收藏片段</span></label>
            </div>
            <div class="inline-editor-body code-body">
              <JsonSnippetEditor v-if="isJsonDraft" v-model="snippetDraft.code" />
              <template v-else><div class="code-body__bar"><span><Code2 :size="15" />代码内容</span><button class="format-code-button" type="button" :disabled="workspace.mutating" @click="formatSnippetCode"><Sparkles :size="14" />格式化</button></div><CodeEditor v-model="snippetDraft.code" :language="snippetDraft.language" placeholder="粘贴完整代码…" required /></template>
            </div>
          </form>

          <form v-else-if="itemView === 'edit' && activeItemType === 'logs'" class="knowledge-inline-editor" aria-label="开发日志编辑器" aria-keyshortcuts="Control+S Meta+S" @keydown.capture="onItemEditorSaveKeydown" @submit.prevent="onLogSubmit">
            <header class="inline-editor-header">
              <button class="detail-back" type="button" aria-label="返回开发日志列表" @click="closeItemWorkspace()"><ArrowLeft :size="18" /><span>返回日志</span></button>
              <div class="inline-editor-title"><input v-model.trim="logDraft.title" autofocus maxlength="240" aria-label="日志标题" placeholder="输入日志标题（可选）" /><span><i></i>{{ editingLog ? '编辑开发日志' : '新建开发日志' }}</span></div>
              <button class="button button-primary inline-save-button" type="submit" :aria-label="editingLog ? '保存开发日志修改' : '保存开发日志'" :disabled="workspace.mutating"><Save :size="16" /><span>{{ editingLog ? '保存修改' : '保存日志' }}</span></button>
            </header>
            <div class="inline-editor-meta log-editor-meta">
              <label><span>类型</span><select v-model="logDraft.category"><option v-for="(meta, value) in categoryMeta" :key="value" :value="value">{{ meta.label }}</option></select></label>
              <label><span>目录</span><select v-model="logDraft.domainId"><option :value="null">未分类</option><option v-for="domain in workspace.domains" :key="domain.id" :value="domain.id">{{ domain.name }}</option></select></label>
              <label class="tag-editor-field"><span>标签</span><input v-model="tagInput" maxlength="500" placeholder="Flutter, 架构, 性能" /></label>
              <label class="inline-toggle"><input v-model="logDraft.pinned" type="checkbox" /><Pin :size="15" /><span>置顶日志</span></label>
            </div>
            <label class="inline-editor-body log-body"><span><BookOpen :size="15" />日志正文</span><textarea v-model="logDraft.content" required maxlength="200000" aria-label="日志正文" placeholder="问题背景、排查过程、方案取舍和最终结果…"></textarea></label>
          </form>

          <div v-else class="knowledge-list-view">
            <div class="module-state-stack">
              <WorkspaceModuleState module="markdownDocuments" title="文章" :has-data="Boolean(workspace.markdownDocuments.length)" compact />
              <WorkspaceModuleState module="snippets" title="代码片段" :has-data="Boolean(workspace.snippets.length)" compact />
              <WorkspaceModuleState module="logs" title="开发日志" :has-data="Boolean(workspace.logs.length)" compact />
            </div>
            <div class="knowledge-toolbar" :class="{ 'selection-mode': selectedKnowledgeKeys.size }">
              <template v-if="selectedKnowledgeKeys.size">
                <div class="selection-context">
                  <span class="selection-context__icon"><CheckSquare :size="18" /></span>
                  <span><strong>已选择 {{ selectedKnowledgeKeys.size }} 条</strong><small>批量整理知识内容</small></span>
                </div>
                <div class="selection-actions">
                  <button v-if="selectedDocuments.length" class="selection-export-button" type="button" :disabled="workspace.mutating" @click="markdownWorkspace?.exportDocuments(selectedDocuments.map((item) => item.id))"><Download :size="15" />导出 {{ selectedDocuments.length }} 篇文章</button>
                  <span class="selection-actions__divider" aria-hidden="true"></span>
                  <span class="selection-actions__label">移动到</span>
                  <div class="styled-select bulk-domain-select" @focusout="onBulkDomainMenuFocusout" @keydown.esc.stop="showBulkDomainMenu = false">
                    <button class="styled-select__trigger" :class="{ open: showBulkDomainMenu, placeholder: !bulkDomainId }" type="button" aria-haspopup="listbox" :aria-expanded="showBulkDomainMenu" aria-label="选择目标目录" @click="showBulkDomainMenu = !showBulkDomainMenu">
                      <span class="styled-select__leading domain"><Folder :size="15" /></span><strong>{{ bulkDomainLabel }}</strong><ChevronDown :size="15" :class="{ rotated: showBulkDomainMenu }" />
                    </button>
                    <Transition name="select-menu"><div v-if="showBulkDomainMenu" class="styled-select__menu domain-menu" role="listbox" aria-label="目标目录">
                      <button type="button" role="option" :aria-selected="bulkDomainId === UNASSIGNED" :class="{ selected: bulkDomainId === UNASSIGNED }" @click="selectBulkDomain(UNASSIGNED)">
                        <span class="styled-select__leading unassigned"><Folder :size="15" /></span><span class="styled-select__option-copy"><strong>未分类</strong><small>{{ domainCount(UNASSIGNED) }} 条知识</small></span><Check v-if="bulkDomainId === UNASSIGNED" :size="15" />
                      </button>
                      <button v-for="domain in workspace.domains" :key="domain.id" type="button" role="option" :aria-selected="bulkDomainId === domain.id" :class="{ selected: bulkDomainId === domain.id }" @click="selectBulkDomain(domain.id)">
                        <span class="styled-select__leading domain"><Folder :size="15" /></span><span class="styled-select__option-copy"><strong>{{ domain.name }}</strong><small>{{ domainCount(domain.id) }} 条知识</small></span><Check v-if="bulkDomainId === domain.id" :size="15" />
                      </button>
                    </div></Transition>
                  </div>
                  <button class="selection-move-button" type="button" :disabled="workspace.mutating || !bulkDomainId" @click="moveSelectedKnowledge"><Folder :size="15" />移动</button>
                  <button class="selection-delete-button" type="button" :disabled="workspace.mutating" @click="deleteSelectedKnowledge"><Trash2 :size="15" />批量删除</button>
                  <button class="selection-cancel-button" type="button" @click="clearKnowledgeSelection">取消选择</button>
                </div>
              </template>
              <template v-else>
                <span class="list-context"><strong>{{ contextLabel }}</strong><small>{{ search ? `搜索“${search}” · ` : '' }}显示 {{ filteredKnowledgeItems.length }} 条</small><button v-if="search" type="button" @click="searchState.clear()">清除搜索</button></span>
                <div class="list-display-controls">
                  <div class="styled-select type-select" @focusout="onTypeMenuFocusout" @keydown.esc.stop="showTypeMenu = false">
                    <button class="styled-select__trigger" :class="{ open: showTypeMenu }" type="button" aria-haspopup="listbox" :aria-expanded="showTypeMenu" aria-label="内容类型" @click="showTypeMenu = !showTypeMenu">
                      <span class="styled-select__leading filter"><Filter :size="15" /></span><strong>{{ selectedTypeOption.label }}</strong><small>{{ selectedTypeOption.count }}</small><ChevronDown :size="15" :class="{ rotated: showTypeMenu }" />
                    </button>
                    <Transition name="select-menu"><div v-if="showTypeMenu" class="styled-select__menu type-menu" role="listbox" aria-label="内容类型">
                      <button v-for="option in typeFilterOptions" :key="option.value" type="button" role="option" :aria-selected="typeFilter === option.value" :class="{ selected: typeFilter === option.value }" @click="selectTypeOption(option.value)">
                        <span class="styled-select__leading" :class="option.value"><Filter v-if="option.value === 'all'" :size="15" /><FileText v-else-if="option.value === 'documents'" :size="15" /><Code2 v-else-if="option.value === 'snippets'" :size="15" /><BookOpen v-else :size="15" /></span><span class="styled-select__option-copy"><strong>{{ option.label }}</strong><small>{{ option.count }} 条知识</small></span><Check v-if="typeFilter === option.value" :size="15" />
                      </button>
                    </div></Transition>
                  </div>
                  <button class="filter-button" :class="{ active: favoriteOnly }" :aria-pressed="favoriteOnly" type="button" @click="favoriteOnly = !favoriteOnly"><Heart :size="16" :fill="favoriteOnly ? 'currentColor' : 'none'" />收藏 / 置顶</button>
                  <div v-if="typeFilter === 'logs'" class="category-filters"><button type="button" :class="{ active: categoryFilter === 'ALL' }" @click="categoryFilter = 'ALL'">全部</button><button v-for="(meta, value) in categoryMeta" :key="value" type="button" :class="{ active: categoryFilter === value }" @click="categoryFilter = value">{{ meta.label }}</button></div>
                  <button class="utility-button" type="button" @click="openTrash()"><Trash2 :size="15" />回收站</button>
                  <button class="utility-button" type="button" @click="markdownWorkspace?.triggerImport()"><Upload :size="15" />导入文章</button>
                  <button class="utility-button" type="button" :disabled="!workspace.markdownDocuments.length || workspace.mutating" @click="markdownWorkspace?.exportDocuments([])"><Download :size="15" />导出文章</button>
                  <label class="page-size-control"><span>每页</span><select v-model.number="pageSize" aria-label="每页显示数量"><option v-for="size in PAGE_SIZE_OPTIONS" :key="size" :value="size">{{ size }} 条</option></select></label>
                </div>
              </template>
            </div>

            <TransitionGroup v-if="pageItems.length" name="list" tag="div" class="unified-table">
              <div key="knowledge-head" class="unified-table__head">
                <button type="button" :disabled="!pageItems.length" :aria-label="allPageItemsSelected ? '取消选择当前页全部知识' : '选择当前页全部知识'" @click="toggleAllPageItems"><CheckSquare v-if="allPageItemsSelected" :size="17" /><Square v-else :size="17" /></button>
                <span>知识内容</span><span>类型</span><span>目录</span><span>最近更新</span><span aria-hidden="true"></span>
              </div>
              <article v-for="item in pageItems" :id="`knowledge-item-${item.id}`" :key="`${item.type}-${item.id}`" class="unified-row" :class="[`type-${item.type}`, { selected: selectedKnowledgeKeys.has(knowledgeItemKey(item)) }]" @contextmenu.prevent="openRowActionMenu($event, item)">
                <button class="select-button" type="button" :aria-label="selectedKnowledgeKeys.has(knowledgeItemKey(item)) ? `取消选择${typeMeta[item.type].label}：${item.title}` : `选择${typeMeta[item.type].label}：${item.title}`" @click="toggleKnowledgeSelection(item)"><CheckSquare v-if="selectedKnowledgeKeys.has(knowledgeItemKey(item))" :size="18" /><Square v-else :size="18" /></button>
                <button class="unified-summary" type="button" :aria-label="`查看${typeMeta[item.type].label}：${item.title}`" @click="openKnowledgeItem(item)">
                  <span class="unified-summary__title"><span class="item-kind-icon" :class="item.type"><FileText v-if="item.type === 'documents'" :size="16" /><Code2 v-else-if="item.type === 'snippets'" :size="16" /><BookOpen v-else :size="16" /></span><strong>{{ item.title }}</strong><Pin v-if="item.featured && item.type === 'logs'" :size="13" fill="currentColor" /><Heart v-else-if="item.featured" :size="13" fill="currentColor" /></span>
                  <code v-if="item.type === 'snippets'" v-html="highlightCode(item.summary, item.source.language)"></code><small v-else>{{ item.summary }}</small>
                  <span v-if="item.type !== 'snippets'" class="unified-summary__detail">{{ item.type === 'logs' ? categoryMeta[item.source.category].label : item.detail }}</span>
                </button>
                <span class="type-cell">
                  <span class="knowledge-kind" :class="item.type" :style="item.type === 'snippets' ? { '--language-hue': languageHue(item.source.language) } : undefined">
                    <span class="knowledge-kind__icon"><Braces v-if="item.type === 'snippets' && isJsonLanguage(item.source.language)" :size="17" /><Code2 v-else-if="item.type === 'snippets'" :size="17" /><FileText v-else-if="item.type === 'documents'" :size="17" /><BookOpen v-else :size="17" /></span>
                    <span class="knowledge-kind__copy"><strong>{{ typeMeta[item.type].label }}</strong><small>{{ item.type === 'snippets' ? item.source.language : item.type === 'documents' ? 'Markdown' : categoryMeta[item.source.category].label }}</small></span>
                  </span>
                </span>
                <span class="domain-badge"><Folder :size="13" />{{ domainName(item.domainId) }}</span>
                <time>{{ formatDate(item.updatedAt) }}</time>
                <button class="row-more-button" type="button" :aria-label="`打开${item.title}的操作菜单`" aria-haspopup="menu" @click.stop="openRowActionMenuFromButton($event, item)"><MoreHorizontal :size="18" /></button>
              </article>
            </TransitionGroup>
            <EmptyState v-else-if="!workspace.loading" :title="totalKnowledgeItems ? '没有匹配的知识' : '开始建立你的个人知识库'" :description="totalKnowledgeItems ? '试试切换目录、内容类型，或清除搜索与收藏筛选。' : '文章、代码片段和开发日志会出现在同一个列表中，需要时再按类型筛选。'">
              <div class="empty-actions"><button class="button button-primary" type="button" @click="openCreate('documents')"><FileText :size="16" />写文章</button><button class="button button-secondary" type="button" @click="openCreate('snippets')"><Code2 :size="16" />存代码</button></div>
            </EmptyState>
            <p v-if="pageItems.length" class="drop-hint"><Upload :size="14" />可拖入 Markdown 文件或 ZIP；在当前目录导入，位于“全部知识”时归入未分类</p>
            <nav v-if="filteredKnowledgeItems.length > pageSize" class="pagination" aria-label="分页"><span>第 {{ currentPage }} / {{ totalPages }} 页 · 共 {{ filteredKnowledgeItems.length }} 条</span><div><button type="button" :disabled="currentPage === 1" aria-label="上一页" @click="currentPage--"><ArrowLeft :size="16" /></button><button v-for="page in pageNumbers" :key="page" type="button" :class="{ active: currentPage === page }" @click="currentPage = page">{{ page }}</button><button type="button" :disabled="currentPage === totalPages" aria-label="下一页" @click="currentPage++"><ArrowRight :size="16" /></button></div></nav>
          </div>
        </div>
        </Transition>
      </div>
      </section>
    </div>

    <Teleport to="body">
      <div v-if="rowActionMenu" class="row-menu-layer" role="presentation" @pointerdown.self="rowActionMenu = null" @contextmenu.prevent.self="rowActionMenu = null">
        <div class="row-action-menu" role="menu" :style="{ left: `${rowActionMenu.x}px`, top: `${rowActionMenu.y}px` }" @keydown.esc.stop="rowActionMenu = null">
          <header><span :class="['item-kind-icon', rowActionMenu.item.type]"><FileText v-if="rowActionMenu.item.type === 'documents'" :size="15" /><Code2 v-else-if="rowActionMenu.item.type === 'snippets'" :size="15" /><BookOpen v-else :size="15" /></span><span><strong>{{ rowActionMenu.item.title }}</strong><small>{{ typeMeta[rowActionMenu.item.type].label }}</small></span></header>
          <button type="button" role="menuitem" @click="toggleKnowledgeFeatured(rowActionMenu.item)">
            <Pin v-if="rowActionMenu.item.type === 'logs'" :size="16" :fill="rowActionMenu.item.featured ? 'currentColor' : 'none'" />
            <Heart v-else :size="16" :fill="rowActionMenu.item.featured ? 'currentColor' : 'none'" />
            {{ rowActionMenu.item.type === 'logs' ? (rowActionMenu.item.featured ? '取消置顶' : '置顶到列表前方') : (rowActionMenu.item.featured ? '取消收藏' : '收藏并移到列表前方') }}
          </button>
          <button v-if="rowActionMenu.item.type === 'snippets'" type="button" role="menuitem" @click="copyCode(rowActionMenu.item.source); rowActionMenu = null"><Clipboard :size="16" />复制代码</button>
          <button type="button" role="menuitem" @click="editKnowledgeItem(rowActionMenu.item)"><Edit3 :size="16" />编辑{{ typeMeta[rowActionMenu.item.type].label }}</button>
          <span class="row-action-menu__divider"></span>
          <button class="danger" type="button" role="menuitem" @click="removeKnowledgeItem(rowActionMenu.item)"><Trash2 :size="16" />移入回收站</button>
        </div>
      </div>
    </Teleport>

    <AppModal v-if="showDomainManager" title="知识目录" description="所有知识类型共用这套目录。目录只表达主题，文章、代码和日志通过类型区分。" wide @close="showDomainManager = false">
      <div class="domain-manager">
        <div class="domain-manager__list"><div class="manager-title"><span>已有目录</span><button type="button" @click="startDomainEdit()"><Plus :size="15" />新建</button></div><button v-for="domain in workspace.domains" :key="domain.id" type="button" :class="{ active: editingDomain?.id === domain.id }" @click="startDomainEdit(domain)"><span><strong>{{ domain.name }}</strong><small>{{ domain.description || '暂无说明' }}</small></span><b>{{ domainCount(domain.id) }} 条</b></button><p v-if="!workspace.domains.length">还没有目录，从右侧创建第一个知识主题。</p></div>
        <form class="form-grid domain-form" @submit.prevent="saveDomain"><div class="manager-title"><span>{{ editingDomain ? '编辑目录' : '新建目录' }}</span><button v-if="editingDomain" class="danger-link" type="button" @click="removeDomain(editingDomain)"><Trash2 :size="14" />删除</button></div><label class="form-field"><span>目录名称</span><input v-model.trim="domainDraft.name" autofocus required maxlength="80" placeholder="例如：采购系统" /></label><label class="form-field"><span>说明（可选）</span><textarea v-model.trim="domainDraft.description" maxlength="240" rows="4" placeholder="例如：供应商、询比价、采购订单相关知识"></textarea></label><div class="form-actions"><button class="button button-ghost" type="button" @click="startDomainEdit()">清空</button><button class="button button-primary" type="submit" :disabled="workspace.mutating">{{ editingDomain ? '保存修改' : '创建目录' }}</button></div></form>
      </div>
    </AppModal>

    <AppModal v-if="showDomainDelete" title="删除知识目录" :description="`目录“${domainDeleteName}”删除后无法恢复，请先决定其中内容的去向。`" @close="!domainDeleteBusy && (showDomainDelete = false)">
      <div v-if="domainDeleteAffected > 0" class="domain-delete__options" role="radiogroup" aria-label="目录数据处置方式">
        <label class="domain-delete__option" :class="{ selected: domainDeleteMode === 'unassigned' }">
          <input v-model="domainDeleteMode" type="radio" value="unassigned" />
          <span><strong>移入“未分类”</strong><small>目录中的 {{ domainDeleteAffected }} 条内容全部保留，回到未分类（默认）</small></span>
        </label>
        <label class="domain-delete__option" :class="{ selected: domainDeleteMode === 'move' }">
          <input v-model="domainDeleteMode" type="radio" value="move" />
          <span><strong>移动到另一个目录</strong><small>选择目标目录后，{{ domainDeleteAffected }} 条内容会整体转移</small></span>
          <select v-if="domainDeleteMode === 'move'" v-model="domainMoveTarget" aria-label="选择目标目录" :disabled="domainDeleteBusy">
            <option value="" disabled>选择目标目录…</option>
            <option v-for="domain in workspace.domains.filter((item) => item.id !== domainDeleteTarget?.id)" :key="domain.id" :value="domain.id">{{ domain.name }}（{{ domainCount(domain.id) }} 条）</option>
          </select>
        </label>
        <label class="domain-delete__option danger" :class="{ selected: domainDeleteMode === 'deleteAll' }">
          <input v-model="domainDeleteMode" type="radio" value="deleteAll" />
          <span><strong>连同数据一起删除</strong><small>{{ domainDeleteAffected }} 条内容会移入回收站，需要时仍可从回收站恢复</small></span>
        </label>
      </div>
      <p v-else class="domain-delete__empty">这个目录是空的，删除后不会影响任何内容。</p>
      <div class="domain-delete__actions">
        <button class="button button-ghost" type="button" :disabled="domainDeleteBusy" @click="showDomainDelete = false">取消</button>
        <button class="button button-primary" type="button" :disabled="domainDeleteBusy || (domainDeleteMode === 'move' && !domainMoveTarget)" @click="confirmRemoveDomain"><LoaderCircle v-if="domainDeleteBusy" :size="15" class="spin" /><Trash2 v-else :size="15" />删除目录</button>
      </div>
    </AppModal>

    <AppModal v-if="showTrash" title="回收站" description="删除的文章、代码片段和开发日志会保留在这里，不会自动清空；恢复后回到原目录或未分类。" wide @close="showTrash = false">
      <div class="trash-tabs" role="tablist" aria-label="回收站类型">
        <button type="button" role="tab" :aria-selected="trashTab === 'documents'" :class="{ active: trashTab === 'documents' }" @click="trashTab = 'documents'"><FileText :size="15" />文章<span>{{ documentTrash.length }}</span></button>
        <button type="button" role="tab" :aria-selected="trashTab === 'snippets'" :class="{ active: trashTab === 'snippets' }" @click="trashTab = 'snippets'"><Code2 :size="15" />代码片段<span>{{ workspace.snippetTrash.length }}</span></button>
        <button type="button" role="tab" :aria-selected="trashTab === 'logs'" :class="{ active: trashTab === 'logs' }" @click="trashTab = 'logs'"><BookOpen :size="15" />开发日志<span>{{ workspace.logTrash.length }}</span></button>
      </div>
      <div class="trash-toolbar"><button class="button button-ghost" type="button" :disabled="trashLoading || trashBusy" @click="reloadTrash"><RotateCcw :size="14" />刷新列表</button></div>
      <p v-if="trashLoading || trashBusy" class="trash-loading" role="status"><LoaderCircle :size="16" class="spin" />处理中…</p>
      <template v-if="trashTab === 'documents'">
        <TransitionGroup name="list" tag="div" class="trash-list">
          <div v-for="item in documentTrash" :key="item.id" class="trash-row">
            <span class="trash-row__icon documents"><FileText :size="17" /></span>
            <div class="trash-row__copy"><strong>{{ item.title }}</strong><small>{{ item.fileName }}<template v-if="item.domainId"> · {{ domainName(item.domainId) }}</template><template v-else> · 未分类</template> · {{ formatTrashTime(item.deletedAt ?? item.updatedAt) }} 删除</small></div>
            <div class="trash-row__actions"><button type="button" class="button button-secondary" :disabled="trashBusy" @click="restoreTrashItem('documents', item.id, item.title)"><RotateCcw :size="14" />恢复</button><button type="button" class="button button-ghost danger-text" :disabled="trashBusy" @click="purgeTrashItem('documents', item.id, item.title)"><Trash2 :size="14" />彻底删除</button></div>
          </div>
        </TransitionGroup>
        <EmptyState v-if="!trashLoading && !documentTrash.length" title="没有已删除的文章" description="删除的文章会出现在这里，可以随时恢复。" />
      </template>
      <template v-else-if="trashTab === 'snippets'">
        <TransitionGroup name="list" tag="div" class="trash-list">
          <div v-for="item in workspace.snippetTrash" :key="item.id" class="trash-row">
            <span class="trash-row__icon snippets"><Code2 :size="17" /></span>
            <div class="trash-row__copy"><strong>{{ item.title }}</strong><small><span class="lang-badge" :style="languageBadgeStyle(item.language)">{{ item.language }}</span><template v-if="item.domainId"> · {{ domainName(item.domainId) }}</template><template v-else> · 未分类</template> · {{ formatTrashTime(item.deletedAt) }} 删除</small><em>{{ item.excerpt }}</em></div>
            <div class="trash-row__actions"><button type="button" class="button button-secondary" :disabled="trashBusy" @click="restoreTrashItem('snippets', item.id, item.title)"><RotateCcw :size="14" />恢复</button><button type="button" class="button button-ghost danger-text" :disabled="trashBusy" @click="purgeTrashItem('snippets', item.id, item.title)"><Trash2 :size="14" />彻底删除</button></div>
          </div>
        </TransitionGroup>
        <EmptyState v-if="!trashLoading && !workspace.snippetTrash.length" title="没有已删除的代码片段" description="删除的代码片段会出现在这里，可以随时恢复。" />
      </template>
      <template v-else>
        <TransitionGroup name="list" tag="div" class="trash-list">
          <div v-for="item in workspace.logTrash" :key="item.id" class="trash-row">
            <span class="trash-row__icon logs"><BookOpen :size="17" /></span>
            <div class="trash-row__copy"><strong>{{ item.title }}</strong><small><span class="category-badge" :class="categoryMeta[item.category].color">{{ categoryMeta[item.category].label }}</span><template v-if="item.domainId"> · {{ domainName(item.domainId) }}</template><template v-else> · 未分类</template> · {{ formatTrashTime(item.deletedAt) }} 删除</small><em>{{ item.excerpt }}</em></div>
            <div class="trash-row__actions"><button type="button" class="button button-secondary" :disabled="trashBusy" @click="restoreTrashItem('logs', item.id, item.title)"><RotateCcw :size="14" />恢复</button><button type="button" class="button button-ghost danger-text" :disabled="trashBusy" @click="purgeTrashItem('logs', item.id, item.title)"><Trash2 :size="14" />彻底删除</button></div>
          </div>
        </TransitionGroup>
        <EmptyState v-if="!trashLoading && !workspace.logTrash.length" title="没有已删除的开发日志" description="删除的开发日志会出现在这里，可以随时恢复。" />
      </template>
    </AppModal>

  </div>
</template>

<style scoped>
.markdown-module { min-width: 0; }
.knowledge-page { padding-top: 12px; padding-bottom: 16px; }
.knowledge-workspace { min-width: 0; }
.knowledge-results { min-width: 0; min-height: max(620px, calc(100svh - 100px)); overflow: hidden; border: 1px solid var(--border); border-radius: 16px; background: var(--panel); box-shadow: var(--shadow-sm); }
.knowledge-header { min-width: 0; display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: stretch; border-bottom: 1px solid var(--border); background: linear-gradient(105deg, color-mix(in srgb, var(--accent-bg) 20%, var(--panel)) 0%, var(--panel) 40%); }
.knowledge-header__body { min-width: 0; }
.knowledge-header__tabs { min-width: 0; padding: 10px 12px 8px; }
.knowledge-header__filter { min-width: 0; display: flex; align-items: center; gap: 10px; padding: 6px 12px 12px; border-top: 1px solid color-mix(in srgb, var(--border) 55%, transparent); }
.knowledge-header__actions { min-width: 0; display: flex; flex-direction: column; align-items: flex-end; justify-content: center; gap: 10px; padding: 12px 18px; border-left: 1px solid var(--border); }
.knowledge-tabs { width: max-content; display: flex; gap: 4px; overflow-x: auto; scrollbar-width: thin; }
.knowledge-tabs > button { display: flex; align-items: center; justify-content: center; gap: 7px; min-height: 38px; padding: 0 13px; color: var(--muted); border: 1px solid transparent; border-radius: 9px; background: transparent; cursor: pointer; white-space: nowrap; transition: color .16s, border-color .16s, background .16s, box-shadow .16s; }
.knowledge-tabs > button:hover { color: var(--text); background: color-mix(in srgb, var(--panel) 75%, transparent); }
.knowledge-tabs > button.active { color: var(--accent); border-color: var(--border); background: var(--panel); box-shadow: 0 1px 3px rgba(31,47,77,.07); }
.knowledge-tabs strong { font-size: var(--font-xs); font-weight: 650; }
.knowledge-tabs small { min-width: 21px; padding: 1px 6px; color: var(--muted); border-radius: 999px; background: var(--panel-strong); font-size: 10px; text-align: center; }
.knowledge-tabs .active small { color: var(--accent); background: color-mix(in srgb, var(--accent-bg) 58%, var(--panel)); }
.knowledge-filter-label { flex: 0 0 auto; display: flex; align-items: center; gap: 5px; color: var(--muted); font-size: 11px; font-weight: 650; letter-spacing: .04em; white-space: nowrap; }
.knowledge-filter-actions { flex: 0 0 auto; display: flex; align-items: center; gap: 3px; margin-left: auto; }
.knowledge-filter-actions button { width: 30px; height: 30px; display: grid; place-items: center; padding: 0; color: var(--muted); border: 1px solid transparent; border-radius: 8px; background: transparent; cursor: pointer; }
.knowledge-filter-actions button:hover { color: var(--accent); border-color: var(--accent-border); background: var(--accent-bg); }
.knowledge-header .domain-module-state { margin: 0 12px 12px; }
.knowledge-total { color: var(--muted); font-size: 11px; white-space: nowrap; }
.knowledge-header__actions .button { min-height: 40px; padding-inline: 14px; box-shadow: 0 5px 14px rgba(45,82,178,.16); white-space: nowrap; }
.domain-list { min-width: 0; display: flex; align-items: center; gap: 6px; overflow-x: auto; padding: 2px; scrollbar-width: thin; }
.domain-list > button { min-width: max-content; min-height: 32px; display: inline-flex; align-items: center; gap: 6px; flex: 0 0 auto; padding: 0 10px; color: var(--subtle); border: 1px solid var(--border); border-radius: 999px; background: var(--panel); cursor: pointer; transition: color .16s, border-color .16s, background .16s, box-shadow .16s; }
.domain-list > button:hover { color: var(--text); border-color: var(--accent-border); }
.domain-list > button.active { color: var(--accent); border-color: color-mix(in srgb, var(--accent-border) 78%, transparent); background: var(--accent-bg); }
.domain-icon { display: grid; place-items: center; color: var(--muted); }
.domain-list > button.active .domain-icon { color: var(--accent); }
.domain-copy { min-width: 0; }
.domain-copy strong { display: block; overflow: hidden; max-width: 150px; font-size: var(--font-2xs); font-weight: 560; text-overflow: ellipsis; white-space: nowrap; }
.domain-list b { color: var(--muted); font-size: 10px; font-weight: 550; }
.domain-list > button.active b { color: var(--accent); }
.knowledge-content { min-width: 0; padding: 18px clamp(16px, 1.5vw, 26px) 26px; }
.knowledge-results__inner { min-width: 0; }
.knowledge-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 14px; margin-bottom: 16px; padding: 10px 12px; border: 1px solid var(--border); border-radius: 11px; background: color-mix(in srgb, var(--surface-sunken) 62%, transparent); }
.filter-button { min-height: 36px; display: flex; align-items: center; gap: 7px; padding: 0 12px; color: var(--subtle); border: 1px solid var(--border); border-radius: 8px; background: var(--panel); cursor: pointer; font-size: 12px; transition: color var(--motion-fast), border-color var(--motion-fast), background var(--motion-fast), transform var(--motion-base) var(--ease-standard); }
.filter-button:hover { color: var(--text); transform: translateY(-1px); }
.filter-button.active { color: var(--accent); border-color: var(--accent-border); background: var(--accent-bg); }
.category-filters { display: flex; gap: 4px; }
.category-filters button { min-height: 36px; padding: 0 10px; color: var(--muted); border: 1px solid transparent; border-radius: 7px; background: transparent; cursor: pointer; font-size: 12px; white-space: nowrap; transition: color var(--motion-fast), background var(--motion-fast), transform var(--motion-base) var(--ease-standard); }
.category-filters button:hover { color: var(--text); background: var(--surface-raised); transform: translateY(-1px); }
.category-filters button.active { color: var(--accent); background: var(--accent-bg); }
.list-display-controls { display: flex; align-items: center; justify-content: flex-end; flex-wrap: wrap; gap: 8px 12px; }
.page-size-control { min-height: 36px; display: flex; align-items: center; gap: 7px; padding-left: 10px; color: var(--muted); border-left: 1px solid var(--border); font-size: 11px; white-space: nowrap; }
.page-size-control select { height: 34px; padding: 0 28px 0 9px; color: var(--text); border: 1px solid var(--border); border-radius: 8px; outline: 0; background: var(--panel); font-size: 11px; cursor: pointer; }
.page-size-control select:focus { border-color: var(--accent); box-shadow: 0 0 0 2px var(--accent-soft); }
.knowledge-page.writing-mode { max-width: none; min-height: 0; flex: 1; display: flex; padding: 0; }
.writing-mode .knowledge-header { display: none; }
.writing-mode .knowledge-workspace { flex: 1; min-height: 0; min-width: 0; display: flex; margin: 0; }
.writing-mode .knowledge-results { display: flex; flex: 1; flex-direction: column; min-height: 0; width: 100%; padding: 0; border: 0; border-radius: 0; box-shadow: none; }
.writing-mode .knowledge-content { min-height: 0; flex: 1; display: flex; flex-direction: column; overflow: hidden; padding: 0; }
.writing-mode .knowledge-results__inner { min-height: 0; flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.writing-mode .markdown-module { min-height: 0; flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.document-writing :deep(.markdown-workspace) { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.document-writing :deep(.markdown-editor) { display: flex; flex-direction: column; flex: 1; min-height: 0; border: 0; border-radius: 0; box-shadow: none; }
.writing-mode :deep(.editor-header), .writing-mode :deep(.editor-meta) { flex-shrink: 0; }
.writing-mode :deep(.editor-columns) { flex: 1; height: auto; min-height: 0; }
.writing-mode :deep(.editor-workarea) { flex: 1; min-height: 0; }
.document-writing :deep(.markdown-reader) { flex: 1; min-height: 0; overflow: auto; border: 0; border-radius: 0; }
.document-writing :deep(.markdown-reader__bar) { position: sticky; top: 0; z-index: 1; background: var(--panel); }
.knowledge-reader { min-height: 0; flex: 1; overflow: auto; background: var(--panel); overscroll-behavior: auto; scrollbar-gutter: stable; }
.knowledge-reader__bar { position: sticky; z-index: 2; top: 0; min-height: 54px; display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 8px 14px; border-bottom: 1px solid var(--border); background: color-mix(in srgb, var(--surface-sunken) 68%, var(--panel)); }
.detail-back { display: flex; align-items: center; gap: 6px; padding: 8px; color: var(--muted); border: 0; border-radius: 8px; background: transparent; cursor: pointer; font-size: var(--font-xs); white-space: nowrap; }
.detail-back:hover { color: var(--text); background: var(--surface-sunken); }
.detail-actions { display: flex; align-items: center; gap: 6px; }
.detail-toolbar-button { height: 36px; display: inline-flex; align-items: center; justify-content: center; gap: 6px; padding: 0 11px; color: var(--subtle); border: 1px solid var(--border); border-radius: 9px; background: var(--surface-sunken); cursor: pointer; font-size: 12px; white-space: nowrap; }
.detail-toolbar-button:hover { color: var(--accent); border-color: var(--accent-border); background: var(--accent-bg); }
.knowledge-reader__paper { width: 100%; min-height: calc(100svh - 108px); margin: 0; padding: 18px clamp(16px, 2.5vw, 36px) 36px; }
.knowledge-reader__headline { padding-bottom: 16px; border-bottom: 1px solid var(--border); }
.knowledge-reader__headline > span { display: flex; align-items: center; gap: 6px; color: var(--accent); font-size: var(--font-xs); font-weight: 700; }
.knowledge-reader__headline h1 { margin: 7px 0 10px; color: var(--text); font-size: clamp(24px, 2.2vw, 34px); line-height: 1.2; letter-spacing: -.025em; overflow-wrap: anywhere; }
.knowledge-reader__headline > div { display: flex; align-items: center; flex-wrap: wrap; gap: 8px 16px; color: var(--muted); font-size: var(--font-2xs); }
.reader-tags { margin-top: 9px; }
.reader-tags span { color: var(--accent); }
.snippet-reader-code { min-width: 0; min-height: calc(100svh - 250px); overflow: auto; margin: 16px 0 0; padding: clamp(18px, 2.2vw, 28px); border: 1px solid var(--border); border-radius: 12px; color: var(--code-text); background: var(--code-bg); font: var(--font-sm)/1.75 "Cascadia Code", Consolas, monospace; tab-size: 2; }
.snippet-reader-code code { font: inherit; white-space: pre; }
.log-reader-content { min-width: 0; min-height: calc(100svh - 250px); margin-top: 16px; padding: clamp(20px, 2.5vw, 34px); color: var(--text); border: 1px solid var(--border); border-radius: 12px; background: color-mix(in srgb, var(--surface-sunken) 42%, var(--panel)); font-size: 16px; line-height: 1.9; white-space: pre-wrap; overflow-wrap: anywhere; }
.knowledge-inline-editor { min-height: 0; flex: 1; display: flex; flex-direction: column; overflow: hidden; background: var(--panel); }
.inline-editor-header { min-height: 72px; display: grid; grid-template-columns: auto minmax(180px, 1fr) auto; align-items: center; gap: 16px; flex-shrink: 0; padding: 12px 15px; border-bottom: 1px solid var(--border); }
.inline-editor-title { min-width: 0; }
.inline-editor-title input { width: 100%; padding: 0; color: var(--text); border: 0; outline: 0; background: transparent; font-size: var(--font-lg); font-weight: 750; }
.inline-editor-title input::placeholder { color: var(--muted); }
.inline-editor-title > span { display: flex; align-items: center; gap: 6px; margin-top: 4px; color: var(--muted); font-size: var(--font-2xs); }
.inline-editor-title i { width: 6px; height: 6px; border-radius: 50%; background: var(--accent); }
.inline-save-button { min-width: 112px; }
.inline-editor-meta { min-height: 54px; display: flex; align-items: center; flex-wrap: wrap; gap: 9px 14px; flex-shrink: 0; padding: 8px 15px; border-bottom: 1px solid var(--border); background: var(--surface-sunken); }
.inline-editor-meta > label:not(.inline-toggle) { min-width: 0; display: flex; align-items: center; gap: 7px; color: var(--muted); font-size: var(--font-2xs); }
.inline-editor-meta input, .inline-editor-meta select { min-width: 0; height: 32px; padding: 0 9px; color: var(--text); border: 1px solid var(--border); border-radius: 7px; outline: 0; background: var(--panel); font-size: var(--font-xs); }
.inline-editor-meta select { max-width: 170px; }
.inline-editor-meta input:focus, .inline-editor-meta select:focus { border-color: var(--accent); box-shadow: 0 0 0 2px var(--accent-soft); }
.tag-editor-field { flex: 1; }
.tag-editor-field input { width: min(320px, 30vw); }
.inline-toggle { display: flex; align-items: center; gap: 6px; color: var(--muted); font-size: var(--font-xs); white-space: nowrap; }
.inline-toggle input { width: 14px; height: 14px; padding: 0; accent-color: var(--accent); }
.inline-toggle svg { color: var(--accent); }
.inline-editor-body { min-height: 0; flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.inline-editor-body > span { height: 44px; flex: 0 0 44px; display: flex; align-items: center; gap: 6px; padding: 0 16px; color: var(--muted); border-bottom: 1px solid var(--border); background: color-mix(in srgb, var(--surface-sunken) 62%, var(--panel)); font-size: var(--font-2xs); font-weight: 650; }
.inline-editor-body textarea { min-width: 0; min-height: 0; flex: 1; overflow: auto; padding: 24px; color: var(--text); border: 0; outline: 0; background: var(--panel); font: inherit; font-size: var(--font-sm); line-height: 1.8; resize: none; }
.code-body__bar { height: 44px; flex: 0 0 44px; display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 0 12px 0 16px; color: var(--muted); border-bottom: 1px solid var(--border); background: color-mix(in srgb, var(--surface-sunken) 62%, var(--panel)); font-size: var(--font-2xs); font-weight: 650; }
.code-body__bar > span { display: flex; align-items: center; gap: 6px; }
.format-code-button { height: 28px; display: inline-flex; align-items: center; gap: 6px; padding: 0 10px; color: var(--accent); border: 1px solid var(--accent-border); border-radius: 8px; background: var(--accent-bg); cursor: pointer; font-size: var(--font-2xs); font-weight: 600; white-space: nowrap; transition: background var(--motion-fast), border-color var(--motion-fast), transform var(--motion-base) var(--ease-standard); }
.format-code-button:hover:not(:disabled) { border-color: var(--accent); transform: translateY(-1px); }
.format-code-button.active { color: #fff; border-color: var(--accent); background: var(--accent); }
.format-code-button:disabled { opacity: .5; cursor: not-allowed; }
.inline-editor-body.log-body textarea { width: min(100%, 960px); margin: 0 auto; padding: clamp(28px, 4vw, 56px); font-size: 16px; line-height: 1.9; }
.snippet-table{overflow:hidden;border:1px solid var(--border);border-radius:15px;background:var(--panel);box-shadow:0 7px 24px rgba(0,0,0,.025)}.snippet-table__head,.snippet-row{display:grid;grid-template-columns:34px minmax(120px,1.7fr) minmax(86px,.7fr) 76px 110px 112px;align-items:center;gap:14px}.snippet-table__head{min-height:44px;padding:0 17px;color:var(--muted);border-bottom:1px solid var(--border);background:var(--surface-sunken);font-size:var(--font-2xs);font-weight:700}.snippet-row{min-height:86px;padding:13px 17px;border-bottom:1px solid var(--border);transition:background .15s ease}.snippet-row:last-child{border-bottom:0}.snippet-row:hover{background:color-mix(in srgb,var(--surface-sunken) 66%,transparent)}.heart-button{display:grid;width:34px;height:34px;place-items:center;color:var(--accent);border:0;border-radius:9px;background:transparent;cursor:pointer}.heart-button:hover{background:var(--accent-bg)}.snippet-summary{min-width:0}.snippet-summary h3{overflow:hidden;margin:0 0 7px;color:var(--text);font-size:var(--font-sm);text-overflow:ellipsis;white-space:nowrap}.snippet-summary code{display:block;overflow:hidden;color:var(--muted);background:none;font:var(--font-2xs)/1.4 "Cascadia Code",Consolas,monospace;text-overflow:ellipsis;white-space:nowrap}.domain-badge,.language-badge{width:fit-content;max-width:100%;display:inline-flex;align-items:center;gap:5px;overflow:hidden;padding:5px 8px;border-radius:999px;color:var(--accent);background:var(--accent-bg);font-size:var(--font-2xs);text-overflow:ellipsis;white-space:nowrap}.language-badge{color:var(--accent);background:var(--accent-soft);font-family:ui-monospace,monospace}.snippet-row time,.log-row>time{color:var(--muted);font-size:var(--font-2xs)}.row-actions{display:flex;align-items:center;justify-content:flex-end;gap:2px}.row-actions span{display:none}.row-actions button{display:flex;align-items:center;gap:5px;padding:7px;color:var(--muted);border:0;border-radius:8px;background:transparent;cursor:pointer;font-size:var(--font-2xs)}.row-actions button:hover{color:var(--text);background:var(--surface-sunken)}button.danger:hover,.danger-link:hover{color:var(--danger)!important}
.snippet-summary { width: 100%; padding: 0; color: inherit; text-align: left; border: 0; background: transparent; cursor: pointer; }
.snippet-summary:hover h3 { color: var(--accent); }
.log-list{overflow:hidden;border:1px solid var(--border);border-radius:15px;background:var(--panel)}.log-row{display:grid;grid-template-columns:minmax(0,1fr) 130px 116px;align-items:center;gap:22px;min-height:116px;padding:18px 20px;border-bottom:1px solid var(--border)}.log-row:last-child{border-bottom:0}.log-row.pinned{box-shadow:inset 3px 0 var(--accent)}.log-row__main{min-width:0}.log-meta{display:flex;align-items:center;flex-wrap:wrap;gap:7px}.category-badge{padding:4px 7px;border-radius:999px;color:var(--accent);background:var(--accent-soft);font-size:var(--font-2xs)}.category-badge.rose{color:var(--danger);background:color-mix(in srgb,var(--danger) 10%,transparent)}.category-badge.violet{color:var(--violet);background:color-mix(in srgb,var(--violet) 10%,transparent)}.category-badge.amber{color:var(--warning);background:color-mix(in srgb,var(--warning) 10%,transparent)}.pin-label{display:inline-flex;align-items:center;gap:4px;color:var(--accent);font-size:var(--font-2xs)}.log-row h3{margin:8px 0 4px;font-size:var(--font-sm)}.log-row p{overflow:hidden;margin:0;color:var(--muted);font-size:var(--font-xs);line-height:1.55;text-overflow:ellipsis;white-space:nowrap}.tag-list{display:flex;flex-wrap:wrap;gap:7px;margin-top:8px}.tag-list span{color:var(--accent);font-size:var(--font-2xs)}.pagination{display:flex;align-items:center;justify-content:space-between;gap:20px;margin-top:17px;color:var(--muted);font-size:var(--font-2xs)}.pagination div{display:flex;gap:5px}.pagination button{min-width:34px;height:34px;display:grid;place-items:center;color:var(--muted);border:1px solid var(--border);border-radius:8px;background:var(--panel);cursor:pointer}.pagination button.active{color:var(--accent);border-color:var(--accent);background:var(--accent-bg)}.pagination button:disabled{opacity:.4;cursor:not-allowed}
.log-row__main { width: 100%; padding: 0; color: inherit; text-align: left; border: 0; background: transparent; cursor: pointer; }
.log-row__main > strong { display: block; margin: 8px 0 4px; color: var(--text); font-size: var(--font-sm); }
.log-row__main:hover > strong { color: var(--accent); }
.log-excerpt { display: block; overflow: hidden; color: var(--muted); font-size: var(--font-xs); line-height: 1.55; text-overflow: ellipsis; white-space: nowrap; }
.domain-manager{display:grid;grid-template-columns:minmax(220px,.8fr) minmax(340px,1.2fr);gap:22px}.domain-manager__list{overflow:hidden;padding:9px;border:1px solid var(--border);border-radius:12px;background:var(--surface-sunken)}.manager-title{min-height:38px;display:flex;align-items:center;justify-content:space-between;gap:12px;color:var(--text);font-size:var(--font-xs);font-weight:750}.manager-title>button{display:flex;align-items:center;gap:4px;padding:6px;color:var(--accent);border:0;background:transparent;cursor:pointer;font-size:var(--font-2xs)}.domain-manager__list>button{width:100%;display:flex;align-items:center;justify-content:space-between;gap:12px;padding:11px;color:var(--muted);border:1px solid transparent;border-radius:9px;background:transparent;text-align:left;cursor:pointer}.domain-manager__list>button.active{color:var(--accent);border-color:var(--border);background:var(--panel)}.domain-manager__list>button span,.domain-manager__list>button strong,.domain-manager__list>button small{display:block;min-width:0}.domain-manager__list>button strong{color:var(--text);font-size:var(--font-xs)}.domain-manager__list>button small{overflow:hidden;max-width:220px;margin-top:3px;color:var(--muted);font-size:var(--font-2xs);text-overflow:ellipsis;white-space:nowrap}.domain-manager__list>button b{flex:0 0 auto;font-size:var(--font-2xs)}.domain-manager__list>p{padding:18px 8px;color:var(--muted);font-size:var(--font-xs);line-height:1.6}.domain-form{align-content:start}.code-editor{min-height:310px;color:var(--code-text)!important;background:var(--code-bg)!important;font:var(--font-xs)/1.65 "Cascadia Code",Consolas,monospace!important;tab-size:2}.switch-field{display:flex;align-items:center;gap:9px;color:var(--muted);font-size:var(--font-xs)}.switch-field input{accent-color:var(--accent)}.category-picker{display:grid;grid-template-columns:repeat(4,1fr);gap:7px}.category-picker button{display:flex;align-items:center;justify-content:center;gap:6px;padding:10px 6px;color:var(--muted);border:1px solid var(--border);border-radius:8px;background:var(--panel);cursor:pointer;font-size:var(--font-xs)}.category-picker button.active{color:var(--accent);border-color:var(--accent);background:var(--accent-bg)}.form-row--language{grid-template-columns:minmax(0,1fr) minmax(0,1fr)}.form-row--language>*{grid-column:1}
@media (max-width: 1260px) {
  .knowledge-header__actions { padding-inline: 14px; }
  .domain-copy strong { max-width: 130px; }
  .snippet-table__head, .snippet-row { grid-template-columns: 26px minmax(100px,1fr) 85px 65px 100px 100px; gap: 8px; }
}
@media (max-width: 1100px) {
  .knowledge-toolbar { align-items: flex-start; flex-direction: column; }
  .snippet-table__head { display: none; }
  .snippet-row { grid-template-columns: 28px minmax(0,1fr) auto; gap: 10px; }
  .snippet-row .domain-badge { grid-column: 2; }
  .snippet-row .language-badge { grid-column: 3; grid-row: 1; }
  .snippet-row time { grid-column: 3; }
  .snippet-row .row-actions { grid-column: 2/-1; justify-content: flex-start; }
  .row-actions span { display: inline; }
}
@media (max-width: 980px) {
  .knowledge-results { min-height: max(560px, calc(100svh - 92px)); }
  .log-row { grid-template-columns: minmax(0,1fr) 108px; }
  .log-row .row-actions { grid-column: 1/-1; justify-content: flex-start; }
  .snippet-reader-code, .log-reader-content { min-height: 320px; }
}
@media (max-width: 720px) {
  .knowledge-page { padding-top: 10px; padding-bottom: 10px; }
  .knowledge-results { min-height: max(560px, calc(100svh - 82px)); border-radius: 14px; }
  .knowledge-header { grid-template-columns: 1fr; }
  .knowledge-header__tabs { padding: 8px 8px 6px; }
  .knowledge-header__filter { gap: 8px; padding: 7px 8px 12px; }
  .knowledge-header__actions { flex-direction: row; align-items: center; justify-content: space-between; gap: 8px; padding: 9px 12px; border-left: 0; border-top: 1px solid var(--border); }
  .knowledge-header__actions .button { min-height: 38px; padding-inline: 11px; }
  .knowledge-tabs { width: 100%; min-width: 0; justify-content: space-between; gap: 4px; }
  .knowledge-tabs > button { gap: 6px; min-height: 40px; padding: 0 9px; border-radius: 8px; }
  .knowledge-tabs strong { font-size: 12px; }
  .knowledge-tabs small { min-width: 18px; padding-inline: 4px; font-size: 10px; }
  .knowledge-tabs svg { width: 15px; }
  .knowledge-filter-label { font-size: 10px; }
  .knowledge-filter-actions button { width: 30px; height: 30px; }
  .knowledge-content { padding: 16px 12px 20px; }
  .knowledge-toolbar { gap: 10px; }
  .list-display-controls { width: 100%; justify-content: flex-start; }
  .page-size-control { margin-left: auto; }
  .category-filters { width: 100%; overflow-x: auto; }
  .list-display-controls .category-filters { width: auto; flex: 1; }
  .category-filters button { flex: 0 0 auto; }
  .log-row { grid-template-columns: 1fr; gap: 9px; padding: 16px; }
  .log-row .row-actions { grid-column: 1; }
  .log-row > time { order: 2; }
  .pagination { align-items: flex-start; flex-direction: column; }
  .pagination div { max-width: 100%; overflow-x: auto; }
  .domain-manager { grid-template-columns: 1fr; }
  .domain-manager__list { max-height: 210px; overflow-y: auto; }
  .category-picker { grid-template-columns: repeat(2,1fr); }
  .form-row, .form-row--language { grid-template-columns: 1fr; }
  .form-row--language > * { grid-column: auto; }
  .knowledge-page.writing-mode { padding: 0; }
  .knowledge-reader__bar { min-height: 48px; gap: 6px; padding-inline: 8px; }
  .detail-back span, .detail-toolbar-button span { display: none; }
  .detail-actions { gap: 3px; }
  .detail-toolbar-button { width: 34px; padding: 0; }
  .knowledge-reader__paper { min-height: calc(100svh - 104px); padding: 14px 12px 28px; }
  .knowledge-reader__headline { padding-bottom: 13px; }
  .knowledge-reader__headline h1 { margin-block: 6px 9px; font-size: 24px; }
  .snippet-reader-code { padding: 18px; font-size: 12px; }
  .log-reader-content { padding: 18px; font-size: 15px; }
  .inline-editor-header { grid-template-columns: auto minmax(0, 1fr) auto; gap: 8px; min-height: 66px; padding: 10px 11px; }
  .inline-editor-title input { font-size: var(--font-md); }
  .inline-save-button { width: 40px; min-width: 40px; height: 40px; padding: 0; }
  .inline-save-button span { display: none; }
  .inline-editor-meta { align-items: stretch; gap: 8px; padding: 9px 11px; }
  .inline-editor-meta > label:not(.inline-toggle) { flex: 1 1 140px; }
  .inline-editor-meta input, .inline-editor-meta select { width: 100%; max-width: none; }
  .tag-editor-field { flex-basis: 100% !important; }
  .tag-editor-field input { width: 100%; }
  .inline-toggle { min-height: 32px; }
  .inline-editor-body > span, .code-body__bar { padding-inline: 12px; }
  .inline-editor-body textarea, .inline-editor-body.log-body textarea { padding: 18px; font-size: 14px; }
}
@media (max-width: 360px) {
  .knowledge-filter-label { display: none; }
  .knowledge-header__actions .button { padding-inline: 10px; }
  .knowledge-header__actions .button svg { display: none; }
}

/* Unified knowledge information architecture. Kept at the end so it cleanly supersedes
   the former per-type tab layout without changing the editor surfaces. */
.knowledge-header { display: block; border-bottom: 1px solid var(--border); background: linear-gradient(112deg, color-mix(in srgb, var(--accent-bg) 30%, var(--panel)) 0%, var(--panel) 46%); }
.knowledge-overview { min-width: 0; display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: 22px; padding: 16px 18px 14px; }
.knowledge-overview__copy { min-width: max-content; display: flex; align-items: center; gap: 11px; }
.knowledge-overview__icon { width: 40px; height: 40px; display: grid; place-items: center; color: var(--accent); border: 1px solid var(--accent-border); border-radius: 12px; background: var(--accent-bg); box-shadow: 0 6px 18px color-mix(in srgb, var(--accent) 12%, transparent); }
.knowledge-overview__copy > div { display: grid; gap: 3px; }
.knowledge-overview__copy strong { color: var(--text); font-size: var(--font-sm); font-weight: 760; }
.knowledge-overview__copy span { color: var(--muted); font-size: var(--font-2xs); }
.directory-bar .domain-list { scrollbar-width: none; }
.directory-bar .domain-list::-webkit-scrollbar { display: none; }
.create-knowledge { position: relative; justify-self: end; }
.create-knowledge__button { min-height: 42px; padding-inline: 15px 11px; gap: 7px; box-shadow: 0 8px 22px color-mix(in srgb, var(--accent) 24%, transparent); white-space: nowrap; }
.create-menu { position: absolute; z-index: 8; top: calc(100% + 8px); right: 0; width: 268px; overflow: hidden; padding: 7px; border: 1px solid var(--border-strong); border-radius: 12px; background: var(--panel); box-shadow: var(--shadow-lg); }
.create-menu > button { width: 100%; display: flex; align-items: center; gap: 11px; padding: 10px; color: var(--text); border: 0; border-radius: 9px; background: transparent; text-align: left; cursor: pointer; }
.create-menu > button:hover { background: var(--surface-sunken); }
.create-menu__icon { width: 36px; height: 36px; display: grid; flex: 0 0 36px; place-items: center; border-radius: 10px; }
.create-menu__icon.document { color: var(--accent); background: var(--accent-bg); }
.create-menu__icon.snippet { color: var(--violet); background: color-mix(in srgb, var(--violet) 10%, transparent); }
.create-menu__icon.log { color: var(--warning); background: color-mix(in srgb, var(--warning) 11%, transparent); }
.create-menu > button > span:last-child { min-width: 0; display: grid; gap: 3px; }
.create-menu strong { font-size: var(--font-xs); }
.create-menu small { color: var(--muted); font-size: var(--font-2xs); }
.directory-bar { min-width: 0; display: flex; align-items: center; gap: 10px; padding: 11px 18px 13px; border-top: 1px solid color-mix(in srgb, var(--border) 68%, transparent); background: color-mix(in srgb, var(--surface-sunken) 38%, transparent); }
.directory-bar__label { min-width: max-content; display: flex; align-items: center; gap: 7px; color: var(--subtle); }
.directory-bar__label > span { display: grid; gap: 1px; }
.directory-bar__label strong { font-size: var(--font-2xs); }
.directory-bar__label small { color: var(--muted); font-size: 9px; }
.directory-bar .domain-list { flex: 1; }
.directory-bar .domain-list > button { min-height: 35px; padding: 0 8px 0 10px; border-radius: 10px; }
.directory-bar .domain-list b { min-width: 23px; height: 21px; display: grid; place-items: center; padding: 0 5px; color: var(--subtle); border-radius: 999px; background: var(--surface-sunken); font-size: 10px; font-weight: 750; }
.directory-bar .domain-list > button.active { box-shadow: 0 3px 10px color-mix(in srgb, var(--accent) 10%, transparent); }
.directory-bar .domain-list > button.active b { color: #fff; background: var(--accent); }
.directory-actions { flex: 0 0 auto; display: flex; align-items: center; gap: 5px; }
.new-directory-button, .manage-directory-button { min-height: 35px; display: inline-flex; align-items: center; justify-content: center; gap: 6px; color: var(--accent); border: 1px solid var(--accent-border); border-radius: 9px; background: var(--accent-bg); cursor: pointer; font-size: var(--font-2xs); font-weight: 680; white-space: nowrap; }
.new-directory-button { padding: 0 11px; }
.manage-directory-button { width: 35px; padding: 0; color: var(--muted); border-color: var(--border); background: var(--panel); }
.new-directory-button:hover, .manage-directory-button:hover { color: #fff; border-color: var(--accent); background: var(--accent); }
.knowledge-header > .domain-module-state { margin: 0 18px 12px; }
.markdown-host { min-width: 0; }
.knowledge-toolbar { position: relative; z-index: 5; align-items: center; flex-direction: row; flex-wrap: wrap; min-height: 52px; margin-bottom: 13px; padding: 8px 10px; transition: border-color .18s, background .18s, box-shadow .18s; }
.list-context { min-width: 150px; display: flex; align-items: center; flex-wrap: wrap; gap: 4px 8px; }
.list-context strong { color: var(--text); font-size: var(--font-xs); }
.list-context small { color: var(--muted); font-size: var(--font-2xs); }
.list-context button { padding: 3px 5px; color: var(--accent); border: 0; background: transparent; cursor: pointer; font-size: var(--font-2xs); }
.list-display-controls { min-width: 0; display: flex; align-items: center; flex-wrap: wrap; justify-content: flex-end; gap: 6px; }
.styled-select { position: relative; flex: 0 0 auto; }
.styled-select__trigger { width: 100%; height: 34px; display: flex; align-items: center; gap: 7px; padding: 0 9px 0 6px; color: var(--subtle); border: 1px solid var(--border); border-radius: 9px; outline: 0; background: var(--panel); cursor: pointer; box-shadow: 0 1px 2px rgba(0,0,0,.03); transition: color .16s, border-color .16s, background .16s, box-shadow .16s; }
.styled-select__trigger:hover, .styled-select__trigger.open { color: var(--text); border-color: var(--accent-border); background: color-mix(in srgb, var(--accent-bg) 28%, var(--panel)); }
.styled-select__trigger:focus-visible { border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }
.styled-select__trigger > strong { overflow: hidden; min-width: 0; color: var(--text); font-size: var(--font-2xs); font-weight: 680; text-overflow: ellipsis; white-space: nowrap; }
.styled-select__trigger > small { min-width: 24px; height: 20px; display: grid; place-items: center; padding-inline: 5px; color: var(--accent); border-radius: 999px; background: var(--accent-bg); font-size: 10px; font-weight: 750; }
.styled-select__trigger > svg:last-child { flex: 0 0 auto; margin-left: auto; transition: transform .18s var(--ease-standard); }
.styled-select__trigger > svg.rotated { transform: rotate(180deg); }
.styled-select__trigger.placeholder > strong { color: var(--muted); font-weight: 560; }
.styled-select__leading { width: 28px; height: 28px; display: grid; flex: 0 0 28px; place-items: center; color: var(--muted); border-radius: 8px; background: var(--surface-sunken); }
.styled-select__leading.filter, .styled-select__leading.all, .styled-select__leading.documents, .styled-select__leading.domain { color: var(--accent); background: var(--accent-bg); }
.styled-select__leading.snippets { color: var(--violet); background: color-mix(in srgb, var(--violet) 11%, transparent); }
.styled-select__leading.logs { color: var(--warning); background: color-mix(in srgb, var(--warning) 12%, transparent); }
.styled-select__leading.unassigned { color: var(--muted); background: var(--surface-sunken); }
.styled-select__menu { position: absolute; z-index: 30; top: calc(100% + 8px); min-width: 220px; overflow: hidden; padding: 6px; border: 1px solid var(--border-strong); border-radius: 12px; background: color-mix(in srgb, var(--panel) 96%, transparent); box-shadow: 0 18px 46px rgba(0,0,0,.2), 0 4px 12px rgba(0,0,0,.08); backdrop-filter: blur(16px); }
.styled-select__menu.type-menu { right: 0; }
.styled-select__menu.domain-menu { right: 0; width: max(100%, 268px); max-height: 294px; overflow-y: auto; }
.styled-select__menu > button { width: 100%; min-height: 46px; display: grid; grid-template-columns: 28px minmax(0,1fr) 18px; align-items: center; gap: 9px; padding: 6px 8px; color: var(--muted); border: 1px solid transparent; border-radius: 9px; background: transparent; text-align: left; cursor: pointer; transition: color .14s, border-color .14s, background .14s; }
.styled-select__menu > button:hover { color: var(--text); background: var(--surface-sunken); }
.styled-select__menu > button.selected { color: var(--accent); border-color: color-mix(in srgb, var(--accent-border) 64%, transparent); background: var(--accent-bg); }
.styled-select__menu > button > svg:last-child { justify-self: end; color: var(--accent); }
.styled-select__option-copy { min-width: 0; display: grid; gap: 2px; }
.styled-select__option-copy strong, .styled-select__option-copy small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.styled-select__option-copy strong { color: var(--text); font-size: var(--font-xs); font-weight: 650; }
.styled-select__option-copy small { color: var(--muted); font-size: 10px; }
.type-select { width: 154px; }
.select-menu-enter-active, .select-menu-leave-active { transition: opacity .14s, transform .14s var(--ease-standard); transform-origin: top right; }
.select-menu-enter-from, .select-menu-leave-to { opacity: 0; transform: translateY(-5px) scale(.98); }
.utility-button { height: 34px; display: inline-flex; align-items: center; gap: 5px; padding: 0 9px; color: var(--subtle); border: 1px solid var(--border); border-radius: 8px; background: var(--panel); cursor: pointer; font-size: var(--font-2xs); white-space: nowrap; }
.utility-button:hover:not(:disabled) { color: var(--accent); border-color: var(--accent-border); background: var(--accent-bg); }
.utility-button:disabled { opacity: .45; cursor: not-allowed; }
.module-state-stack:empty { display: none; }
.module-state-stack :deep(.module-state) { margin-top: 0; }
.unified-table { overflow: hidden; border: 1px solid var(--border); border-radius: 13px; background: var(--panel); box-shadow: 0 7px 24px rgba(0,0,0,.025); }
.unified-table__head, .unified-row { display: grid; grid-template-columns: 28px minmax(280px, 2fr) minmax(138px, .6fr) minmax(112px, .55fr) 118px 34px; align-items: center; gap: 13px; }
.unified-table__head { min-height: 43px; padding: 0 15px; color: var(--muted); border-bottom: 1px solid var(--border); background: color-mix(in srgb, var(--surface-sunken) 82%, var(--panel)); font-size: var(--font-2xs); font-weight: 700; }
.unified-table__head > button, .select-button { display: grid; width: 28px; height: 30px; place-items: center; padding: 0; color: var(--muted); border: 0; border-radius: 7px; background: transparent; cursor: pointer; }
.unified-table__head > button:hover:not(:disabled), .select-button:hover { color: var(--accent); background: var(--accent-bg); }
.unified-table__head > button:disabled { opacity: .3; cursor: not-allowed; }
.unified-row { min-height: 92px; padding: 13px 15px; border-bottom: 1px solid var(--border); transition: background .15s, box-shadow .15s; }
.unified-row:last-child { border-bottom: 0; }
.unified-row:hover { background: color-mix(in srgb, var(--surface-sunken) 65%, transparent); }
.unified-row.selected { box-shadow: inset 3px 0 var(--accent); background: color-mix(in srgb, var(--accent-bg) 42%, transparent); }
.unified-summary { min-width: 0; padding: 0; color: inherit; text-align: left; border: 0; background: transparent; cursor: pointer; }
.unified-summary__title { min-width: 0; display: flex; align-items: center; gap: 7px; color: var(--accent); }
.unified-summary__title strong { overflow: hidden; color: var(--text); font-size: var(--font-sm); font-weight: 710; text-overflow: ellipsis; white-space: nowrap; }
.unified-summary:hover .unified-summary__title strong { color: var(--accent); }
.item-kind-icon { width: 28px; height: 28px; display: grid; flex: 0 0 28px; place-items: center; border-radius: 8px; }
.item-kind-icon.documents { color: var(--accent); background: var(--accent-bg); }
.item-kind-icon.snippets { color: var(--violet); background: color-mix(in srgb, var(--violet) 10%, transparent); }
.item-kind-icon.logs { color: var(--warning); background: color-mix(in srgb, var(--warning) 11%, transparent); }
.unified-summary > small, .unified-summary > code { display: block; overflow: hidden; margin: 5px 0 0 35px; color: var(--muted); background: transparent; font-size: var(--font-xs); line-height: 1.4; text-overflow: ellipsis; white-space: nowrap; }
.unified-summary > code { font-family: "Cascadia Code", Consolas, monospace; }
.unified-summary__detail { display: block; overflow: hidden; margin: 4px 0 0 35px; color: var(--muted); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.domain-badge { width: fit-content; max-width: 100%; display: inline-flex; align-items: center; gap: 5px; overflow: hidden; padding: 5px 8px; border-radius: 999px; font-size: var(--font-2xs); font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.unified-row > time { color: var(--muted); font-size: var(--font-2xs); }
.row-more-button { width: 32px; height: 32px; display: grid; place-items: center; padding: 0; color: var(--muted); border: 1px solid transparent; border-radius: 8px; background: transparent; opacity: 0; cursor: pointer; transition: opacity var(--motion-fast), color var(--motion-fast), border-color var(--motion-fast), background var(--motion-fast); }
.unified-row:hover .row-more-button, .row-more-button:focus-visible { opacity: 1; }
.row-more-button:hover { color: var(--accent); border-color: var(--accent-border); background: var(--accent-bg); }
.knowledge-toolbar.selection-mode { flex-wrap: nowrap; min-height: 58px; padding: 8px 10px 8px 12px; border-color: color-mix(in srgb, var(--accent-border) 80%, var(--border)); background: linear-gradient(100deg, color-mix(in srgb, var(--accent-bg) 72%, var(--panel)) 0%, color-mix(in srgb, var(--surface-sunken) 72%, var(--panel)) 58%); box-shadow: inset 3px 0 var(--accent), 0 7px 22px color-mix(in srgb, var(--accent) 7%, transparent); }
.selection-context { min-width: max-content; display: flex; align-items: center; gap: 9px; }
.selection-context__icon { width: 34px; height: 34px; display: grid; place-items: center; color: #fff; border-radius: 10px; background: var(--accent); box-shadow: 0 6px 16px color-mix(in srgb, var(--accent) 24%, transparent); }
.selection-context > span:last-child { display: grid; gap: 2px; }
.selection-context strong { color: var(--text); font-size: var(--font-xs); font-weight: 760; }
.selection-context small { color: var(--muted); font-size: 10px; }
.selection-actions { min-width: 0; display: flex; align-items: center; justify-content: flex-end; gap: 9px; margin-left: auto; }
.selection-actions__divider { width: 1px; height: 28px; margin-inline: 2px; background: var(--border); }
.selection-actions__label { color: var(--muted); font-size: var(--font-2xs); white-space: nowrap; }
.bulk-domain-select { width: clamp(184px, 16vw, 236px); }
.bulk-domain-select .styled-select__trigger { height: 38px; padding-left: 5px; border-color: color-mix(in srgb, var(--accent-border) 58%, var(--border)); background: var(--panel); }
.selection-export-button, .selection-move-button, .selection-delete-button, .selection-cancel-button { height: 38px; display: inline-flex; align-items: center; justify-content: center; gap: 6px; padding: 0 11px; border-radius: 9px; cursor: pointer; font-size: var(--font-2xs); font-weight: 680; white-space: nowrap; transition: color .15s, border-color .15s, background .15s, transform .15s; }
.selection-export-button { color: var(--subtle); border: 1px solid var(--border); background: var(--panel); }
.selection-export-button:hover:not(:disabled) { color: var(--accent); border-color: var(--accent-border); background: var(--accent-bg); }
.selection-move-button { min-width: 76px; color: #fff; border: 1px solid var(--accent); background: var(--accent); box-shadow: 0 6px 16px color-mix(in srgb, var(--accent) 22%, transparent); }
.selection-move-button:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 8px 20px color-mix(in srgb, var(--accent) 28%, transparent); }
.selection-delete-button { color: var(--danger); border: 1px solid color-mix(in srgb, var(--danger) 28%, var(--border)); background: color-mix(in srgb, var(--danger) 7%, var(--panel)); }
.selection-delete-button:hover:not(:disabled) { border-color: color-mix(in srgb, var(--danger) 50%, var(--border)); background: color-mix(in srgb, var(--danger) 12%, var(--panel)); }
.selection-export-button:disabled, .selection-move-button:disabled, .selection-delete-button:disabled { opacity: .45; cursor: not-allowed; box-shadow: none; }
.selection-cancel-button { color: var(--muted); border: 1px solid transparent; background: transparent; }
.selection-cancel-button:hover { color: var(--text); background: var(--surface-sunken); }
.drop-hint { display: flex; align-items: center; justify-content: center; gap: 6px; margin: 14px 0 0; color: var(--muted); font-size: var(--font-2xs); }
.empty-actions { display: flex; gap: 8px; }

@media (max-width: 1220px) {
  .knowledge-overview { grid-template-columns: minmax(0, 1fr) auto; gap: 14px; }
  .knowledge-overview__copy > div { display: none; }
  .unified-table__head, .unified-row { grid-template-columns: 26px minmax(220px, 1fr) 128px 100px 100px 32px; gap: 8px; }
}
@media (max-width: 980px) {
  .knowledge-overview { grid-template-columns: minmax(0, 1fr) auto; }
  .knowledge-overview__copy { display: none; }
  .directory-bar { align-items: flex-start; flex-wrap: wrap; }
  .directory-bar__label { padding-top: 8px; }
  .directory-bar .domain-list { order: 3; flex-basis: 100%; }
  .directory-actions { margin-left: auto; }
  .knowledge-toolbar.selection-mode { align-items: stretch; flex-direction: column; gap: 9px; }
  .selection-actions { width: 100%; justify-content: flex-start; margin-left: 0; }
  .bulk-domain-select { flex: 1 1 200px; width: auto; }
  .unified-table__head { display: none; }
  .unified-row { grid-template-columns: 28px minmax(0, 1fr) auto; gap: 9px 11px; }
  .unified-row .unified-summary { grid-column: 2 / -1; }
  .unified-row .type-cell { grid-column: 2; }
  .unified-row .domain-badge { grid-column: 2; }
  .unified-row > time { grid-column: 3; grid-row: 3; }
  .unified-row .row-actions { grid-column: 2 / -1; justify-content: flex-start; }
}
@media (max-width: 720px) {
  .knowledge-overview { gap: 9px; padding: 10px; }
  .create-knowledge__button { min-width: 42px; width: 42px; padding: 0; font-size: 0; }
  .create-knowledge__button svg:last-child { display: none; }
  .create-menu { position: fixed; top: 72px; right: 12px; left: 12px; width: auto; }
  .directory-bar { gap: 7px; padding: 8px 10px 10px; }
  .directory-bar__label { display: none; }
  .directory-actions { width: 100%; margin: 0; }
  .new-directory-button { flex: 1; }
  .directory-bar .domain-list { order: 2; flex-basis: 100%; }
  .knowledge-header > .domain-module-state { margin-inline: 10px; }
  .knowledge-toolbar { align-items: stretch; flex-direction: column; padding: 9px; }
  .list-display-controls { width: 100%; justify-content: flex-start; }
  .type-select { flex: 1 1 150px; width: auto; }
  .type-select .styled-select__menu { right: auto; left: 0; width: min(260px, calc(100vw - 54px)); }
  .utility-button { flex: 1 1 auto; }
  .page-size-control { margin-left: 0; }
  .selection-context small { display: none; }
  .selection-actions { display: grid; grid-template-columns: minmax(0,1fr) auto auto; gap: 7px; }
  .selection-export-button { grid-column: 1 / -1; justify-self: start; }
  .selection-actions__divider, .selection-actions__label { display: none; }
  .bulk-domain-select { min-width: 0; width: 100%; }
  .bulk-domain-select .styled-select__menu { right: auto; left: 0; width: min(280px, calc(100vw - 54px)); }
  .selection-move-button { min-width: 68px; padding-inline: 9px; }
  .selection-cancel-button { padding-inline: 8px; }
  .unified-row { min-height: 112px; padding: 13px 11px; }
  .unified-summary > small, .unified-summary > code, .unified-summary__detail { margin-left: 0; }
  .unified-row .type-cell, .unified-row .domain-badge { grid-column: 2 / -1; }
  .unified-row > time { grid-column: 2 / -1; grid-row: auto; }
  .pagination { align-items: flex-start; flex-direction: column; }
  .empty-actions { align-items: stretch; flex-direction: column; }
}
/* 语言彩色徽标与 JSON 工具 */
.language-badge { border: 1px solid transparent; }
.lang-badge { display: inline-flex; align-items: center; gap: 4px; width: fit-content; max-width: 100%; overflow: hidden; padding: 3px 8px; border: 1px solid transparent; border-radius: 999px; font-family: ui-monospace, monospace; font-size: 10px; font-weight: 600; line-height: 1.5; text-overflow: ellipsis; white-space: nowrap; }
.type-cell { min-width: 0; max-width: 100%; display: grid; align-content: center; }
.knowledge-kind { --kind-color: var(--accent); display: inline-flex; align-items: center; gap: 8px; width: fit-content; max-width: 100%; color: var(--kind-color); }
.knowledge-kind.snippets { --kind-color: hsl(var(--language-hue) 60% 40%); }
.knowledge-kind.logs { --kind-color: var(--warning); }
:global([data-theme="dark"]) .knowledge-kind.snippets { --kind-color: hsl(var(--language-hue) 80% 70%); }
.knowledge-kind__icon { width: 32px; height: 32px; display: grid; flex: 0 0 32px; place-items: center; border: 1px solid color-mix(in srgb, var(--kind-color) 22%, var(--border)); border-radius: 9px; background: color-mix(in srgb, var(--kind-color) 9%, var(--panel)); }
.knowledge-kind__copy { min-width: 0; display: grid; gap: 2px; }
.knowledge-kind strong, .knowledge-kind small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.knowledge-kind strong { color: var(--text); font-size: 11px; font-weight: 720; }
.knowledge-kind small { color: var(--kind-color); font: 650 10px/1.25 "Cascadia Code", Consolas, sans-serif; }
.row-menu-layer { position: fixed; z-index: 120; inset: 0; }
.row-action-menu { position: fixed; width: 210px; padding: 7px; color: var(--text); border: 1px solid var(--border-strong); border-radius: 12px; background: var(--modal-bg); box-shadow: 0 18px 50px rgba(0,0,0,.3); animation: row-menu-in var(--motion-fast) var(--ease-emphasized) both; }
.row-action-menu header { display: flex; align-items: center; gap: 8px; margin-bottom: 5px; padding: 7px 8px 9px; border-bottom: 1px solid var(--border); }
.row-action-menu header > span:last-child { min-width: 0; display: grid; gap: 2px; }
.row-action-menu header strong, .row-action-menu header small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.row-action-menu header strong { font-size: var(--font-xs); }
.row-action-menu header small { color: var(--muted); font-size: 10px; }
.row-action-menu > button { width: 100%; display: flex; align-items: center; gap: 9px; padding: 9px 10px; color: var(--subtle); text-align: left; border: 0; border-radius: 8px; background: transparent; cursor: pointer; font-size: var(--font-xs); }
.row-action-menu > button:hover { color: var(--accent); background: var(--accent-bg); }
.row-action-menu > button.danger { color: var(--danger); }
.row-action-menu > button.danger:hover { background: color-mix(in srgb, var(--danger) 10%, transparent); }
.row-action-menu__divider { display: block; height: 1px; margin: 4px 2px; background: var(--border); }
@keyframes row-menu-in { from { opacity: 0; transform: translateY(-4px) scale(.98); } }
.inline-editor-meta label > span { flex-shrink: 0; white-space: nowrap; }
.trash-row .lang-badge { margin-top: 5px; }
.code-body__tools { color: inherit; font: inherit; font-weight: 500; }
.editor-json-tree { min-width: 0; min-height: 0; flex: 1; display: flex; }

/* 目录删除对话框 */
.domain-delete__options { display: grid; gap: 8px; }
.domain-delete__option { display: grid; grid-template-columns: 18px minmax(0, 1fr); align-items: start; gap: 8px 10px; padding: 13px 14px; border: 1px solid var(--border); border-radius: 11px; background: var(--surface-sunken); cursor: pointer; }
.domain-delete__option.selected { border-color: var(--accent-border); background: var(--accent-bg); }
.domain-delete__option.danger.selected { border-color: color-mix(in srgb, var(--danger) 55%, var(--border)); background: color-mix(in srgb, var(--danger) 9%, transparent); }
.domain-delete__option > input { margin: 2px 0 0; accent-color: var(--accent); }
.domain-delete__option.danger > input { accent-color: var(--danger); }
.domain-delete__option strong, .domain-delete__option small { display: block; }
.domain-delete__option strong { color: var(--text); font-size: var(--font-sm); }
.domain-delete__option small { margin-top: 3px; color: var(--muted); font-size: var(--font-2xs); line-height: 1.55; }
.domain-delete__option select { width: 100%; grid-column: 2; height: 34px; padding: 0 10px; color: var(--text); border: 1px solid var(--border-strong); border-radius: 8px; outline: 0; background: var(--panel); font-size: var(--font-xs); }
.domain-delete__empty { margin: 0 0 6px; padding: 14px 4px; color: var(--muted); font-size: var(--font-xs); }
.domain-delete__actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 18px; padding-top: 16px; border-top: 1px solid var(--border); }

/* 统一回收站 */
.trash-tabs { display: flex; gap: 4px; border-bottom: 1px solid var(--border); }
.trash-tabs > button { min-height: 42px; display: inline-flex; align-items: center; gap: 7px; padding: 0 14px; color: var(--muted); border: 0; border-bottom: 2px solid transparent; background: transparent; cursor: pointer; font-size: var(--font-xs); font-weight: 650; }
.trash-tabs > button:hover { color: var(--text); }
.trash-tabs > button.active { color: var(--accent); border-bottom-color: var(--accent); }
.trash-tabs span { min-width: 18px; padding: 1px 6px; color: var(--muted); border-radius: 999px; background: var(--panel-strong); font-size: 10px; text-align: center; }
.trash-toolbar { display: flex; justify-content: flex-end; margin: 12px 0 4px; }
.trash-loading { display: flex; align-items: center; gap: 8px; color: var(--muted); font-size: var(--font-xs); }
.trash-row { display: grid; grid-template-columns: 36px minmax(0, 1fr) auto; align-items: start; gap: 12px; padding: 14px 4px; border-bottom: 1px solid var(--border); }
.trash-row:last-child { border-bottom: 0; }
.trash-row__icon { width: 34px; height: 34px; display: grid; place-items: center; border-radius: 9px; }
.trash-row__icon.documents { color: var(--accent); background: var(--accent-bg); }
.trash-row__icon.snippets { color: var(--violet); background: color-mix(in srgb, var(--violet) 10%, transparent); }
.trash-row__icon.logs { color: var(--warning); background: color-mix(in srgb, var(--warning) 11%, transparent); }
.trash-row__copy { min-width: 0; }
.trash-row__copy strong { display: block; color: var(--text); font-size: var(--font-sm); overflow-wrap: anywhere; }
.trash-row__copy small { display: block; margin-top: 5px; color: var(--muted); font-size: var(--font-2xs); line-height: 1.7; overflow-wrap: anywhere; }
.trash-row__copy em { display: block; overflow: hidden; margin-top: 5px; color: var(--muted); font: var(--font-2xs)/1.5 "Cascadia Code", Consolas, monospace; text-overflow: ellipsis; white-space: nowrap; }
.trash-row__actions { display: flex; align-items: center; gap: 6px; }
.trash-row__actions .button { min-height: 34px; padding-inline: 11px; font-size: var(--font-2xs); }
.danger-text { color: var(--danger); }
@media (max-width: 720px) {
  .trash-row { grid-template-columns: 32px minmax(0, 1fr); }
  .trash-row__actions { grid-column: 2; justify-content: flex-start; }
  .domain-delete__option select { grid-column: 1 / -1; }
}
</style>
