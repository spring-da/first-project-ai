<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { AlertCircle, ArrowRight, BookOpen, Check, Clock3, Code2, FileText, FolderOpen, Link2, LoaderCircle, Moon, RefreshCw, Search, Sun, X } from 'lucide-vue-next'
import { useRoute } from 'vue-router'
import AppLogo from '../components/AppLogo.vue'
import EmptyState from '../components/EmptyState.vue'
import SharedContentReader from '../components/SharedContentReader.vue'
import { ApiError } from '../services/api'
import { getPublicBundleContent, getPublicShareBundle } from '../services/sharing'
import { useThemeStore } from '../stores/theme'
import type { PublicShareBundle, SharedContent, ShareResourceType } from '../types/sharing'

const route = useRoute()
const theme = useThemeStore()
const bundle = ref<PublicShareBundle | null>(null)
const resource = ref<SharedContent | null>(null)
const selectedId = ref<string | null>(null)
const search = ref('')
const searchInput = ref<HTMLInputElement | null>(null)
const loading = ref(false)
const pageError = ref('')
const unavailable = ref(false)
const contentError = ref('')
const contentUnavailable = ref(false)
let request: AbortController | null = null
let sequence = 0
let expiryTimer: ReturnType<typeof setTimeout> | undefined
let disposed = false
const originalTitle = document.title
const token = computed(() => String(route.params.token ?? ''))
const validToken = computed(() => /^[A-Za-z0-9_-]{43}$/.test(token.value))
const typeLabels: Record<ShareResourceType, string> = { MARKDOWN: '文章', SNIPPET: '代码片段', FLOWCHART: '流程图' }
const filteredItems = computed(() => {
  const query = search.value.trim().toLocaleLowerCase()
  return bundle.value?.items.filter((item) => !query || item.title.toLocaleLowerCase().includes(query)) ?? []
})
const selectedTitle = computed(() => bundle.value?.items.find((item) => item.id === selectedId.value)?.title ?? '这项内容')
const imageBasePath = computed(() => `/public/share-bundles/${encodeURIComponent(token.value)}/items/${encodeURIComponent(selectedId.value ?? '')}/images`)
const expiresAtLabel = computed(() => {
  if (!bundle.value) return ''
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(bundle.value.expiresAt))
})

function clearExpiryTimer() {
  if (expiryTimer !== undefined) clearTimeout(expiryTimer)
  expiryTimer = undefined
}

function showUnavailable(message = '这个分享链接不存在、已过期或已被撤销。') {
  sequence++
  request?.abort()
  request = null
  clearExpiryTimer()
  // Unmount the renderer so pending images are aborted and its Blob URLs are revoked.
  resource.value = null
  bundle.value = null
  loading.value = false
  contentError.value = ''
  pageError.value = message
  unavailable.value = true
  document.title = '分享链接不可用 · DevNest'
}

function scheduleExpiry() {
  clearExpiryTimer()
  if (!bundle.value) return
  const remaining = new Date(bundle.value.expiresAt).getTime() - Date.now()
  if (!Number.isFinite(remaining) || remaining <= 0) {
    showUnavailable('这个分享链接已过期，请联系分享者获取新的链接。')
    return
  }
  // Browser timers overflow after roughly 24 days; longer links are checked in stages.
  expiryTimer = setTimeout(scheduleExpiry, Math.min(remaining, 2_147_483_647))
}

function isUnavailableError(cause: unknown) {
  return cause instanceof ApiError && [400, 401, 403, 404, 410].includes(cause.status ?? 0)
}

function acceptBundle(next: PublicShareBundle) {
  const expiresAt = new Date(next.expiresAt).getTime()
  if (!Number.isFinite(expiresAt) || expiresAt <= Date.now()) {
    showUnavailable('这个分享链接已过期，请联系分享者获取新的链接。')
    return false
  }
  if (!next.items.length) {
    showUnavailable('这个分享链接已没有可查看的内容，请联系分享者获取新的链接。')
    return false
  }
  bundle.value = next
  document.title = `${next.title} · DevNest 分享`
  scheduleExpiry()
  return true
}

