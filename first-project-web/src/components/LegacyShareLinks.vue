<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { listMarkdownShares, revokeMarkdownShare } from '../services/markdownShares'
import { listKnowledgeShares, revokeKnowledgeShare } from '../services/knowledgeShares'
import { useConfirmationStore } from '../stores/confirmation'
import { useNotificationStore } from '../stores/notifications'
import type { KnowledgeShareLink } from '../types'
import type { ShareSelection } from '../types/sharing'
const props = defineProps<{ item: ShareSelection }>()
const emit = defineEmits<{ busy: [value: boolean] }>()
const confirmation = useConfirmationStore()
const notifications = useNotificationStore()
const links = ref<KnowledgeShareLink[]>([])
const loading = ref(false)
const busy = ref(false)
const error = ref('')
let active = true
function date(value: string) { return new Date(value).toLocaleString('zh-CN') }
function isActive(link: KnowledgeShareLink) { return link.active && Date.parse(link.expiresAt) > Date.now() }
async function load(event: Event) {
  if (!(event.target as HTMLDetailsElement).open || loading.value) return
  loading.value = true; error.value = ''
  try {
    const result = props.item.type === 'MARKDOWN' ? await listMarkdownShares(props.item.id)
      : await listKnowledgeShares('snippets', props.item.id)
    if (active) links.value = result
  } catch (cause) { if (active) error.value = cause instanceof Error ? cause.message : '旧链接读取失败，请重新展开重试。' }
  finally { if (active) loading.value = false }
}
async function revoke(link: KnowledgeShareLink) {
  if (busy.value || link.revokedAt) return
  busy.value = true; emit('busy', true)
  try {
    if (!await confirmation.ask({ title: '撤销这个旧链接？', message: `此链接将无法继续查看“${props.item.title}”。`, detail: '其他链接和知识广场中的分享不受影响。需要再次分享时可生成新链接。', confirmText: '撤销链接', cancelText: '保留链接', tone: 'warning', icon: 'leave' }) || !active) return
    if (props.item.type === 'MARKDOWN') await revokeMarkdownShare(props.item.id, link.id)
    else await revokeKnowledgeShare('snippets', props.item.id, link.id)
    if (!active) return
    links.value = links.value.map(item => item.id === link.id ? { ...item, active: false, revokedAt: new Date().toISOString() } : item)
    notifications.notify('旧分享链接已撤销。', { type: 'success' })
  } catch (cause) { if (active) error.value = cause instanceof Error ? cause.message : '撤销失败，请重试。' }
  finally { busy.value = false; emit('busy', false) }
}
onBeforeUnmount(() => { active = false })
</script>
<template>
  <details class="legacy-links" @toggle="load">
    <summary>管理这篇内容的旧版单篇链接</summary>
    <p>新生成的链接统一在“知识广场 → 外链管理”中管理。</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-if="loading" role="status">正在读取旧分享记录…</p>
    <ul v-else-if="links.length"><li v-for="link in links" :key="link.id"><div><strong>{{ isActive(link) ? '有效' : link.revokedAt ? '已撤销' : '已失效' }}</strong><span>创建于 {{ date(link.createdAt) }}</span><span>有效至 {{ date(link.expiresAt) }}</span></div><button v-if="!link.revokedAt" class="button button-ghost" type="button" :disabled="busy" @click="revoke(link)">撤销</button></li></ul>
    <p v-else>没有旧版单篇链接。</p>
  </details>
</template>
<style scoped>
.legacy-links { padding-top: 16px; border-top: 1px solid var(--border); font-size: 12px; } summary { padding: 5px 0; cursor: pointer; color: var(--subtle); } p { color: var(--muted); line-height: 1.6; } p[role=alert] { color: var(--danger); } ul { display: grid; padding: 0; margin: 12px 0 0; list-style: none; max-height: 240px; overflow: auto; } li { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 12px 0; border-top: 1px solid var(--border); } li div { display: grid; gap: 4px; min-width: 0; } span { color: var(--muted); overflow-wrap: anywhere; }
</style>
