<script setup lang="ts">
import { computed, nextTick, onActivated, onBeforeUnmount, onDeactivated, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import {
  AlertCircle,
  ArrowLeft,
  Bold,
  BookOpen,
  Check,
  CheckSquare,
  Clock3,
  Code,
  Columns2,
  Copy,
  Download,
  Edit3,
  Eye,
  FileText,
  Folder,
  Heading2,
  Heart,
  History,
  Italic,
  ImagePlus,
  Link2,
  List,
  ListTree,
  LoaderCircle,
  PenLine,
  Plus,
  Quote,
  RotateCcw,
  Save,
  Share2,
  Square,
  Trash2,
  Upload,
} from 'lucide-vue-next'
import { useWorkspaceStore } from '../stores/workspace'
import { useSearchStore } from '../stores/search'
import { useAuthStore } from '../stores/auth'
import { useNotificationStore } from '../stores/notifications'
import { useConfirmationStore } from '../stores/confirmation'
import { matchesDocumentSearch } from '../utils/search'
import type { MarkdownDocument, MarkdownDocumentDraft, MarkdownShareLink, MarkdownShareSecret } from '../types'
import { buildMarkdownHeadingTree, extractMarkdownHeadings, markdownExcerpt } from '../utils/markdown'
import EmptyState from './EmptyState.vue'
import AppModal from './AppModal.vue'
import MarkdownRecoveryPanel from './MarkdownRecoveryPanel.vue'
import WorkspaceUtilities from './WorkspaceUtilities.vue'
import { copyMarkdownDraft, createMarkdownDraftStorage, mergeSavedMarkdownDraft, serializeMarkdownDraft } from '../utils/markdownDrafts'
import type { LocalMarkdownDraft } from '../utils/markdownDrafts'
import { createClientId } from '../utils/clientId'
import { completeImageUpload, handleSaveShortcut, imageMarkdown, isDefaultFileName, MAX_IMAGE_BATCH, needsDocumentName, validateImageFile } from '../utils/markdownEditing'
import { primeMarkdownImageCache, uploadMarkdownImage } from '../services/markdownImages'
import { createMarkdownShare, listMarkdownShares, revokeMarkdownShare } from '../services/markdownShares'
import MarkdownContent from './MarkdownContent.vue'
import MarkdownGuide from './MarkdownGuide.vue'
import MarkdownOutlineTree from './MarkdownOutlineTree.vue'

const ALL_DOMAINS = 'ALL'
const UNASSIGNED = 'UNASSIGNED'
const MAX_IMPORT_COUNT = 50
const MAX_IMPORT_REQUEST_BYTES = 100 * 1024 * 1024
const MAX_EXPORT_COUNT = 100

const props = defineProps<{
  selectedDomain: string
  selectedDomainName: string
  compactListHeader?: boolean
  headlessList?: boolean
}>()
const emit = defineEmits<{
  'editing-change': [editing: boolean]
}>()

const workspace = useWorkspaceStore()
const auth = useAuthStore()
const notifications = useNotificationStore()
const confirmation = useConfirmationStore()
// Capture the account that owns this editor; logout must not write to another account.
const draftOwnerId = auth.workspaceKey ?? null
const searchState = useSearchStore()
const search = computed(() => searchState.pageQuery)
const favoriteOnly = ref(false)
const selectedIds = ref(new Set<string>())
const bulkDomainId = ref('')
const editingId = ref<string | null>(null)
const editorOpen = ref(false)
const documentView = ref<'preview' | 'edit'>('edit')
const editorMode = ref<'split' | 'write' | 'preview'>('split')
const tocCollapsed = ref(window.matchMedia('(max-width: 900px)').matches)
const activeHeadingId = ref('')
const editorRoot = ref<HTMLElement | null>(null)
const titleInput = ref<HTMLInputElement | null>(null)
const editorTextarea = ref<HTMLTextAreaElement | null>(null)
const openingId = ref<string | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const imageInput = ref<HTMLInputElement | null>(null)
const guideButton = ref<HTMLButtonElement | null>(null)
const guideOpen = ref(false)
const imageUploading = ref(0)
const imageUploads = new Map<string, AbortController>()
let componentActive = true
const originalDraft = ref('')
const baseVersion = ref<number | undefined>()
const localDraftId = ref<string | null>(null)
const localDrafts = ref<LocalMarkdownDraft[]>([])
const localSavedAt = ref('')
const draftError = ref('')
const showDrafts = ref(false)
const showShares = ref(false)
const shareLinks = ref<MarkdownShareLink[]>([])
const shareSecret = ref<MarkdownShareSecret | null>(null)
const shareDays = ref(7)
const shareLoading = ref(false)
const shareBusy = ref(false)
const shareError = ref('')
const recoveryMode = ref<'trash' | 'history' | null>(null)
const recoveryBusy = ref(false)
const pendingDelete = ref<MarkdownDocument | null>(null)
const pendingDiscard = ref<LocalMarkdownDraft | null>(null)
let draftTimer: ReturnType<typeof setTimeout> | undefined
let editorSession = 0
const saveState = ref<'idle' | 'saving' | 'saved' | 'error'>('idle')
const validationField = ref<'title' | 'content' | null>(null)
const draft = reactive<MarkdownDocumentDraft>({
  title: '',
  fileName: '',
  content: '',
  domainId: null,
  favorite: false,
})
const importProgress = reactive({ active: false, current: 0, total: 0, message: '' })
const shareUrl = computed(() => shareSecret.value
  ? `${window.location.origin}/share/markdown/${encodeURIComponent(shareSecret.value.token)}`
  : '')

const filteredDocuments = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  return workspace.markdownDocuments
    .filter((item) => {
      const domainMatches = props.selectedDomain === ALL_DOMAINS
        || (props.selectedDomain === UNASSIGNED ? !item.domainId : item.domainId === props.selectedDomain)
      const searchMatches = matchesDocumentSearch(keyword, item)
      return domainMatches && searchMatches && (!favoriteOnly.value || item.favorite)
    })
    .sort((a, b) => Number(b.favorite) - Number(a.favorite) || b.updatedAt.localeCompare(a.updatedAt))
})

const allVisibleSelected = computed(() => filteredDocuments.value.length > 0
  && filteredDocuments.value.every((item) => selectedIds.value.has(item.id)))
