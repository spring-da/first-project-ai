<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, BookOpen, Check, ChevronDown, Code2, FileText, Filter, Link2, LoaderCircle, RefreshCw, Share2, Unlink, UsersRound, X } from 'lucide-vue-next'
import AppModal from '../components/AppModal.vue'
import EmptyState from '../components/EmptyState.vue'
import SharedContentReader from '../components/SharedContentReader.vue'
import { useAuthStore } from '../stores/auth'
import { useConfirmationStore, type ConfirmationOptions } from '../stores/confirmation'
import { useSharingStore } from '../stores/sharing'
import { useSearchStore } from '../stores/search'
import type { ShareBundleItem, ShareBundleSummary, ShareResourceType, SharedPoolEntry } from '../types/sharing'

type Tab = 'all' | 'mine' | 'links'
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const sharing = useSharingStore()
const searchState = useSearchStore()
const confirmation = useConfirmationStore()
const tab = computed<Tab>(() => route.query.tab === 'links' ? 'links' : route.query.tab === 'mine' ? 'mine' : 'all')
const search = computed(() => searchState.pageQuery)
const typeFilter = ref<ShareResourceType | ''>('')
const showTypeMenu = ref(false)
const typeMenu = ref<HTMLElement | null>(null)
const typeTrigger = ref<HTMLButtonElement | null>(null)
const typeOptions: Array<{ value: ShareResourceType | ''; label: string; description: string; tone: string }> = [
  { value: '', label: '全部', description: '浏览所有类型的分享', tone: 'all' },
  { value: 'MARKDOWN', label: '文章', description: '笔记与完整文章', tone: 'documents' },
  { value: 'SNIPPET', label: '代码片段', description: '可复用的代码与示例', tone: 'snippets' },
  { value: 'FLOWCHART', label: '流程图', description: '业务流程与系统结构', tone: 'flowcharts' },
]
const selectedTypeOption = computed(() => typeOptions.find((option) => option.value === typeFilter.value) ?? typeOptions[0]!)
const selected = ref<string[]>([])
const confirming = ref(false)
const isLinks = computed(() => tab.value === 'links')
const currentPage = computed(() => isLinks.value ? sharing.links : sharing.pool)
const request = computed(() => isLinks.value ? sharing.requests.links : sharing.requests.pool)
const pageCount = computed(() => Math.max(1, Math.ceil(currentPage.value.total / currentPage.value.size)))
const hasFilters = computed(() => !!search.value.trim() || (!isLinks.value && !!typeFilter.value))
const busy = computed(() => sharing.busy || confirming.value)
const selectableIds = computed(() => isLinks.value
  ? sharing.links.items.filter((item) => !item.revokedAt).map((item) => item.id)
  : sharing.pool.items.filter((item) => item.mine).map((item) => item.id))
const allSelected = computed(() => selectableIds.value.length > 0 && selectableIds.value.every((id) => selected.value.includes(id)))
const typeLabels: Record<ShareResourceType, string> = { MARKDOWN: '文章', SNIPPET: '代码片段', FLOWCHART: '流程图' }
const typeIcons = { MARKDOWN: FileText, SNIPPET: Code2, FLOWCHART: BookOpen }
let mounted = true
let viewVersion = 0

async function openTypeMenu(index = typeOptions.findIndex((option) => option.value === typeFilter.value)) {
  if (busy.value) return
  showTypeMenu.value = true
  await nextTick()
  if (showTypeMenu.value) typeMenu.value?.querySelectorAll<HTMLButtonElement>('[role="option"]')[index]?.focus()
}

function closeTypeMenu(restoreFocus = false) {
  showTypeMenu.value = false
  if (restoreFocus) typeTrigger.value?.focus()
}

function selectTypeOption(value: ShareResourceType | '') {
  if (busy.value) return
  typeFilter.value = value
  closeTypeMenu(true)
}

function onTypeMenuKeydown(event: KeyboardEvent) {
  if (event.key === 'Tab' && showTypeMenu.value) {
    closeTypeMenu(true)
    return
  }
  if (event.key === 'Escape' && showTypeMenu.value) {
    event.preventDefault()
    event.stopPropagation()
    closeTypeMenu(true)
    return
  }
  if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  const options = Array.from(typeMenu.value?.querySelectorAll<HTMLButtonElement>('[role="option"]') ?? [])
  const current = options.indexOf(document.activeElement as HTMLButtonElement)
  if (!showTypeMenu.value) {
    void openTypeMenu(event.key === 'Home' ? 0 : event.key === 'End' ? typeOptions.length - 1 : undefined)
    return
  }
  const next = event.key === 'Home' ? 0 : event.key === 'End' ? options.length - 1
    : (current + (event.key === 'ArrowDown' ? 1 : -1) + options.length) % options.length
  options[next]?.focus()
}

