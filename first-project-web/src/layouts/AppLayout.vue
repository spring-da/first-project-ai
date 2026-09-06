<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import type { RouteLocationRaw } from 'vue-router'
import { storeToRefs } from 'pinia'
import {
  ArrowLeft,
  ShieldCheck,
  BookOpen,
  ChevronRight,
  CircleUserRound,
  Cloud,
  FolderKanban,
  Grid2X2,
  MessageSquareText,
  PanelLeftClose,
  PanelLeftOpen,
  RefreshCw,
  Search,
  X,
  UsersRound,
  Wrench,
} from 'lucide-vue-next'
import AppLogo from '../components/AppLogo.vue'
import AppModal from '../components/AppModal.vue'
import WorkspaceUtilities from '../components/WorkspaceUtilities.vue'
import SearchScopeSelect from '../components/SearchScopeSelect.vue'
import { useAuthStore } from '../stores/auth'
import { useWorkspaceStore } from '../stores/workspace'
import { useSearchStore } from '../stores/search'
import { useCommunicationStore } from '../stores/communication'
import { useNotificationStore } from '../stores/notifications'
import { matchesDocumentSearch, matchesSearch } from '../utils/search'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const workspace = useWorkspaceStore()
const search = useSearchStore()
const communication = useCommunicationStore()
const notifications = useNotificationStore()
const { query, scope } = storeToRefs(search)
const searchInput = ref<HTMLInputElement | null>(null)
const searchBox = ref<HTMLElement | null>(null)
const globalSearchOpen = ref(false)
const activeResult = ref(-1)
const acknowledgingAnnouncement = ref(false)
const SIDEBAR_COLLAPSED_STORAGE_KEY = 'devnest.sidebar-collapsed'

function readSidebarCollapsed() {
  try {
    return window.localStorage.getItem(SIDEBAR_COLLAPSED_STORAGE_KEY) === 'true'
  } catch {
    return false
  }
}

const sidebarCollapsed = ref(readSidebarCollapsed())
const editorFocused = ref(false)
const editorSidebarCollapsed = ref(true)
const toolsSidebarCollapsed = ref(true)
const toolsFocused = computed(() => route.name === 'tools')
const isSidebarCollapsed = computed(() => editorFocused.value
  ? editorSidebarCollapsed.value
  : toolsFocused.value ? toolsSidebarCollapsed.value : sidebarCollapsed.value)

function setEditorFocused(editing: boolean) {
  editorFocused.value = route.name === 'knowledge' && editing
  if (editorFocused.value) {
    editorSidebarCollapsed.value = true
    globalSearchOpen.value = false
  }
}

interface GlobalSearchResult {
  key: string
  type: string
  title: string
  description: string
  to: RouteLocationRaw
}

const navItems = computed(() => [
  { to: '/', label: '工作台', icon: Grid2X2 },
  { to: '/projects', label: '项目', icon: FolderKanban },
  { to: '/knowledge', label: '知识库', icon: BookOpen },
  { to: '/messages', label: '消息中心', icon: MessageSquareText },
  { to: '/tools', label: '系统工具', icon: Wrench, auxiliary: true },
  ...(auth.isAdmin && !auth.workspaceMember ? [{ to: '/admin/accounts', label: '人员管理', icon: UsersRound }] : []),
  { to: '/profile', label: '我的', icon: CircleUserRound },
])

const activeAnnouncement = computed(() => communication.unreadAnnouncements[0] ?? null)

async function acknowledgeAnnouncement() {
  if (!activeAnnouncement.value || acknowledgingAnnouncement.value) return
  acknowledgingAnnouncement.value = true
  try {
    await communication.acknowledgeAnnouncement(activeAnnouncement.value.id)
  } catch (error) {
    notifications.notify(error instanceof Error ? error.message : '公告已读状态保存失败', { type: 'error' })
  } finally {
    acknowledgingAnnouncement.value = false
  }
}

function workspaceLink(path: string) {
  return auth.workspaceMember && path !== '/admin/accounts'
    ? { path, query: { workspace: auth.workspaceMember.id } } : path
}

