import { defineStore } from 'pinia'
import { computed, reactive, ref } from 'vue'
import { apiDownload, apiRequest, jsonBody } from '../services/api'
import { localDateKey, sortTasks } from '../utils/tasks'
import { settleWorkspaceModules } from '../utils/workspaceLoading'
import type {
  CodeSnippet,
  DeveloperProfile,
  DevLogEntry,
  DevProject,
  DevTask,
  DomainDraft,
  KnowledgeBulkMoveItem,
  KnowledgeDomain,
  LogDraft,
  MarkdownDocument,
  MarkdownDocumentDraft,
  MarkdownDocumentUpdateDraft,
  MarkdownImportDocument,
  MarkdownImportResult,
  MarkdownRevision,
  MarkdownRevisionDetail,
  ProfileDraft,
  ProjectDraft,
  SnippetDraft,
  TaskDraft,
  TrashedLog,
  TrashedSnippet,
} from '../types'

export type WorkspaceModuleKey = 'tasks' | 'projects' | 'snippets' | 'logs' | 'markdownDocuments' | 'domains' | 'profile'

export interface WorkspaceModuleState {
  loading: boolean
  loaded: boolean
  error: string
}

const workspaceModules: WorkspaceModuleKey[] = [
  'tasks', 'projects', 'snippets', 'logs', 'markdownDocuments', 'domains', 'profile',
]