function onTypeMenuFocusout(event: FocusEvent) {
  if (!(event.relatedTarget instanceof Node) || !typeMenu.value?.contains(event.relatedTarget)) closeTypeMenu()
}

function onOutsidePointerdown(event: PointerEvent) {
  if (event.target instanceof Node && !typeMenu.value?.contains(event.target)) closeTypeMenu()
}

function switchTab(value: Tab) {
  if (busy.value) return
  void router.replace({ query: { ...route.query, tab: value === 'all' ? undefined : value } })
}

function load(page = 0, debounce = false) {
  if (!mounted || !auth.workspaceKey) return
  selected.value = []
  sharing.cancelLists()
  if (isLinks.value) {
    const query = { page, size: 20, q: search.value }
    if (debounce) sharing.searchLinks(query)
    else void sharing.loadLinks(query)
  } else {
    const query = { page, size: 20, q: search.value, type: typeFilter.value || undefined, mine: tab.value === 'mine' }
    if (debounce) sharing.searchPool(query)
    else void sharing.loadPool(query)
  }
}

function toggle(id: string) {
  if (busy.value || request.value.loading || !selectableIds.value.includes(id)) return
  selected.value = selected.value.includes(id) ? selected.value.filter((item) => item !== id) : [...selected.value, id]
}

function toggleAll() {
  if (busy.value || request.value.loading) return
  selected.value = allSelected.value ? [] : [...selectableIds.value]
}

function date(value: string) {
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? '时间未知' : parsed.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

function linkStatus(link: ShareBundleSummary) {
  if (link.revokedAt) return '已撤销'
  if (new Date(link.expiresAt).getTime() <= Date.now()) return '已过期'
  return link.active ? '有效' : '不可访问'
}

async function confirmAction(options: ConfirmationOptions, action: () => Promise<boolean>) {
  if (busy.value) return
  const owner = auth.workspaceKey
  const version = viewVersion
  confirming.value = true
  try {
    if (!await confirmation.ask(options) || !mounted || owner !== auth.workspaceKey || version !== viewVersion) return
    if (await action()) selected.value = []
  } finally {
    if (mounted && version === viewVersion) confirming.value = false
  }
}

function withdraw(items: SharedPoolEntry[]) {
  const own = items.filter((item) => item.mine)
  if (!own.length) return
  void confirmAction({
    title: own.length === 1 ? '撤下这项共享？' : `撤下 ${own.length} 项共享？`,
    message: own.length === 1 ? `“${own[0]!.title}”将从知识广场移除，成员无法再从知识广场访问。` : '所选内容将从知识广场移除，成员无法再从知识广场访问。',
    detail: '原文和外部链接不受影响。之后需要主动重新发布；已被接收者保存的内容无法收回。',
    confirmText: '从知识广场撤下', tone: 'danger', icon: 'leave',
  }, () => sharing.withdraw(own.map((item) => item.id)))
}

function revoke(items: ShareBundleSummary[]) {
  const active = items.filter((item) => !item.revokedAt)
  if (!active.length) return
  void confirmAction({
    title: active.length === 1 ? '撤销这个外部链接？' : `撤销 ${active.length} 个外部链接？`,
    message: active.length === 1 ? `“${active[0]!.title}”的链接将失效，接收者无法再通过此链接访问其中的任何内容。` : '所选链接将失效，接收者无法再通过这些链接访问其中的任何内容。',
    detail: '原文、知识广场和其他外部链接不受影响。此操作无法恢复，已被接收者保存的内容无法收回。',
    confirmText: '撤销外部链接', tone: 'danger', icon: 'leave',
  }, () => sharing.revoke(active.map((item) => item.id)))
}

function removeItem(item: ShareBundleItem) {
  const detail = sharing.bundleDetail
  if (!detail || item.removedAt || detail.link.revokedAt) return
  const lastAvailable = detail.items.filter((entry) => entry.available && !entry.removedAt).length <= 1 && item.available
  void confirmAction({
    title: '从此链接移除内容？',
    message: `“${item.title}”将不再通过此链接提供访问。${lastAvailable ? '这是最后一项可访问内容，移除后整个链接将无法访问。' : '此链接中的其他可用内容仍可访问。'}`,
    detail: '原文、知识广场及其他链接不受影响。移除后无法添加回此链接，已被接收者保存的内容无法收回。',
    confirmText: '移除此项', tone: 'danger', icon: 'leave',
  }, () => sharing.removeItem(detail.link.id, item.id))
}

function bulkAction() {
  if (isLinks.value) revoke(sharing.links.items.filter((item) => selected.value.includes(item.id)))
  else withdraw(sharing.pool.items.filter((item) => selected.value.includes(item.id)))
}

function clearFilters() { searchState.clear(); typeFilter.value = '' }

watch([tab, search, typeFilter], ([nextTab, nextSearch], previous) => {
  const changedTab = previous?.[0] !== nextTab
  if (changedTab) { closeTypeMenu(); sharing.closePoolContent(); sharing.closeBundleDetail() }
  load(0, !changedTab && previous?.[1] !== nextSearch)
}, { immediate: true })

watch(selectableIds, (ids) => { selected.value = selected.value.filter((id) => ids.includes(id)) })
watch(busy, (value) => { if (value) closeTypeMenu() })
watch(() => auth.workspaceKey, () => {
  closeTypeMenu()
  viewVersion++
  if (confirming.value) confirmation.cancel()
  confirming.value = false
  selected.value = []
  clearFilters()
  load()
})

onMounted(() => document.addEventListener('pointerdown', onOutsidePointerdown))
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onOutsidePointerdown)
  mounted = false
  viewVersion++
  if (confirming.value) confirmation.cancel()
  sharing.reset()
})
</script>