const isDirty = computed(() => editorOpen.value && documentView.value === 'edit' && serializeDraft() !== originalDraft.value)
const readerContent = computed(() => {
  const lines = draft.content.split(/\r?\n/)
  const firstContentLine = lines.findIndex((line) => line.trim())
  if (firstContentLine >= 0) {
    const heading = lines[firstContentLine]?.match(/^#\s+(.+)$/)?.[1]?.trim()
    if (heading && heading === draft.title.trim()) lines.splice(firstContentLine, 1)
  }
  return lines.join('\n')
})
const readerHeadings = computed(() => extractMarkdownHeadings(readerContent.value))
const readerHeadingTree = computed(() => buildMarkdownHeadingTree(readerHeadings.value))
const wordCount = computed(() => draft.content.trim() ? draft.content.trim().split(/\s+/).length : 0)
const readingMinutes = computed(() => Math.max(1, Math.ceil(wordCount.value / 300)))
const currentDocument = computed<MarkdownDocument | undefined>(() => editingId.value ? {
  ...copyMarkdownDraft(draft), id: editingId.value, version: baseVersion.value,
  createdAt: '', updatedAt: '',
} : undefined)
const saveStatus = computed(() => {
  if (saveState.value === 'saving') return '正在保存到云端…'
  if (!isDirty.value && editingId.value) return '当前内容已保存到云端'
  if (draftError.value) return '本机草稿未备份 · 请保存到云端'
  if (isDirty.value) return localSavedAt.value ? '草稿已存本机 · 尚未保存到云端' : '有未保存修改 · 正在保存本机草稿…'
  return editingId.value ? '当前内容已保存到云端' : '新文章 · 输入后自动保存本机草稿'
})

function serializeDraft() {
  return serializeMarkdownDraft(draft)
}

function draftStorage() { return createMarkdownDraftStorage(window.localStorage) }

function refreshLocalDrafts() {
  if (!draftOwnerId || auth.workspaceKey !== draftOwnerId) { localDrafts.value = []; return }
  try { localDrafts.value = draftStorage().list(draftOwnerId) }
  catch { draftError.value = '无法读取本机草稿，请检查浏览器存储权限。' }
}

function flushLocalDraft() {
  clearTimeout(draftTimer)
  if (!editorOpen.value || documentView.value !== 'edit') return true
  if (!draftOwnerId) { draftError.value = '未登录，无法保存本机草稿。'; return false }
  try {
    if (isDirty.value) {
      localDraftId.value ??= createClientId()
      const savedAt = new Date().toISOString()
      draftStorage().write(draftOwnerId, {
        schemaVersion: 1, id: localDraftId.value, documentId: editingId.value,
        baseVersion: baseVersion.value ?? null, savedAt,
        draft: { ...copyMarkdownDraft(draft), content: contentWithoutPendingImages() },
      })
      localSavedAt.value = savedAt
    } else if (localDraftId.value) {
      draftStorage().remove(draftOwnerId, localDraftId.value)
      localSavedAt.value = ''
    }
    draftError.value = ''
    refreshLocalDrafts()
    return true
  } catch {
    localSavedAt.value = ''
    draftError.value = '本机草稿保存失败（存储空间或权限不足），请及时保存到云端。'
    return false
  }
}

function openDraftManager() {
  pendingDiscard.value = null
  flushLocalDraft()
  refreshLocalDrafts()
  showDrafts.value = true
}

async function recoverDraft(record: LocalMarkdownDraft, asNew = false) {
  if (recoveryBusy.value || !await canLeaveEditor() || recoveryBusy.value) return
  const session = ++editorSession
  recoveryBusy.value = true
  try {
    let cloud: MarkdownDocument | undefined
    let notice = ''
    if (record.documentId && !asNew) {
      try { cloud = await workspace.loadMarkdownDocument(record.documentId) }
      catch { notice = '暂时无法读取原文章。草稿可继续编辑；若原文章已删除，请先从回收站恢复，或将草稿另存为新文章。' }
    }
    if (session !== editorSession || auth.workspaceKey !== draftOwnerId) return
    editingId.value = asNew ? null : record.documentId
    baseVersion.value = asNew ? undefined : record.baseVersion ?? undefined
    // Recover into a separate key so another tab holding the original draft cannot overwrite it.
    localDraftId.value = createClientId()
    localSavedAt.value = ''
    documentView.value = 'edit'
    Object.assign(draft, copyMarkdownDraft(record.draft))
    if (draft.domainId && !workspace.domains.some((item) => item.id === draft.domainId)) {
      // Only normalize folders after a successful fetch; offline data may be incomplete.
      if (cloud) draft.domainId = null
    }
    originalDraft.value = cloud ? serializeMarkdownDraft({ ...cloud, content: cloud.content ?? '' }) : ''
    editorOpen.value = true
    saveState.value = 'idle'
    validationField.value = null
    if (cloud && cloud.version !== record.baseVersion) notice = '云端文章已有新版本。已保留你的草稿，直接保存会进行冲突检查；也可以另存为新文章。'
    notifications.notify(notice || '已恢复本机草稿，确认内容后请保存到云端。', { type: notice ? 'warning' : 'info' })
    if (flushLocalDraft() && draftOwnerId) {
      draftStorage().remove(draftOwnerId, record.id)
      refreshLocalDrafts()
    }
    showDrafts.value = false
    pendingDiscard.value = null
    editorMode.value = window.matchMedia('(max-width: 1100px)').matches ? 'write' : 'split'
    revealEditor('content')
  } catch {
    draftError.value = '草稿处理失败，原草稿仍保留在本机。'
  } finally { recoveryBusy.value = false }
}

function discardDraft(record: LocalMarkdownDraft) {
  if (!draftOwnerId || recoveryBusy.value) return
  try {
    draftStorage().remove(draftOwnerId, record.id)
    pendingDiscard.value = null
    refreshLocalDrafts()
  } catch { draftError.value = '无法删除本机草稿，请检查浏览器存储权限。' }
}

async function openHistory() {
  if (!editingId.value || !await canLeaveEditor()) return
  recoveryMode.value = 'history'
}

async function openShare() {
  if (!editingId.value) return
  const documentId = editingId.value
  showShares.value = true
  shareSecret.value = null
  shareLinks.value = []
  shareError.value = ''
  shareLoading.value = true
  try {
    const links = await listMarkdownShares(documentId)
    if (showShares.value && editingId.value === documentId) shareLinks.value = links
  } catch (cause) {
    if (showShares.value && editingId.value === documentId) {
      shareError.value = cause instanceof Error ? cause.message : '暂时无法读取分享链接。'
    }
  } finally {
    if (editingId.value === documentId) shareLoading.value = false
  }
}

function closeShare() {
  if (shareBusy.value) return
  showShares.value = false
  shareSecret.value = null
  shareError.value = ''
}

async function createShare() {
  if (!editingId.value || shareBusy.value) return
  const days = Number(shareDays.value)
  if (!Number.isInteger(days) || days < 1 || days > 365) {
    shareError.value = '有效期请输入 1–365 天的整数。'
    return
  }
  const documentId = editingId.value
  shareBusy.value = true
  shareError.value = ''
  try {
    const secret = await createMarkdownShare(
      documentId,
      new Date(Date.now() + days * 24 * 60 * 60 * 1000).toISOString(),
    )
    if (!showShares.value || editingId.value !== documentId) return
    shareSecret.value = secret
    shareLinks.value = [{ ...secret, revokedAt: null, active: true }, ...shareLinks.value]
    notifications.notify('分享链接已生成，请复制后发送给需要查看的人。', { type: 'success' })
  } catch (cause) {
    shareError.value = cause instanceof Error ? cause.message : '分享链接生成失败，请稍后重试。'
  } finally {
    shareBusy.value = false
  }
}

async function copyShareUrl() {
  if (!shareUrl.value) return
  try {
    if (window.isSecureContext && navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(shareUrl.value)
    } else {
      const field = window.document.createElement('textarea')
      field.value = shareUrl.value
      field.style.position = 'fixed'
      field.style.opacity = '0'
      window.document.body.append(field)
      field.select()
      window.document.execCommand('copy')
      field.remove()
    }
    notifications.notify('分享链接已复制。', { type: 'success' })
  } catch {
    notifications.notify('自动复制失败，请手动选择并复制链接。', { type: 'warning' })
  }
}

function selectShareUrl(event: FocusEvent) {
  if (event.target instanceof HTMLTextAreaElement) event.target.select()
}

async function revokeShare(link: MarkdownShareLink) {
  if (!editingId.value || !link.active || shareBusy.value) return
  if (!await confirmation.ask({
    title: '撤销这个分享链接？',
    message: '收到此链接的人将立即无法继续查看文章。',
    detail: '撤销后无法恢复；如需再次分享，可以生成一个新链接。',
    confirmText: '撤销链接', cancelText: '保留链接', tone: 'warning', icon: 'leave',
  })) return
  const documentId = editingId.value
  shareBusy.value = true
  shareError.value = ''
  try {
    await revokeMarkdownShare(documentId, link.id)
    const revokedAt = new Date().toISOString()
    shareLinks.value = shareLinks.value.map((item) => item.id === link.id
      ? { ...item, active: false, revokedAt }
      : item)
    if (shareSecret.value?.id === link.id) shareSecret.value = null
    notifications.notify('分享链接已撤销。', { type: 'success' })
  } catch (cause) {
    shareError.value = cause instanceof Error ? cause.message : '撤销失败，请稍后重试。'
  } finally {
    shareBusy.value = false
  }
}

function onVersionRestored(document: MarkdownDocument) {
  // Any unsaved draft was persisted before opening history; keep it available for recovery.
  editorSession++
  editingId.value = document.id
  baseVersion.value = document.version
  localDraftId.value = null
  localSavedAt.value = ''
  Object.assign(draft, copyMarkdownDraft({ ...document, content: document.content ?? '' }))
  originalDraft.value = serializeDraft()
  saveState.value = 'saved'
  recoveryMode.value = null
  notifications.notify('历史版本已恢复到云端，恢复前的版本仍保留在历史记录中。', { type: 'success' })
}

function activeDomainId() {
  return props.selectedDomain === ALL_DOMAINS || props.selectedDomain === UNASSIGNED
    ? null
    : props.selectedDomain
}

function fileNameFromTitle(title: string) {
  const slug = title.trim()
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 80)
  return `${slug || 'untitled'}.md`
}

function revealEditor(focus: 'title' | 'content' | null = null) {
  nextTick(() => {
    editorRoot.value?.scrollIntoView({ block: 'start' })
    if (focus === 'title') titleInput.value?.focus()
    if (focus === 'content') editorTextarea.value?.focus()
  })
}