const displayName = computed(() => workspace.profile?.name || auth.workspaceUser?.displayName || '开发者')
const displayRole = computed(() => workspace.profile?.role || auth.workspaceUser?.email || 'DevNest User')
const initial = computed(() => displayName.value.trim().charAt(0).toUpperCase() || 'D')
const isKnowledgeRoute = computed(() => route.name === 'knowledge')
const hasPageSearch = computed(() => isKnowledgeRoute.value || route.name === 'projects')
const currentNav = computed(() => navItems.value.find((item) => item.to === route.path) ?? navItems.value[0]!)
const moduleErrorSummary = computed(() => Object.values(workspace.moduleStates)
  .filter((state) => state.error).map((state) => state.error).join('；'))
const searchPlaceholder = computed(() => {
  if (scope.value === 'global') return '搜索项目、文章标题、代码或日志…'
  if (route.name === 'projects') return '搜索项目名称、描述或技术栈…'
  if (route.query.tab === 'snippets') return '搜索片段标题、语言或代码…'
  if (route.query.tab === 'logs') return '搜索日志标题、正文或标签…'
  return '搜索文章标题或文件名…'
})
const showSearchResults = computed(() => scope.value === 'global' && globalSearchOpen.value && Boolean(query.value.trim()))
const searchResults = computed<GlobalSearchResult[]>(() => {
  const value = query.value.trim().toLowerCase()
  if (!value) return []
  return [
    ...workspace.projects.filter((item) => matchesSearch(value, item.name, item.description, item.techStack.join(' ')))
      .map((item) => ({
        key: `project-${item.id}`,
        type: '项目',
        title: item.name,
        description: item.description || '项目记录',
        to: { name: 'projects', query: { focus: item.id } },
      })),
    ...workspace.markdownDocuments.filter((item) => matchesDocumentSearch(value, item))
      .map((item) => ({
        key: `markdown-${item.id}`,
        type: 'Markdown 文章',
        title: item.title,
        description: workspace.domains.find((domain) => domain.id === item.domainId)?.name ?? '未分类',
        to: { name: 'knowledge', query: { tab: 'documents', focus: item.id } },
      })),
    ...workspace.snippets.filter((item) => matchesSearch(value, item.title, item.language, item.code))
      .map((item) => ({
        key: `snippet-${item.id}`,
        type: '代码片段',
        title: item.title,
        description: `${workspace.domains.find((domain) => domain.id === item.domainId)?.name ?? '未分类'} · ${item.language}`,
        to: { name: 'knowledge', query: { tab: 'snippets', focus: item.id } },
      })),
    ...workspace.logs.filter((item) => matchesSearch(value, item.title, item.content, item.tags.join(' ')))
      .map((item) => ({
        key: `log-${item.id}`,
        type: '开发日志',
        title: item.title || item.content.slice(0, 30),
        description: workspace.domains.find((domain) => domain.id === item.domainId)?.name ?? '未分类',
        to: { name: 'knowledge', query: { tab: 'logs', focus: item.id } },
      })),
  ].slice(0, 7)
})

function openGlobalSearch() {
  globalSearchOpen.value = true
  nextTick(() => searchInput.value?.focus())
}

function onSearchKeydown(event: KeyboardEvent) {
  if (!showSearchResults.value || !searchResults.value.length) return
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    const count = searchResults.value.length
    activeResult.value = activeResult.value < 0
      ? event.key === 'ArrowDown' ? 0 : count - 1
      : (activeResult.value + (event.key === 'ArrowDown' ? 1 : count - 1)) % count
  } else if (event.key === 'Enter' && activeResult.value >= 0) {
    event.preventDefault()
    const result = searchResults.value[activeResult.value]
    if (result) selectResult(result.to)
  }
}

function onSearchFocusOut(event: FocusEvent) {
  if (!searchBox.value?.contains(event.relatedTarget as Node | null)) globalSearchOpen.value = false
}

function onPointerDown(event: PointerEvent) {
  if (!searchBox.value?.contains(event.target as Node)) globalSearchOpen.value = false
}

function selectResult(to: RouteLocationRaw) {
  query.value = ''
  globalSearchOpen.value = false
  router.push(to)
}

function onShortcut(event: KeyboardEvent) {
  if (editorFocused.value || document.body.matches('.modal-open, .confirmation-open, .notifications-open')) return
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    openGlobalSearch()
  } else if (event.key === 'Escape' && searchBox.value?.contains(document.activeElement)) {
    search.clear()
    globalSearchOpen.value = false
    searchInput.value?.blur()
  }
}

