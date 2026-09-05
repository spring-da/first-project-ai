<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { AlertCircle, Clock3, FileText, LoaderCircle, Moon, Sun } from 'lucide-vue-next'
import { useRoute } from 'vue-router'
import AppLogo from '../components/AppLogo.vue'
import MarkdownContent from '../components/MarkdownContent.vue'
import { getPublicMarkdownShare } from '../services/markdownShares'
import { useThemeStore } from '../stores/theme'
import type { PublicMarkdownShare } from '../types'

const route = useRoute()
const theme = useThemeStore()
const document = ref<PublicMarkdownShare | null>(null)
const loading = ref(true)
const error = ref('')
let request: AbortController | null = null
const originalTitle = window.document.title

const token = computed(() => String(route.params.token ?? ''))
const imageBasePath = computed(() => `/public/markdown-shares/${encodeURIComponent(token.value)}/images`)
const readerContent = computed(() => {
  if (!document.value) return ''
  const lines = document.value.content.split(/\r?\n/)
  const firstContentLine = lines.findIndex((line) => line.trim())
  if (firstContentLine >= 0) {
    const heading = lines[firstContentLine]?.match(/^#\s+(.+)$/)?.[1]?.trim()
    if (heading === document.value.title.trim()) lines.splice(firstContentLine, 1)
  }
  return lines.join('\n')
})
const readingMinutes = computed(() => Math.max(1, Math.ceil((document.value?.content.trim().split(/\s+/).length ?? 0) / 300)))

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}

async function loadShare() {
  request?.abort()
  request = new AbortController()
  document.value = null
  error.value = ''
  loading.value = true
  try {
    if (!/^[A-Za-z0-9_-]{43}$/.test(token.value)) throw new Error('这个分享链接无效。')
    document.value = await getPublicMarkdownShare(token.value, request.signal)
    window.document.title = `${document.value.title} · DevNest 分享`
  } catch (cause) {
    if (request.signal.aborted) return
    error.value = cause instanceof Error && cause.message
      ? cause.message
      : '这个分享链接不存在、已过期或已被撤销。'
    window.document.title = '分享链接不可用 · DevNest'
  } finally {
    if (!request.signal.aborted) loading.value = false
  }
}

watch(token, loadShare, { immediate: true })
onBeforeUnmount(() => {
  request?.abort()
  window.document.title = originalTitle
})
</script>

<template>
  <div class="public-share-page">
    <header class="public-share-bar">
      <AppLogo />
      <div class="public-share-badge"><FileText :size="15" />公开只读分享</div>
      <button class="theme-button" type="button" :aria-label="theme.isDark ? '切换到浅色主题' : '切换到深色主题'" @click="theme.toggleTheme">
        <Sun v-if="theme.isDark" :size="18" /><Moon v-else :size="18" />
      </button>
    </header>

    <main class="public-share-main">
      <section v-if="loading" class="share-state" aria-live="polite">
        <LoaderCircle class="spin" :size="28" /><strong>正在打开分享文章</strong><span>正在验证链接有效期…</span>
      </section>

      <section v-else-if="error" class="share-state share-state--error" role="alert">
        <AlertCircle :size="32" /><strong>无法查看这篇文章</strong><span>{{ error }}</span>
        <small>请联系分享者重新生成一个仍在有效期内的链接。</small>
      </section>

      <article v-else-if="document" class="shared-document">
        <header class="shared-document__headline">
          <p><FileText :size="15" />来自 DevNest 的 Markdown 分享</p>
          <h1>{{ document.title }}</h1>
          <div>
            <code>{{ document.fileName }}</code>
            <span>约 {{ readingMinutes }} 分钟阅读</span>
            <span><Clock3 :size="14" />链接有效至 {{ formatDate(document.expiresAt) }}</span>
          </div>
        </header>
        <MarkdownContent
          v-if="readerContent.trim()"
          class="shared-document__body"
          :source="readerContent"
          :image-base-path="imageBasePath"
          :authenticated-images="false"
        />
        <p v-else class="shared-document__empty">这篇文章暂时没有正文。</p>
      </article>
    </main>

    <footer class="public-share-footer">此页面为只读分享 · 链接过期或被撤销后将无法继续访问</footer>
  </div>
</template>