async function loadBundle(itemId: string | null = null) {
  if (disposed) return
  request?.abort()
  const controller = new AbortController()
  request = controller
  const currentSequence = ++sequence
  const currentToken = token.value
  const isCurrent = () => !disposed && !controller.signal.aborted && sequence === currentSequence && token.value === currentToken
  resource.value = null
  selectedId.value = itemId
  contentError.value = ''
  contentUnavailable.value = false
  pageError.value = ''
  unavailable.value = false
  loading.value = true
  let readingContent = false
  try {
    if (!validToken.value) {
      showUnavailable('这个分享链接无效，请检查链接是否完整。')
      return
    }
    // Refresh the directory before every read; content is never reused across selections.
    const next = await getPublicShareBundle(currentToken, controller.signal)
    if (!isCurrent() || !acceptBundle(next)) return
    if (!itemId) return
    if (!next.items.some((item) => item.id === itemId)) {
      contentUnavailable.value = true
      contentError.value = '这项内容已被移除或停止分享，请从目录中选择其他内容。'
      return
    }
    readingContent = true
    const nextResource = await getPublicBundleContent(currentToken, itemId, controller.signal)
    if (!isCurrent()) return
    if (new Date(next.expiresAt).getTime() <= Date.now()) {
      showUnavailable('这个分享链接已过期，请联系分享者获取新的链接。')
      return
    }
    resource.value = nextResource
  } catch (cause) {
    if (!isCurrent()) return
    if (isUnavailableError(cause)) {
      if (!readingContent) {
        showUnavailable()
        return
      }
      // An individual item may have been removed while the bundle remains available.
      // Recheck the directory to distinguish that case from a revoked whole link.
      try {
        const next = await getPublicShareBundle(currentToken, controller.signal)
        if (!isCurrent() || !acceptBundle(next)) return
        contentUnavailable.value = true
        contentError.value = '这项内容已被移除或停止分享，请从目录中选择其他内容。'
      } catch (recheckCause) {
        if (!isCurrent()) return
        if (isUnavailableError(recheckCause)) showUnavailable()
        else {
          bundle.value = null
          pageError.value = '暂时无法确认分享状态，请检查网络后重试。'
        }
      }
    } else if (readingContent) {
      contentError.value = '正文加载失败，请检查网络后重试。'
    } else {
      bundle.value = null
      pageError.value = '分享目录加载失败，请检查网络后重试。'
      document.title = '分享暂时无法打开 · DevNest'
    }
  } finally {
    if (isCurrent()) {
      loading.value = false
      request = null
    }
  }
}

function revalidate() {
  if (document.visibilityState !== 'visible' || disposed || !validToken.value) return
  // Returning to this page never reveals content retained before revalidation.
  // Replace in-flight reads too: they may have started before the page was hidden.
  void loadBundle(selectedId.value)
}

function clearSearch() {
  search.value = ''
  searchInput.value?.focus()
}

watch(token, () => {
  clearExpiryTimer()
  bundle.value = null
  search.value = ''
  void loadBundle()
}, { immediate: true })

onMounted(() => {
  window.addEventListener('focus', revalidate)
  document.addEventListener('visibilitychange', revalidate)
})
onBeforeUnmount(() => {
  disposed = true
  sequence++
  request?.abort()
  clearExpiryTimer()
  window.removeEventListener('focus', revalidate)
  document.removeEventListener('visibilitychange', revalidate)
  resource.value = null
  document.title = originalTitle
})
</script>