<template>
  <section class="page-content page-content--wide sharing-page">
    <div class="sharing-workspace">
      <header class="sharing-header">
        <div class="sharing-overview">
          <span class="sharing-overview__icon"><UsersRound :size="20" aria-hidden="true" /></span>
          <div><h1>知识广场</h1><p>发现好内容，分享新收获</p></div>
        </div>
        <RouterLink class="button button-primary" :to="{ path: '/knowledge', query: route.query.workspace ? { workspace: route.query.workspace } : {} }"><Share2 :size="17" aria-hidden="true" />分享知识</RouterLink>
      </header>

      <div class="sharing-toolbar">
        <nav class="sharing-tabs" aria-label="分享范围">
          <button type="button" :class="{ active: tab === 'all' }" :aria-pressed="tab === 'all'" :disabled="busy" @click="switchTab('all')"><BookOpen :size="17" aria-hidden="true" />全部内容</button>
          <button type="button" :class="{ active: tab === 'mine' }" :aria-pressed="tab === 'mine'" :disabled="busy" @click="switchTab('mine')"><Share2 :size="17" aria-hidden="true" />我的分享</button>
          <button type="button" :class="{ active: tab === 'links' }" :aria-pressed="tab === 'links'" :disabled="busy" @click="switchTab('links')"><Link2 :size="17" aria-hidden="true" />外链管理</button>
        </nav>

        <div class="sharing-filters">
          <div v-if="!isLinks" ref="typeMenu" class="sharing-filter styled-select" @focusout="onTypeMenuFocusout" @keydown="onTypeMenuKeydown">
            <button ref="typeTrigger" class="styled-select__trigger" :class="{ open: showTypeMenu }" type="button" aria-label="内容类型" aria-haspopup="listbox" aria-controls="sharing-type-menu" :aria-expanded="showTypeMenu" :disabled="busy" @click="showTypeMenu ? closeTypeMenu() : openTypeMenu()">
              <span class="styled-select__leading filter"><Filter :size="15" aria-hidden="true" /></span><strong>{{ selectedTypeOption.label }}</strong><ChevronDown :size="15" :class="{ rotated: showTypeMenu }" aria-hidden="true" />
            </button>
            <Transition name="select-menu">
              <div v-if="showTypeMenu" id="sharing-type-menu" class="styled-select__menu" role="listbox" aria-label="内容类型">
                <button v-for="option in typeOptions" :key="option.value" type="button" role="option" :aria-selected="typeFilter === option.value" tabindex="-1" :class="{ selected: typeFilter === option.value }" @click="selectTypeOption(option.value)">
                  <span class="styled-select__leading" :class="option.tone"><component :is="option.value ? typeIcons[option.value] : Filter" :size="15" aria-hidden="true" /></span><span class="styled-select__option-copy"><strong>{{ option.label }}</strong><small>{{ option.description }}</small></span><Check v-if="typeFilter === option.value" :size="15" aria-hidden="true" />
                </button>
              </div>
            </Transition>
          </div>
          <button class="icon-btn refresh-button" type="button" aria-label="刷新列表" title="刷新列表" :disabled="request.loading || busy" @click="load(currentPage.page)"><RefreshCw :size="17" aria-hidden="true" /></button>
        </div>
      </div>

      <div class="list-context-bar" :class="{ 'has-selection': selected.length }">
        <div class="list-context-copy">
          <label v-if="selectableIds.length && !request.error" class="select-all"><input type="checkbox" :checked="allSelected" :indeterminate="selected.length > 0 && !allSelected" :disabled="request.loading || busy" @change="toggleAll" /><span>{{ isLinks ? '选择本页可撤销链接' : '选择本页我的分享' }}</span></label>
          <span class="total-count" aria-live="polite" aria-atomic="true">{{ request.loading ? '正在查找…' : `共 ${currentPage.total} ${isLinks ? '个链接' : '项内容'}` }}</span>
        </div>
        <div v-if="selected.length" class="selection-actions"><span aria-live="polite">已选 {{ selected.length }} 项</span><button class="button button-ghost" type="button" :disabled="busy" @click="selected = []">取消选择</button><button class="button subtle-danger" type="button" :disabled="busy || request.loading" @click="bulkAction"><Unlink :size="15" aria-hidden="true" />{{ isLinks ? '批量撤销' : '批量撤下' }}</button></div>
        <p v-else class="scope-hint">{{ isLinks ? '完整链接仅在创建时显示一次' : tab === 'mine' ? '撤下仅影响广场，原文和外链保留' : '成员共享 · 按分享时间排序' }}</p>
      </div>

      <div class="sharing-results" :aria-busy="request.loading">
        <div v-if="request.loading" class="loading-state" role="status"><LoaderCircle class="loading-icon" :size="24" aria-hidden="true" /><span>正在加载{{ isLinks ? '外部链接' : '共享内容' }}…</span></div>
        <EmptyState v-else-if="request.error" title="暂时无法加载" :description="request.error"><button class="button button-primary" type="button" @click="load(currentPage.page)">重试</button></EmptyState>
        <EmptyState v-else-if="!currentPage.items.length" :title="hasFilters ? '没有匹配的结果' : isLinks ? '还没有创建外部链接' : tab === 'mine' ? '你还没有发布共享内容' : '知识广场等待第一份知识'" :description="hasFilters ? '试试其他关键词，或清除筛选后查看全部。' : isLinks ? '在知识库选择一项或多项内容，选择外部链接分享，即可在这里管理。' : '在知识库选择文章、代码片段或流程图，发布到知识广场。'">
          <button v-if="hasFilters" class="button button-secondary" type="button" @click="clearFilters">清除筛选</button>
        </EmptyState>
        <ul v-else-if="!isLinks" class="sharing-list" aria-label="共享内容列表">
          <li v-for="entry in sharing.pool.items" :key="entry.id" class="sharing-row" :class="{ selected: selected.includes(entry.id) }">
            <div class="row-leading"><label v-if="entry.mine" class="row-checkbox"><input type="checkbox" :checked="selected.includes(entry.id)" :disabled="busy" :aria-label="`选择 ${entry.title}`" @change="toggle(entry.id)" /></label><span v-else class="resource-icon"><component :is="typeIcons[entry.resourceType]" :size="20" aria-hidden="true" /></span></div>
            <div class="row-copy"><div class="row-heading"><button class="resource-title" type="button" @click="sharing.openPoolContent(entry.id)">{{ entry.title || '未命名内容' }}</button><span class="type-label">{{ typeLabels[entry.resourceType] }}</span><span v-if="entry.mine" class="mine-label">我的分享</span></div><p class="excerpt">{{ entry.excerpt || '打开查看完整内容' }}</p><div class="row-meta"><span>{{ entry.authorName }}</span><span>分享于 {{ date(entry.sharedAt) }}</span><span>更新于 {{ date(entry.updatedAt) }}</span></div></div>
            <div class="row-actions"><button class="button button-ghost small" type="button" @click="sharing.openPoolContent(entry.id)">阅读<ArrowRight :size="15" aria-hidden="true" /></button><button v-if="entry.mine" class="button subtle-danger small" type="button" :disabled="busy" @click="withdraw([entry])">撤下</button></div>
          </li>
        </ul>
        <ul v-else class="sharing-list" aria-label="外部链接列表">
          <li v-for="link in sharing.links.items" :key="link.id" class="sharing-row" :class="{ selected: selected.includes(link.id) }">
            <div class="row-leading"><label v-if="!link.revokedAt" class="row-checkbox"><input type="checkbox" :checked="selected.includes(link.id)" :disabled="busy" :aria-label="`选择链接 ${link.title}`" @change="toggle(link.id)" /></label><span v-else class="resource-icon"><Unlink :size="20" aria-hidden="true" /></span></div>
            <div class="row-copy"><div class="row-heading"><button class="resource-title" type="button" @click="sharing.openBundleDetail(link.id)">{{ link.title || '未命名合集' }}</button><span class="link-status" :class="{ available: link.active }"><Check v-if="link.active" :size="12" aria-hidden="true" />{{ linkStatus(link) }}</span><span class="type-label">{{ link.itemCount }} 项内容</span></div><div class="row-meta"><span>创建于 {{ date(link.createdAt) }}</span><span>到期 {{ date(link.expiresAt) }}</span><span v-if="link.revokedAt">撤销于 {{ date(link.revokedAt) }}</span></div></div>
            <div class="row-actions"><button class="button button-ghost small" type="button" @click="sharing.openBundleDetail(link.id)">管理内容<ArrowRight :size="15" aria-hidden="true" /></button><button v-if="!link.revokedAt" class="button subtle-danger small" type="button" :disabled="busy" @click="revoke([link])">撤销链接</button></div>
          </li>
        </ul>
      </div>

      <nav v-if="currentPage.total > 0 && !request.error" class="sharing-pagination" aria-label="共享列表分页"><span>第 {{ currentPage.page + 1 }} / {{ pageCount }} 页</span><div><button class="button button-secondary" type="button" :disabled="currentPage.page === 0 || request.loading || busy" @click="load(currentPage.page - 1)"><ArrowLeft :size="16" aria-hidden="true" />上一页</button><button class="button button-secondary" type="button" :disabled="currentPage.page + 1 >= pageCount || request.loading || busy" @click="load(currentPage.page + 1)">下一页<ArrowRight :size="16" aria-hidden="true" /></button></div></nav>
    </div>

    <AppModal v-if="sharing.contentPoolId" :title="sharing.content?.title || '阅读共享内容'" description="只读内容 · 展示作者最新已保存的版本" wide @close="sharing.closePoolContent">
      <div v-if="sharing.requests.content.loading" class="loading-state" role="status"><LoaderCircle class="loading-icon" :size="22" aria-hidden="true" />正在加载正文…</div>
      <EmptyState v-else-if="sharing.requests.content.error" title="内容暂不可访问" :description="sharing.requests.content.error"><button class="button button-secondary" type="button" @click="sharing.openPoolContent(sharing.contentPoolId)">重试</button></EmptyState>
      <SharedContentReader v-else-if="sharing.content" :resource="sharing.content" :image-base-path="`/sharing/pool/${encodeURIComponent(sharing.contentPoolId)}/images`" />
    </AppModal>

    <AppModal v-if="sharing.detailLinkId" :title="sharing.bundleDetail?.link.title || '管理外部链接'" description="移除内容仅影响这个链接，原文和其他分享保持不变。" wide @close="sharing.closeBundleDetail">
      <div v-if="sharing.requests.detail.loading" class="loading-state" role="status"><LoaderCircle class="loading-icon" :size="22" aria-hidden="true" />正在加载链接内容…</div>
      <EmptyState v-else-if="sharing.requests.detail.error" title="暂时无法加载链接" :description="sharing.requests.detail.error"><button class="button button-secondary" type="button" @click="sharing.openBundleDetail(sharing.detailLinkId)">重试</button></EmptyState>
      <template v-else-if="sharing.bundleDetail">
        <div class="bundle-summary"><div><span class="link-status" :class="{ available: sharing.bundleDetail.link.active }">{{ linkStatus(sharing.bundleDetail.link) }}</span><p>{{ sharing.bundleDetail.link.itemCount }} 项内容 · {{ date(sharing.bundleDetail.link.expiresAt) }} 到期</p></div><button v-if="!sharing.bundleDetail.link.revokedAt" class="button danger" type="button" :disabled="busy" @click="revoke([sharing.bundleDetail.link])"><Unlink :size="16" aria-hidden="true" />撤销整个链接</button></div>
        <ul class="bundle-items" aria-label="链接包含的内容"><li v-for="item in sharing.bundleDetail.items" :key="item.id"><span class="resource-icon"><component :is="typeIcons[item.resourceType]" :size="19" aria-hidden="true" /></span><div><strong>{{ item.title || '未命名内容' }}</strong><p>{{ typeLabels[item.resourceType] }} · {{ item.removedAt ? '已从链接移除' : item.available ? '可访问' : '原文不可用' }}</p></div><button v-if="!item.removedAt && !sharing.bundleDetail.link.revokedAt" class="button subtle-danger small" type="button" :disabled="busy" :aria-label="`从此链接移除 ${item.title}`" @click="removeItem(item)"><X :size="15" aria-hidden="true" />移除</button></li></ul>
        <p class="bundle-note">内容随作者后续保存同步更新。撤销或移除后，已被接收者保存的内容无法收回。</p>
      </template>
    </AppModal>
  </section>
