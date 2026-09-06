<script setup lang="ts">
import { computed, nextTick, onActivated, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Archive, CheckCircle2, CirclePause, Clock3, MoreHorizontal, Plus, Trash2 } from 'lucide-vue-next'
import AppModal from '../components/AppModal.vue'
import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import WorkspaceModuleState from '../components/WorkspaceModuleState.vue'
import { useWorkspaceStore } from '../stores/workspace'
import { useSearchStore } from '../stores/search'
import { useConfirmationStore } from '../stores/confirmation'
import { matchesSearch } from '../utils/search'
import type { DevProject, ProjectDraft, ProjectStatus } from '../types'

const workspace = useWorkspaceStore()
const confirmation = useConfirmationStore()
const route = useRoute()
const router = useRouter()
const searchState = useSearchStore()
const search = computed(() => searchState.pageQuery)
const statusFilter = ref<'ALL' | ProjectStatus>('ALL')
const focusedId = ref('')
let focusTimer: ReturnType<typeof setTimeout> | undefined
const editing = ref<DevProject | null>(null)
const showEditor = ref(false)
const techInput = ref('')
const draft = reactive<ProjectDraft>({ name: '', description: '', techStack: [], status: 'PLANNING', progress: 0, nextAction: '' })

const statusMeta: Record<ProjectStatus, { label: string; icon: typeof Clock3 }> = {
  PLANNING: { label: '规划中', icon: Clock3 },
  BUILDING: { label: '构建中', icon: Archive },
  PAUSED: { label: '已暂停', icon: CirclePause },
  COMPLETED: { label: '已完成', icon: CheckCircle2 },
}
const filters: Array<{ value: 'ALL' | ProjectStatus; label: string }> = [
  { value: 'ALL', label: '全部项目' },
  { value: 'PLANNING', label: '规划中' },
  { value: 'BUILDING', label: '构建中' },
  { value: 'PAUSED', label: '已暂停' },
  { value: 'COMPLETED', label: '已完成' },
]

const filteredProjects = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  return workspace.projects.filter((project) => {
    const matchesStatus = statusFilter.value === 'ALL' || project.status === statusFilter.value
    return matchesStatus && matchesSearch(keyword, project.name, project.description, project.techStack.join(' '))
  })
})
const projectsUnavailable = computed(() => !workspace.projects.length
  && (workspace.moduleStates.projects.loading || Boolean(workspace.moduleStates.projects.error)))

function readStatusFilter(value: unknown): 'ALL' | ProjectStatus {
  const candidate = typeof value === 'string' ? value.toUpperCase() : ''
  if (candidate === 'ACTIVE') return 'BUILDING'
  return filters.some((filter) => filter.value === candidate) ? candidate as 'ALL' | ProjectStatus : 'ALL'
}

function selectStatusFilter(value: 'ALL' | ProjectStatus) {
  statusFilter.value = value
  const query = { ...route.query }
  delete query.focus
  if (value === 'ALL') delete query.status
  else query.status = value
  void router.replace({ query })
}

