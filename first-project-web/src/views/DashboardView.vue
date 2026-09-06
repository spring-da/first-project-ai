<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Archive, ArrowUpRight, BookOpen, CalendarDays, Check, Clock3, Code2, FileText, FolderKanban, Inbox, Pencil, Plus, RotateCcw, Trash2 } from 'lucide-vue-next'
import AppModal from '../components/AppModal.vue'
import EmptyState from '../components/EmptyState.vue'
import WorkspaceModuleState from '../components/WorkspaceModuleState.vue'
import { useAuthStore } from '../stores/auth'
import { useConfirmationStore } from '../stores/confirmation'
import { useWorkspaceStore } from '../stores/workspace'
import { localDateKey, matchesTaskView, sortTasks } from '../utils/tasks'
import type { DevTask, TaskDraft, TaskPriority, TaskView } from '../types'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const workspace = useWorkspaceStore()
const confirmation = useConfirmationStore()
const showTaskModal = ref(false)
const editingTask = ref<DevTask | null>(null)
const taskView = ref<TaskView>('TODAY')
const taskScroll = ref<HTMLElement | null>(null)
const taskPanel = ref<HTMLElement | null>(null)
const dueLocal = ref('')
const taskDraft = reactive<TaskDraft>({ title: '', scheduledDate: localDateKey(), dueAt: null, priority: 'NORMAL' })
const today = computed(() => localDateKey())

const name = computed(() => workspace.profile?.name || auth.workspaceUser?.displayName || '开发者')
const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '上午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})
const dateLabel = computed(() => new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' }).format(new Date()))
const recentLogs = computed(() => [...workspace.logs].sort((a, b) => Number(b.pinned) - Number(a.pinned) || b.createdAt.localeCompare(a.createdAt)).slice(0, 3))
const recentDocuments = computed(() => [...workspace.markdownDocuments].sort((a, b) => Number(b.favorite) - Number(a.favorite) || b.updatedAt.localeCompare(a.updatedAt)).slice(0, 3))
const visibleTasks = computed(() => sortTasks(workspace.tasks.filter((task) => matchesTaskView(task, taskView.value, today.value))))

const taskViews: Array<{ value: TaskView; label: string; icon: typeof CalendarDays }> = [
  { value: 'TODAY', label: '今天', icon: CalendarDays },
  { value: 'UPCOMING', label: '即将到期', icon: Clock3 },
  { value: 'INBOX', label: '收件箱', icon: Inbox },
  { value: 'COMPLETED', label: '已完成', icon: Check },
  { value: 'ARCHIVED', label: '归档', icon: Archive },
]
const priorityMeta: Record<TaskPriority, { label: string; className: string }> = {
  LOW: { label: '低', className: 'low' },
  NORMAL: { label: '普通', className: 'normal' },
  HIGH: { label: '高', className: 'high' },
  URGENT: { label: '紧急', className: 'urgent' },
}
const activeTaskView = computed(() => taskViews.find((item) => item.value === taskView.value)!)
const taskViewCount = (view: TaskView) => workspace.tasks.filter((task) => matchesTaskView(task, view, today.value)).length
const workspaceQuery = (query: Record<string, string> = {}) => auth.workspaceMember
  ? { ...query, workspace: auth.workspaceMember.id } : query

function readTaskView(value: unknown): TaskView | null {
  const candidate = typeof value === 'string' ? value.toUpperCase() : ''
  return taskViews.some((view) => view.value === candidate) ? candidate as TaskView : null
}

async function selectTaskView(view: TaskView, scrollToPanel = false) {
  taskView.value = view
  await router.replace({ query: { ...route.query, tasks: view.toLowerCase() } })
  if (scrollToPanel) {
    await nextTick()
    taskPanel.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    taskScroll.value?.focus({ preventScroll: true })
  }
}

watch(() => route.query.tasks, (value) => {
  const requested = readTaskView(value)
  if (requested) taskView.value = requested
}, { immediate: true })

function tomorrowKey() {
  const date = new Date()
  date.setDate(date.getDate() + 1)
  return localDateKey(date)
}

function toLocalDateTime(value: string | null) {
  if (!value) return ''
  const date = new Date(value)
  const offset = date.getTimezoneOffset() * 60_000
  return new Date(date.getTime() - offset).toISOString().slice(0, 16)
}

function openTaskEditor(task?: DevTask) {
  editingTask.value = task ?? null
  Object.assign(taskDraft, task ? {
    title: task.title,
    scheduledDate: task.scheduledDate,
    dueAt: task.dueAt,
    priority: task.priority,
  } : {
    title: '',
    scheduledDate: taskView.value === 'INBOX' ? null : taskView.value === 'UPCOMING' ? tomorrowKey() : today.value,
    dueAt: null,
    priority: 'NORMAL' as TaskPriority,
  })
  dueLocal.value = toLocalDateTime(task?.dueAt ?? null)
  showTaskModal.value = true
}

async function saveTask() {
  if (!taskDraft.title.trim()) return
  const draft: TaskDraft = {
    ...taskDraft,
    title: taskDraft.title.trim(),
    scheduledDate: taskDraft.scheduledDate || null,
    dueAt: dueLocal.value ? new Date(dueLocal.value).toISOString() : null,
  }
  if (editingTask.value) await workspace.updateTask(editingTask.value, draft)
  else await workspace.createTask(draft)
  showTaskModal.value = false
  await nextTick()
  taskScroll.value?.scrollTo({ top: taskScroll.value.scrollHeight, behavior: 'smooth' })
}

async function toggleTask(task: DevTask) {
  await workspace.updateTask(task, { done: !task.done, archived: false })
}

async function archiveTask(task: DevTask) {
  await workspace.updateTask(task, { archived: !task.archived })
}

async function removeTask(task: DevTask) {
  if (!await confirmation.ask({
    title: '永久删除这个任务？',
    message: `“${task.title}”将从任务列表中删除。`,
    detail: '删除无法撤销；如果只是暂时不想看到它，可以先归档。',
    confirmText: '删除任务', tone: 'danger', icon: 'delete',
  })) return
  try { await workspace.deleteTask(task.id) } catch { /* Global mutation notification reports the error. */ }
}

function formatTaskDate(task: DevTask) {
  if (task.scheduledDate === today.value) return '今天'
  if (task.scheduledDate) return new Intl.DateTimeFormat('zh-CN', { month: 'short', day: 'numeric' }).format(new Date(`${task.scheduledDate}T00:00:00`))
  return '收件箱'
}

function formatDueAt(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(value))
}
</script>