function toggleSidebar() {
  if (editorFocused.value) {
    editorSidebarCollapsed.value = !editorSidebarCollapsed.value
    return
  }
  if (toolsFocused.value) {
    toolsSidebarCollapsed.value = !toolsSidebarCollapsed.value
    return
  }
  sidebarCollapsed.value = !sidebarCollapsed.value
  try {
    window.localStorage.setItem(SIDEBAR_COLLAPSED_STORAGE_KEY, String(sidebarCollapsed.value))
  } catch {
    // The layout still works when storage is unavailable (for example in private mode).
  }
}

watch(() => route.name, () => {
  if (route.name !== 'knowledge') editorFocused.value = false
  if (route.name === 'tools') toolsSidebarCollapsed.value = true
  search.reset(hasPageSearch.value ? 'page' : 'global')
  globalSearchOpen.value = false
}, { immediate: true })
watch([query, scope], () => { activeResult.value = -1 })

onMounted(() => {
  workspace.loadAll()
  communication.loadUnreadAnnouncements().catch((error) => {
    notifications.notify(error instanceof Error ? error.message : '系统公告加载失败', { type: 'error' })
  })
  window.addEventListener('keydown', onShortcut)
  window.addEventListener('pointerdown', onPointerDown)
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onShortcut)
  window.removeEventListener('pointerdown', onPointerDown)
})
</script>