export const useWorkspaceStore = defineStore('workspace', () => {
  const tasks = ref<DevTask[]>([])
  const projects = ref<DevProject[]>([])
  const snippets = ref<CodeSnippet[]>([])
  const logs = ref<DevLogEntry[]>([])
  const markdownDocuments = ref<MarkdownDocument[]>([])
  const domains = ref<KnowledgeDomain[]>([])
  const snippetTrash = ref<TrashedSnippet[]>([])
  const logTrash = ref<TrashedLog[]>([])
  const profile = ref<DeveloperProfile | null>(null)
  const moduleStates = reactive<Record<WorkspaceModuleKey, WorkspaceModuleState>>(
    Object.fromEntries(workspaceModules.map((module) => [module, { loading: false, loaded: false, error: '' }])) as
      Record<WorkspaceModuleKey, WorkspaceModuleState>,
  )
  const loading = computed(() => workspaceModules.some((module) => moduleStates[module].loading))
  const hasLoadErrors = computed(() => workspaceModules.some((module) => Boolean(moduleStates[module].error)))
  const mutationCount = ref(0)
  const mutating = computed(() => mutationCount.value > 0)
  const error = ref('')
  const lastSyncedAt = ref<Date | null>(null)
  let workspaceEpoch = 0
  const inFlightSaves = new Map<string, Promise<unknown>>()

  const completedTasks = computed(() => tasks.value.filter((item) => item.done).length)
  const todayTasks = computed(() => sortTasks(tasks.value.filter((item) =>
    !item.archived && item.scheduledDate === localDateKey(),
  )))
  const todayCompletedTasks = computed(() => todayTasks.value.filter((item) => item.done).length)
  const taskProgress = computed(() => todayTasks.value.length
    ? Math.round((todayCompletedTasks.value / todayTasks.value.length) * 100)
    : 0)
  const activeProjects = computed(() => projects.value.filter((item) => item.status === 'BUILDING').length)
  const favoriteSnippets = computed(() => snippets.value.filter((item) => item.favorite).length)
  const primaryProject = computed(() =>
    projects.value.find((item) => item.status === 'BUILDING') ??
    projects.value.find((item) => item.status !== 'COMPLETED') ?? null,
  )

  async function loadModule(module: WorkspaceModuleKey, force = true) {
    const state = moduleStates[module]
    if (state.loading || (!force && state.loaded)) return
    const epoch = workspaceEpoch
    state.loading = true
    state.error = ''
    try {
      switch (module) {
        case 'tasks': {
          const data = await apiRequest<DevTask[]>('/tasks')
          if (epoch === workspaceEpoch) tasks.value = data
          break
        }
        case 'projects': {
          const data = await apiRequest<DevProject[]>('/projects')
          if (epoch === workspaceEpoch) projects.value = data
          break
        }
        case 'snippets': {
          const data = await apiRequest<CodeSnippet[]>('/snippets')
          if (epoch === workspaceEpoch) snippets.value = data
          break
        }
        case 'logs': {
          const data = await apiRequest<DevLogEntry[]>('/logs')
          if (epoch === workspaceEpoch) logs.value = data
          break
        }
        case 'markdownDocuments': {
          const data = await apiRequest<MarkdownDocument[]>('/markdown-documents')
          if (epoch === workspaceEpoch) markdownDocuments.value = data
          break
        }
        case 'domains': {
          const data = await apiRequest<KnowledgeDomain[]>('/domains')
          if (epoch === workspaceEpoch) domains.value = data
          break
        }
        case 'profile': {
          const data = await apiRequest<DeveloperProfile>('/profile')
          if (epoch === workspaceEpoch) profile.value = data
          break
        }
      }
      if (epoch !== workspaceEpoch) return
      state.loaded = true
      lastSyncedAt.value = new Date()
    } catch (cause) {
      if (epoch === workspaceEpoch) state.error = cause instanceof Error ? cause.message : '模块加载失败'
      throw cause
    } finally {
      if (epoch === workspaceEpoch) state.loading = false
    }
  }

  async function loadAll(force = false) {
    await settleWorkspaceModules(workspaceModules.map((module) => () => loadModule(module, force)))
  }

  async function runMutation<T>(operation: () => Promise<T>): Promise<T> {
    const epoch = workspaceEpoch
    mutationCount.value++
    error.value = ''
    try {
      const result = await operation()
      if (epoch !== workspaceEpoch) throw new Error('账号已切换，请重新加载工作区。')
      lastSyncedAt.value = new Date()
      return result
    } catch (cause) {
      if (epoch === workspaceEpoch) error.value = cause instanceof Error ? cause.message : '操作失败，请稍后重试'
      throw cause
    } finally {
      if (epoch === workspaceEpoch) mutationCount.value--
    }
  }

  function coalesceSave<T>(key: string, operation: () => Promise<T>): Promise<T> {
    const existing = inFlightSaves.get(key)
    if (existing) return existing as Promise<T>
    const request = operation().finally(() => {
      if (inFlightSaves.get(key) === request) inFlightSaves.delete(key)
    })
    inFlightSaves.set(key, request)
    return request
  }

  async function createTask(draft: TaskDraft) {
    const task = await runMutation(() => apiRequest<DevTask>('/tasks', {
      method: 'POST',
      body: jsonBody({ ...draft, title: draft.title.trim(), sortOrder: tasks.value.length }),
    }))
    tasks.value.push(task)
  }

  async function updateTask(
    task: DevTask,
    patch: Partial<Pick<DevTask, 'title' | 'done' | 'scheduledDate' | 'dueAt' | 'priority' | 'archived'>>,
  ) {
    const updated = await runMutation(() => apiRequest<DevTask>(`/tasks/${task.id}`, {
      method: 'PUT',
      body: jsonBody({
        title: patch.title ?? task.title,
        done: patch.done ?? task.done,
        sortOrder: task.sortOrder,
        scheduledDate: patch.scheduledDate === undefined ? task.scheduledDate : patch.scheduledDate,
        dueAt: patch.dueAt === undefined ? task.dueAt : patch.dueAt,
        priority: patch.priority ?? task.priority,
        archived: patch.archived ?? task.archived,
      }),
    }))
    tasks.value = tasks.value.map((item) => item.id === updated.id ? updated : item)
  }

  async function deleteTask(id: string) {
    await runMutation(() => apiRequest<void>(`/tasks/${id}`, { method: 'DELETE' }))
    tasks.value = tasks.value.filter((item) => item.id !== id)
  }

  async function saveProject(draft: ProjectDraft, id?: string) {
    const project = await runMutation(() => apiRequest<DevProject>(id ? `/projects/${id}` : '/projects', {
      method: id ? 'PUT' : 'POST', body: jsonBody(draft),
    }))
    projects.value = id
      ? projects.value.map((item) => item.id === project.id ? project : item)
      : [project, ...projects.value]
  }

  async function deleteProject(id: string) {
    await runMutation(() => apiRequest<void>(`/projects/${id}`, { method: 'DELETE' }))
    projects.value = projects.value.filter((item) => item.id !== id)
  }

  async function saveSnippet(draft: SnippetDraft, id?: string) {
    const body = jsonBody(draft)
    const snippet = await coalesceSave(`snippet:${id ?? 'new'}:${body}`, () => runMutation(() => apiRequest<CodeSnippet>(id ? `/snippets/${id}` : '/snippets', {
      method: id ? 'PUT' : 'POST', body,
    })))
    snippets.value = id
      ? snippets.value.map((item) => item.id === snippet.id ? snippet : item)
      : [snippet, ...snippets.value]
    return snippet
  }

  async function deleteSnippet(id: string) {
    await runMutation(() => apiRequest<void>(`/snippets/${id}`, { method: 'DELETE' }))
    snippets.value = snippets.value.filter((item) => item.id !== id)
  }

  async function saveLog(draft: LogDraft, id?: string) {
    const entry = await runMutation(() => apiRequest<DevLogEntry>(id ? `/logs/${id}` : '/logs', {
      method: id ? 'PUT' : 'POST', body: jsonBody(draft),
    }))
    logs.value = id
      ? logs.value.map((item) => item.id === entry.id ? entry : item)
      : [entry, ...logs.value]
  }

  async function deleteLog(id: string) {
    await runMutation(() => apiRequest<void>(`/logs/${id}`, { method: 'DELETE' }))
    logs.value = logs.value.filter((item) => item.id !== id)
  }

  async function loadItemTrash() {
    const epoch = workspaceEpoch
    error.value = ''
    try {
      const [snips, logItems] = await Promise.all([
        apiRequest<TrashedSnippet[]>('/snippets/trash'),
        apiRequest<TrashedLog[]>('/logs/trash'),
      ])
      if (epoch !== workspaceEpoch) throw new Error('账号已切换，请重新加载工作区。')
      snippetTrash.value = snips
      logTrash.value = logItems
    } catch (cause) {
      if (epoch === workspaceEpoch) error.value = cause instanceof Error ? cause.message : '回收站加载失败，请稍后重试'
      throw cause
    }
  }

  async function restoreSnippetFromTrash(id: string) {
    const snippet = await runMutation(() => apiRequest<CodeSnippet>(`/snippets/${id}/restore`, { method: 'POST' }))
    snippetTrash.value = snippetTrash.value.filter((item) => item.id !== id)
    snippets.value = [snippet, ...snippets.value.filter((item) => item.id !== id)]
    return snippet
  }

  async function purgeSnippetFromTrash(id: string) {
    await runMutation(() => apiRequest<void>(`/snippets/${id}/permanent`, { method: 'DELETE' }))
    snippetTrash.value = snippetTrash.value.filter((item) => item.id !== id)
  }

  async function restoreLogFromTrash(id: string) {
    const log = await runMutation(() => apiRequest<DevLogEntry>(`/logs/${id}/restore`, { method: 'POST' }))
    logTrash.value = logTrash.value.filter((item) => item.id !== id)
    logs.value = [log, ...logs.value.filter((item) => item.id !== id)]
    return log
  }

  async function purgeLogFromTrash(id: string) {
    await runMutation(() => apiRequest<void>(`/logs/${id}/permanent`, { method: 'DELETE' }))
    logTrash.value = logTrash.value.filter((item) => item.id !== id)
  }

  function saveMarkdownDocument(draft: MarkdownDocumentDraft): Promise<MarkdownDocument>
  function saveMarkdownDocument(draft: MarkdownDocumentUpdateDraft, id: string): Promise<MarkdownDocument>
  async function saveMarkdownDocument(draft: MarkdownDocumentDraft | MarkdownDocumentUpdateDraft, id?: string) {
    if (id && (!('expectedVersion' in draft) || draft.expectedVersion === undefined)) {
      throw new Error('缺少文章版本，请重新打开文章后再保存。')
    }
    const body = jsonBody(id ? draft : {
      title: draft.title,
      fileName: draft.fileName,
      content: draft.content,
      domainId: draft.domainId,
      favorite: draft.favorite,
    })
    const document = await coalesceSave(`markdown:${id ?? 'new'}:${body}`, () => runMutation(() => apiRequest<MarkdownDocument>(id ? `/markdown-documents/${id}` : '/markdown-documents', {
      method: id ? 'PUT' : 'POST',
      body,
    })))
    markdownDocuments.value = id
      ? markdownDocuments.value.map((item) => item.id === document.id ? document : item)
      : [document, ...markdownDocuments.value]
    return document
  }

  async function loadMarkdownDocument(id: string) {
    const epoch = workspaceEpoch
    error.value = ''
    try {
      const document = await apiRequest<MarkdownDocument>(`/markdown-documents/${id}`)
      if (epoch !== workspaceEpoch) throw new Error('账号已切换，请重新打开文章。')
      markdownDocuments.value = markdownDocuments.value.map((item) => item.id === document.id ? document : item)
      return document
    } catch (cause) {
      if (epoch === workspaceEpoch) error.value = cause instanceof Error ? cause.message : '文章加载失败，请稍后重试'
      throw cause
    }
  }

  async function deleteMarkdownDocument(id: string) {
    await runMutation(() => apiRequest<void>(`/markdown-documents/${id}`, { method: 'DELETE' }))
    markdownDocuments.value = markdownDocuments.value.filter((item) => item.id !== id)
  }

  const loadMarkdownTrash = () => apiRequest<MarkdownDocument[]>('/markdown-documents/trash')
  const loadMarkdownHistory = (id: string, page = 0) =>
    apiRequest<MarkdownRevision[]>(`/markdown-documents/${id}/revisions?page=${page}`)
  const loadMarkdownRevision = (id: string, revisionId: string) =>
    apiRequest<MarkdownRevisionDetail>(`/markdown-documents/${id}/revisions/${revisionId}`)

  async function restoreMarkdownDocument(id: string) {
    const document = await runMutation(() => apiRequest<MarkdownDocument>(`/markdown-documents/${id}/restore`, { method: 'POST' }))
    markdownDocuments.value = [document, ...markdownDocuments.value.filter((item) => item.id !== id)]
    return document
  }

  async function purgeMarkdownDocument(id: string) {
    await runMutation(() => apiRequest<void>(`/markdown-documents/${id}/permanent`, { method: 'DELETE' }))
  }

  async function restoreMarkdownRevision(id: string, revisionId: string, expectedVersion: number) {
    const document = await runMutation(() => apiRequest<MarkdownDocument>(`/markdown-documents/${id}/revisions/${revisionId}/restore`, {
      method: 'POST', body: jsonBody({ expectedVersion }),
    }))
    markdownDocuments.value = markdownDocuments.value.map((item) => item.id === id ? document : item)
    return document
  }

  async function importMarkdownDocuments(documents: MarkdownImportDocument[]) {
    const payload = await runMutation(() => apiRequest<MarkdownImportResult | MarkdownDocument[]>('/markdown-documents/import', {
      method: 'POST', body: jsonBody({ documents }),
    }))
    const result: MarkdownImportResult = Array.isArray(payload) ? { documents: payload } : payload
    const importedIds = new Set(result.documents.map((item) => item.id))
    markdownDocuments.value = [
      ...result.documents,
      ...markdownDocuments.value.filter((item) => !importedIds.has(item.id)),
    ]
    return result
  }

  async function importMarkdownFiles(files: File[], domainId: string | null) {
    const form = new FormData()
    files.forEach((file) => form.append('files', file, file.name))
    if (domainId) form.append('domainId', domainId)
    const result = await runMutation(() => apiRequest<MarkdownImportResult>('/markdown-documents/import', {
      method: 'POST', body: form, timeoutMs: 120_000,
    }))
    const importedIds = new Set(result.documents.map((item) => item.id))
    markdownDocuments.value = [
      ...result.documents,
      ...markdownDocuments.value.filter((item) => !importedIds.has(item.id)),
    ]
    return result
  }

  async function moveMarkdownDocumentsToDomain(
    documents: Array<{ id: string; expectedVersion: number }>,
    domainId: string | null,
  ) {
    const moved = await runMutation(() => apiRequest<MarkdownDocument[]>('/markdown-documents/bulk-domain', {
      method: 'PATCH', body: jsonBody({ documents, domainId }),
    }))
    const byId = new Map(moved.map((item) => [item.id, item]))
    markdownDocuments.value = markdownDocuments.value.map((item) => byId.get(item.id) ?? item)
    return moved
  }

  async function moveKnowledgeItemsToDomain(items: KnowledgeBulkMoveItem[], domainId: string | null) {
    await runMutation(() => apiRequest<void>('/knowledge-items/bulk-domain', {
      method: 'PATCH', body: jsonBody({ items, domainId }),
    }))
    await settleWorkspaceModules([
      () => loadModule('markdownDocuments'),
      () => loadModule('snippets'),
      () => loadModule('logs'),
    ])
  }

  async function exportMarkdownDocuments(ids: string[]) {
    return runMutation(() => apiDownload('/markdown-documents/export', {
      method: 'POST', body: jsonBody({ ids }),
    }))
  }

  async function saveDomain(draft: DomainDraft, id?: string) {
    const domain = await runMutation(() => apiRequest<KnowledgeDomain>(id ? `/domains/${id}` : '/domains', {
      method: id ? 'PUT' : 'POST', body: jsonBody(draft),
    }))
    domains.value = (id
      ? domains.value.map((item) => item.id === domain.id ? domain : item)
      : [...domains.value, domain]
    ).sort((a, b) => a.sortOrder - b.sortOrder || a.createdAt.localeCompare(b.createdAt))
  }

  async function deleteDomain(id: string) {
    await runMutation(() => apiRequest<void>(`/domains/${id}`, { method: 'DELETE' }))
    domains.value = domains.value.filter((item) => item.id !== id)
    snippets.value = snippets.value.map((item) => item.domainId === id ? { ...item, domainId: null } : item)
    logs.value = logs.value.map((item) => item.domainId === id ? { ...item, domainId: null } : item)
    markdownDocuments.value = markdownDocuments.value.map((item) => item.domainId === id ? { ...item, domainId: null } : item)
  }

  async function saveProfile(draft: ProfileDraft) {
    profile.value = await runMutation(() => apiRequest<DeveloperProfile>('/profile', {
      method: 'PUT', body: jsonBody(draft),
    }))
  }

  async function uploadProfileAvatar(file: File) {
    const form = new FormData()
    form.append('file', file, file.name)
    profile.value = await runMutation(() => apiRequest<DeveloperProfile>('/profile/avatar', {
      method: 'POST', body: form, timeoutMs: 90_000,
    }))
  }

  async function clearProfileAvatar() {
    profile.value = await runMutation(() => apiRequest<DeveloperProfile>('/profile/avatar', {
      method: 'DELETE',
    }))
  }

  function clear() {
    workspaceEpoch++
    mutationCount.value = 0
    inFlightSaves.clear()
    tasks.value = []
    projects.value = []
    snippets.value = []
    logs.value = []
    markdownDocuments.value = []
    domains.value = []
    snippetTrash.value = []
    logTrash.value = []
    profile.value = null
    error.value = ''
    lastSyncedAt.value = null
    workspaceModules.forEach((module) => Object.assign(moduleStates[module], { loading: false, loaded: false, error: '' }))
  }

  return {
    tasks, projects, snippets, logs, markdownDocuments, domains, profile,
    snippetTrash, logTrash,
    moduleStates, loading, hasLoadErrors, mutating, error, lastSyncedAt,
    completedTasks, todayTasks, todayCompletedTasks, taskProgress, activeProjects, favoriteSnippets, primaryProject,
    loadAll, loadModule, createTask, updateTask, deleteTask, saveProject, deleteProject,
    saveSnippet, deleteSnippet, saveLog, deleteLog, loadMarkdownDocument, saveMarkdownDocument, deleteMarkdownDocument,
    importMarkdownDocuments, importMarkdownFiles, moveMarkdownDocumentsToDomain, moveKnowledgeItemsToDomain,
    exportMarkdownDocuments, saveDomain, deleteDomain, saveProfile, uploadProfileAvatar, clearProfileAvatar, clear,
    loadMarkdownTrash, loadMarkdownHistory, loadMarkdownRevision, restoreMarkdownDocument, purgeMarkdownDocument, restoreMarkdownRevision,
    loadItemTrash, restoreSnippetFromTrash, purgeSnippetFromTrash, restoreLogFromTrash, purgeLogFromTrash,
  }
})