<template>
  <div class="page-content dashboard-page">
    <section class="hero-copy">
      <div><p class="eyebrow">{{ dateLabel }}</p><h1>{{ greeting }}，{{ name }}</h1><p>把今天最重要的事情做完，其他的交给 DevNest 帮你记住。</p></div>
      <div class="focus-pill" :class="{ warning: workspace.hasLoadErrors }"><span></span>{{ workspace.hasLoadErrors ? '部分模块需要重试' : workspace.loading ? '正在同步数据' : '工作区已就绪' }}</div>
    </section>
    <WorkspaceModuleState module="profile" title="个人资料" :has-data="Boolean(workspace.profile)" compact />

    <section class="metric-grid" aria-label="工作台概览">
      <button class="metric-card tasks" type="button" aria-label="查看今天的任务" @click="selectTaskView('TODAY', true)"><div class="metric-card-heading"><span class="metric-icon"><CalendarDays :size="18" /></span><span>查看任务<ArrowUpRight :size="15" /></span></div><div class="metric-top"><span>今日进度</span><span class="metric-badge">{{ workspace.taskProgress }}%</span></div><strong>{{ !workspace.tasks.length && (workspace.moduleStates.tasks.loading || workspace.moduleStates.tasks.error) ? '—' : `${workspace.todayCompletedTasks} / ${workspace.todayTasks.length}` }}</strong><p>今天安排的任务已完成</p><div class="progress"><span :style="{ width: `${workspace.taskProgress}%` }"></span></div></button>
      <button class="metric-card projects" type="button" aria-label="查看构建中的项目" @click="router.push({ name: 'projects', query: workspaceQuery({ status: 'BUILDING' }) })"><div class="metric-card-heading"><span class="metric-icon"><FolderKanban :size="18" /></span><span>筛选项目<ArrowUpRight :size="15" /></span></div><span>进行中的项目</span><strong>{{ !workspace.projects.length && (workspace.moduleStates.projects.loading || workspace.moduleStates.projects.error) ? '—' : workspace.activeProjects }}</strong><p>保持节奏，持续推进</p></button>
      <button class="metric-card snippets" type="button" aria-label="查看收藏的代码片段" @click="router.push({ name: 'knowledge', query: workspaceQuery({ tab: 'snippets', featured: '1' }) })"><div class="metric-card-heading"><span class="metric-icon"><Code2 :size="18" /></span><span>查看收藏<ArrowUpRight :size="15" /></span></div><span>收藏的片段</span><strong>{{ !workspace.snippets.length && (workspace.moduleStates.snippets.loading || workspace.moduleStates.snippets.error) ? '—' : workspace.favoriteSnippets }}</strong><p>随时可复用的代码资产</p></button>
      <button class="metric-card documents" type="button" aria-label="查看 Markdown 文章" @click="router.push({ name: 'knowledge', query: workspaceQuery({ tab: 'documents' }) })"><div class="metric-card-heading"><span class="metric-icon"><FileText :size="18" /></span><span>打开文章<ArrowUpRight :size="15" /></span></div><span>Markdown 文章</span><strong>{{ !workspace.markdownDocuments.length && (workspace.moduleStates.markdownDocuments.loading || workspace.moduleStates.markdownDocuments.error) ? '—' : workspace.markdownDocuments.length }}</strong><p>持续生长的个人知识库</p></button>
    </section>

    <section class="dashboard-grid">
      <article ref="taskPanel" class="panel task-panel" aria-labelledby="task-heading">
        <div class="panel-heading"><div><p class="eyebrow">TASK PLANNER</p><h2 id="task-heading">{{ activeTaskView.label }}</h2></div><button class="icon-btn accent" type="button" aria-label="添加任务" @click="openTaskEditor()"><Plus :size="19" /></button></div>
        <div class="task-view-tabs" role="tablist" aria-label="任务视图">
          <button v-for="view in taskViews" :key="view.value" type="button" role="tab" :aria-selected="taskView === view.value" :class="{ active: taskView === view.value }" @click="selectTaskView(view.value)"><component :is="view.icon" :size="14" /><span>{{ view.label }}</span><b>{{ taskViewCount(view.value) }}</b></button>
        </div>
        <WorkspaceModuleState module="tasks" title="任务" :has-data="Boolean(workspace.tasks.length)" compact />
        <div ref="taskScroll" class="dashboard-scroll task-scroll" role="region" :aria-label="`${activeTaskView.label}任务列表`" tabindex="0">
          <TransitionGroup v-if="visibleTasks.length" name="list" tag="ul" class="task-list">
            <li v-for="task in visibleTasks" :key="task.id" class="task-row" :class="{ done: task.done }">
              <button class="task-check" type="button" role="checkbox" :aria-checked="task.done" :aria-label="task.done ? `重新打开：${task.title}` : `完成：${task.title}`" @click="toggleTask(task)"><Check v-if="task.done" :size="14" /></button>
              <button class="task-copy" type="button" @click="openTaskEditor(task)"><strong>{{ task.title }}</strong><span><i class="priority" :class="priorityMeta[task.priority].className">{{ priorityMeta[task.priority].label }}</i><small><CalendarDays :size="12" />{{ formatTaskDate(task) }}</small><small v-if="task.dueAt" :class="{ overdue: !task.done && new Date(task.dueAt) < new Date() }"><Clock3 :size="12" />{{ formatDueAt(task.dueAt) }}</small></span></button>
              <div class="task-actions"><button type="button" :aria-label="`编辑任务：${task.title}`" @click="openTaskEditor(task)"><Pencil :size="14" /></button><button type="button" :aria-label="task.archived ? `取消归档：${task.title}` : `归档任务：${task.title}`" @click="archiveTask(task)"><RotateCcw v-if="task.archived" :size="14" /><Archive v-else :size="14" /></button><button class="danger" type="button" :aria-label="`删除任务：${task.title}`" @click="removeTask(task)"><Trash2 :size="14" /></button></div>
            </li>
          </TransitionGroup>
          <EmptyState v-else-if="!workspace.moduleStates.tasks.loading && !workspace.moduleStates.tasks.error" class="panel-empty" compact :title="taskView === 'TODAY' ? '今天没有待办压力' : `“${activeTaskView.label}”暂无任务`" :description="taskView === 'INBOX' ? '没有日期的灵感和待办会出现在这里。' : taskView === 'ARCHIVED' ? '归档后的任务会保留在这里。' : '切换其他视图，或添加一项新任务。'"><button class="button button-secondary" @click="openTaskEditor()"><Plus :size="15" />添加任务</button></EmptyState>
        </div>
        <footer class="task-footer"><button class="text-button" type="button" @click="openTaskEditor()"><Plus :size="16" />添加任务</button><span>{{ visibleTasks.length }} 项 · 共 {{ workspace.tasks.length }} 项</span></footer>
      </article>

      <article class="panel project-panel">
        <div class="panel-heading"><div><p class="eyebrow">PRIMARY PROJECT</p><h2>当前主项目</h2></div><button class="text-link" @click="router.push({ name: 'projects', query: workspaceQuery(workspace.primaryProject ? { focus: workspace.primaryProject.id } : {}) })">查看项目</button></div>
        <WorkspaceModuleState module="projects" title="项目" :has-data="Boolean(workspace.projects.length)" compact />
        <div class="dashboard-scroll" role="region" aria-label="当前主项目详情" tabindex="0">
          <template v-if="workspace.primaryProject">
            <div class="project-main"><div class="project-icon">{{ workspace.primaryProject.name.slice(0, 2).toUpperCase() }}</div><div><div class="project-title"><h3>{{ workspace.primaryProject.name }}</h3><span>构建中</span></div><p>{{ workspace.primaryProject.description || '这个项目还没有补充描述。' }}</p></div></div>
            <div class="project-progress"><div><span>项目进度</span><strong>{{ workspace.primaryProject.progress }}%</strong></div><div class="progress"><span :style="{ width: `${workspace.primaryProject.progress}%` }"></span></div></div>
            <div class="next-action"><small>下一步行动</small><p>{{ workspace.primaryProject.nextAction }}</p></div>
          </template>
          <EmptyState v-else-if="!workspace.moduleStates.projects.loading && !workspace.moduleStates.projects.error" class="panel-empty" compact title="还没有进行中的项目" description="创建一个项目，明确接下来真正要推进的事情。"><button class="button button-secondary" @click="router.push({ name: 'projects', query: workspaceQuery() })"><Plus :size="15" />创建项目</button></EmptyState>
        </div>
      </article>
    </section>

    <section class="knowledge-strip">
      <div class="section-title"><div><p class="eyebrow">RECENT KNOWLEDGE</p><h2>最近沉淀</h2></div><button class="text-link" @click="router.push({ name: 'knowledge', query: workspaceQuery() })">打开知识库</button></div>
      <WorkspaceModuleState module="markdownDocuments" title="Markdown 文章" :has-data="Boolean(workspace.markdownDocuments.length)" compact />
      <WorkspaceModuleState module="snippets" title="代码片段" :has-data="Boolean(workspace.snippets.length)" compact />
      <WorkspaceModuleState module="logs" title="开发日志" :has-data="Boolean(workspace.logs.length)" compact />
      <TransitionGroup name="list" tag="div" class="knowledge-grid">
        <button v-for="document in recentDocuments" :key="document.id" class="knowledge-item" @click="router.push({ name: 'knowledge', query: workspaceQuery({ tab: 'documents', focus: document.id }) })"><span class="knowledge-icon"><FileText :size="17" /></span><span><small>Markdown 文章</small><strong>{{ document.title }}</strong></span></button>
        <button v-for="snippet in workspace.snippets.slice(0, 1)" :key="snippet.id" class="knowledge-item" @click="router.push({ name: 'knowledge', query: workspaceQuery({ tab: 'snippets', focus: snippet.id }) })"><span class="knowledge-icon"><Code2 :size="17" /></span><span><small>{{ snippet.language }}</small><strong>{{ snippet.title }}</strong></span></button>
        <button v-for="entry in recentLogs.slice(0, 1)" :key="entry.id" class="knowledge-item" @click="router.push({ name: 'knowledge', query: workspaceQuery({ tab: 'logs', focus: entry.id }) })"><span class="knowledge-icon"><BookOpen :size="17" /></span><span><small>开发日志</small><strong>{{ entry.title || entry.content.slice(0, 24) }}</strong></span></button>
        <button v-if="!workspace.loading && !workspace.hasLoadErrors && !workspace.markdownDocuments.length && !workspace.snippets.length && !recentLogs.length" class="knowledge-item create" @click="router.push({ name: 'knowledge', query: workspaceQuery({ tab: 'documents', create: '1' }) })"><span class="knowledge-icon"><Plus :size="17" /></span><span><small>知识库</small><strong>写下第一篇文章</strong></span></button>
      </TransitionGroup>
    </section>

    <AppModal v-if="showTaskModal" :title="editingTask ? '编辑任务' : '添加任务'" description="安排日期、截止时间和优先级；不指定日期的任务会进入收件箱。" @close="showTaskModal = false">
      <form class="form-grid" @submit.prevent="saveTask">
        <label class="form-field"><span>任务内容</span><input v-model.trim="taskDraft.title" autofocus required maxlength="240" placeholder="例如：完成 Web 端登录页面" /></label>
        <div class="form-row task-schedule-row">
          <label class="form-field"><span>安排日期</span><input v-model="taskDraft.scheduledDate" type="date" /><small>清空日期可移入收件箱</small></label>
          <label class="form-field"><span>截止时间（可选）</span><input v-model="dueLocal" type="datetime-local" /></label>
        </div>
        <label class="form-field"><span>优先级</span><select v-model="taskDraft.priority"><option value="LOW">低</option><option value="NORMAL">普通</option><option value="HIGH">高</option><option value="URGENT">紧急</option></select></label>
        <div class="form-actions"><button class="button button-ghost" type="button" @click="showTaskModal = false">取消</button><button class="button button-primary" type="submit" :disabled="workspace.mutating"><Plus v-if="!editingTask" :size="16" />{{ editingTask ? '保存修改' : '添加任务' }}</button></div>
      </form>
    </AppModal>
  </div>