<template>
  <div class="app-shell" :class="{ 'sidebar-collapsed': isSidebarCollapsed, 'editor-focused': editorFocused, 'tools-focused': toolsFocused }">
    <aside class="sidebar">
      <RouterLink class="brand-link" :to="workspaceLink('/')" aria-label="返回 DevNest 工作台" title="DevNest"><AppLogo /></RouterLink>
      <button
        class="sidebar-toggle"
        type="button"
        aria-controls="main-navigation"
        :aria-expanded="!isSidebarCollapsed"
        :aria-label="isSidebarCollapsed ? '展开左侧菜单' : '收起左侧菜单'"
        :title="isSidebarCollapsed ? '展开菜单' : '收起菜单'"
        @click="toggleSidebar"
      >
        <Transition name="icon-swap" mode="out-in">
          <PanelLeftOpen v-if="isSidebarCollapsed" key="open" :size="16" />
          <PanelLeftClose v-else key="close" :size="16" />
        </Transition>
      </button>
      <nav id="main-navigation" class="main-nav" aria-label="主导航" :style="{ '--nav-count': navItems.length }">
        <span class="nav-caption">工作空间</span>
        <RouterLink
          v-for="item in navItems"
          :key="item.to"
          class="nav-item"
          :class="{ auxiliary: item.auxiliary }"
          :to="workspaceLink(item.to)"
          :title="item.label"
          :aria-label="item.label"
        >
          <component :is="item.icon" :size="19" /><span>{{ item.label }}</span>
        </RouterLink>
      </nav>
      <div class="sidebar-footer">
        <div
          class="sync-status"
          :class="{ error: workspace.hasLoadErrors || workspace.error }"
          :title="workspace.error || moduleErrorSummary || (workspace.loading ? '正在连接云端…' : '云端数据已同步')"
        >
          <Cloud :size="16" />
          <span>{{ workspace.error || workspace.hasLoadErrors ? '部分数据同步失败 · 可局部重试' : (workspace.loading ? '正在连接云端…' : '云端数据已同步') }}</span>
        </div>
        <RouterLink class="profile-chip" :to="workspaceLink('/profile')" :title="displayName" :aria-label="`打开 ${displayName} 的个人设置`">
          <span class="avatar">{{ initial }}</span>
          <span><strong>{{ displayName }}</strong><small>{{ displayRole }}</small></span>
          <ChevronRight :size="16" />
        </RouterLink>
      </div>
    </aside>

    <main class="workspace">
      <div v-if="auth.workspaceMember" class="member-access-banner" role="status">
        <ShieldCheck :size="17" />
        <span><strong>正在模拟登录：{{ displayName }}</strong><small>{{ auth.workspaceMember.email }} · 管理员可编辑此成员的全部工作区数据</small></span>
        <RouterLink to="/admin/accounts" class="button button-secondary"><ArrowLeft :size="15" />返回人员管理</RouterLink>
      </div>
      <Transition name="topbar">
      <header v-if="!editorFocused" class="topbar">
        <div class="workspace-breadcrumb"><span>个人空间</span><ChevronRight :size="13" /><strong>{{ currentNav.label }}</strong></div>
        <div ref="searchBox" class="global-search" @focusout="onSearchFocusOut">
          <Search :size="18" />
          <SearchScopeSelect v-if="hasPageSearch" v-model="scope" @change="openGlobalSearch" @open-change="(open) => { if (open) globalSearchOpen = false }" />
          <input ref="searchInput" v-model="query" type="search" :placeholder="searchPlaceholder" aria-label="搜索" :role="scope === 'global' ? 'combobox' : 'searchbox'" :aria-expanded="scope === 'global' ? showSearchResults : undefined" :aria-controls="showSearchResults ? 'global-search-results' : undefined" :aria-activedescendant="showSearchResults && activeResult >= 0 ? `search-result-${activeResult}` : undefined" autocomplete="off" @focus="globalSearchOpen = true" @keydown="onSearchKeydown" />
          <button v-if="query" type="button" aria-label="清空搜索" @click="search.clear(); searchInput?.focus()"><X :size="15" /></button>
          <kbd v-else>Ctrl K</kbd>
          <Transition name="popover">
          <div v-if="showSearchResults" id="global-search-results" class="search-results" role="listbox" aria-label="全局搜索结果">
            <div class="search-results__heading">搜索整个工作空间 <span>↑ ↓ 选择 · Enter 打开</span></div>
            <TransitionGroup name="list" tag="div">
              <button v-for="(item, index) in searchResults" :id="`search-result-${index}`" :key="item.key" type="button" role="option" :aria-selected="activeResult === index" :class="{ 'is-active': activeResult === index }" @click="selectResult(item.to)">
                <span>{{ item.type }}</span><span class="result-copy"><strong>{{ item.title }}</strong><small>{{ item.description }}</small></span><ChevronRight :size="15" />
              </button>
              <p v-if="!searchResults.length" key="empty">没有找到“{{ query }}”相关内容</p>
            </TransitionGroup>
          </div>
          </Transition>
        </div>
        <div class="topbar-actions">
          <button class="icon-btn" type="button" :disabled="workspace.loading" aria-label="刷新数据" @click="workspace.loadAll(true)">
            <RefreshCw :size="18" :class="{ spin: workspace.loading }" />
          </button>
          <WorkspaceUtilities />
        </div>
      </header>
      </Transition>

      <RouterView v-slot="{ Component, route }">
        <Transition name="page" mode="out-in" appear>
          <KeepAlive v-if="route.meta.keepAlive" :max="8">
            <component :is="Component" :key="route.name" @editing-change="setEditorFocused" />
          </KeepAlive>
          <component :is="Component" v-else :key="route.fullPath" />
        </Transition>
      </RouterView>
    </main>

    <AppModal
      v-if="activeAnnouncement"
      :title="activeAnnouncement.title"
      :description="`${activeAnnouncement.publisherName} · ${new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(activeAnnouncement.publishedAt))}`"
      @close="acknowledgeAnnouncement"
    >
      <div class="announcement-modal-copy">
        <span><MessageSquareText :size="20" /></span>
        <p>{{ activeAnnouncement.content }}</p>
      </div>
      <div class="announcement-modal-actions">
        <small v-if="communication.unreadAnnouncements.length > 1">确认后继续查看下一则公告</small>
        <button class="button button-primary" type="button" :disabled="acknowledgingAnnouncement" @click="acknowledgeAnnouncement">
          {{ acknowledgingAnnouncement ? '正在保存…' : '我已阅读' }}
        </button>
      </div>
    </AppModal>
  </div>
</template>

<style scoped>
.member-access-banner { display: flex; flex: 0 0 auto; align-items: center; gap: 10px; padding: 10px 20px; color: var(--accent); background: var(--accent-bg); border-bottom: 1px solid var(--accent-border); }
.member-access-banner > svg { flex-shrink: 0; }
.member-access-banner > span { flex: 1; min-width: 0; overflow-wrap: anywhere; }
.member-access-banner strong, .member-access-banner small { display: block; font-size: var(--font-xs); }
.member-access-banner small { margin-top: 3px; color: var(--muted); }
.member-access-banner > a { flex-shrink: 0; }
.announcement-modal-copy { display: grid; grid-template-columns: 42px minmax(0, 1fr); gap: 14px; align-items: start; }
.announcement-modal-copy > span { width: 42px; height: 42px; display: grid; place-items: center; color: var(--accent); border-radius: 11px; background: var(--accent-bg); }
.announcement-modal-copy p { margin: 2px 0 0; color: var(--subtle); font-size: var(--font-sm); line-height: 1.78; white-space: pre-wrap; overflow-wrap: anywhere; }
.announcement-modal-actions { display: flex; align-items: center; justify-content: flex-end; gap: 14px; margin-top: 24px; }
.announcement-modal-actions small { color: var(--muted); font-size: var(--font-2xs); }
@media (max-width: 600px) { .member-access-banner { flex-wrap: wrap; padding: 9px 12px; } .member-access-banner > span { flex-basis: calc(100% - 30px); } .member-access-banner > a { margin-left: auto; min-height: 32px; } }