<template>
  <div class="public-bundle-page">
    <a class="bundle-skip-link" :href="bundle ? '#shared-content' : '#bundle-main'">跳至阅读区域</a>
    <header class="bundle-topbar">
      <AppLogo />
      <span class="bundle-public-badge"><Link2 :size="15" aria-hidden="true" />公开只读分享</span>
      <button class="bundle-icon-button" type="button" :aria-label="theme.isDark ? '切换到浅色主题' : '切换到深色主题'" @click="theme.toggleTheme">
        <Sun v-if="theme.isDark" :size="18" aria-hidden="true" /><Moon v-else :size="18" aria-hidden="true" />
      </button>
    </header>

    <main id="bundle-main" class="bundle-main" tabindex="-1">
      <section v-if="!bundle && loading" class="bundle-page-state" aria-live="polite" aria-busy="true">
        <LoaderCircle class="spin" :size="30" aria-hidden="true" />
        <h1>正在打开分享目录</h1>
        <p>正在验证链接，内容即将准备好。</p>
        <div class="bundle-skeleton" aria-hidden="true"><span></span><span></span><span></span></div>
      </section>
      <section v-else-if="pageError" class="bundle-page-state" role="alert">
        <AlertCircle :size="32" aria-hidden="true" />
        <h1>{{ unavailable ? '这个分享链接已不可用' : '暂时无法打开分享' }}</h1>
        <p>{{ pageError }}</p>
        <p v-if="unavailable" class="bundle-state-hint">请联系分享者获取仍然有效的链接。</p>
        <button v-if="validToken" class="bundle-action" type="button" @click="loadBundle()"><RefreshCw :size="16" aria-hidden="true" />重新检查链接</button>
      </section>
      <template v-else-if="bundle">
        <header class="bundle-heading">
          <p><FolderOpen :size="16" aria-hidden="true" />分享内容集</p>
          <h1>{{ bundle.title }}</h1>
          <div><span>共 {{ bundle.items.length }} 项内容</span><span><Clock3 :size="14" aria-hidden="true" /><time :datetime="bundle.expiresAt">有效至 {{ expiresAtLabel }}</time></span></div>
        </header>

        <div class="bundle-layout">
          <aside class="bundle-directory" aria-labelledby="bundle-directory-title">
            <div class="bundle-directory-heading"><h2 id="bundle-directory-title">内容目录</h2><span>{{ bundle.items.length }} 项</span></div>
            <label for="bundle-search" class="bundle-search-label">搜索目录标题</label>
            <div class="bundle-search">
              <Search :size="16" aria-hidden="true" />
              <input id="bundle-search" ref="searchInput" v-model="search" type="search" placeholder="输入标题关键词" autocomplete="off" />
              <button v-if="search" type="button" aria-label="清空目录搜索" @click="clearSearch"><X :size="15" aria-hidden="true" /></button>
            </div>
            <p class="bundle-search-result" role="status">{{ search.trim() ? `找到 ${filteredItems.length} 项内容` : '选择一项内容开始阅读' }}</p>
            <nav v-if="filteredItems.length" aria-label="分享内容目录">
              <ul class="bundle-items">
                <li v-for="item in filteredItems" :key="item.id">
                  <button type="button" class="bundle-item" :class="{ selected: selectedId === item.id }" :aria-pressed="selectedId === item.id" aria-controls="shared-content" @click="loadBundle(item.id)">
                    <span class="bundle-item-icon"><FileText v-if="item.resourceType === 'MARKDOWN'" :size="18" aria-hidden="true" /><Code2 v-else-if="item.resourceType === 'SNIPPET'" :size="18" aria-hidden="true" /><BookOpen v-else :size="18" aria-hidden="true" /></span>
                    <span class="bundle-item-copy"><strong>{{ item.title }}</strong><small>{{ typeLabels[item.resourceType] }}</small></span>
                    <Check v-if="selectedId === item.id" class="bundle-item-marker" :size="16" aria-hidden="true" /><ArrowRight v-else class="bundle-item-marker" :size="15" aria-hidden="true" />
                  </button>
                </li>
              </ul>
            </nav>
            <EmptyState v-else compact title="没有匹配的标题" description="换一个关键词，或清空搜索查看全部内容。">
              <button class="bundle-action" type="button" @click="clearSearch">清空搜索</button>
            </EmptyState>
          </aside>

          <section id="shared-content" class="bundle-reader" tabindex="-1" aria-label="分享内容阅读区域" :aria-busy="loading">
            <div v-if="loading" class="bundle-reader-state" role="status">
              <LoaderCircle class="spin" :size="26" aria-hidden="true" />
              <h2>正在打开内容</h2><p>{{ selectedTitle }}</p><span>正在验证分享状态并加载最新正文…</span>
              <div class="bundle-skeleton" aria-hidden="true"><span></span><span></span><span></span></div>
            </div>
            <div v-else-if="contentError" class="bundle-reader-state" role="alert">
              <AlertCircle :size="28" aria-hidden="true" /><h2>{{ contentUnavailable ? '这项内容已不可用' : '正文加载失败' }}</h2><p>{{ contentError }}</p>
              <button v-if="!contentUnavailable" class="bundle-action" type="button" @click="loadBundle(selectedId)"><RefreshCw :size="16" aria-hidden="true" />重新加载正文</button>
            </div>
            <SharedContentReader v-else-if="resource" :key="`${token}:${selectedId}`" :resource="resource" :image-base-path="imageBasePath" :authenticated-images="false" />
            <div v-else class="bundle-reader-state">
              <BookOpen :size="32" aria-hidden="true" /><h2>从目录中选择内容</h2><p>这里包含 {{ bundle.items.length }} 项分享，点击标题即可阅读。</p><span>文章、代码片段和流程图均以只读方式展示。</span>
            </div>
          </section>
        </div>
      </template>
    </main>
    <footer class="bundle-footer">内容随分享者的保存更新 · 链接过期或被撤销后将无法继续访问</footer>
  </div>