</template>

<style scoped>
.dashboard-page { --dashboard-column-gap: clamp(16px, .9vw, 22px); padding-top: 32px; }
.focus-pill.warning { color: var(--warning); }
.focus-pill.warning > span { background: var(--warning); }
.metric-grid { gap: var(--dashboard-column-gap); }
.metric-card { --metric-color: var(--accent); position: relative; min-width: 0; overflow: hidden; color: var(--text); text-align: left; font: inherit; cursor: pointer; background: linear-gradient(145deg, color-mix(in srgb, var(--metric-color) 8%, var(--panel)) 0%, var(--panel) 66%); transition: border-color var(--motion-fast), box-shadow var(--motion-base), transform var(--motion-base) var(--ease-emphasized); }
.metric-card::after { content: ''; position: absolute; right: -42px; bottom: -54px; width: 128px; height: 128px; border: 22px solid color-mix(in srgb, var(--metric-color) 7%, transparent); border-radius: 50%; pointer-events: none; }
.metric-card:hover { border-color: color-mix(in srgb, var(--metric-color) 32%, var(--border)); box-shadow: 0 12px 28px color-mix(in srgb, var(--metric-color) 10%, transparent); transform: translateY(-3px); }
.metric-card:active { transform: translateY(-1px) scale(.995); }
.metric-card:focus-visible { outline: 3px solid var(--accent-soft); outline-offset: 2px; }
.metric-card.projects { --metric-color: var(--success); }.metric-card.snippets { --metric-color: #8464d6; }.metric-card.documents { --metric-color: #c77a2a; }
.metric-card-heading { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 15px; }
.metric-card-heading > span:last-child { display: inline-flex; align-items: center; gap: 4px; color: var(--muted); font-size: 10px; font-weight: 650; }
.metric-card-heading .metric-icon { width: 34px; height: 34px; display: grid; place-items: center; color: var(--metric-color); border-radius: 10px; background: color-mix(in srgb, var(--metric-color) 12%, var(--panel)); }
.metric-card:hover .metric-card-heading > span:last-child { color: var(--metric-color); }
.metric-card > strong { position: relative; z-index: 1; }
.metric-card > p { position: relative; z-index: 1; }
.metric-card .progress { position: relative; z-index: 1; }
.metric-card.tasks { background: linear-gradient(145deg, color-mix(in srgb, var(--accent) 11%, var(--panel)) 0%, var(--panel) 70%); border-color: var(--accent-border); }
.dashboard-grid { --dashboard-panel-height: clamp(430px, 52svh, 560px); grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--dashboard-column-gap); }
.dashboard-grid > .panel { display: flex; flex-direction: column; height: var(--dashboard-panel-height); min-height: 0; min-width: 0; overflow: hidden; }
.panel-heading { flex-shrink: 0; gap: 12px; }
.panel-heading .text-link { flex-shrink: 0; }
.dashboard-scroll { flex: 1; min-height: 0; min-width: 0; overflow: auto; margin-top: 12px; overscroll-behavior-y: auto; scrollbar-gutter: stable; scrollbar-width: thin; scrollbar-color: var(--border-strong) transparent; }
.dashboard-scroll:focus-visible { outline-offset: -2px; border-radius: 8px; }
.dashboard-scroll > .panel-empty { min-height: 100%; }
.task-view-tabs { display: flex; gap: 4px; overflow-x: auto; flex-shrink: 0; margin-top: 14px; padding-bottom: 3px; scrollbar-width: thin; }
.task-view-tabs button { min-width: max-content; display: inline-flex; align-items: center; gap: 5px; padding: 7px 8px; color: var(--muted); border: 1px solid transparent; border-radius: 8px; background: transparent; cursor: pointer; font-size: var(--font-2xs); }
.task-view-tabs button.active { color: var(--accent); border-color: var(--accent-border); background: var(--accent-bg); }
.task-view-tabs b { min-width: 17px; padding: 1px 4px; border-radius: 999px; background: var(--surface-sunken); font-size: 9px; }
.task-scroll { margin-top: 4px; }
.task-list { margin: 0; padding: 0; list-style: none; }
.task-row { display: grid; grid-template-columns: 28px minmax(0, 1fr) auto; align-items: start; gap: 7px; padding: 10px 2px; border-bottom: 1px solid var(--border); }
.task-check { width: 22px; height: 22px; display: grid; place-items: center; margin-top: 2px; color: var(--accent-contrast); border: 1px solid var(--border-strong); border-radius: 7px; background: transparent; cursor: pointer; }
.task-row.done .task-check { border-color: var(--accent); background: var(--accent); }
.task-copy { min-width: 0; padding: 0; color: var(--text); text-align: left; border: 0; background: transparent; cursor: pointer; }
.task-copy strong { display: block; line-height: 1.5; overflow-wrap: anywhere; }
.task-row.done .task-copy strong { color: var(--muted); text-decoration: line-through; }
.task-copy > span { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin-top: 5px; }
.task-copy small { display: inline-flex; align-items: center; gap: 3px; color: var(--muted); font-size: 10px; }
.task-copy small.overdue { color: var(--danger); }
.priority { padding: 2px 5px; color: var(--muted); border-radius: 5px; background: var(--surface-sunken); font-size: 9px; font-style: normal; }
.priority.high { color: var(--warning); }.priority.urgent { color: var(--danger); }.priority.low { color: var(--subtle); }
.task-actions { display: flex; opacity: 0; transform: translateX(4px); transition: opacity var(--motion-fast), transform var(--motion-fast); }
.task-row:hover .task-actions, .task-row:focus-within .task-actions { opacity: 1; transform: none; }
.task-actions button { display: grid; place-items: center; width: 27px; height: 27px; padding: 0; color: var(--muted); border: 0; border-radius: 6px; background: transparent; cursor: pointer; }
.task-actions button:hover { color: var(--accent); background: var(--surface-raised); }.task-actions button.danger:hover { color: var(--danger); }
.task-footer { display: flex; align-items: center; justify-content: space-between; flex-shrink: 0; flex-wrap: wrap; gap: 8px 12px; padding-top: 10px; margin-top: 6px; border-top: 1px solid var(--border); }
.task-footer .text-button { margin: 0; }.task-footer > span { color: var(--muted); font-size: var(--font-2xs); white-space: nowrap; }
.dashboard-scroll .project-main { margin-top: 0; grid-template-columns: 46px minmax(0, 1fr); }
.project-main > div, .next-action { min-width: 0; overflow-wrap: anywhere; }.project-title { flex-wrap: wrap; }.project-title h3 { min-width: 0; overflow-wrap: anywhere; }.project-title span { flex-shrink: 0; }
.section-title { display: flex; align-items: end; justify-content: space-between; margin-bottom: 17px; }.section-title h2 { margin: 0; font-size: 17px; }
.knowledge-strip { margin-top: clamp(17px, 1vw, 22px); padding: clamp(24px, 1.4vw, 32px); border: 1px solid var(--border); border-radius: 17px; background: var(--panel); }
.knowledge-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: clamp(10px, .7vw, 16px); }
.knowledge-item { min-width: 0; display: grid; grid-template-columns: 36px 1fr; align-items: center; gap: 11px; padding: 13px; color: var(--text); text-align: left; border: 1px solid var(--border); border-radius: var(--radius-md); background: var(--surface-raised); cursor: pointer; transition: color var(--motion-fast), background var(--motion-fast), border-color var(--motion-fast), transform var(--motion-base) var(--ease-emphasized), box-shadow var(--motion-base); }
.knowledge-item:hover { border-color: var(--accent-border); background: var(--panel-strong); box-shadow: var(--shadow-sm); transform: translateY(-2px); }.knowledge-item:active { transform: translateY(0) scale(.99); }
.knowledge-icon { width: 36px; height: 36px; display: grid; place-items: center; color: var(--accent); border-radius: 9px; background: var(--accent-bg); }
.knowledge-item small, .knowledge-item strong { display: block; }.knowledge-item small { color: var(--muted); font-size: var(--font-2xs); }.knowledge-item strong { overflow: hidden; margin-top: 5px; font-size: var(--font-sm); font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.task-schedule-row { align-items: start; }
.form-field > small { margin-top: 5px; color: var(--muted); font-size: var(--font-2xs); }
@media (hover: none) { .task-actions { opacity: .85; transform: none; } }
@media (max-width: 1200px) { .knowledge-grid { grid-template-columns: repeat(2, 1fr); } }
@media (min-width: 1700px) { .knowledge-grid { grid-template-columns: repeat(4, 1fr); } }
@media (max-width: 1040px) { .dashboard-grid { grid-template-columns: 1fr; } }
@media (max-width: 720px) {
  .dashboard-page { padding-top: 25px; }.dashboard-grid { --dashboard-panel-height: min(590px, 68svh); }.knowledge-strip { padding: 18px; }.knowledge-grid { grid-template-columns: 1fr; }.section-title { align-items: center; }
  .task-view-tabs button span { display: none; }.task-actions { opacity: .85; transform: none; }.form-row { grid-template-columns: 1fr; }
}
</style>