async function locateProject(id: string) {
  if (!id || !workspace.projects.some((project) => project.id === id)) return

  searchState.clear()
  statusFilter.value = 'ALL'
  await nextTick()

  focusedId.value = id
  document.getElementById(`project-item-${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })

  if (focusTimer) clearTimeout(focusTimer)
  focusTimer = setTimeout(() => {
    if (focusedId.value === id) focusedId.value = ''
  }, 4200)
}

watch(
  () => route.query.focus,
  (focus) => locateProject(typeof focus === 'string' ? focus : ''),
  { immediate: true },
)
watch(() => route.query.status, (status) => {
  if (!route.query.focus) statusFilter.value = readStatusFilter(status)
}, { immediate: true })
watch(() => workspace.projects.length, () => {
  if (typeof route.query.focus === 'string') void locateProject(route.query.focus)
})

onActivated(() => locateProject(typeof route.query.focus === 'string' ? route.query.focus : ''))
onBeforeUnmount(() => {
  if (focusTimer) clearTimeout(focusTimer)
})

function openEditor(project?: DevProject) {
  editing.value = project ?? null
  Object.assign(draft, project ? {
    name: project.name,
    description: project.description,
    techStack: [...project.techStack],
    status: project.status,
    progress: project.progress,
    nextAction: project.nextAction,
  } : { name: '', description: '', techStack: [], status: 'PLANNING', progress: 0, nextAction: '' })
  techInput.value = draft.techStack.join(', ')
  showEditor.value = true
}

async function submitProject() {
  draft.techStack = techInput.value.split(/[,，]/).map((item) => item.trim()).filter((item, index, list) => item && list.indexOf(item) === index)
  await workspace.saveProject({ ...draft, techStack: [...draft.techStack] }, editing.value?.id)
  showEditor.value = false
}

async function removeProject(project: DevProject) {
  if (!await confirmation.ask({
    title: '删除这个项目？', message: `“${project.name}”将从你的工作空间中删除。`,
    detail: '此操作无法撤销，请确认不再需要这个项目。',
    confirmText: '删除项目', tone: 'danger', icon: 'delete',
  })) return
  try { await workspace.deleteProject(project.id) }
  catch { /* The global notification host reports API failures. */ }
}
</script>

<template>
  <div class="page-content">
    <PageHeader eyebrow="PROJECT RADAR" title="项目雷达" description="把每个想法放在正确的阶段，始终看清下一步行动。">
      <button class="button button-primary" type="button" @click="openEditor()"><Plus :size="17" />新建项目</button>
    </PageHeader>
    <WorkspaceModuleState module="projects" title="项目" :has-data="Boolean(workspace.projects.length)" />

    <section class="project-overview">
      <div><span>全部项目</span><strong>{{ projectsUnavailable ? '—' : workspace.projects.length }}</strong></div>
      <div><span>构建中</span><strong>{{ projectsUnavailable ? '—' : workspace.projects.filter((item) => item.status === 'BUILDING').length }}</strong></div>
      <div><span>平均进度</span><strong>{{ projectsUnavailable ? '—' : `${workspace.projects.length ? Math.round(workspace.projects.reduce((sum, item) => sum + item.progress, 0) / workspace.projects.length) : 0}%` }}</strong></div>
      <div><span>已经完成</span><strong>{{ projectsUnavailable ? '—' : workspace.projects.filter((item) => item.status === 'COMPLETED').length }}</strong></div>
    </section>

    <div class="toolbar">
      <div class="filter-tabs">
        <button v-for="filter in filters" :key="filter.value" type="button" :class="{ active: statusFilter === filter.value }" @click="selectStatusFilter(filter.value)">{{ filter.label }}</button>
      </div>
      <span class="list-context" aria-live="polite">{{ search ? `搜索“${search}” · ` : '' }}{{ filteredProjects.length }} 个项目<button v-if="search" type="button" @click="searchState.clear()">清除</button></span>
    </div>

    <TransitionGroup v-if="filteredProjects.length" name="list" tag="div" class="project-grid">
      <article
        v-for="project in filteredProjects"
        :id="`project-item-${project.id}`"
        :key="project.id"
        class="project-card"
        :class="{ focused: focusedId === project.id }"
      >
        <div class="project-card__top">
          <span class="status-badge" :class="project.status.toLowerCase()"><component :is="statusMeta[project.status].icon" :size="13" />{{ statusMeta[project.status].label }}</span>
          <div class="project-actions"><button type="button" aria-label="编辑项目" @click="openEditor(project)"><MoreHorizontal :size="18" /></button><button type="button" aria-label="删除项目" @click="removeProject(project)"><Trash2 :size="16" /></button></div>
        </div>
        <div class="project-card__identity"><span>{{ project.name.slice(0, 2).toUpperCase() }}</span><div><h2>{{ project.name }}</h2><p>{{ project.description || '还没有项目描述。' }}</p></div></div>
        <div class="tech-list"><span v-for="tech in project.techStack" :key="tech">{{ tech }}</span><span v-if="!project.techStack.length" class="empty-tech">待补充技术栈</span></div>
        <div class="card-progress"><div><span>当前进度</span><strong>{{ project.progress }}%</strong></div><div class="progress"><span :style="{ width: `${project.progress}%` }"></span></div></div>
        <div class="card-next"><small>下一步行动</small><p>{{ project.nextAction }}</p></div>
      </article>
    </TransitionGroup>
    <EmptyState v-else-if="!workspace.moduleStates.projects.loading && !workspace.moduleStates.projects.error" :title="workspace.projects.length ? '没有符合条件的项目' : '建立你的第一个项目'" :description="workspace.projects.length ? '尝试调整搜索词或阶段筛选。' : '从一个清晰的目标和下一步行动开始。'"><button v-if="!workspace.projects.length" class="button button-secondary" @click="openEditor()"><Plus :size="16" />新建项目</button></EmptyState>

    <AppModal v-if="showEditor" :title="editing ? '编辑项目' : '新建项目'" description="定义项目阶段、当前进度与最具体的下一步。" @close="showEditor = false">
      <form class="form-grid" @submit.prevent="submitProject">
        <label class="form-field"><span>项目名称</span><input v-model.trim="draft.name" autofocus required maxlength="160" placeholder="例如：DevNest Web" /></label>
        <label class="form-field"><span>项目描述</span><textarea v-model.trim="draft.description" maxlength="5000" rows="3" placeholder="这个项目要解决什么问题？"></textarea></label>
        <div class="form-row">
          <label class="form-field"><span>当前阶段</span><select v-model="draft.status"><option v-for="(meta, value) in statusMeta" :key="value" :value="value">{{ meta.label }}</option></select></label>
          <label class="form-field"><span>项目进度 · {{ draft.progress }}%</span><input v-model.number="draft.progress" class="range-input" type="range" min="0" max="100" /></label>
        </div>
        <label class="form-field"><span>技术栈</span><input v-model="techInput" maxlength="500" placeholder="Flutter, Vue 3, Spring Boot（使用逗号分隔）" /></label>
        <label class="form-field"><span>下一步行动</span><textarea v-model.trim="draft.nextAction" required maxlength="500" rows="2" placeholder="下一件可以立刻开始的具体事情"></textarea></label>
        <div class="form-actions"><button class="button button-ghost" type="button" @click="showEditor = false">取消</button><button class="button button-primary" type="submit" :disabled="workspace.mutating">{{ editing ? '保存修改' : '创建项目' }}</button></div>
      </form>
    </AppModal>
  </div>
</template>

<style scoped>
.project-overview { display: grid; grid-template-columns: repeat(4, 1fr); margin-bottom: 18px; border: 1px solid var(--border); border-radius: 14px; background: var(--panel); }
.project-overview > div { padding: clamp(20px, 1.2vw, 28px); border-right: 1px solid var(--border); }
.project-overview > div:last-child { border: 0; }
.project-overview span, .project-overview strong { display: block; }
.project-overview span { color: var(--muted); font-size: var(--font-xs); }
.project-overview strong { margin-top: 9px; font-size: var(--font-lg); }
.toolbar { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin: clamp(24px, 1.4vw, 32px) 0 clamp(18px, 1vw, 24px); }
.filter-tabs { display: flex; gap: 5px; }
.filter-tabs button { min-height: var(--control-height); padding: 0 14px; color: var(--muted); border: 1px solid transparent; border-radius: 9px; background: transparent; cursor: pointer; font-size: var(--font-xs); transition: color var(--motion-fast), border-color var(--motion-fast), background var(--motion-fast), transform var(--motion-base) var(--ease-standard); }
.filter-tabs button:hover { color: var(--text); background: var(--surface-raised); transform: translateY(-1px); }
.filter-tabs button.active { color: var(--accent); border-color: var(--accent-border); background: var(--accent-bg); }
.project-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: clamp(14px, 1vw, 22px); }
.project-card { padding: clamp(22px, 1.35vw, 30px); border: 1px solid var(--border); border-radius: 17px; background: var(--panel); box-shadow: 0 5px 22px rgba(0,0,0,.025); transition: border-color .2s, transform .2s, box-shadow .2s; }
.project-card:hover { border-color: var(--border-strong); box-shadow: 0 10px 28px rgba(0,0,0,.06); transform: translateY(-2px); }
.project-card.focused { border-color: var(--accent); box-shadow: 0 0 0 4px var(--accent-soft), var(--shadow-md); animation: project-focus 1.15s ease-in-out 2; }
@keyframes project-focus {
  0%, 100% { box-shadow: 0 0 0 3px var(--accent-soft), var(--shadow-md); }
  50% { box-shadow: 0 0 0 8px var(--accent-soft), var(--shadow-lg); }
}
.project-card__top { display: flex; align-items: center; justify-content: space-between; }
.status-badge { display: inline-flex; align-items: center; gap: 6px; padding: 5px 8px; border-radius: 999px; color: var(--warning); background: color-mix(in srgb,var(--warning) 10%,transparent); font-size: var(--font-2xs); font-weight: 700; }
.status-badge.building { color: var(--success); background: rgba(111,207,151,.08); }
.status-badge.paused { color: var(--muted); background: rgba(170,176,187,.08); }
.status-badge.completed { color: var(--accent); background: rgba(132,184,232,.08); }
.project-actions { display: flex; gap: 2px; }
.project-actions button { padding: 6px; color: var(--muted); border: 0; border-radius: 7px; background: transparent; cursor: pointer; transition: color var(--motion-fast), background var(--motion-fast), transform var(--motion-fast); }
.project-actions button:hover { color: var(--text); background: var(--surface-raised); transform: translateY(-1px); }
.project-card__identity { display: grid; grid-template-columns: 45px 1fr; gap: 13px; margin-top: 20px; }
.project-card__identity > span { width: 45px; height: 45px; display: grid; place-items: center; color: var(--accent); border: 1px solid var(--accent-border); border-radius: 11px; background: var(--accent-bg); font-size: var(--font-sm); font-weight: 800; }
.project-card h2 { margin: 1px 0 0; font-size: var(--font-lg); }
.project-card__identity p { min-height: 44px; display: -webkit-box; overflow: hidden; margin: 8px 0 0; color: var(--muted); font-size: var(--font-xs); line-height: 1.65; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.tech-list { min-height: 26px; display: flex; flex-wrap: wrap; gap: 6px; margin-top: 18px; }
.tech-list span { padding: 4px 7px; color: var(--subtle); border: 1px solid var(--border); border-radius: 6px; background: var(--surface-raised); font-size: var(--font-2xs); }
.tech-list .empty-tech { border-style: dashed; color: var(--muted); }
.card-progress { margin-top: 19px; }
.card-progress > div:first-child { display: flex; justify-content: space-between; margin-bottom: 8px; color: var(--muted); font-size: var(--font-xs); }
.card-progress strong { color: var(--accent); }
.card-next { margin-top: 17px; padding: 12px 13px; border-left: 2px solid var(--accent); background: var(--surface-raised); }
.card-next small { color: var(--muted); font-size: var(--font-2xs); }
.card-next p { margin: 5px 0 0; color: var(--subtle); font-size: var(--font-xs); line-height: 1.55; }
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
.range-input { padding-inline: 0 !important; accent-color: var(--accent); }
@media (max-width: 1160px) { .toolbar { align-items: stretch; flex-direction: column; } }
@media (min-width: 1700px) { .project-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 720px) {
  .project-overview { grid-template-columns: repeat(2, 1fr); }
  .project-overview > div { border-bottom: 1px solid var(--border); }
  .project-overview > div:nth-child(2) { border-right: 0; }
  .project-overview > div:nth-child(n+3) { border-bottom: 0; }
  .filter-tabs { overflow-x: auto; padding-bottom: 3px; }
  .filter-tabs button { flex: 0 0 auto; }
  .project-grid { grid-template-columns: 1fr; }
  .project-card { padding: 18px; }
  .form-row { grid-template-columns: 1fr; }
}
</style>