</template>

<style scoped>
.public-bundle-page { min-height: 100dvh; color: var(--text); background: var(--bg); }
.bundle-skip-link { position: fixed; z-index: 20; top: 8px; left: 16px; padding: 12px 18px; transform: translateY(-180%); color: var(--accent-contrast); border-radius: var(--radius-sm); background: var(--accent); }
.bundle-skip-link:focus { transform: translateY(0); }
.bundle-topbar { min-height: 76px; display: flex; align-items: center; gap: 16px; padding: 14px clamp(16px, 4vw, 56px); border-bottom: 1px solid var(--border); background: var(--panel); }
.bundle-public-badge { display: inline-flex; align-items: center; gap: 7px; margin-left: auto; padding: 7px 11px; color: var(--accent); border: 1px solid var(--accent-border); border-radius: 999px; background: var(--accent-bg); font-size: 12px; font-weight: 650; white-space: nowrap; }
.bundle-icon-button { width: 44px; height: 44px; display: grid; flex: 0 0 auto; place-items: center; padding: 0; color: var(--muted); border: 1px solid var(--border); border-radius: var(--radius-md); background: var(--panel); cursor: pointer; }
.bundle-icon-button:hover { color: var(--text); background: var(--surface-raised); }
.bundle-main { width: min(100% - 48px, 1320px); min-height: calc(100dvh - 148px); margin: 0 auto; padding: clamp(28px, 4vw, 48px) 0 40px; }
.bundle-heading { margin-bottom: 28px; }
.bundle-heading > p { display: flex; align-items: center; gap: 8px; margin: 0; color: var(--accent); font-size: var(--font-xs); font-weight: 650; }
.bundle-heading h1 { margin: 12px 0; font-size: clamp(26px, 3vw, 38px); line-height: 1.3; letter-spacing: -.025em; }
.bundle-heading > div { display: flex; flex-wrap: wrap; gap: 8px 20px; color: var(--muted); font-size: var(--font-xs); }
.bundle-heading > div > span { display: inline-flex; align-items: center; gap: 6px; }
.bundle-heading svg { flex: 0 0 auto; }
.bundle-layout { display: grid; grid-template-columns: minmax(248px, 300px) minmax(0, 1fr); align-items: start; gap: 24px; }
.bundle-directory { min-width: 0; padding: 20px 14px 14px; border: 1px solid var(--border); border-radius: var(--radius-lg); background: var(--panel); }
.bundle-directory-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 0 6px; }
.bundle-directory-heading h2 { margin: 0; font-size: 16px; }
.bundle-directory-heading > span { color: var(--muted); font-size: 12px; }
.bundle-search-label { display: block; margin: 20px 6px 7px; color: var(--subtle); font-size: 12px; }
.bundle-search { display: flex; align-items: center; gap: 8px; min-width: 0; min-height: 44px; margin: 0 4px; padding: 0 11px; color: var(--muted); border: 1px solid var(--border-strong); border-radius: var(--radius-sm); background: var(--surface-sunken); }
.bundle-search:focus-within { outline: 2px solid var(--accent); outline-offset: 2px; }
.bundle-search > svg { flex: 0 0 auto; }
.bundle-search input { width: 100%; min-width: 0; padding: 10px 0; color: var(--text); border: 0; outline: none; background: transparent; font-size: 14px; }
.bundle-search input::-webkit-search-cancel-button { display: none; }
.bundle-search input::placeholder { color: var(--muted); }
.bundle-search button { width: 30px; height: 32px; flex: 0 0 auto; display: grid; place-items: center; margin-right: -6px; padding: 0; color: var(--muted); border: 0; border-radius: 5px; background: transparent; cursor: pointer; }
.bundle-search button:hover { color: var(--text); background: var(--panel-strong); }
.bundle-search-result { margin: 12px 6px; color: var(--muted); font-size: 12px; }
.bundle-items { display: grid; gap: 5px; max-height: 560px; overflow-y: auto; margin: 0; padding: 4px; list-style: none; scrollbar-gutter: stable; }
.bundle-items > li { min-width: 0; }
.bundle-item { width: 100%; min-height: 72px; display: grid; grid-template-columns: 32px minmax(0, 1fr) 16px; align-items: start; gap: 10px; padding: 12px 10px; text-align: left; border: 1px solid transparent; border-radius: var(--radius-md); background: transparent; cursor: pointer; transition: background var(--motion-fast), border-color var(--motion-fast); }
.bundle-item:hover { background: var(--surface-raised); }
.bundle-item.selected { border-color: var(--accent-border); background: var(--accent-bg); }
.bundle-item-icon { width: 32px; height: 32px; display: grid; place-items: center; color: var(--muted); border: 1px solid var(--border); border-radius: 8px; background: var(--surface-sunken); }
.bundle-item.selected .bundle-item-icon { color: var(--accent); border-color: var(--accent-border); background: var(--panel); }
.bundle-item-copy { min-width: 0; }
.bundle-item-copy strong { display: block; font-size: var(--font-sm); font-weight: 600; line-height: 1.5; overflow-wrap: anywhere; }
.bundle-item-copy small { display: block; margin-top: 5px; color: var(--muted); font-size: 12px; }
.bundle-item-marker { margin-top: 8px; color: var(--muted); }
.bundle-item.selected .bundle-item-marker { color: var(--accent); }
.bundle-reader { min-width: 0; min-height: 480px; padding: clamp(24px, 3vw, 40px); border: 1px solid var(--border); border-radius: var(--radius-lg); background: var(--panel); box-shadow: var(--shadow-sm); scroll-margin-top: 16px; }
.bundle-reader-state, .bundle-page-state { display: flex; align-items: center; justify-content: center; flex-direction: column; gap: 12px; min-width: 0; min-height: 390px; color: var(--muted); text-align: center; }
.bundle-reader-state > svg, .bundle-page-state > svg { flex: 0 0 auto; color: var(--accent); }
.bundle-reader-state h2, .bundle-page-state h1 { margin: 4px 0 0; color: var(--text); font-size: 20px; line-height: 1.5; }
.bundle-reader-state p, .bundle-page-state p { max-width: 420px; margin: 0; font-size: var(--font-sm); line-height: 1.7; }
.bundle-reader-state > span, .bundle-state-hint { max-width: 420px; font-size: var(--font-xs); line-height: 1.7; }
.bundle-page-state { min-height: 480px; padding: 24px; border: 1px solid var(--border); border-radius: var(--radius-lg); background: var(--panel); }
.bundle-action { min-height: 44px; display: inline-flex; align-items: center; justify-content: center; gap: 8px; margin-top: 8px; padding: 9px 15px; color: var(--accent); border: 1px solid var(--accent-border); border-radius: var(--radius-sm); background: var(--accent-bg); font-size: var(--font-sm); font-weight: 600; cursor: pointer; }
.bundle-action:hover { color: var(--accent-strong); border-color: var(--accent); }
.bundle-skeleton { display: grid; gap: 12px; width: min(100%, 320px); margin-top: 16px; }
.bundle-skeleton span { height: 10px; border-radius: 5px; background: var(--surface-raised); }
.bundle-skeleton span:nth-child(2) { width: 84%; }.bundle-skeleton span:nth-child(3) { width: 60%; }
.bundle-footer { padding: 0 20px 28px; color: var(--muted); font-size: 12px; line-height: 1.7; text-align: center; }
@media (max-width: 800px) {
  .bundle-layout { grid-template-columns: minmax(0, 1fr); gap: 20px; }
  .bundle-directory { padding: 18px 14px 12px; }
  .bundle-items { max-height: 250px; }
  .bundle-reader { min-height: 360px; }
  .bundle-reader-state { min-height: 300px; }
}
@media (max-width: 600px) {
  .bundle-topbar { min-height: 68px; gap: 10px; padding: 12px 14px; }
  .bundle-topbar :deep(.app-logo__copy) { display: none; }
  .bundle-main { width: calc(100% - 24px); padding-top: 24px; }
  .bundle-heading { padding-inline: 4px; margin-bottom: 20px; }
  .bundle-reader { padding: 24px 18px; }
  .bundle-search input { font-size: 16px; }
  .bundle-page-state { min-height: 420px; padding: 24px 16px; }
}
@media (prefers-reduced-motion: reduce) {
  .bundle-item { transition: none; }
  .spin { animation: none; }
}
</style>
