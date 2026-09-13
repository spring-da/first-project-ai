<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { AlertCircle, Check, Copy, FileText, Link2, LoaderCircle, UsersRound } from 'lucide-vue-next'
import AppModal from './AppModal.vue'
import LegacyShareLinks from './LegacyShareLinks.vue'
import { createShareBundle, publishToPool } from '../services/sharing'
import { prepareShareSelection, shareExpiration } from '../utils/shareSelection'
import { useAuthStore } from '../stores/auth'
import { useNotificationStore } from '../stores/notifications'
import type { ShareBundleSecret, ShareSelection } from '../types/sharing'
const props = withDefaults(defineProps<{ items: ShareSelection[]; unsaved?: boolean }>(), { unsaved: false })
const emit = defineEmits<{ close: [] }>()
const auth = useAuthStore()
const router = useRouter()
const notifications = useNotificationStore()
const root = ref<HTMLElement | null>(null)
const channel = ref<'pool' | 'link'>('pool')
const title = ref((props.items.length === 1 ? props.items[0]!.title : `${props.items[0]?.title || '知识分享'} 等 ${props.items.length} 项`).slice(0, 160))
const days = ref(7)
const busy = ref(false)
const legacyBusy = ref(false)
const error = ref('')
const poolResult = ref('')
const secret = ref<ShareBundleSecret | null>(null)
const copied = ref(false)
const labels = { MARKDOWN: '文章', SNIPPET: '代码片段', FLOWCHART: '流程图' }
const blocked = computed(() => busy.value || legacyBusy.value)
const shareUrl = computed(() => secret.value ? `${window.location.origin}/share/bundle/${encodeURIComponent(secret.value.token)}` : '')
const originalFocus = document.activeElement as HTMLElement | null
const owner = auth.workspaceKey
let active = true
function close() { if (!blocked.value) emit('close') }
function showManagement(tab: 'mine' | 'links') {
  if (blocked.value) return
  emit('close')
  void router.push({ name: 'shared-pool', query: { tab, ...(auth.workspaceMember ? { workspace: auth.workspaceMember.id } : {}) } })
}
async function submit() {
  if (blocked.value || secret.value || (channel.value === 'pool' && poolResult.value)) return
  error.value = ''; busy.value = true
  try {
    const items = prepareShareSelection(props.items)
    if (channel.value === 'pool') {
      const result = await publishToPool(items)
      if (!active || owner !== auth.workspaceKey) return
      poolResult.value = result.publishedCount ? `已将 ${result.publishedCount} 项内容发布到知识广场${result.existingCount ? `，另有 ${result.existingCount} 项已在知识广场中` : ''}。` : '所选内容已在知识广场中，无需重复发布。'
      notifications.notify(poolResult.value, { type: 'success' })
    } else {
      if (!title.value.trim() || title.value.trim().length > 160) throw new Error('请填写 1–160 个字的分享名称。')
      const result = await createShareBundle(title.value.trim(), items, shareExpiration(Number(days.value)))
      if (!active || owner !== auth.workspaceKey) return
      secret.value = result
      notifications.notify('分享链接已生成，请复制保存。', { type: 'success' })
      await nextTick(); root.value?.querySelector<HTMLTextAreaElement>('textarea')?.focus()
    }
  } catch (cause) { if (active && owner === auth.workspaceKey) error.value = cause instanceof Error ? cause.message : '分享失败，请重试。' }
  finally { if (active) busy.value = false }
}
async function copy() {
  if (!shareUrl.value) return
  try {
    if (window.isSecureContext && navigator.clipboard?.writeText) await navigator.clipboard.writeText(shareUrl.value)
    else {
      const field = root.value?.querySelector<HTMLTextAreaElement>('textarea')
      field?.focus(); field?.select()
      if (!document.execCommand('copy')) throw new Error('copy failed')
    }
    copied.value = true; notifications.notify('分享链接已复制。', { type: 'success' })
  } catch { notifications.notify('自动复制失败，请手动选择并复制链接。', { type: 'warning' }) }
}
function onKeydown(event: KeyboardEvent) {
  if (event.key !== 'Tab' || document.body.classList.contains('confirmation-open')) return
  const modal = root.value?.closest('[role="dialog"]')
  const controls = Array.from(modal?.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), textarea, a[href], summary') ?? []).filter(element => element.getClientRects().length)
  const first = controls[0], last = controls[controls.length - 1]
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
}
watch(channel, () => { error.value = '' })
watch(() => auth.workspaceKey, () => { secret.value = null; active = false; emit('close') }, { flush: 'sync' })
onMounted(() => { window.addEventListener('keydown', onKeydown); void nextTick(() => root.value?.querySelector<HTMLInputElement>('input[type=radio]')?.focus()) })
onBeforeUnmount(() => { active = false; secret.value = null; window.removeEventListener('keydown', onKeydown); if (originalFocus?.isConnected) originalFocus.focus() })
</script>
<template>
  <AppModal :title="items.length === 1 ? '分享内容' : `批量分享 ${items.length} 项内容`" description="选择分享方式，让知识被合适的人看见。" wide @close="close">
    <div ref="root" class="share-dialog">
      <fieldset class="channel-options" :disabled="blocked || !!secret">
        <legend class="channel-options__title">分享方式</legend>
        <div class="channel-options__grid">
          <label :class="{ selected: channel === 'pool' }"><input v-model="channel" type="radio" value="pool" name="share-channel" /><UsersRound :size="23" aria-hidden="true" /><span><strong>分享到知识广场</strong><small>登录成员可搜索、阅读，随时可撤下</small></span></label>
          <label :class="{ selected: channel === 'link' }"><input v-model="channel" type="radio" value="link" name="share-channel" /><Link2 :size="23" aria-hidden="true" /><span><strong>生成外部链接</strong><small>持有链接的人无需登录，在有效期内查看</small></span></label>
        </div>
      </fieldset>
      <details class="selected-items"><summary><FileText :size="16" /><span>将分享 {{ items.length }} 项内容</span><small>查看清单</small></summary><ul><li v-for="item in items" :key="`${item.type}:${item.id}`"><span>{{ labels[item.type] }}</span><strong>{{ item.title }}</strong></li></ul></details>
      <p v-if="unsaved" class="notice"><AlertCircle :size="17" />当前有未保存的修改，只会分享最近一次成功保存的内容。</p>
      <p class="scope-note">分享内容会随后续保存同步更新。{{ channel === 'pool' ? '从知识广场撤下不会影响外部链接。' : '可以撤销整条链接，也可以仅移除其中某项内容。' }}</p>
      <form v-if="!secret && !(channel === 'pool' && poolResult)" class="share-form" @submit.prevent="submit">
        <template v-if="channel === 'link'">
          <label class="name-field"><span>分享名称</span><input v-model="title" maxlength="160" required :disabled="busy" /></label>
          <div class="expiry-field"><label><span>有效期</span><input v-model.number="days" type="number" min="1" max="365" step="1" required aria-label="分享链接有效天数" :disabled="busy" /><span>天</span></label><div><button v-for="preset in [1, 7, 30]" :key="preset" type="button" :aria-pressed="days === preset" :disabled="busy" @click="days = preset">{{ preset }} 天</button></div></div>
        </template>
        <p v-if="error" class="error" role="alert"><AlertCircle :size="16" />{{ error }}</p>
        <div class="form-actions"><span>{{ channel === 'pool' ? '仅限站内登录成员，只读查看' : '一个链接包含全部所选内容' }}</span><button class="button button-primary" type="submit" :disabled="blocked || !items.length || items.length > 100"><LoaderCircle v-if="busy" :size="17" class="spin" /><UsersRound v-else-if="channel === 'pool'" :size="17" /><Link2 v-else :size="17" />{{ busy ? '正在分享…' : channel === 'pool' ? '发布到知识广场' : '生成链接' }}</button></div>
        <p v-if="items.length > 100" class="error" role="alert">每次最多分享 100 项内容，请关闭窗口并减少选择。</p>
      </form>
      <section v-if="channel === 'pool' && poolResult" class="share-success" aria-live="polite"><Check :size="24" /><div><strong>{{ poolResult }}</strong><p>可以在“我的分享”中单篇或批量撤下。</p></div><button class="button button-secondary" type="button" @click="showManagement('mine')">查看我的分享</button></section>
      <section v-if="secret" class="share-secret" aria-live="polite"><div><Check :size="19" /><strong>链接已生成</strong><span>有效至 {{ new Date(secret.expiresAt).toLocaleString('zh-CN') }}</span></div><label><span class="sr-only">新生成的分享链接</span><textarea :value="shareUrl" readonly rows="2" @focus="($event.target as HTMLTextAreaElement).select()" /></label><p>请现在复制保存，关闭窗口后无法再次查看这条链接。</p><div class="secret-actions"><button class="button button-primary" type="button" @click="copy"><Check v-if="copied" :size="16" /><Copy v-else :size="16" />{{ copied ? '已复制' : '复制链接' }}</button><a class="button button-secondary" :href="shareUrl" target="_blank" rel="noopener noreferrer">打开预览</a></div></section>
      <div class="management-links"><button type="button" :disabled="blocked" @click="showManagement('mine')">管理我的广场分享</button><button type="button" :disabled="blocked" @click="showManagement('links')">外链管理与撤销</button></div>
      <LegacyShareLinks v-if="items.length === 1 && items[0]?.type !== 'FLOWCHART'" :item="items[0]!" @busy="legacyBusy = $event" />
    </div>
  </AppModal>
