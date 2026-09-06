<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { AlertCircle, Clock3, Copy, LoaderCircle, Share2 } from 'lucide-vue-next'
import AppModal from './AppModal.vue'
import {
  createKnowledgeShare,
  listKnowledgeShares,
  revokeKnowledgeShare,
  type ShareableKnowledgeKind,
} from '../services/knowledgeShares'
import { useConfirmationStore } from '../stores/confirmation'
import { useNotificationStore } from '../stores/notifications'
import type { KnowledgeShareLink, KnowledgeShareSecret } from '../types'

const props = defineProps<{
  kind: ShareableKnowledgeKind
  resourceId: string
  resourceTitle: string
}>()
const emit = defineEmits<{ close: [] }>()
const confirmation = useConfirmationStore()
const notifications = useNotificationStore()
const links = ref<KnowledgeShareLink[]>([])
const secret = ref<KnowledgeShareSecret | null>(null)
const days = ref(7)
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const label = computed(() => props.kind === 'snippets' ? '代码片段' : '开发日志')
const shareUrl = computed(() => secret.value
  ? `${window.location.origin}/share/knowledge/${secret.value.token}`
  : '')

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    links.value = await listKnowledgeShares(props.kind, props.resourceId)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '暂时无法读取分享链接。'
  } finally {
    loading.value = false
  }
}

async function create() {
  const lifetime = Number(days.value)
  if (!Number.isInteger(lifetime) || lifetime < 1 || lifetime > 365) {
    error.value = '有效期请输入 1–365 天的整数。'
    return
  }
  busy.value = true
  error.value = ''
  try {
    secret.value = await createKnowledgeShare(
      props.kind,
      props.resourceId,
      new Date(Date.now() + lifetime * 24 * 60 * 60 * 1000).toISOString(),
    )
    links.value = [{ ...secret.value, revokedAt: null, active: true }, ...links.value]
    notifications.notify('分享链接已生成，请及时复制。', { type: 'success' })
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '分享链接生成失败，请稍后重试。'
  } finally {
    busy.value = false
  }
}