function normalizedFileName(value: string, title: string) {
  const cleaned = value.trim().replace(/[\\/:*?"<>|]/g, '-').replace(/\s+/g, '-')
  if (isDefaultFileName(cleaned)) return fileNameFromTitle(title)
  return /\.(?:md|markdown|mdown)$/i.test(cleaned) ? cleaned.replace(/\.(?:markdown|mdown)$/i, '.md') : `${cleaned}.md`
}

async function openCreate() {
  if (!await canLeaveEditor()) return
  guideOpen.value = false
  editorSession++
  localDraftId.value = null
  localSavedAt.value = ''
  draftError.value = ''
  baseVersion.value = undefined
  editingId.value = null
  documentView.value = 'edit'
  validationField.value = null
  Object.assign(draft, {
    title: '',
    fileName: 'untitled.md',
    content: '',
    domainId: activeDomainId(),
    favorite: false,
  })
  originalDraft.value = serializeDraft()
  saveState.value = 'idle'
  editorOpen.value = true
  editorMode.value = window.matchMedia('(max-width: 1100px)').matches ? 'write' : 'split'
  revealEditor('title')
}

async function openDocument(document: MarkdownDocument, view: 'preview' | 'edit' = 'preview') {
  if (openingId.value || !await canLeaveEditor() || openingId.value) return
  const session = ++editorSession
  openingId.value = document.id
  try {
    // Always obtain the current version before editing instead of reusing a stale cached body.
    const resolved = await workspace.loadMarkdownDocument(document.id)
    if (session !== editorSession || auth.workspaceKey !== draftOwnerId) return
    localDraftId.value = null
    localSavedAt.value = ''
    draftError.value = ''
    baseVersion.value = resolved.version
    editingId.value = resolved.id
    documentView.value = view
    Object.assign(draft, {
      title: resolved.title,
      fileName: resolved.fileName,
      content: resolved.content ?? '',
      domainId: resolved.domainId ?? null,
      favorite: resolved.favorite,
    })
    originalDraft.value = serializeDraft()
    saveState.value = 'idle'
    validationField.value = null
    editorOpen.value = true
    editorMode.value = window.matchMedia('(max-width: 1100px)').matches ? 'write' : 'split'
    revealEditor()
  } catch {
    // API failures are reported by the global notification host.
  } finally {
    openingId.value = null
  }
}

async function canLeaveEditor() {
  if (imageUploading.value) {
    notifications.notify('图片正在上传，请稍候再保存或离开。')
    return false
  }
  if (saveState.value === 'saving') {
    notifications.notify('文章正在保存，请稍候再离开。')
    return false
  }
  if (!isDirty.value) return true
  if (flushLocalDraft()) return true
  const session = editorSession
  const accepted = await confirmation.ask({
    title: '还有未备份的修改',
    message: '本机草稿暂时无法保存，继续离开可能丢失刚才的修改。',
    detail: '建议先取消并保存到云端，或复制正文到其他位置。',
    confirmText: '仍然离开', cancelText: '继续编辑', tone: 'warning', icon: 'leave',
  })
  return accepted && session === editorSession && auth.workspaceKey === draftOwnerId
}

async function closeEditor() {
  if (!await canLeaveEditor()) return
  editorSession++
  editorOpen.value = false
  guideOpen.value = false
  editingId.value = null
  documentView.value = 'edit'
  saveState.value = 'idle'
  validationField.value = null
  draftError.value = ''
}

async function saveDocument() {
  if (saveState.value === 'saving' || recoveryBusy.value) return
  if (editingId.value && !isDirty.value) {
    saveState.value = 'saved'
    return
  }
  if (imageUploading.value) {
    notifications.notify('图片正在上传，完成后再保存即可。', { type: 'info' })
    return
  }
  if (needsDocumentName(draft.title, draft.fileName, editingId.value)) {
    saveState.value = 'error'
    validationField.value = 'title'
    notifications.notify('请先为新文件命名（填写文章标题或文件名），再按 Ctrl+S 保存。', { type: 'warning', title: '先给笔记起个名字' })
    await nextTick()
    titleInput.value?.focus()
    return
  }
  const title = draft.title.trim() || titleFromMarkdown(draft.content, draft.fileName)
  if (!title) {
    saveState.value = 'error'
    validationField.value = 'title'
    notifications.notify('请先填写文章标题。', { type: 'warning' })
    await nextTick()
    titleInput.value?.focus()
    return
  }
  if (!draft.content.trim()) {
    saveState.value = 'error'
    validationField.value = 'content'
    notifications.notify('正文还是空的，写一点内容后再保存吧。', { type: 'warning' })
    await nextTick()
    editorTextarea.value?.focus()
    return
  }
  if (!draft.title.trim()) draft.title = title
  draft.fileName = normalizedFileName(draft.fileName, title)
  const submitted = copyMarkdownDraft(draft)
  const session = editorSession
  flushLocalDraft()
  const creating = !editingId.value
  saveState.value = 'saving'
  validationField.value = null
  try {
    const saved = creating
      ? await workspace.saveMarkdownDocument(submitted)
      : await workspace.saveMarkdownDocument({
          ...submitted,
          expectedVersion: baseVersion.value!,
        }, editingId.value!)
    if (session !== editorSession) return
    editingId.value = saved.id
    baseVersion.value = saved.version
    const savedDraft = copyMarkdownDraft({ ...saved, content: saved.content ?? submitted.content })
    Object.assign(draft, mergeSavedMarkdownDraft(submitted, draft, savedDraft))
    originalDraft.value = serializeMarkdownDraft(savedDraft)
    flushLocalDraft()
    saveState.value = 'saved'
    notifications.notify(isDirty.value ? '提交的版本已保存；保存期间的新修改仍保留在本机草稿中。' : creating ? '文章已创建并保存到知识库。' : '文章修改已保存。', { type: 'success' })
    window.setTimeout(() => {
      if (session !== editorSession) return
      if (saveState.value === 'saved') saveState.value = 'idle'
    }, 2200)
  } catch {
    if (session !== editorSession) return
    saveState.value = 'error'
    flushLocalDraft()
  }
}

async function toggleFavorite(document: MarkdownDocument) {
  try {
    const resolved = await workspace.loadMarkdownDocument(document.id)
    if (resolved.version === undefined) throw new Error('文章版本缺失，请重新加载后再操作。')
    await workspace.saveMarkdownDocument({
      title: resolved.title,
      fileName: resolved.fileName,
      content: resolved.content ?? '',
      domainId: resolved.domainId,
      favorite: !resolved.favorite,
      expectedVersion: resolved.version,
    }, resolved.id)
  } catch {
    // API failures are reported by the global notification host.
  }
}

async function removeDocument(document: MarkdownDocument) {
  if (workspace.mutating) return
  try {
    await workspace.deleteMarkdownDocument(document.id)
    notifications.notify('文章已移到回收站，可随时恢复。', { type: 'success' })
    pendingDelete.value = null
    selectedIds.value = new Set([...selectedIds.value].filter((id) => id !== document.id))
    if (editingId.value === document.id) {
      editorOpen.value = false
      editingId.value = null
    }
  } catch {
    // Keep the confirmation open; the global host reports the failure.
  }
}

function toggleSelection(id: string) {
  const next = new Set(selectedIds.value)
  next.has(id) ? next.delete(id) : next.add(id)
  selectedIds.value = next
}

function toggleSelectAll() {
  const next = new Set(selectedIds.value)
  if (allVisibleSelected.value) filteredDocuments.value.forEach((item) => next.delete(item.id))
  else filteredDocuments.value.forEach((item) => next.add(item.id))
  selectedIds.value = next
}

function triggerImport() {
  fileInput.value?.click()
}

function titleFromMarkdown(content: string, fileName: string) {
  const frontMatter = content.match(/^---\s*\n[\s\S]*?^title:\s*["']?(.+?)["']?\s*$[\s\S]*?^---\s*$/mi)?.[1]
  const heading = content.match(/^#\s+(.+)$/m)?.[1]
  return (frontMatter || heading || fileName.replace(/\.(?:md|markdown|mdown)$/i, '')).trim().slice(0, 200)
}

async function importFiles(files: File[]) {
  const errors: string[] = []
  if (files.length > MAX_IMPORT_COUNT) errors.push(`一次最多选择 ${MAX_IMPORT_COUNT} 个文件`)
  for (const file of files.slice(0, MAX_IMPORT_COUNT)) {
    if (!/\.(?:md|markdown|mdown|zip)$/i.test(file.name)) {
      errors.push(`${file.name}：仅支持 Markdown 或 ZIP 文件`)
    } else if (/\.(?:md|markdown|mdown)$/i.test(file.name) && file.size > 1024 * 1024) {
      errors.push(`${file.name}：单个 Markdown 文件不能超过 1 MB`)
    } else if (/\.zip$/i.test(file.name) && file.size > MAX_IMPORT_REQUEST_BYTES) {
      errors.push(`${file.name}：单个 ZIP 不能超过 100 MB`)
    }
  }
  if (files.reduce((sum, file) => sum + file.size, 0) > MAX_IMPORT_REQUEST_BYTES) {
    errors.push('本次选择的文件总量不能超过 100 MB')
  }
  if (errors.length) {
    notifications.notify('导入文件未通过检查。', { type: 'warning', details: errors })
    if (fileInput.value) fileInput.value.value = ''
    return
  }

  importProgress.active = true
  importProgress.current = 0
  importProgress.total = 1
  importProgress.message = '正在上传、解析正文并保存压缩包图片…'
  try {
    const result = await workspace.importMarkdownFiles(files, activeDomainId())
    importProgress.current = 1
    const serverErrors = (result.errors ?? []).map((item) => `${item.fileName}：${item.message}`)
    const destination = activeDomainId() ? props.selectedDomainName : '未分类'
    notifications.notify(`已导入 ${result.documents.length} 篇文章到“${destination}”${serverErrors.length ? `，${serverErrors.length} 个文件未完成` : ''}。`, { type: serverErrors.length ? 'warning' : 'success', details: serverErrors })
  } catch {
    // The workspace store exposes the server error through the global notification host.
  } finally {
    importProgress.active = false
    if (fileInput.value) fileInput.value.value = ''
  }
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  if (input.files?.length) void importFiles([...input.files])
}

function onDrop(event: DragEvent) {
  if (event.dataTransfer?.files.length) void importFiles([...event.dataTransfer.files])
}

async function exportSelected() {
  const ids = [...selectedIds.value]
  const exportCount = ids.length || workspace.markdownDocuments.length
  if (exportCount > MAX_EXPORT_COUNT) {
    notifications.notify(`一次最多导出 ${MAX_EXPORT_COUNT} 篇文章，请先勾选部分文章后再导出。`, { type: 'warning' })
    return
  }
  try {
    const { blob, fileName } = await workspace.exportMarkdownDocuments(ids)
    const url = URL.createObjectURL(blob)
    const anchor = window.document.createElement('a')
    anchor.href = url
    anchor.download = fileName || `devnest-markdown-${new Date().toISOString().slice(0, 10)}.zip`
    window.document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    window.setTimeout(() => URL.revokeObjectURL(url), 1000)
    notifications.notify(ids.length
      ? `已导出 ${ids.length} 篇 Markdown 文章。`
      : `已导出全部 ${workspace.markdownDocuments.length} 篇 Markdown 文章。`, { type: 'success' })
  } catch (cause) {
    if (!workspace.error) notifications.notify(cause instanceof Error ? cause.message : '导出失败，请稍后重试。', { type: 'error' })
  }
}

async function insertMarkup(before: string, after = before, placeholder = '文字') {
  const textarea = editorTextarea.value
  if (!textarea) return
  const start = textarea.selectionStart
  const end = textarea.selectionEnd
  const selected = draft.content.slice(start, end) || placeholder
  draft.content = `${draft.content.slice(0, start)}${before}${selected}${after}${draft.content.slice(end)}`
  await nextTick()
  textarea.focus()
  textarea.setSelectionRange(start + before.length, start + before.length + selected.length)
}

async function insertLinePrefix(prefix: string) {
  const textarea = editorTextarea.value
  if (!textarea) return
  const position = textarea.selectionStart
  const lineStart = draft.content.lastIndexOf('\n', Math.max(0, position - 1)) + 1
  draft.content = `${draft.content.slice(0, lineStart)}${prefix}${draft.content.slice(lineStart)}`
  await nextTick()
  textarea.focus()
  textarea.setSelectionRange(position + prefix.length, position + prefix.length)
}

function onSaveKeydown(event: KeyboardEvent) {
  handleSaveShortcut(event, componentActive && editorOpen.value && documentView.value === 'edit', () => {
    if (document.body.matches('.modal-open, .confirmation-open, .notifications-open')) return
    void saveDocument()
  })
}

function openTrash() {
  recoveryMode.value = 'trash'
}

function requestDelete(document: MarkdownDocument) {
  pendingDelete.value = document
}

async function exportDocuments(ids: string[] = []) {
  selectedIds.value = new Set(ids)
  await exportSelected()
}

async function moveSelectedToDomain() {
  if (!bulkDomainId.value) {
    notifications.notify('请先选择目标目录。', { type: 'warning' })
    return
  }
  const selected = workspace.markdownDocuments.filter((item) => selectedIds.value.has(item.id))
  if (selected.some((item) => item.version === undefined)) {
    notifications.notify('文章版本信息不完整，请刷新页面后重试。', { type: 'warning' })
    return
  }
  const destination = bulkDomainId.value === UNASSIGNED ? null : bulkDomainId.value
  try {
    await workspace.moveMarkdownDocumentsToDomain(
      selected.map((item) => ({ id: item.id, expectedVersion: item.version! })),
      destination,
    )
    const destinationName = destination
      ? workspace.domains.find((item) => item.id === destination)?.name ?? '目标目录'
      : '未分类'
    notifications.notify(`已将 ${selected.length} 篇文章移动到“${destinationName}”。`, { type: 'success' })
    selectedIds.value = new Set()
    bulkDomainId.value = ''
  } catch {
    // The workspace store exposes the server error through the global notification host.
  }
}

function contentWithoutPendingImages() {
  let content = draft.content
  for (const marker of imageUploads.keys()) content = completeImageUpload(content, marker, '')
  return content
}

function cancelImageUploads() {
  draft.content = contentWithoutPendingImages()
  for (const controller of imageUploads.values()) controller.abort()
  imageUploads.clear()
  imageUploading.value = 0
}

async function uploadImages(files: File[]) {
  if (!files.length || !editorOpen.value || documentView.value !== 'edit') return
  if (files.length + imageUploads.size > MAX_IMAGE_BATCH) {
    notifications.notify('一次最多上传 5 张图片，请等待当前上传完成后再试。', { type: 'warning' })
    return
  }
  const valid: File[] = []
  for (const file of files) {
    const error = validateImageFile(file)
    if (error) notifications.notify(`${file.name || '粘贴图片'}：${error}`, { type: 'warning' })
    else valid.push(file)
  }
  if (!valid.length) return
  const session = editorSession
  const controller = new AbortController()
  const jobs = valid.map((file) => ({ file, marker: `![图片上传中…](devnest-upload:${createClientId()})` }))
  for (const job of jobs) imageUploads.set(job.marker, controller)
  imageUploading.value = imageUploads.size
  const position = editorTextarea.value?.selectionEnd ?? draft.content.length
  const insertion = `\n${jobs.map((job) => job.marker).join('\n')}\n`
  draft.content = draft.content.slice(0, position) + insertion + draft.content.slice(position)
  await nextTick()
  editorTextarea.value?.focus()
  editorTextarea.value?.setSelectionRange(position + insertion.length, position + insertion.length)
  let completed = 0
  // Upload an accepted batch concurrently. A single shared controller still cancels the
  // complete batch when the editor closes or the account changes.
  await Promise.all(jobs.map(async ({ file, marker }) => {
    if (controller.signal.aborted) return
    try {
      const result = await uploadMarkdownImage(file, controller.signal)
      if (session !== editorSession || auth.workspaceKey !== draftOwnerId || controller.signal.aborted) return
      // Reuse the already-local file for the first preview instead of downloading it
      // immediately from OSS through the authenticated backend route.
      primeMarkdownImageCache(result.id, file.slice(0, file.size, result.contentType))
      const syntax = imageMarkdown(file.name, result.url)
      if (draft.content.includes(marker)) {
        draft.content = completeImageUpload(draft.content, marker, syntax)
        completed++
      } else {
        notifications.notify('图片已上传，但插入位置已被修改。可从消息详情复制图片语法。', { type: 'info', details: [syntax] })
      }
    } catch (cause) {
      if (!controller.signal.aborted && session === editorSession && auth.workspaceKey === draftOwnerId) {
        draft.content = completeImageUpload(draft.content, marker, '')
        notifications.notify(cause instanceof Error ? cause.message : '图片上传失败，请重试。', { type: 'error', title: '图片上传失败' })
      }
    } finally {
      imageUploads.delete(marker)
      imageUploading.value = imageUploads.size
    }
  }))
  if (!controller.signal.aborted && session === editorSession && completed) {
    notifications.notify(`${completed} 张图片已插入，请保存文章以同步到云端。`, { type: 'success' })
    flushLocalDraft()
  }
}

function onPasteImage(event: ClipboardEvent) {
  const files = Array.from(event.clipboardData?.items ?? [])
    .filter((item) => item.kind === 'file' && item.type.startsWith('image/'))
    .map((item) => item.getAsFile()).filter((file): file is File => !!file)
  if (!files.length) return
  event.preventDefault()
  void uploadImages(files)
}

function onDropImage(event: DragEvent) {
  const files = Array.from(event.dataTransfer?.files ?? [])
  if (!files.length) return
  event.preventDefault()
  event.stopPropagation()
  void uploadImages(files)
}

function onImageSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  void uploadImages(files)
}

function closeGuide() {
  guideOpen.value = false
  guideButton.value?.focus()
}

async function insertGuideExample(text: string) {
  if (editorMode.value === 'preview') editorMode.value = 'write'
  await nextTick()
  const position = editorTextarea.value?.selectionEnd ?? draft.content.length
  const insertion = `\n\n${text}\n\n`
  draft.content = draft.content.slice(0, position) + insertion + draft.content.slice(position)
  if (window.matchMedia('(max-width: 720px)').matches) guideOpen.value = false
  await nextTick()
  editorTextarea.value?.focus()
  editorTextarea.value?.setSelectionRange(position + insertion.length, position + insertion.length)
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'short', day: 'numeric' }).format(new Date(value))
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}

function domainName(id: string | null) {
  if (!id) return '未分类'
  return workspace.domains.find((item) => item.id === id)?.name ?? '已删除目录'
}

function scrollToHeading(id: string) {
  const target = Array.from(editorRoot.value?.querySelectorAll<HTMLElement>('[id]') ?? [])
    .find((element) => element.id === id)
  if (!target) return
  activeHeadingId.value = id
  target.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function onBeforeUnload(event: BeforeUnloadEvent) {
  const stored = flushLocalDraft()
  if (!imageUploading.value && saveState.value !== 'saving' && (!isDirty.value || stored)) return
  event.preventDefault()
  event.returnValue = ''
}

watch(() => workspace.markdownDocuments, (documents) => {
  const validIds = new Set(documents.map((item) => item.id))
  selectedIds.value = new Set([...selectedIds.value].filter((id) => validIds.has(id)))
}, { deep: false })
watch(readerHeadings, (headings) => { activeHeadingId.value = headings[0]?.id ?? '' }, { immediate: true })
watch(editorOpen, (editing) => emit('editing-change', editing), { immediate: true })
watch(draftError, (message) => {
  if (message) notifications.notify(message, { type: 'warning', title: '本机草稿提醒' })
})
watch(() => [draft.title, draft.fileName], () => {
  if (validationField.value === 'title' && (draft.title.trim() || !isDefaultFileName(draft.fileName))) {
    validationField.value = null
    if (saveState.value === 'error') saveState.value = 'idle'
  }
})
watch(() => draft.content, (value) => {
  if (validationField.value === 'content' && value.trim()) {
    validationField.value = null
    if (saveState.value === 'error') saveState.value = 'idle'
  }
})

window.addEventListener('beforeunload', onBeforeUnload)
window.addEventListener('keydown', onSaveKeydown, true)
function onVisibilityChange() { if (window.document.visibilityState === 'hidden') flushLocalDraft() }
window.addEventListener('pagehide', flushLocalDraft)
window.addEventListener('storage', refreshLocalDrafts)
window.document.addEventListener('visibilitychange', onVisibilityChange)
watch(() => serializeDraft(), () => {
  if (!editorOpen.value || documentView.value !== 'edit') return
  if (isDirty.value && saveState.value === 'saved') saveState.value = 'idle'
  localSavedAt.value = ''
  clearTimeout(draftTimer)
  draftTimer = setTimeout(flushLocalDraft, 600)
})
watch(() => auth.workspaceKey, (id) => {
  if (id === draftOwnerId) return
  cancelImageUploads()
  flushLocalDraft()
  editorSession++
  editorOpen.value = false
  showDrafts.value = false
  showShares.value = false
  shareSecret.value = null
  pendingDelete.value = null
  pendingDiscard.value = null
  recoveryMode.value = null
  localDrafts.value = []
}, { flush: 'sync' })
onActivated(() => { componentActive = true; refreshLocalDrafts() })
onDeactivated(() => { componentActive = false; cancelImageUploads(); flushLocalDraft(); showDrafts.value = false; showShares.value = false; shareSecret.value = null; recoveryMode.value = null; pendingDelete.value = null; pendingDiscard.value = null })
onBeforeUnmount(() => {
  componentActive = false
  cancelImageUploads()
  flushLocalDraft()
  shareSecret.value = null
  editorSession++
  clearTimeout(draftTimer)
  window.removeEventListener('beforeunload', onBeforeUnload)
  window.removeEventListener('keydown', onSaveKeydown, true)
  window.removeEventListener('pagehide', flushLocalDraft)
  window.removeEventListener('storage', refreshLocalDrafts)
  window.document.removeEventListener('visibilitychange', onVisibilityChange)
})
refreshLocalDrafts()
onBeforeRouteLeave(() => canLeaveEditor())
onBeforeRouteUpdate((to, from) => to.query.workspace !== from.query.workspace ? canLeaveEditor() : true)

defineExpose({
  openCreate,
  openDocument,
  canLeaveEditor,
  toggleFavorite,
  requestDelete,
  openTrash,
  triggerImport,
  importFiles,
  exportDocuments,
})
</script>

<template>
  <div class="markdown-workspace" @dragover.prevent @drop.prevent="onDrop">
    <Transition name="workspace-view" mode="out-in">
    <section v-if="!editorOpen" class="markdown-list-view" :class="{ 'headless-list': props.headlessList }">
      <div class="markdown-list-header" :class="{ 'compact-list-header': props.compactListHeader }">
      <div v-if="!props.compactListHeader" class="markdown-heading">
        <span class="markdown-heading__icon"><Folder :size="17" /></span>
        <div class="markdown-heading__copy"><span>当前目录</span><h2>{{ selectedDomainName }}</h2></div>
        <strong aria-live="polite">{{ filteredDocuments.length }} 篇文章</strong>
      </div>

      <div class="markdown-toolbar">
        <button v-if="localDrafts.length" class="toolbar-button" type="button" title="继续编辑本浏览器中尚未同步的草稿" @click="openDraftManager"><RotateCcw :size="15" />本机草稿 {{ localDrafts.length }}</button>
        <button class="toolbar-button" type="button" @click="recoveryMode = 'trash'"><Trash2 :size="16" />回收站</button>
        <button class="toolbar-button" :class="{ active: favoriteOnly }" :aria-pressed="favoriteOnly" type="button" @click="favoriteOnly = !favoriteOnly"><Heart :size="16" :fill="favoriteOnly ? 'currentColor' : 'none'" />只看收藏</button>
        <button class="toolbar-button" type="button" @click="triggerImport"><Upload :size="16" />导入</button>
        <button class="toolbar-button" type="button" :disabled="!workspace.markdownDocuments.length || workspace.mutating" @click="exportSelected"><Download :size="16" />{{ selectedIds.size ? `导出 ${selectedIds.size}` : '导出全部' }}</button>
        <input ref="fileInput" class="sr-only" type="file" accept=".md,.markdown,.mdown,.zip,text/markdown,application/zip" multiple @change="onFileChange" />
      </div>

      </div>
      <Transition name="reveal"><p v-if="search" class="list-context search-summary">搜索“{{ search }}”<button type="button" @click="searchState.clear()">清除</button></p></Transition>

      <Transition name="reveal"><div v-if="selectedIds.size" class="bulk-domain-bar">
        <strong>已选择 {{ selectedIds.size }} 篇</strong>
        <span>批量调整目录</span>
        <select v-model="bulkDomainId" aria-label="选择目标目录">
          <option value="" disabled>选择目标目录</option>
          <option :value="UNASSIGNED">未分类</option>
          <option v-for="domain in workspace.domains" :key="domain.id" :value="domain.id">{{ domain.name }}</option>
        </select>
        <button class="button button-primary" type="button" :disabled="workspace.mutating" @click="moveSelectedToDomain"><Folder :size="15" />移动文章</button>
        <button class="button button-ghost" type="button" @click="selectedIds = new Set(); bulkDomainId = ''">取消选择</button>
      </div></Transition>

      <Transition name="reveal"><div v-if="importProgress.active" class="import-progress" role="status">
        <LoaderCircle class="spin" :size="17" /><div><span>{{ importProgress.message }}</span><div><i :style="{ width: `${importProgress.total ? importProgress.current / importProgress.total * 100 : 0}%` }"></i></div></div><b>{{ importProgress.current }}/{{ importProgress.total }}</b>
      </div></Transition>

      <TransitionGroup v-if="filteredDocuments.length" name="list" tag="div" class="document-list">
        <div key="document-head" class="document-list__head">
          <button type="button" :aria-label="allVisibleSelected ? '取消全选' : '全选当前结果'" @click="toggleSelectAll"><CheckSquare v-if="allVisibleSelected" :size="17" /><Square v-else :size="17" /></button>
          <span>文章</span><span>目录</span><span>更新日期</span><span>操作</span>
        </div>
        <article v-for="document in filteredDocuments" :key="document.id" class="document-row" :class="{ selected: selectedIds.has(document.id) }">
          <button class="select-button" type="button" :aria-label="selectedIds.has(document.id) ? '取消选择' : '选择文章'" @click="toggleSelection(document.id)"><CheckSquare v-if="selectedIds.has(document.id)" :size="18" /><Square v-else :size="18" /></button>
          <button class="document-summary" type="button" :aria-label="`预览文章 ${document.title}`" :disabled="openingId === document.id" @click="openDocument(document)"><span><FileText :size="16" /><strong>{{ document.title }}</strong><Heart v-if="document.favorite" :size="13" fill="currentColor" /></span><small>{{ markdownExcerpt(document.excerpt ?? document.content ?? '') || '这篇文章还没有正文，点击查看文章。' }}</small><code>{{ document.fileName }}</code></button>
          <span class="document-domain"><Folder :size="13" />{{ domainName(document.domainId) }}</span>
          <time>{{ formatDate(document.updatedAt) }}</time>
          <div class="document-actions"><button type="button" :title="document.favorite ? '取消收藏' : '收藏'" @click="toggleFavorite(document)"><Heart :size="16" :fill="document.favorite ? 'currentColor' : 'none'" /></button><button type="button" title="编辑文章" :aria-label="`编辑文章 ${document.title}`" :disabled="openingId === document.id" @click="openDocument(document, 'edit')"><Edit3 :size="16" /></button><button class="danger" type="button" title="删除文章" @click="pendingDelete = document"><Trash2 :size="16" /></button></div>
        </article>
      </TransitionGroup>

      <EmptyState v-else :title="workspace.markdownDocuments.length ? '没有匹配的文章' : '开始建立你的个人知识库'" :description="workspace.markdownDocuments.length ? '尝试调整搜索词、目录或收藏筛选。' : '新建文章，或批量拖入 Markdown / ZIP；ZIP 中的相对图片会自动上传 OSS。'">
        <div class="empty-actions"><button class="button button-primary" type="button" @click="openCreate"><Plus :size="16" />写第一篇文章</button><button class="button button-secondary" type="button" @click="triggerImport"><Upload :size="16" />导入 Markdown</button></div>
      </EmptyState>

      <p v-if="filteredDocuments.length" class="drop-hint"><Upload :size="14" />可拖入多个 Markdown 文件或 ZIP；默认导入到当前目录，在“全部知识”下导入则归入未分类</p>
    </section>

    <section v-else-if="documentView === 'preview'" ref="editorRoot" class="markdown-reader" aria-label="文章预览">
      <header class="markdown-reader__bar">
        <button class="editor-back" type="button" aria-label="返回文章列表" @click="closeEditor"><ArrowLeft :size="18" /><span>返回文章</span></button>
        <div class="editor-actions"><button class="toolbar-button outline-toggle" type="button" :aria-label="tocCollapsed ? '显示标题导航' : '隐藏标题导航'" :aria-expanded="!tocCollapsed" aria-controls="markdown-outline" :title="tocCollapsed ? '显示标题导航' : '隐藏标题导航'" @click="tocCollapsed = !tocCollapsed"><ListTree :size="16" /><span>{{ tocCollapsed ? '显示导航' : '隐藏导航' }}</span></button><button class="toolbar-button" type="button" @click="openHistory"><History :size="16" /><span>历史版本</span></button><button class="toolbar-button share-button" type="button" @click="openShare"><Share2 :size="16" /><span>分享</span></button><WorkspaceUtilities /><button class="toolbar-button" type="button" @click="documentView = 'edit'"><Edit3 :size="16" /><span>编辑文章</span></button></div>
      </header>
      <div class="markdown-reader__layout" :class="{ 'outline-collapsed': tocCollapsed }">
        <aside v-if="!tocCollapsed" id="markdown-outline" class="markdown-outline" aria-label="文章标题导航">
          <div class="markdown-outline__head"><span><ListTree :size="15" />标题导航</span><small>{{ readerHeadings.length }}</small></div>
          <nav v-if="readerHeadings.length" aria-label="文章标题树">
            <MarkdownOutlineTree :nodes="readerHeadingTree" :active-id="activeHeadingId" @select="scrollToHeading" />
          </nav>
          <p v-else>正文中还没有 Markdown 标题。添加 <code>## 标题</code> 后会自动生成导航。</p>
        </aside>
        <div class="markdown-reader__paper">
          <header class="markdown-reader__headline">
            <span><Folder :size="14" />{{ domainName(draft.domainId) }}</span>
            <h1>{{ draft.title }}</h1>
            <div><code>{{ draft.fileName }}</code><span>{{ draft.content.length.toLocaleString() }} 字符</span><span>约 {{ readingMinutes }} 分钟阅读</span></div>
          </header>
          <!-- MarkdownContent escapes raw HTML and loads private images with authentication. -->
          <Transition name="content-swap" mode="out-in">
            <MarkdownContent v-if="draft.content.trim()" class="markdown-reader__body" :source="readerContent" />
            <div v-else class="preview-empty markdown-reader__empty"><FileText :size="30" /><strong>这篇文章还没有正文</strong><span>可以返回列表后通过编辑按钮补充内容。</span></div>
          </Transition>
        </div>
      </div>
    </section>

    <section v-else ref="editorRoot" class="markdown-editor" :class="`mode-${editorMode}`">
      <header class="editor-header">
        <button class="editor-back" type="button" aria-label="返回文章" @click="closeEditor"><ArrowLeft :size="18" /><span>返回文章</span></button>
        <div class="editor-title">
          <input ref="titleInput" v-model="draft.title" maxlength="200" placeholder="输入文章标题" aria-label="文章标题" :aria-invalid="validationField === 'title'" :class="{ invalid: validationField === 'title' }" />
          <span role="status"><i :class="{ dirty: isDirty, error: saveState === 'error' || draftError }"></i>{{ saveStatus }}</span>
        </div>
        <div class="editor-actions">
          <button v-if="editingId" class="toolbar-button share-button" type="button" aria-label="分享文章" title="分享文章" :disabled="saveState === 'saving'" @click="openShare"><Share2 :size="16" /><span>分享</span></button>
          <button v-if="editingId" class="toolbar-button history-button" type="button" aria-label="历史版本" title="历史版本" :disabled="saveState === 'saving'" @click="openHistory"><History :size="16" /><span>历史版本</span></button>
          <WorkspaceUtilities />
          <button class="button button-primary save-button" type="button" aria-label="保存文章" :disabled="workspace.mutating || saveState === 'saving'" @click="saveDocument"><Transition name="icon-swap" mode="out-in"><LoaderCircle v-if="saveState === 'saving'" key="saving" class="spin" :size="16" /><Check v-else-if="saveState === 'saved'" key="saved" :size="16" /><Save v-else key="save" :size="16" /></Transition><span>{{ saveState === 'saving' ? '保存中' : saveState === 'saved' ? '已保存' : '保存文章' }}</span></button>
        </div>
      </header>

      <div class="editor-meta">
        <label><span>文件名</span><input v-model="draft.fileName" maxlength="180" placeholder="article.md" aria-label="文件名" /></label>
        <label><span>目录</span><select v-model="draft.domainId" aria-label="目录"><option :value="null">未分类</option><option v-for="domain in workspace.domains" :key="domain.id" :value="domain.id">{{ domain.name }}</option></select></label>
        <label class="favorite-field"><input v-model="draft.favorite" type="checkbox" aria-label="收藏文章" /><Heart :size="15" :fill="draft.favorite ? 'currentColor' : 'none'" />收藏文章</label>
        <button ref="guideButton" class="guide-toggle" :class="{ active: guideOpen }" type="button" aria-label="Markdown 语法手册" :aria-expanded="guideOpen" aria-controls="markdown-guide" @click="guideOpen = !guideOpen"><BookOpen :size="15" /><span>语法手册</span></button>
        <button v-if="localDrafts.some((item) => item.documentId === editingId && item.id !== localDraftId)" class="editor-drafts-link" type="button" @click="openDraftManager"><RotateCcw :size="14" />可恢复草稿</button>
        <div class="view-switch" role="group" aria-label="编辑器视图"><button type="button" :class="{ active: editorMode === 'write' }" title="仅编辑" aria-label="编辑" @click="editorMode = 'write'"><PenLine :size="15" /><span>编辑</span></button><button type="button" :class="{ active: editorMode === 'split' }" title="双栏" aria-label="双栏" @click="editorMode = 'split'"><Columns2 :size="15" /><span>双栏</span></button><button type="button" :class="{ active: editorMode === 'preview' }" title="仅预览" aria-label="预览" @click="editorMode = 'preview'"><Eye :size="15" /><span>预览</span></button></div>
      </div>

      <div v-if="imageUploading" class="image-upload-status" role="status"><LoaderCircle class="spin" :size="14" />正在上传 {{ imageUploading }} 张图片，可以继续输入正文…</div>
      <div class="editor-workarea" :class="{ 'guide-open': guideOpen }">
      <div class="editor-columns">
        <section class="write-pane">
          <div class="format-toolbar" aria-label="Markdown 快捷格式">
            <button type="button" title="二级标题" @click="insertLinePrefix('## ')"><Heading2 :size="16" /></button>
            <button type="button" title="粗体" @click="insertMarkup('**', '**', '粗体文字')"><Bold :size="16" /></button>
            <button type="button" title="斜体" @click="insertMarkup('*', '*', '斜体文字')"><Italic :size="16" /></button>
            <button type="button" title="链接" @click="insertMarkup('[', '](https://)', '链接文字')"><Link2 :size="16" /></button>
            <button type="button" title="无序列表" @click="insertLinePrefix('- ')"><List :size="16" /></button>
            <button type="button" title="引用" @click="insertLinePrefix('> ')"><Quote :size="16" /></button>
            <button type="button" title="行内代码" @click="insertMarkup('`', '`', 'code')"><Code :size="16" /></button>
            <button type="button" title="上传图片（也可粘贴或拖入正文，单张 ≤ 20 MB）" aria-label="上传图片" :disabled="imageUploading >= MAX_IMAGE_BATCH" @click="imageInput?.click()"><ImagePlus :size="16" /></button>
            <span>{{ draft.content.length.toLocaleString() }} 字符</span>
          </div>
          <input ref="imageInput" type="file" accept="image/png,image/jpeg,image/gif,image/webp" multiple hidden @change="onImageSelected" />
          <textarea ref="editorTextarea" v-model="draft.content" maxlength="1000000" spellcheck="false" placeholder="# 从这里开始写作&#10;&#10;支持标题、列表、引用、代码块、链接、表格等常用 Markdown 语法。&#10;可直接粘贴截图，或使用工具栏上传图片。" aria-label="Markdown 正文" :aria-invalid="validationField === 'content'" :class="{ invalid: validationField === 'content' }" @paste="onPasteImage" @dragover.prevent @drop="onDropImage"></textarea>
        </section>

        <section class="preview-pane">
          <div class="preview-head"><span><Eye :size="15" />实时预览</span><small>约 {{ readingMinutes }} 分钟阅读</small></div>
          <Transition name="content-swap" mode="out-in">
            <MarkdownContent v-if="draft.content.trim()" :source="draft.content" tabindex="0" aria-label="Markdown 实时预览" />
            <div v-else class="preview-empty"><FileText :size="28" /><strong>预览会实时出现在这里</strong><span>左侧输入 Markdown 正文即可开始。</span></div>
          </Transition>
        </section>
      </div>

      <Transition name="guide"><MarkdownGuide v-if="guideOpen" @close="closeGuide" @insert="insertGuideExample" /></Transition>
      </div>
      <footer class="mobile-save-bar"><button class="button button-ghost" type="button" @click="closeEditor">返回</button><button class="button button-primary" type="button" :disabled="workspace.mutating" @click="saveDocument"><Save :size="16" />保存文章</button></footer>
    </section>
    </Transition>

    <AppModal v-if="showShares" title="分享这篇文章" description="生成一个无需登录即可打开的只读链接，并为它设置自动失效时间。" wide @close="closeShare">
      <div class="share-dialog">
        <p v-if="isDirty" class="share-notice"><Clock3 :size="16" />当前还有未保存修改；访客看到的是最近一次成功保存到云端的内容。</p>

        <form class="share-create" @submit.prevent="createShare">
          <div><strong>创建新链接</strong><span>链接令牌只显示一次，数据库不会保存明文。</span></div>
          <label><span>有效期</span><input v-model.number="shareDays" type="number" min="1" max="365" step="1" required aria-label="分享链接有效天数" /><b>天</b></label>
          <button class="button button-primary" type="submit" :disabled="shareBusy"><LoaderCircle v-if="shareBusy" class="spin" :size="16" /><Share2 v-else :size="16" />{{ shareBusy ? '生成中' : '生成链接' }}</button>
        </form>

        <p v-if="shareError" class="share-error" role="alert"><AlertCircle :size="16" />{{ shareError }}</p>

        <section v-if="shareSecret" class="share-secret" aria-live="polite">
          <div><strong>分享链接已生成</strong><span>有效至 {{ formatDateTime(shareSecret.expiresAt) }}</span></div>
          <label><span class="sr-only">新生成的分享链接</span><textarea :value="shareUrl" readonly rows="2" @focus="selectShareUrl" /></label>
          <p><Clock3 :size="15" />请现在复制保存；关闭窗口后无法再次查看这条链接。</p>
          <div class="share-secret__actions"><button class="button button-primary" type="button" @click="copyShareUrl"><Copy :size="16" />复制链接</button><a class="button button-secondary" :href="shareUrl" target="_blank" rel="noopener noreferrer">打开预览</a></div>
        </section>

        <section class="share-history">
          <div class="share-history__head"><div><strong>已创建的链接</strong><span>可查看有效期和撤销状态，但不会重新展示令牌。</span></div><b>{{ shareLinks.filter((item) => item.active).length }} 个有效</b></div>
          <p v-if="shareLoading" class="share-loading" role="status"><LoaderCircle class="spin" :size="17" />正在读取分享记录…</p>
          <div v-else-if="shareLinks.length" class="share-link-list">
            <article v-for="link in shareLinks" :key="link.id" class="share-link-row">
              <span class="share-link-icon"><Share2 :size="16" /></span>
              <div><strong>{{ link.active ? '有效分享链接' : link.revokedAt ? '已撤销' : '已过期' }}</strong><small>创建于 {{ formatDateTime(link.createdAt) }} · 有效至 {{ formatDateTime(link.expiresAt) }}</small></div>
              <span class="share-status" :class="{ active: link.active }">{{ link.active ? '有效' : link.revokedAt ? '已撤销' : '已过期' }}</span>
              <button v-if="link.active" class="button button-ghost" type="button" :disabled="shareBusy" @click="revokeShare(link)">撤销</button>
            </article>
          </div>
          <p v-else class="share-empty">还没有创建过分享链接。</p>
        </section>
      </div>
    </AppModal>

    <AppModal v-if="showDrafts" title="本机草稿" description="草稿仅保存在此浏览器。恢复后请保存到云端；清除浏览器数据会移除本机草稿。" @close="!recoveryBusy && (showDrafts = false)">
      <div v-if="pendingDiscard" class="draft-banner" role="alertdialog" aria-label="确认丢弃草稿"><p>丢弃“{{ pendingDiscard.draft.title || '未命名文章' }}”的本机草稿？此操作不会删除云端文章。</p><button type="button" class="button button-secondary" @click="pendingDiscard = null">取消</button><button type="button" class="button button-primary" @click="discardDraft(pendingDiscard)">确认丢弃</button></div>
      <p v-if="recoveryBusy" role="status">正在恢复草稿…</p>
      <TransitionGroup name="list" tag="div">
      <div v-for="item in localDrafts" :key="item.id" class="local-draft-row">
        <div><strong>{{ item.draft.title || item.draft.fileName || '未命名文章' }}</strong><small>{{ new Date(item.savedAt).toLocaleString('zh-CN') }} · {{ item.draft.content.length.toLocaleString() }} 字符</small></div>
        <div class="local-draft-actions">
          <button class="button button-primary" type="button" :disabled="recoveryBusy" @click="recoverDraft(item)"><RotateCcw :size="15" />恢复草稿</button>
          <button v-if="item.documentId" class="button button-secondary" type="button" :disabled="recoveryBusy" @click="recoverDraft(item, true)">另存为新文章</button>
          <button class="button button-ghost" type="button" :disabled="recoveryBusy || (editorOpen && item.id === localDraftId)" @click="pendingDiscard = item">丢弃</button>
        </div>
      </div>
      </TransitionGroup>
      <EmptyState v-if="!localDrafts.length" title="没有待恢复的草稿" description="编辑时会自动保存本机草稿，成功保存到云端后清除对应草稿。" />
    </AppModal>
    <AppModal v-if="pendingDelete" title="移到回收站" :description="`将文章“${pendingDelete.title}”移到回收站？文章内容和历史版本都会保留，之后可以恢复。`" @close="!workspace.mutating && (pendingDelete = null)">
      <div class="local-draft-actions"><button class="button button-secondary" type="button" :disabled="workspace.mutating" @click="pendingDelete = null">取消</button><button class="button button-primary" type="button" :disabled="workspace.mutating" @click="removeDocument(pendingDelete)">确认移到回收站</button></div>
    </AppModal>
    <MarkdownRecoveryPanel v-if="recoveryMode" :mode="recoveryMode" :document="currentDocument" @close="recoveryMode = null" @restored="onVersionRestored" />
  </div>
</template>

<style scoped>
.editor-workarea { position: relative; display: flex; flex: 1; min-height: 0; min-width: 0; overflow: hidden; }
.editor-workarea > .editor-columns { flex: 1; min-width: 0; min-height: 0; height: auto; }
.guide-toggle { display: flex; align-items: center; gap: 6px; height: 32px; padding: 0 9px; margin-left: auto; flex-shrink: 0; color: var(--muted); border: 1px solid var(--border); border-radius: 7px; background: var(--panel); cursor: pointer; font-size: 12px; }
.guide-toggle.active, .guide-toggle:hover { color: var(--accent); background: var(--accent-soft); }
.guide-toggle ~ .view-switch { margin-left: 0; }
.image-upload-status { display: flex; align-items: center; flex-shrink: 0; gap: 7px; padding: 7px 16px; color: var(--accent); border-bottom: 1px solid var(--border); background: var(--accent-soft); font-size: 12px; }
.guide-enter-active, .guide-leave-active { transition: transform .2s ease, opacity .2s ease; }
.guide-enter-from, .guide-leave-to { opacity: 0; transform: translateX(24px); }
@media (max-width: 720px) { .format-toolbar { gap: 0 !important; padding-inline: 6px !important; } .format-toolbar button { width: 28px !important; flex-shrink: 0; } .format-toolbar > span { font-size: 10px !important; } }
.draft-banner { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; margin-bottom: 18px; padding: 15px 18px; border: 1px solid var(--border); border-radius: 12px; background: var(--accent-bg); font-size: var(--font-sm); }
.local-draft-row { padding: 16px 0; border-bottom: 1px solid var(--border); overflow-wrap: anywhere; }
.local-draft-row small { display: block; margin: 7px 0 12px; color: var(--muted); font-size: var(--font-xs); }
.local-draft-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.share-dialog { display: grid; gap: 18px; }
.share-notice, .share-error { display: flex; align-items: flex-start; gap: 8px; margin: 0; padding: 11px 13px; border: 1px solid var(--accent-border); border-radius: 9px; color: var(--accent); background: var(--accent-bg); font-size: 12px; line-height: 1.55; }
.share-notice svg, .share-error svg { flex: 0 0 auto; margin-top: 1px; }
.share-error { color: var(--danger); border-color: color-mix(in srgb, var(--danger) 28%, var(--border)); background: color-mix(in srgb, var(--danger) 7%, var(--panel)); }
.share-create { display: grid; grid-template-columns: minmax(0, 1fr) auto auto; align-items: end; gap: 14px; padding: 17px; border: 1px solid var(--border); border-radius: 12px; background: var(--surface-sunken); }
.share-create > div strong, .share-create > div span, .share-secret > div strong, .share-secret > div span, .share-history__head strong, .share-history__head span { display: block; }
.share-create > div strong, .share-secret > div strong, .share-history__head strong { color: var(--text); font-size: 13px; }
.share-create > div span, .share-secret > div span, .share-history__head span { margin-top: 4px; color: var(--muted); font-size: 11px; line-height: 1.5; }
.share-create label { display: grid; grid-template-columns:auto 74px auto; align-items: center; gap: 7px; color: var(--muted); font-size: 12px; }
.share-create input { width: 74px; height: 38px; padding: 0 9px; color: var(--text); border: 1px solid var(--border); border-radius: 8px; outline: 0; background: var(--panel); }
.share-create input:focus { border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }
.share-create b { font-weight: 500; }
.share-secret { display: grid; grid-template-columns:minmax(0,1fr) auto; gap: 13px 16px; padding: 17px; border: 1px solid var(--accent-border); border-radius: 12px; background: var(--accent-bg); }
.share-secret label { grid-column: 1 / -1; }
.share-secret textarea { width: 100%; resize: none; padding: 10px 11px; color: var(--text); border: 1px solid var(--accent-border); border-radius: 8px; outline: 0; background: var(--panel); font: 12px/1.55 "Cascadia Code", Consolas, monospace; overflow-wrap: anywhere; }
.share-secret textarea:focus { border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }
.share-secret > p { display: flex; align-items: center; gap: 6px; margin: 0; color: var(--muted); font-size: 11px; }
.share-secret__actions { display: flex; justify-content: flex-end; gap: 8px; }
.share-history { display: grid; gap: 11px; }
.share-history__head { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.share-history__head b { padding: 4px 8px; color: var(--accent); border-radius: 999px; background: var(--accent-bg); font-size: 11px; white-space: nowrap; }
.share-loading, .share-empty { display: flex; align-items: center; justify-content: center; gap: 7px; min-height: 76px; margin: 0; color: var(--muted); border: 1px dashed var(--border); border-radius: 10px; font-size: 12px; }
.share-link-list { overflow: hidden; border: 1px solid var(--border); border-radius: 11px; }
.share-link-row { min-height: 68px; display: grid; grid-template-columns: 34px minmax(0,1fr) auto 66px; align-items: center; gap: 11px; padding: 10px 12px; border-bottom: 1px solid var(--border); }
.share-link-row:last-child { border-bottom: 0; }
.share-link-icon { width: 32px; height: 32px; display: grid; place-items: center; color: var(--accent); border-radius: 8px; background: var(--accent-bg); }
.share-link-row > div { min-width: 0; }
.share-link-row > div strong, .share-link-row > div small { display: block; }
.share-link-row > div strong { font-size: 12px; }
.share-link-row > div small { overflow: hidden; margin-top: 4px; color: var(--muted); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.share-status { padding: 4px 7px; color: var(--muted); border-radius: 999px; background: var(--surface-sunken); font-size: 10px; white-space: nowrap; }
.share-status.active { color: var(--accent); background: var(--accent-bg); }
.share-link-row > button { min-height: 32px; padding-inline: 10px; }
@media (max-width: 720px) {
  .history-button span, .share-button span, .markdown-reader__bar .toolbar-button span { display: none; }
  .markdown-reader__bar { gap: 6px; padding-inline: 8px; }
  .markdown-reader__bar .editor-actions { gap: 2px; }
  .markdown-reader__bar .toolbar-button { width: 32px; padding: 0; }
  .share-create { grid-template-columns: 1fr; align-items: stretch; }
  .share-create label { width: fit-content; }
  .share-create .button { width: 100%; }
  .share-secret { grid-template-columns: 1fr; }
  .share-secret__actions { justify-content: stretch; flex-direction: column; }
  .share-secret__actions .button { width: 100%; }
  .share-link-row { grid-template-columns: 32px minmax(0,1fr) auto; }
  .share-link-row > button { grid-column: 2 / -1; width: fit-content; }
}
.markdown-workspace { min-width: 0; }
.markdown-list-view { min-width: 0; }
.markdown-list-view.headless-list { display: none; }
.workspace-view-enter-active, .workspace-view-leave-active { transition: opacity var(--motion-base) ease, transform var(--motion-slow) var(--ease-emphasized); }
.workspace-view-enter-from { opacity: 0; transform: translateX(12px); }
.workspace-view-leave-to { opacity: 0; transform: translateX(-8px); }
.markdown-list-header { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 16px 24px; margin-bottom: 18px; padding-bottom: 17px; border-bottom: 1px solid var(--border); }
.markdown-list-header.compact-list-header { justify-content: flex-end; margin-bottom: 14px; padding-bottom: 0; border-bottom: 0; }
.markdown-heading { min-width: 0; display: flex; align-items: center; gap: 11px; }
.markdown-heading__icon { width: 38px; height: 38px; display: grid; flex: 0 0 auto; place-items: center; color: var(--accent); border: 1px solid var(--accent-border); border-radius: 11px; background: var(--accent-bg); }
.markdown-heading__copy { min-width: 0; }
.markdown-heading__copy > span { display: block; margin-bottom: 2px; color: var(--muted); font-size: 10px; font-weight: 650; letter-spacing: .08em; }
.markdown-heading h2 { overflow: hidden; margin: 0; font-size: 22px; font-weight: 700; letter-spacing: -.45px; text-overflow: ellipsis; white-space: nowrap; }
.markdown-heading > strong { align-self: flex-end; margin-bottom: 2px; color: var(--muted); font-size: 12px; font-weight: 400; white-space: nowrap; }
.markdown-toolbar { display: flex; align-items: center; flex-wrap: wrap; justify-content: flex-end; gap: 7px; }
.search-summary { margin: -2px 0 16px; }
.toolbar-button { height: 36px; display: inline-flex; align-items: center; justify-content: center; gap: 6px; padding: 0 11px; color: var(--subtle); border: 1px solid var(--border); border-radius: 9px; background: var(--surface-sunken); cursor: pointer; font-size: 12px; white-space: nowrap; transition: color var(--motion-fast), border-color var(--motion-fast), background var(--motion-fast), transform var(--motion-base) var(--ease-standard); }
.toolbar-button:hover:not(:disabled), .toolbar-button.active { color: var(--accent); border-color: var(--accent-border); background: var(--accent-bg); }
.toolbar-button:hover:not(:disabled) { transform: translateY(-1px); }
.toolbar-button:disabled { opacity: .45; cursor: not-allowed; }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0,0,0,0); }
.import-progress { display: flex; align-items: center; gap: 10px; margin-bottom: 13px; padding: 11px 13px; color: var(--muted); border: 1px solid var(--border); border-radius: 10px; background: var(--surface-sunken); font-size: var(--font-xs); }
.import-progress > div { min-width: 0; flex: 1; }
.import-progress > div > div { height: 3px; overflow: hidden; margin-top: 7px; border-radius: 999px; background: var(--border); }
.import-progress i { height: 100%; display: block; border-radius: inherit; background: var(--accent); transition: width .2s; }
.import-progress b { font-size: var(--font-2xs); }
.bulk-domain-bar { display: flex; align-items: center; flex-wrap: wrap; gap: 9px; margin: 0 0 13px; padding: 11px 13px; border: 1px solid var(--accent-border); border-radius: 10px; background: var(--accent-bg); }
.bulk-domain-bar strong { color: var(--accent); font-size: var(--font-xs); }
.bulk-domain-bar > span { color: var(--muted); font-size: var(--font-2xs); }
.bulk-domain-bar select { min-width: 180px; height: 34px; padding: 0 30px 0 10px; color: var(--text); border: 1px solid var(--border); border-radius: 8px; outline: 0; background: var(--panel); }
.bulk-domain-bar select:focus { border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }
.document-list{overflow:hidden;border:1px solid var(--border);border-radius:12px;background:var(--panel)}.document-list__head,.document-row{display:grid;grid-template-columns:28px minmax(180px,1.6fr) minmax(110px,.55fr) 112px 102px;align-items:center;gap:14px}.document-list__head{min-height:43px;padding:0 15px;color:var(--muted);border-bottom:1px solid var(--border);background:color-mix(in srgb,var(--surface-sunken) 82%,var(--panel));font-size:var(--font-2xs);font-weight:700}.document-list__head button,.select-button{display:grid;place-items:center;padding:6px;color:var(--muted);border:0;background:transparent;cursor:pointer}.document-row{min-height:94px;padding:13px 15px;border-bottom:1px solid var(--border);transition:background .15s}.document-row:last-child{border-bottom:0}.document-row:hover,.document-row.selected{background:color-mix(in srgb,var(--surface-sunken) 68%,transparent)}.document-row.selected{box-shadow:inset 3px 0 var(--accent)}.select-button:hover{color:var(--accent)}.document-summary{min-width:0;padding:0;text-align:left;border:0;background:transparent;cursor:pointer}.document-summary>span{display:flex;align-items:center;gap:7px;color:var(--accent)}.document-summary strong{overflow:hidden;color:var(--text);font-size:var(--font-sm);font-weight:700;text-overflow:ellipsis;white-space:nowrap}.document-summary small{display:block;overflow:hidden;margin-top:6px;color:var(--muted);font-size:var(--font-xs);line-height:1.45;text-overflow:ellipsis;white-space:nowrap}.document-summary code{display:block;margin-top:5px;color:var(--muted);background:transparent;font-size:var(--font-2xs)}.document-domain{width:fit-content;max-width:100%;display:inline-flex;align-items:center;gap:5px;overflow:hidden;padding:5px 8px;color:var(--subtle);border:1px solid var(--border);border-radius:999px;background:var(--surface-sunken);font-size:var(--font-2xs);text-overflow:ellipsis;white-space:nowrap}.document-row time{color:var(--muted);font-size:var(--font-2xs)}.document-actions{display:flex;justify-content:flex-end;gap:2px}.document-actions button{display:grid;width:32px;height:32px;place-items:center;color:var(--muted);border:0;border-radius:8px;background:transparent;cursor:pointer}.document-actions button:hover{color:var(--text);background:var(--surface-sunken)}.document-actions .danger:hover{color:var(--danger)}.drop-hint{display:flex;align-items:center;justify-content:center;gap:6px;margin:15px 0 0;color:var(--muted);font-size:var(--font-2xs)}.empty-actions{display:flex;gap:8px}
.markdown-reader{min-height:calc(100svh - 112px);overflow:hidden;scroll-margin-top:78px;border:1px solid var(--border);border-radius:15px;background:var(--panel)}.markdown-reader__bar{min-height:54px;display:flex;align-items:center;justify-content:space-between;gap:16px;padding:8px 14px;border-bottom:1px solid var(--border);background:color-mix(in srgb,var(--surface-sunken) 68%,var(--panel))}.markdown-reader__bar>span{display:flex;align-items:center;gap:6px;padding:0 8px;color:var(--muted);font-size:var(--font-2xs);font-weight:650}.markdown-reader__paper{width:min(100%,960px);min-height:calc(100svh - 168px);margin:0 auto;padding:clamp(38px,5vw,76px) clamp(28px,5vw,70px) 76px}.markdown-reader__headline{padding-bottom:28px;border-bottom:1px solid var(--border)}.markdown-reader__headline>span{display:flex;align-items:center;gap:6px;color:var(--accent);font-size:var(--font-xs);font-weight:700}.markdown-reader__headline h1{margin:13px 0 15px;color:var(--text);font-size:clamp(30px,3.2vw,48px);line-height:1.18;letter-spacing:-.035em}.markdown-reader__headline>div{display:flex;align-items:center;flex-wrap:wrap;gap:7px 16px;color:var(--muted);font-size:var(--font-2xs)}.markdown-reader__headline code{padding:4px 7px;color:var(--subtle);border-radius:6px;background:var(--surface-sunken);font:inherit}.markdown-reader__body{overflow:visible;padding:34px 0 0;font-size:16px;line-height:1.88}.markdown-reader__empty{min-height:360px}.markdown-reader__empty span{font-size:var(--font-xs)}
.markdown-reader__layout { min-height: calc(100svh - 168px); display: grid; grid-template-columns: minmax(190px, 226px) minmax(0, 1fr); align-items: start; }
.markdown-reader__layout.outline-collapsed { grid-template-columns: minmax(0, 1fr); }
.markdown-outline { position: sticky; top: 54px; max-height: calc(100svh - 54px); align-self: start; overflow: auto; padding: 18px 12px 24px; border-right: 1px solid var(--border); background: color-mix(in srgb, var(--surface-sunken) 72%, var(--panel)); scrollbar-width: thin; }
.markdown-outline__head { min-height: 34px; display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 0 8px 10px; color: var(--text); border-bottom: 1px solid var(--border); font-size: 12px; font-weight: 700; }
.markdown-outline__head span { display: flex; align-items: center; gap: 6px; }
.markdown-outline__head small { min-width: 22px; padding: 1px 6px; color: var(--muted); border-radius: 999px; background: var(--panel); font-size: 10px; text-align: center; }
.markdown-outline nav { display: block; }
.markdown-outline > p { margin: 13px 8px 0; color: var(--muted); font-size: 11px; line-height: 1.65; }
.markdown-outline > p code { color: var(--accent); }
.markdown-reader__body :deep(h1), .markdown-reader__body :deep(h2), .markdown-reader__body :deep(h3), .markdown-reader__body :deep(h4), .markdown-reader__body :deep(h5), .markdown-reader__body :deep(h6) { scroll-margin-top: 76px; }
.markdown-editor{overflow:hidden;scroll-margin-top:78px;border:1px solid var(--border);border-radius:15px;background:var(--panel);box-shadow:0 12px 36px rgba(0,0,0,.04)}.editor-header{min-height:72px;display:grid;grid-template-columns:auto minmax(180px,1fr) auto;align-items:center;gap:16px;padding:12px 15px;border-bottom:1px solid var(--border)}.editor-back{display:flex;align-items:center;gap:6px;padding:8px;color:var(--muted);border:0;border-radius:8px;background:transparent;cursor:pointer;font-size:var(--font-xs)}.editor-back:hover{color:var(--text);background:var(--surface-sunken)}.editor-title{min-width:0}.editor-title input{width:100%;padding:0;color:var(--text);border:0;outline:0;background:transparent;font-size:var(--font-lg);font-weight:750}.editor-title input::placeholder{color:var(--muted)}.editor-title>span{display:flex;align-items:center;gap:6px;margin-top:4px;color:var(--muted);font-size:var(--font-2xs)}.editor-title i{width:6px;height:6px;border-radius:50%;background:#6aaa83}.editor-title i.dirty{background:var(--accent)}.save-button{min-width:112px}.editor-meta{min-height:54px;display:flex;align-items:center;gap:14px;padding:8px 15px;border-bottom:1px solid var(--border);background:var(--surface-sunken)}.editor-meta>label:not(.favorite-field){display:flex;align-items:center;gap:7px;color:var(--muted);font-size:var(--font-2xs)}.editor-meta input,.editor-meta select{height:32px;padding:0 9px;color:var(--text);border:1px solid var(--border);border-radius:7px;outline:0;background:var(--panel);font-size:var(--font-xs)}.editor-meta label:first-child input{width:min(190px,18vw)}.editor-meta select{max-width:150px}.favorite-field{display:flex;align-items:center;gap:6px;color:var(--muted);font-size:var(--font-xs);white-space:nowrap}.favorite-field input{width:14px;height:14px;padding:0;accent-color:var(--accent)}.view-switch{display:flex;gap:3px;margin-left:auto;padding:3px;border:1px solid var(--border);border-radius:8px;background:var(--panel)}.view-switch button{height:29px;display:flex;align-items:center;gap:5px;padding:0 8px;color:var(--muted);border:0;border-radius:6px;background:transparent;cursor:pointer;font-size:var(--font-2xs)}.view-switch button.active{color:var(--accent);background:var(--accent-bg)}
.editor-title i.error { background: var(--danger); }
.view-switch button { transition: color var(--motion-fast), background var(--motion-fast), transform var(--motion-fast); }
.view-switch button:active { transform: scale(.94); }
.editor-title input.invalid { color: var(--danger); }
.write-pane textarea.invalid { box-shadow: inset 0 0 0 2px var(--danger); }
/* Keep the grid row and its flex children within the available editor height.
   An auto minimum otherwise lets long Markdown stretch both panes past the
   clipped editor shell, leaving no overflow for the mouse wheel to scroll. */
.editor-columns { height: clamp(540px, 63vh, 760px); display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); grid-template-rows: minmax(0, 1fr); transition: grid-template-columns var(--motion-slow) var(--ease-emphasized); }
.write-pane, .preview-pane { min-width: 0; min-height: 0; display: flex; flex-direction: column; overflow: hidden; }
.write-pane { border-right: 1px solid var(--border); }
.format-toolbar, .preview-head { height: 44px; flex: 0 0 44px; display: flex; align-items: center; gap: 3px; padding: 0 11px; border-bottom: 1px solid var(--border); background: color-mix(in srgb, var(--surface-sunken) 62%, var(--panel)); }
.format-toolbar button { display: grid; width: 30px; height: 30px; place-items: center; color: var(--muted); border: 0; border-radius: 6px; background: transparent; cursor: pointer; transition: color var(--motion-fast), background var(--motion-fast), transform var(--motion-fast); }
.format-toolbar button:hover { color: var(--text); background: var(--surface-sunken); }
.format-toolbar button:active { transform: scale(.9); }
.format-toolbar > span { margin-left: auto; color: var(--muted); font-size: var(--font-2xs); }
.write-pane textarea { min-width: 0; min-height: 0; flex: 1; padding: 22px; color: var(--code-text); border: 0; outline: 0; background: var(--code-bg); font: var(--font-sm)/1.75 "Cascadia Code", Consolas, monospace; resize: none; tab-size: 2; }
.write-pane textarea, .preview-pane > .markdown-body { overflow: auto; overscroll-behavior: auto; scrollbar-gutter: stable; }
.preview-pane > .markdown-body:focus-visible { outline-offset: -3px; }
.preview-head { justify-content: space-between; color: var(--muted); font-size: var(--font-2xs); }
.preview-head span { display: flex; align-items: center; gap: 6px; font-weight: 650; }
.markdown-body { min-height: 0; flex: 1; overflow: auto; padding: clamp(26px, 2.4vw, 44px); color: var(--text); font-size: 15px; line-height: 1.78; }
.preview-empty { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; color: var(--muted); text-align: center; }
.preview-empty strong { color: var(--text); font-size: var(--font-sm); }
.preview-empty span { font-size: var(--font-xs); }
.markdown-body :deep(h1),.markdown-body :deep(h2),.markdown-body :deep(h3),.markdown-body :deep(h4){margin:1.55em 0 .6em;line-height:1.28;letter-spacing:-.02em}.markdown-body :deep(h1:first-child),.markdown-body :deep(h2:first-child),.markdown-body :deep(h3:first-child){margin-top:0}.markdown-body :deep(h1){padding-bottom:.35em;border-bottom:1px solid var(--border);font-size:2em}.markdown-body :deep(h2){padding-bottom:.3em;border-bottom:1px solid var(--border);font-size:1.55em}.markdown-body :deep(h3){font-size:1.25em}.markdown-body :deep(p){margin:0 0 1.1em}.markdown-body :deep(a){color:var(--accent);text-decoration:underline;text-underline-offset:3px}.markdown-body :deep(img){max-width:100%;height:auto;border-radius:10px}.markdown-body :deep(blockquote){margin:1.2em 0;padding:.2em 1em;color:var(--muted);border-left:3px solid var(--accent);background:var(--surface-sunken)}.markdown-body :deep(pre){overflow:auto;margin:1.3em 0;padding:17px;border:1px solid var(--border);border-radius:10px;background:var(--code-bg)}.markdown-body :deep(code){padding:.15em .35em;color:var(--code-text);border-radius:4px;background:var(--code-bg);font:13px/1.6 "Cascadia Code",Consolas,monospace}.markdown-body :deep(pre code){padding:0;background:transparent}.markdown-body :deep(ul),.markdown-body :deep(ol){padding-left:1.55em}.markdown-body :deep(li){margin:.35em 0}.markdown-body :deep(.task-item){display:flex;align-items:flex-start;gap:7px;list-style:none}.markdown-body :deep(.task-item input){margin-top:.45em;accent-color:var(--accent)}.markdown-body :deep(hr){margin:2em 0;border:0;border-top:1px solid var(--border)}.markdown-body :deep(.markdown-table-wrap){overflow-x:auto;margin:1.3em 0}.markdown-body :deep(table){width:100%;border-collapse:collapse;font-size:var(--font-sm)}.markdown-body :deep(th),.markdown-body :deep(td){padding:9px 11px;text-align:left;border:1px solid var(--border)}.markdown-body :deep(th){background:var(--surface-sunken)}.mobile-save-bar{display:none}
.mode-write .editor-columns{grid-template-columns:1fr}.mode-write .preview-pane{display:none}.mode-write .write-pane{border-right:0}.mode-preview .editor-columns{grid-template-columns:1fr}.mode-preview .write-pane{display:none}
@media(max-width:1260px){.document-list__head,.document-row{grid-template-columns:24px minmax(100px,1fr) 90px 96px 94px}}
@media(max-width:1100px){.editor-meta{flex-wrap:wrap}.editor-columns{height:clamp(520px,62vh,700px)}.mode-split .preview-pane{display:none}.mode-split .editor-columns{grid-template-columns:1fr}.mode-split .write-pane{border-right:0}.view-switch button:nth-child(2){display:none}.view-switch button span{display:none}.mode-split .view-switch button:first-child{color:var(--accent);background:var(--accent-bg)}}
@media(max-width:900px){.markdown-reader__layout{display:block}.markdown-outline{position:static;max-height:220px;margin:12px 12px 0;padding:12px;border:1px solid var(--border);border-radius:11px;background:var(--surface-sunken)}.markdown-outline__head{min-height:30px}.markdown-outline nav{grid-template-columns:repeat(2,minmax(0,1fr))}.markdown-reader__layout>.markdown-reader__paper{width:min(100%,960px)}}
@media(max-width:720px){.markdown-toolbar{display:grid;grid-template-columns:repeat(3,1fr)}.markdown-list-header{gap:14px}.markdown-toolbar{width:100%}.toolbar-button{padding-inline:8px}.document-list__head{display:none}.document-row{grid-template-columns:28px minmax(0,1fr) auto;gap:9px;padding:13px 11px}.document-summary{grid-column:2/-1}.document-domain{grid-column:2}.document-row time{grid-column:3}.document-actions{grid-column:2/-1;justify-content:flex-start}.drop-hint{display:none}.markdown-reader,.markdown-editor{scroll-margin-top:12px}.markdown-reader__bar{min-height:48px}.markdown-reader__paper{min-height:calc(100svh - 124px);padding:30px 20px 52px}.markdown-reader__headline{padding-bottom:22px}.markdown-reader__headline h1{font-size:30px}.markdown-reader__body{padding:26px 0 0;font-size:15px}.editor-header{grid-template-columns:auto minmax(0,1fr) auto;gap:8px}.editor-back span{display:none}.editor-title input{font-size:var(--font-md)}.editor-header>.save-button{min-width:40px;width:40px;height:40px;padding:0}.editor-header>.save-button span{display:none}.editor-meta{gap:8px;padding:9px 11px}.editor-meta>label:not(.favorite-field)>span{display:none}.editor-meta label:first-child input{width:145px}.favorite-field{font-size:0}.view-switch{margin-left:auto}.editor-columns{height:calc(100svh - 320px);min-height:420px}.write-pane textarea,.markdown-body{padding:17px}.markdown-reader__body{padding:26px 0 0}.empty-actions{align-items:stretch;flex-direction:column}}
@media(max-width:460px){.editor-meta label:first-child{width:calc(100% - 120px)}.editor-meta label:first-child input{width:100%}.editor-meta>label:nth-child(2){width:110px}.editor-meta select{width:100%}.view-switch{width:100%;justify-content:stretch}.view-switch button{flex:1;justify-content:center}.favorite-field{display:none}}
.editor-header { min-height: 68px; grid-template-columns: auto minmax(0, 1fr) auto; padding: 10px 20px; }
.editor-actions { display: flex; flex-shrink: 0; align-items: center; gap: 8px; }
.editor-title > span { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.editor-title i { flex-shrink: 0; }
.editor-drafts-link { display: inline-flex; align-items: center; gap: 5px; padding: 4px 0; color: var(--accent); border: 0; background: transparent; font-size: 12px; cursor: pointer; white-space: nowrap; }
@media (max-width: 720px) {
  .editor-header { grid-template-columns: 30px minmax(0, 1fr) auto; gap: 5px; padding: 10px; }
  .editor-actions { gap: 3px; }
  .editor-actions .save-button { min-width: 36px; width: 36px; height: 36px; min-height: 36px; padding: 0; }
  .editor-actions .save-button span, .history-button span { display: none; }
  .editor-title > span { display: block; font-size: 10px; }
  .editor-title i { display: inline-block; margin-right: 4px; }
  .history-button { width: 34px; padding: 0; }
  .markdown-toolbar { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