.brand-link { padding: 0 8px; }
.workspace-breadcrumb { display: flex; align-items: center; gap: 10px; color: var(--muted); font-size: var(--font-xs); white-space: nowrap; }
.workspace-breadcrumb strong { color: var(--subtle); font-weight: 500; }
.global-search { position: relative; width: min(540px, 52vw); min-width: 0; display: flex; align-items: center; gap: 10px; margin-left: auto; padding: 0 12px; color: var(--muted); border: 1px solid var(--border); border-radius: 10px; background: var(--surface-raised); transition: border-color .18s, box-shadow .18s; }
.global-search:focus-within { border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); background: var(--panel); }
.global-search > svg { flex-shrink: 0; }
.global-search input { min-width: 0; width: 100%; height: 40px; padding: 0; color: var(--text); border: 0; outline: 0; background: transparent; font-size: var(--font-xs); }
.global-search input::-webkit-search-cancel-button { appearance: none; }
.global-search input::placeholder { color: var(--muted); }
.global-search > button { display: grid; flex-shrink: 0; width: 26px; height: 26px; place-items: center; padding: 3px; color: var(--muted); border: 0; border-radius: 6px; background: transparent; cursor: pointer; }
.global-search > button:hover { color: var(--text); background: var(--panel-strong); }
.search-results { position: absolute; z-index: 20; top: calc(100% + 8px); right: 0; width: max(100%, 420px); max-height: min(540px, 75vh); overflow-y: auto; padding: 8px; border: 1px solid var(--border); border-radius: var(--radius-lg); background: var(--panel); box-shadow: var(--shadow-lg); transform-origin: top center; }
.search-results__heading { display: flex; justify-content: space-between; gap: 12px; padding: 8px 10px 12px; color: var(--muted); font-size: 11px; }
.search-results button { width: 100%; display: grid; grid-template-columns: 72px 1fr auto; align-items: center; gap: 10px; padding: 11px 12px; color: var(--text); text-align: left; border: 0; border-radius: 8px; background: transparent; cursor: pointer; transition: color var(--motion-fast), background var(--motion-fast), transform var(--motion-base) var(--ease-standard); }
.search-results button:hover, .search-results button.is-active { background: var(--accent-soft); }
.search-results button:hover, .search-results button.is-active { transform: translateX(2px); }
.search-results button span { color: var(--accent); font-size: var(--font-xs); }
.result-copy { min-width: 0; display: block; }
.search-results button strong, .search-results button small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.search-results button strong { font-size: var(--font-sm); font-weight: 650; }
.search-results button small { margin-top: 3px; color: var(--muted); font-size: var(--font-2xs); }
.search-results p { margin: 16px; color: var(--muted); text-align: center; font-size: var(--font-sm); }
.topbar-actions { display: flex; flex-shrink: 0; align-items: center; gap: 6px; }
.topbar-actions .icon-btn { width: 36px; height: 36px; border-color: transparent; background: transparent; }
.topbar-actions .icon-btn:hover { background: var(--surface-raised); }
.workspace-loading { height: calc(100vh - 70px); display: flex; align-items: center; justify-content: center; gap: 11px; color: var(--muted); font-size: var(--font-md); }
.sync-status.error { color: var(--danger); }
.sync-status.error svg { color: var(--danger); }
@media (max-width: 720px) {
  .workspace-breadcrumb { display: none; }
  .global-search { width: auto; flex: 1; gap: 6px; padding-inline: 9px; }
  .global-search kbd { display: none; }
  .search-results { position: fixed; top: 55px; left: 12px; right: 12px; width: auto; }
  .search-results__heading span { display: none; }
  .topbar-actions { gap: 2px; }
  .topbar-actions .icon-btn { width: 32px; height: 34px; }
}
</style>