<style scoped>
.public-share-page { min-height: 100svh; color: var(--text); background: radial-gradient(circle at 50% -20%, var(--accent-bg), transparent 42%), var(--page); }
.public-share-bar { position: sticky; z-index: 5; top: 0; min-height: 68px; display: flex; align-items: center; gap: 18px; padding: 10px clamp(18px, 4vw, 56px); border-bottom: 1px solid color-mix(in srgb, var(--border) 82%, transparent); background: color-mix(in srgb, var(--page) 88%, transparent); backdrop-filter: blur(16px); }
.public-share-badge { display: inline-flex; align-items: center; gap: 6px; margin-left: auto; padding: 7px 10px; color: var(--accent); border: 1px solid var(--accent-border); border-radius: 999px; background: var(--accent-bg); font-size: 12px; font-weight: 650; }
.theme-button { width: 36px; height: 36px; display: grid; flex: 0 0 auto; place-items: center; color: var(--muted); border: 1px solid var(--border); border-radius: 9px; background: var(--panel); cursor: pointer; }
.theme-button:hover { color: var(--text); background: var(--surface-raised); }
.public-share-main { width: min(100% - 32px, 960px); min-height: calc(100svh - 126px); margin: 0 auto; padding: clamp(38px, 7vw, 82px) 0 72px; }
.share-state { min-height: 420px; display: flex; align-items: center; justify-content: center; flex-direction: column; gap: 10px; color: var(--muted); text-align: center; }
.share-state strong { color: var(--text); font-size: 18px; }
.share-state span { font-size: 14px; }
.share-state small { max-width: 430px; margin-top: 4px; line-height: 1.6; }
.share-state--error > svg { color: var(--danger); }
.shared-document { padding: clamp(28px, 5vw, 68px); border: 1px solid var(--border); border-radius: 20px; background: var(--panel); box-shadow: var(--shadow-lg); }
.shared-document__headline { padding-bottom: 30px; border-bottom: 1px solid var(--border); }
.shared-document__headline > p { display: flex; align-items: center; gap: 7px; margin: 0; color: var(--accent); font-size: 12px; font-weight: 700; }
.shared-document__headline h1 { margin: 15px 0 17px; font-size: clamp(32px, 5vw, 52px); line-height: 1.15; letter-spacing: -.04em; overflow-wrap: anywhere; }
.shared-document__headline > div { display: flex; align-items: center; flex-wrap: wrap; gap: 8px 18px; color: var(--muted); font-size: 12px; }
.shared-document__headline code { padding: 5px 8px; color: var(--subtle); border-radius: 6px; background: var(--surface-sunken); font: inherit; }
.shared-document__headline span { display: inline-flex; align-items: center; gap: 5px; }
.shared-document__body { display: block; padding: 36px 0 0; font-size: 16px; line-height: 1.88; }
.shared-document__empty { min-height: 260px; display: grid; place-items: center; color: var(--muted); }
.shared-document__body :deep(h1), .shared-document__body :deep(h2), .shared-document__body :deep(h3), .shared-document__body :deep(h4) { margin: 1.55em 0 .6em; line-height: 1.28; letter-spacing: -.02em; }
.shared-document__body :deep(h1:first-child), .shared-document__body :deep(h2:first-child), .shared-document__body :deep(h3:first-child) { margin-top: 0; }
.shared-document__body :deep(h1) { padding-bottom: .35em; border-bottom: 1px solid var(--border); font-size: 2em; }
.shared-document__body :deep(h2) { padding-bottom: .3em; border-bottom: 1px solid var(--border); font-size: 1.55em; }
.shared-document__body :deep(h3) { font-size: 1.25em; }
.shared-document__body :deep(p) { margin: 0 0 1.1em; }
.shared-document__body :deep(a) { color: var(--accent); text-decoration: underline; text-underline-offset: 3px; }
.shared-document__body :deep(pre) { max-width: 100%; overflow: auto; margin: 1.3em 0; padding: 17px; border: 1px solid var(--border); border-radius: 10px; background: var(--code-bg); }
.shared-document__body :deep(code) { padding: .15em .35em; color: var(--code-text); border-radius: 4px; background: var(--code-bg); font: 13px/1.6 "Cascadia Code", Consolas, monospace; }
.shared-document__body :deep(pre code) { padding: 0; background: transparent; }
.shared-document__body :deep(blockquote) { margin: 1.2em 0; padding: .2em 1em; color: var(--muted); border-left: 3px solid var(--accent); background: var(--surface-sunken); }
.shared-document__body :deep(ul), .shared-document__body :deep(ol) { padding-left: 1.55em; }
.shared-document__body :deep(li) { margin: .35em 0; }
.shared-document__body :deep(.markdown-table-wrap) { max-width: 100%; overflow-x: auto; margin: 1.3em 0; }
.shared-document__body :deep(table) { width: 100%; border-collapse: collapse; font-size: 14px; }
.shared-document__body :deep(th), .shared-document__body :deep(td) { padding: 9px 11px; text-align: left; border: 1px solid var(--border); }
.shared-document__body :deep(th) { background: var(--surface-sunken); }
.public-share-footer { padding: 0 18px 28px; color: var(--muted); text-align: center; font-size: 11px; }
@media (max-width: 600px) {
  .public-share-bar { min-height: 60px; padding-inline: 14px; }
  .public-share-bar :deep(.app-logo__copy) { display: none; }
  .public-share-badge { margin-left: auto; }
  .public-share-main { width: min(100% - 20px, 960px); padding-top: 18px; }
  .shared-document { padding: 26px 19px 42px; border-radius: 15px; }
  .shared-document__headline { padding-bottom: 23px; }
  .shared-document__headline h1 { font-size: 31px; }
  .shared-document__body { padding-top: 27px; font-size: 15px; }
}
</style>
