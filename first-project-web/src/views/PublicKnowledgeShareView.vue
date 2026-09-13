<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { AlertCircle, Clock3, Code2, LoaderCircle, Moon, Sun } from 'lucide-vue-next'
import { useRoute } from 'vue-router'
import AppLogo from '../components/AppLogo.vue'
import { getPublicKnowledgeShare } from '../services/knowledgeShares'
import { useThemeStore } from '../stores/theme'
import type { PublicKnowledgeShare } from '../types'
import { highlightCode } from '../utils/codeHighlight'

const route = useRoute()
const theme = useThemeStore()
const resource = ref<PublicKnowledgeShare | null>(null)
const loading = ref(true)
const error = ref('')
let request: AbortController | null = null
const originalTitle = window.document.title
const token = computed(() => String(route.params.token ?? ''))
const isSnippet = computed(() => resource.value?.resourceType === 'SNIPPET')
const highlightedCode = computed(() => resource.value && isSnippet.value
  ? highlightCode(resource.value.content, resource.value.language)
  : '')

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}

async function loadShare() {
  request?.abort()
  request = new AbortController()
  resource.value = null
  error.value = ''
  loading.value = true
  try {
    if (!/^[A-Za-z0-9_-]{43}$/.test(token.value)) throw new Error('这个分享链接无效。')
    resource.value = await getPublicKnowledgeShare(token.value, request.signal)
    window.document.title = `${resource.value.title} · DevNest 分享`
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
  <div class="public-knowledge-page">
    <header class="public-share-bar">
      <AppLogo />
      <div class="public-share-badge"><Code2 v-if="isSnippet" :size="15" />公开只读分享</div>
      <button class="theme-button" type="button" :aria-label="theme.isDark ? '切换到浅色主题' : '切换到深色主题'" @click="theme.toggleTheme">
        <Sun v-if="theme.isDark" :size="18" /><Moon v-else :size="18" />
      </button>
    </header>

    <main class="public-share-main">
      <section v-if="loading" class="share-state" aria-live="polite">
        <LoaderCircle class="spin" :size="28" /><strong>正在打开分享内容</strong><span>正在验证链接有效期…</span>
      </section>
      <section v-else-if="error" class="share-state share-state--error" role="alert">
        <AlertCircle :size="32" /><strong>无法查看这项内容</strong><span>{{ error }}</span>
        <small>请联系分享者重新生成一个仍在有效期内的链接。</small>
      </section>
      <article v-else-if="resource" class="shared-resource" :class="{ snippet: isSnippet }">
        <header class="resource-headline">
          <p><Code2 v-if="isSnippet" :size="15" />来自 DevNest 的代码片段分享</p>
          <h1>{{ resource.title }}</h1>
          <div>
            <code v-if="isSnippet">{{ resource.language || 'Plain Text' }}</code>
            <span>更新于 {{ formatDate(resource.updatedAt) }}</span>
            <span><Clock3 :size="14" />链接有效至 {{ formatDate(resource.expiresAt) }}</span>
          </div>
        </header>
        <pre v-if="isSnippet" class="shared-code"><code v-html="highlightedCode"></code></pre>
      </article>
    </main>
    <footer>此页面为只读分享 · 链接过期或被撤销后将无法继续访问</footer>
  </div>
</template>

<style scoped>
.public-knowledge-page { min-height: 100svh; color: var(--text); background: radial-gradient(circle at 50% -20%, var(--accent-bg), transparent 42%), var(--page); }
.public-share-bar { position: sticky; z-index: 5; top: 0; min-height: 68px; display: flex; align-items: center; gap: 18px; padding: 10px clamp(18px, 4vw, 56px); border-bottom: 1px solid color-mix(in srgb, var(--border) 82%, transparent); background: color-mix(in srgb, var(--page) 88%, transparent); backdrop-filter: blur(16px); }
.public-share-badge { display: inline-flex; align-items: center; gap: 6px; margin-left: auto; padding: 7px 10px; color: var(--accent); border: 1px solid var(--accent-border); border-radius: 999px; background: var(--accent-bg); font-size: 12px; font-weight: 650; }
.theme-button { width: 36px; height: 36px; display: grid; flex: 0 0 auto; place-items: center; color: var(--muted); border: 1px solid var(--border); border-radius: 9px; background: var(--panel); cursor: pointer; }
.public-share-main { width: min(100% - 32px, 1080px); min-height: calc(100svh - 126px); margin: 0 auto; padding: clamp(38px, 6vw, 72px) 0; }
.share-state { min-height: 420px; display: flex; align-items: center; justify-content: center; flex-direction: column; gap: 10px; color: var(--muted); text-align: center; }
.share-state strong { color: var(--text); font-size: 18px; }.share-state span { font-size: 14px; }.share-state small { margin-top: 4px; }.share-state--error > svg { color: var(--danger); }
.shared-resource { padding: clamp(28px, 4vw, 54px); border: 1px solid var(--border); border-radius: 20px; background: var(--panel); box-shadow: var(--shadow-lg); }
.resource-headline { padding-bottom: 28px; border-bottom: 1px solid var(--border); }
.resource-headline > p { display: flex; align-items: center; gap: 7px; margin: 0; color: var(--accent); font-size: 12px; font-weight: 700; }
.resource-headline h1 { margin: 14px 0 17px; font-size: clamp(30px, 4vw, 46px); line-height: 1.16; letter-spacing: -.035em; overflow-wrap: anywhere; }
.resource-headline > div { display: flex; align-items: center; flex-wrap: wrap; gap: 8px 18px; color: var(--muted); font-size: 12px; }
.resource-headline code { padding: 5px 8px; color: var(--accent); border-radius: 6px; background: var(--accent-bg); font: 650 12px "Cascadia Code", Consolas, monospace; }
.resource-headline span { display: inline-flex; align-items: center; gap: 5px; }
.resource-tags { margin-top: 15px; }.resource-tags span { color: var(--accent); }
.shared-code { max-height: calc(100svh - 280px); overflow: auto; margin: 26px 0 0; padding: clamp(20px, 2.7vw, 32px); color: var(--code-text); border: 1px solid var(--border); border-radius: 13px; background: var(--code-bg); font: 14px/1.75 "Cascadia Code", Consolas, monospace; tab-size: 2; }
.shared-code code { font: inherit; white-space: pre; }
.public-knowledge-page > footer { padding: 0 18px 28px; color: var(--muted); text-align: center; font-size: 11px; }
</style>