</template>
<style scoped>
.share-dialog { display: grid; gap: 18px; min-width: 0; }
.channel-options { min-width: 0; margin: 0; padding: 0; border: 0; }
.channel-options__title { padding: 0 0 14px; color: var(--subtle); font-size: 13px; font-weight: 650; line-height: 1.5; }
.channel-options__grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.channel-options label { position: relative; min-width: 0; display: flex; align-items: flex-start; gap: 12px; padding: 20px 18px; border: 1px solid var(--border-strong); border-radius: 13px; background: var(--surface-sunken); cursor: pointer; }
.channel-options label.selected { border-color: var(--accent); background: var(--accent-bg); }
.channel-options input { flex: 0 0 auto; margin: 4px 0 0; accent-color: var(--accent); }
.channel-options svg { flex: 0 0 auto; color: var(--accent); }
.channel-options label > span { display: grid; min-width: 0; gap: 7px; }
.channel-options strong { font-size: 14px; line-height: 1.5; }
.channel-options small { color: var(--muted); font-size: 12px; line-height: 1.7; }
.channel-options label:has(input:focus-visible) { outline: 2px solid var(--accent); outline-offset: 2px; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
.selected-items { border: 1px solid var(--border); border-radius: 10px; }.selected-items summary { display: flex; align-items: center; gap: 8px; padding: 13px; font-size: 13px; cursor: pointer; }.selected-items small { margin-left: auto; color: var(--muted); }.selected-items ul { max-height: 190px; overflow: auto; padding: 0 14px; list-style: none; margin: 0; }.selected-items li { display: flex; align-items: baseline; gap: 12px; padding: 10px 0; border-top: 1px solid var(--border); font-size: 12px; }.selected-items li span { flex: 0 0 60px; color: var(--muted); }.selected-items strong { font-weight: 550; overflow-wrap: anywhere; }
.notice, .error { display: flex; align-items: flex-start; gap: 8px; margin: 0; font-size: 13px; line-height: 1.6; }.notice { padding: 12px; border-radius: 8px; color: var(--warning); background: color-mix(in srgb, var(--warning) 8%, var(--panel)); }.notice svg, .error svg { flex: 0 0 auto; margin-top: 2px; }.error { color: var(--danger); }.scope-note { margin: 0; font-size: 12px; color: var(--muted); line-height: 1.7; }
.share-form { display: grid; gap: 17px; }.name-field { display: grid; gap: 8px; font-size: 13px; }.share-form input, textarea { min-width: 0; border: 1px solid var(--border-strong); border-radius: 9px; padding: 10px 12px; color: var(--text); background: var(--panel); font: inherit; }.share-form input:focus, textarea:focus { outline: 2px solid var(--accent); outline-offset: 2px; }.expiry-field, .expiry-field label, .expiry-field > div { display: flex; align-items: center; gap: 9px; flex-wrap: wrap; font-size: 13px; }.expiry-field > div { margin-left: auto; }.expiry-field input { width: 84px; }.expiry-field button { min-height: 36px; padding: 4px 12px; border: 1px solid var(--border); border-radius: 8px; color: var(--muted); background: var(--surface-sunken); cursor: pointer; }.expiry-field button[aria-pressed=true] { color: var(--accent); border-color: var(--accent-border); background: var(--accent-bg); }
.form-actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; }.form-actions > span { color: var(--muted); font-size: 12px; }.button { min-height: 42px; flex: 0 0 auto; }.share-success { display: flex; flex-wrap: wrap; align-items: flex-start; gap: 12px; padding: 18px; border: 1px solid var(--accent-border); border-radius: 12px; background: var(--accent-bg); }.share-success > svg { color: var(--success); flex: 0 0 auto; }.share-success > div { flex: 1; min-width: 120px; }.share-success strong { font-size: 14px; line-height: 1.6; }.share-success p { color: var(--muted); font-size: 12px; }
.share-secret { display: grid; gap: 12px; padding: 18px; border: 1px solid var(--accent-border); border-radius: 12px; background: var(--accent-bg); }.share-secret > div:first-child { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }.share-secret strong { font-size: 14px; }.share-secret span { color: var(--muted); font-size: 12px; }.share-secret textarea { width: 100%; resize: none; font: 12px/1.6 monospace; }.share-secret p { margin: 0; color: var(--muted); font-size: 12px; line-height: 1.6; }.secret-actions { display: flex; gap: 8px; flex-wrap: wrap; }.management-links { display: flex; justify-content: space-between; gap: 8px; flex-wrap: wrap; }.management-links button { padding: 8px 0; border: 0; color: var(--accent); background: transparent; font: inherit; font-size: 12px; cursor: pointer; }
@media (max-width: 600px) { .channel-options__grid { grid-template-columns: minmax(0, 1fr); }.channel-options label { padding: 16px 14px; gap: 10px; }.form-actions { align-items: stretch; flex-direction: column; }.form-actions .button { width: 100%; }.expiry-field > div { width: 100%; margin-left: 0; }.share-dialog { gap: 15px; } }
</style>