async function copyUrl() {
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

async function revoke(link: KnowledgeShareLink) {
  if (!link.active || busy.value) return
  if (!await confirmation.ask({
    title: '撤销这个分享链接？',
    message: `收到此链接的人将立即无法继续查看该${label.value}。`,
    detail: '撤销后无法恢复；需要再次分享时可以生成新链接。',
    confirmText: '撤销链接', cancelText: '保留链接', tone: 'warning', icon: 'leave',
  })) return
  busy.value = true
  error.value = ''
  try {
    await revokeKnowledgeShare(props.kind, props.resourceId, link.id)
    const revokedAt = new Date().toISOString()
    links.value = links.value.map(item => item.id === link.id
      ? { ...item, active: false, revokedAt }
      : item)
    if (secret.value?.id === link.id) secret.value = null
    notifications.notify('分享链接已撤销。', { type: 'success' })
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '撤销失败，请稍后重试。'
  } finally {
    busy.value = false
  }
}

function selectUrl(event: FocusEvent) {
  if (event.target instanceof HTMLTextAreaElement) event.target.select()
}

onMounted(load)
</script>

<template>
  <AppModal
    :title="`分享${label}`"
    :description="`为“${resourceTitle}”生成无需登录即可打开的只读链接。`"
    wide
    @close="!busy && emit('close')"
  >
    <div class="knowledge-share-dialog">
      <form class="share-create" @submit.prevent="create">
        <div><strong>创建新链接</strong><span>明文令牌只显示一次，链接最长有效 365 天。</span></div>
        <label><span>有效期</span><input v-model.number="days" type="number" min="1" max="365" step="1" required /><b>天</b></label>
        <button class="button button-primary" type="submit" :disabled="busy"><LoaderCircle v-if="busy" class="spin" :size="16" /><Share2 v-else :size="16" />{{ busy ? '生成中' : '生成链接' }}</button>
      </form>

      <p v-if="error" class="share-error" role="alert"><AlertCircle :size="16" />{{ error }}</p>

      <section v-if="secret" class="share-secret" aria-live="polite">
        <div><strong>分享链接已生成</strong><span>有效至 {{ formatDate(secret.expiresAt) }}</span></div>
        <label><span class="sr-only">新生成的分享链接</span><textarea :value="shareUrl" readonly rows="2" @focus="selectUrl" /></label>
        <p><Clock3 :size="15" />请现在复制保存，关闭窗口后不会再次展示这条链接。</p>
        <div><button class="button button-primary" type="button" @click="copyUrl"><Copy :size="16" />复制链接</button><a class="button button-secondary" :href="shareUrl" target="_blank" rel="noopener noreferrer">打开预览</a></div>
      </section>

      <section class="share-history">
        <header><div><strong>已创建的链接</strong><span>历史记录仅显示状态与有效期，不会泄露令牌。</span></div><b>{{ links.filter(item => item.active).length }} 个有效</b></header>
        <p v-if="loading" class="share-loading" role="status"><LoaderCircle class="spin" :size="17" />正在读取分享记录…</p>
        <div v-else-if="links.length" class="share-link-list">
          <article v-for="link in links" :key="link.id">
            <span class="share-link-icon"><Share2 :size="16" /></span>
            <div><strong>{{ link.active ? '有效分享链接' : link.revokedAt ? '已撤销' : '已过期' }}</strong><small>创建于 {{ formatDate(link.createdAt) }} · 有效至 {{ formatDate(link.expiresAt) }}</small></div>
            <span class="share-status" :class="{ active: link.active }">{{ link.active ? '有效' : link.revokedAt ? '已撤销' : '已过期' }}</span>
            <button v-if="link.active" class="button button-ghost" type="button" :disabled="busy" @click="revoke(link)">撤销</button>
          </article>
        </div>
        <p v-else class="share-empty">还没有创建过分享链接。</p>
      </section>
    </div>
  </AppModal>
</template>

<style scoped>
.knowledge-share-dialog { display: grid; gap: 18px; }
.share-create { display: grid; grid-template-columns: minmax(0, 1fr) auto auto; align-items: end; gap: 14px; padding: 17px; border: 1px solid var(--border); border-radius: 13px; background: var(--surface-sunken); }
.share-create > div { display: grid; gap: 5px; }
.share-create strong, .share-history strong, .share-secret strong { color: var(--text); font-size: var(--font-sm); }
.share-create span, .share-history span, .share-secret span { color: var(--muted); font-size: var(--font-2xs); }
.share-create label { display: flex; align-items: center; gap: 7px; color: var(--subtle); font-size: var(--font-xs); }
.share-create input { width: 74px; height: 38px; padding: 0 9px; color: var(--text); border: 1px solid var(--border-strong); border-radius: 8px; background: var(--panel); }
.share-create b { color: var(--muted); font-size: var(--font-xs); }
.share-error { display: flex; align-items: center; gap: 8px; margin: 0; padding: 11px 13px; color: var(--danger); border: 1px solid color-mix(in srgb, var(--danger) 24%, var(--border)); border-radius: 9px; background: color-mix(in srgb, var(--danger) 7%, transparent); font-size: var(--font-xs); }
.share-secret { display: grid; gap: 12px; padding: 17px; border: 1px solid var(--accent-border); border-radius: 13px; background: var(--accent-bg); }
.share-secret > div:first-child { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.share-secret textarea { width: 100%; resize: none; padding: 10px 12px; color: var(--text); border: 1px solid var(--accent-border); border-radius: 8px; outline: 0; background: var(--panel); font: 12px/1.5 "Cascadia Code", Consolas, monospace; }
.share-secret p { display: flex; align-items: center; gap: 7px; margin: 0; color: var(--muted); font-size: var(--font-2xs); }
.share-secret > div:last-child { display: flex; gap: 8px; }
.share-history { overflow: hidden; border: 1px solid var(--border); border-radius: 13px; }
.share-history > header { display: flex; align-items: center; justify-content: space-between; gap: 15px; padding: 15px 17px; border-bottom: 1px solid var(--border); background: var(--surface-raised); }
.share-history > header div { display: grid; gap: 4px; }
.share-history > header b { padding: 4px 8px; color: var(--accent); border-radius: 999px; background: var(--accent-bg); font-size: var(--font-2xs); }
.share-link-list { padding: 4px 14px; }
.share-link-list article { min-height: 62px; display: grid; grid-template-columns: 36px minmax(0, 1fr) auto auto; align-items: center; gap: 10px; padding: 9px 2px; border-bottom: 1px solid var(--border); }
.share-link-list article:last-child { border-bottom: 0; }
.share-link-list article > div { display: grid; gap: 4px; }
.share-link-icon { width: 32px; height: 32px; display: grid; place-items: center; color: var(--accent); border-radius: 9px; background: var(--accent-bg); }
.share-status { padding: 4px 8px; border-radius: 999px; background: var(--surface-sunken); }
.share-status.active { color: var(--success); background: color-mix(in srgb, var(--success) 10%, transparent); }
.share-loading, .share-empty { min-height: 92px; display: flex; align-items: center; justify-content: center; gap: 7px; margin: 0; color: var(--muted); font-size: var(--font-xs); }
</style>