</template>

<style scoped>
.sharing-page { min-width: 0; width: 100%; max-width: none; padding-top: 12px; padding-bottom: 16px; }
.sharing-workspace { display: flex; flex-direction: column; min-width: 0; min-height: max(620px, calc(100svh - 100px)); overflow: hidden; border: 1px solid var(--border); border-radius: 16px; background: var(--panel); box-shadow: var(--shadow-sm); }
.sharing-header { display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 14px 18px; border-bottom: 1px solid var(--border); background: linear-gradient(112deg, color-mix(in srgb, var(--accent-bg) 30%, var(--panel)) 0%, var(--panel) 46%); }
.sharing-overview { display: flex; align-items: center; gap: 11px; min-width: 0; }
.sharing-overview__icon { width: 40px; height: 40px; display: grid; place-items: center; flex-shrink: 0; color: var(--accent); border: 1px solid var(--accent-border); border-radius: 12px; background: var(--accent-bg); }
.sharing-overview h1 { margin: 0; color: var(--text); font-size: var(--font-md); font-weight: 760; }
.sharing-overview p { margin: 4px 0 0; color: var(--muted); font-size: var(--font-2xs); }
.sharing-header > .button { flex-shrink: 0; }
.sharing-toolbar { position: relative; z-index: 5; display: flex; align-items: center; flex-wrap: wrap; gap: 8px 20px; padding: 8px 18px; border-bottom: 1px solid var(--border); }
.sharing-tabs { display: flex; gap: 4px; max-width: 100%; }
.sharing-tabs button { min-height: 40px; display: flex; align-items: center; justify-content: center; gap: 7px; padding: 8px 11px; border: 1px solid transparent; border-radius: 8px; background: transparent; color: var(--muted); font-size: var(--font-xs); font-weight: 650; white-space: nowrap; cursor: pointer; transition: background var(--motion-fast), color var(--motion-fast); }
.sharing-tabs button.active { background: var(--accent-bg); color: var(--accent); }
.sharing-tabs button:hover:not(:disabled) { color: var(--text); background: var(--surface-sunken); }
.sharing-filters { display: flex; align-items: center; flex: 0 0 auto; gap: 8px; min-width: 0; margin-left: auto; }
.sharing-filter { flex: 0 0 154px; min-width: 0; }
.styled-select { position: relative; }
.styled-select__trigger { width: 100%; height: 40px; display: flex; align-items: center; gap: 7px; padding: 0 9px 0 6px; color: var(--subtle); border: 1px solid var(--border); border-radius: 9px; outline: 0; background: var(--panel); cursor: pointer; box-shadow: 0 1px 2px rgba(0,0,0,.03); transition: color .16s, border-color .16s, background .16s, box-shadow .16s; }
.styled-select__trigger:hover:not(:disabled), .styled-select__trigger.open { color: var(--text); border-color: var(--accent-border); background: color-mix(in srgb, var(--accent-bg) 28%, var(--panel)); }
.styled-select__trigger:focus-visible { border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }
.styled-select__trigger > strong { overflow: hidden; min-width: 0; color: var(--text); font-size: var(--font-2xs); font-weight: 680; text-overflow: ellipsis; white-space: nowrap; }
.styled-select__trigger > svg:last-child { flex: 0 0 auto; margin-left: auto; transition: transform .18s var(--ease-standard); }
.styled-select__trigger > svg.rotated { transform: rotate(180deg); }
.styled-select__leading { width: 28px; height: 28px; display: grid; flex: 0 0 28px; place-items: center; color: var(--muted); border-radius: 8px; background: var(--surface-sunken); }
.styled-select__leading.filter, .styled-select__leading.all, .styled-select__leading.documents { color: var(--accent); background: var(--accent-bg); }
.styled-select__leading.snippets { color: var(--violet); background: color-mix(in srgb, var(--violet) 11%, transparent); }
.styled-select__leading.flowcharts { color: var(--warning); background: color-mix(in srgb, var(--warning) 12%, transparent); }
.styled-select__menu { position: absolute; z-index: 30; top: calc(100% + 8px); right: 0; width: 220px; overflow: hidden; padding: 6px; border: 1px solid var(--border-strong); border-radius: 12px; background: color-mix(in srgb, var(--panel) 96%, transparent); box-shadow: 0 18px 46px rgba(0,0,0,.2), 0 4px 12px rgba(0,0,0,.08); backdrop-filter: blur(16px); }
.styled-select__menu > button { width: 100%; min-height: 46px; display: grid; grid-template-columns: 28px minmax(0,1fr) 18px; align-items: center; gap: 9px; padding: 6px 8px; color: var(--muted); border: 1px solid transparent; border-radius: 9px; background: transparent; text-align: left; cursor: pointer; transition: color .14s, border-color .14s, background .14s; }
.styled-select__menu > button:hover { color: var(--text); background: var(--surface-sunken); }
.styled-select__menu > button.selected { color: var(--accent); border-color: color-mix(in srgb, var(--accent-border) 64%, transparent); background: var(--accent-bg); }
.styled-select__menu > button:focus-visible { outline-offset: -2px; }
.styled-select__menu > button > svg:last-child { justify-self: end; color: var(--accent); }
.styled-select__option-copy { min-width: 0; display: grid; gap: 2px; }
.styled-select__option-copy strong, .styled-select__option-copy small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.styled-select__option-copy strong { color: var(--text); font-size: var(--font-xs); font-weight: 650; }
.styled-select__option-copy small { color: var(--muted); font-size: 10px; }
.select-menu-enter-active, .select-menu-leave-active { transition: opacity .14s, transform .14s var(--ease-standard); transform-origin: top right; }
.select-menu-enter-from, .select-menu-leave-to { opacity: 0; transform: translateY(-5px) scale(.98); }
.refresh-button { flex-shrink: 0; width: 40px; height: 40px; }
.sharing-page .button { min-height: 38px; }
.list-context-bar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 0 16px; min-height: 44px; padding: 0 18px; border-bottom: 1px solid var(--border); background: color-mix(in srgb, var(--surface-sunken) 42%, var(--panel)); font-size: var(--font-2xs); }
.list-context-bar.has-selection { background: var(--accent-bg); }
.list-context-copy { display: flex; align-items: center; flex-wrap: wrap; gap: 0 16px; min-height: 44px; }
.total-count { color: var(--muted); }
.scope-hint { margin: 8px 0; color: var(--muted); line-height: 1.6; }
.select-all { display: flex; align-items: center; min-height: 44px; gap: 10px; cursor: pointer; color: var(--subtle); }
input[type='checkbox'] { width: 16px; height: 16px; flex-shrink: 0; margin: 0; accent-color: var(--accent); cursor: pointer; }
.selection-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 4px; color: var(--accent); }
.selection-actions > span { margin-right: 8px; }
.selection-actions .button { padding: 0 8px; font-size: var(--font-xs); }
.sharing-results { flex: 1; min-height: 230px; }
.sharing-list { margin: 0; padding: 0; list-style: none; }
.sharing-row { display: grid; grid-template-columns: 36px minmax(0, 1fr) auto; align-items: center; gap: 12px; padding: 14px 18px; border-bottom: 1px solid var(--border); transition: background var(--motion-fast); }
.sharing-row:last-child { border-bottom: 0; }
.sharing-row:hover { background: color-mix(in srgb, var(--surface-sunken) 55%, var(--panel)); }
.sharing-row.selected { background: color-mix(in srgb, var(--accent-bg) 60%, var(--panel)); }
.row-leading { align-self: start; padding-top: 3px; }
.row-checkbox { width: 36px; height: 40px; display: grid; place-items: center; border-radius: 8px; cursor: pointer; }
.resource-icon { display: grid; place-items: center; width: 36px; height: 36px; flex-shrink: 0; border-radius: 9px; background: var(--accent-bg); color: var(--accent); }
.row-copy { min-width: 0; }
.row-heading { display: flex; flex-wrap: wrap; align-items: center; gap: 4px 9px; }
.type-label { font-size: var(--font-2xs); color: var(--muted); white-space: nowrap; }
.mine-label, .link-status { display: inline-flex; align-items: center; gap: 4px; padding: 2px 7px; border-radius: 5px; font-size: var(--font-2xs); line-height: 1.5; color: var(--muted); background: var(--surface-raised); white-space: nowrap; }
.mine-label { color: var(--accent); background: var(--accent-bg); }
.link-status.available { color: var(--success); background: color-mix(in srgb, var(--success) 12%, var(--panel)); }
.resource-title { display: block; min-width: 0; min-height: 30px; max-width: 100%; padding: 3px 0; border: 0; background: none; color: var(--text); font-size: var(--font-sm); font-weight: 700; line-height: 1.5; text-align: left; overflow-wrap: anywhere; cursor: pointer; }
.resource-title:hover { color: var(--accent); }
.excerpt { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; margin: 3px 0 7px; color: var(--subtle); font-size: var(--font-xs); line-height: 1.65; white-space: normal; overflow-wrap: anywhere; }
.row-meta { display: flex; flex-wrap: wrap; gap: 4px 14px; margin-top: 5px; color: var(--muted); font-size: var(--font-2xs); overflow-wrap: anywhere; }
.row-actions { display: flex; align-items: center; justify-content: flex-end; flex-wrap: wrap; gap: 4px; }
.row-actions .button { padding-inline: 10px; font-size: var(--font-xs); }
.subtle-danger { color: var(--danger); background: transparent; border-color: transparent; }
.subtle-danger:hover:not(:disabled) { border-color: color-mix(in srgb, var(--danger) 35%, var(--border)); background: color-mix(in srgb, var(--danger) 7%, transparent); }
.danger { color: var(--danger); border-color: color-mix(in srgb, var(--danger) 35%, var(--border)); background: color-mix(in srgb, var(--danger) 7%, var(--panel)); }
.loading-state { display: flex; align-items: center; justify-content: center; gap: 12px; min-height: 230px; color: var(--muted); font-size: var(--font-sm); }
.loading-icon { animation: sharing-spin 1s linear infinite; }
.sharing-pagination { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 18px; border-top: 1px solid var(--border); color: var(--muted); font-size: var(--font-xs); }
.sharing-pagination > div { display: flex; gap: 8px; }
.bundle-summary { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding-bottom: 20px; }
.bundle-summary p { margin: 9px 0 0; color: var(--muted); font-size: var(--font-sm); }
.bundle-items { display: grid; gap: 10px; padding: 0; margin: 0; list-style: none; }
.bundle-items li { display: grid; grid-template-columns: 36px minmax(0, 1fr) auto; align-items: center; gap: 12px; padding: 14px; border: 1px solid var(--border); border-radius: var(--radius-md); }
.bundle-items strong { overflow-wrap: anywhere; font-size: var(--font-sm); }
.bundle-items p { color: var(--muted); font-size: var(--font-xs); margin: 4px 0 0; }
.bundle-note { color: var(--muted); font-size: var(--font-xs); margin: 18px 0 0; line-height: 1.7; }
@keyframes sharing-spin { to { transform: rotate(360deg); } }
@media (max-width: 1024px) {
  .sharing-row { grid-template-columns: 36px minmax(0, 1fr); gap: 4px 12px; }
  .row-actions { grid-column: 2; justify-content: flex-start; }
}
@media (max-width: 720px) {
  .sharing-page { padding-top: 10px; padding-bottom: 10px; }
  .sharing-workspace { min-height: calc(100svh - 84px); border-radius: 14px; }
  .sharing-header { padding: 12px; gap: 10px; }
  .sharing-overview { gap: 8px; }
  .sharing-overview p { display: none; }
  .sharing-header > .button { padding-inline: 12px; }
  .sharing-toolbar { gap: 8px; padding: 8px 12px 12px; }
  .sharing-tabs { width: 100%; gap: 3px; }
  .sharing-tabs button { flex: 1; min-height: 44px; gap: 5px; padding: 8px 5px; font-size: 12px; }
  .sharing-tabs svg { width: 15px; }
  .sharing-filters { flex: 1; }
  .sharing-filter { flex: 1; }
  .styled-select__trigger, .refresh-button { min-height: 44px; }
  .styled-select__menu { right: auto; left: 0; width: min(260px, calc(100vw - 58px)); }
  .list-context-bar { padding-inline: 12px; }
  .list-context-copy { gap: 0 10px; }
  .scope-hint { width: 100%; margin: 0 0 10px; }
  .selection-actions { width: 100%; padding-bottom: 4px; }
  .selection-actions > span { margin-right: auto; }
  .sharing-row { padding: 12px; gap: 4px 8px; grid-template-columns: 28px minmax(0, 1fr); }
  .row-leading { padding-top: 3px; }
  .row-checkbox, .row-leading .resource-icon { width: 28px; height: 44px; }
  .resource-title, .sharing-page .button { min-height: 44px; }
  .row-heading { gap: 2px 8px; }
  .row-meta { gap: 4px 10px; }
  .sharing-pagination { align-items: flex-start; flex-direction: column; padding: 12px; gap: 8px; }
  .sharing-pagination > div { width: 100%; }
  .sharing-pagination .button { flex: 1; }
  .bundle-summary { align-items: flex-start; flex-direction: column; }
  .bundle-summary .button { width: 100%; }
  .bundle-items li { grid-template-columns: 32px minmax(0, 1fr); padding: 12px; gap: 10px; }
  .bundle-items .resource-icon { width: 32px; height: 38px; }
  .bundle-items .button { grid-column: 2; justify-self: start; min-height: 44px; }
}
@media (max-width: 360px) { .sharing-overview__icon { display: none; } }
@media (prefers-reduced-motion: reduce) { .loading-icon { animation: none; }.select-menu-enter-active, .select-menu-leave-active, .styled-select__trigger > svg:last-child { transition: none; } }
</style>
