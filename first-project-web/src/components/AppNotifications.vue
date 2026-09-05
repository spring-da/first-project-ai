<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { Bell, BellRing, CheckCheck, CheckCircle2, CircleAlert, Info, Trash2, TriangleAlert, X } from 'lucide-vue-next'
import { useNotificationStore } from '../stores/notifications'

const notifications = useNotificationStore()
const filter = ref<'all' | 'unread'>('all')
const drawer = ref<HTMLElement | null>(null)
const closeButton = ref<HTMLButtonElement | null>(null)
const icons = { success: CheckCircle2, info: Info, warning: TriangleAlert, error: CircleAlert }
const labels = { success: '成功', info: '提示', warning: '警告', error: '错误' }
const visibleMessages = computed(() => notifications.history.filter((item) => filter.value === 'all' || !item.read))
const hasRead = computed(() => notifications.history.some((item) => item.read))
let returnFocus: HTMLElement | null = null
const time = (value: number) => new Date(value).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })

function onKeydown(event: KeyboardEvent) {
  if (!notifications.isOpen) return
  if (event.key === 'Escape') {
    event.preventDefault()
    event.stopImmediatePropagation()
    notifications.close()
  } else if (event.key === 'Tab') {
    const elements = [...(drawer.value?.querySelectorAll<HTMLElement>('button:not(:disabled), summary, [tabindex="0"]') ?? [])].filter((item) => item.offsetParent !== null)
    const first = elements[0]
    const last = elements.at(-1)
    if (event.shiftKey && (document.activeElement === first || !drawer.value?.contains(document.activeElement))) {
      event.preventDefault(); last?.focus()
    } else if (!event.shiftKey && (document.activeElement === last || !drawer.value?.contains(document.activeElement))) {
      event.preventDefault(); first?.focus()
    }
  }
}

function onToastFocusOut(event: FocusEvent, id: string) {
  const element = event.currentTarget as HTMLElement
  if (!element.contains(event.relatedTarget as Node | null) && !element.matches(':hover')) notifications.resume(id)
}

function onToastMouseLeave(event: MouseEvent, id: string) {
  if (!(event.currentTarget as HTMLElement).contains(document.activeElement)) notifications.resume(id)
}

watch(() => notifications.isOpen, async (open) => {
  document.body.classList.toggle('notifications-open', open)
  if (open) {
    returnFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
    filter.value = 'all'
    window.addEventListener('keydown', onKeydown, true)
    await nextTick()
    closeButton.value?.focus()
  } else {
    window.removeEventListener('keydown', onKeydown, true)
    if (returnFocus?.isConnected) returnFocus.focus()
  }
})
onBeforeUnmount(() => { document.body.classList.remove('notifications-open'); window.removeEventListener('keydown', onKeydown, true) })
</script>

<template>
  <Teleport to="body">
    <TransitionGroup name="toast" tag="div" class="toast-stack" aria-label="即时通知">
      <article v-for="item in notifications.toasts" :key="item.id" class="toast-card notification-tone" :class="item.type" :role="item.type === 'error' ? 'alert' : 'status'" aria-atomic="true" @mouseenter="notifications.pause(item.id)" @mouseleave="onToastMouseLeave($event, item.id)" @focusin="notifications.pause(item.id)" @focusout="onToastFocusOut($event, item.id)">
        <span class="notification-icon"><component :is="icons[item.type]" :size="20" /></span>
        <div class="toast-copy"><strong>{{ item.title }} <small v-if="item.occurrences > 1">×{{ item.occurrences }}</small></strong><p>{{ item.message }}</p><button type="button" class="toast-history" @click="notifications.open">{{ item.details.length ? `查看 ${item.details.length} 条详情` : '查看消息记录' }}</button></div>
        <button type="button" class="notification-close" aria-label="关闭提示" @click="notifications.dismiss(item.id)"><X :size="16" /></button>
      </article>
    </TransitionGroup>

    <Transition name="notification-drawer">
      <div v-if="notifications.isOpen" class="notification-backdrop" @mousedown.self="notifications.close">
        <section id="notification-center" ref="drawer" class="notification-drawer" role="dialog" aria-modal="true" aria-labelledby="notification-heading">
          <header class="notification-header">
            <div class="notification-heading"><span class="center-icon"><BellRing :size="21" /></span><div><h2 id="notification-heading">消息通知</h2><p>每一次提醒，都可以在这里找回</p></div></div>
            <button ref="closeButton" class="notification-close" type="button" aria-label="关闭消息通知" @click="notifications.close"><X :size="20" /></button>
          </header>
          <div class="notification-toolbar">
            <div class="notification-filters" role="group" aria-label="消息筛选"><button type="button" :class="{ active: filter === 'all' }" :aria-pressed="filter === 'all'" @click="filter = 'all'">全部 <span>{{ notifications.history.length }}</span></button><button type="button" :class="{ active: filter === 'unread' }" :aria-pressed="filter === 'unread'" @click="filter = 'unread'">未读 <span>{{ notifications.unreadCount }}</span></button></div>
            <button class="notification-text-button" type="button" :disabled="!notifications.unreadCount" @click="notifications.markAllRead"><CheckCheck :size="15" />全部已读</button>
          </div>
          <TransitionGroup name="notification-list" tag="div" class="notification-list">
            <article v-for="item in visibleMessages" :key="item.id" class="notification-item notification-tone" :class="[item.type, { unread: !item.read }]">
              <span class="notification-icon"><component :is="icons[item.type]" :size="18" /></span>
              <div class="notification-item-copy">
                <div class="notification-item-heading"><strong>{{ item.title }}</strong><span class="notification-kind">{{ labels[item.type] }}</span><i v-if="!item.read" aria-label="未读"></i></div>
                <p>{{ item.message }}</p>
                <details v-if="item.details.length" @toggle="($event.target as HTMLDetailsElement).open && notifications.markRead(item.id)"><summary>查看详情（{{ item.details.length }}）</summary><ul><li v-for="(detail, index) in item.details" :key="index">{{ detail }}</li></ul></details>
                <div class="notification-item-meta"><time :datetime="new Date(item.updatedAt).toISOString()">{{ time(item.updatedAt) }}</time><span v-if="item.occurrences > 1">重复 {{ item.occurrences }} 次</span><button v-if="!item.read" type="button" @click="notifications.markRead(item.id)">标为已读</button></div>
              </div>
              <button class="notification-close remove-notification" type="button" :aria-label="`删除消息：${item.message}`" title="删除这条消息" @click="notifications.remove(item.id)"><X :size="15" /></button>
            </article>
            <div v-if="!visibleMessages.length" key="empty" class="notification-empty"><span><Bell :size="30" /></span><h3>{{ filter === 'unread' ? '没有未读消息' : '消息列表很清爽' }}</h3><p>{{ filter === 'unread' ? '所有提醒都已经处理完了。' : '操作提示、警告和错误会留存在这里。' }}</p></div>
          </TransitionGroup>
          <footer class="notification-footer"><div><button class="notification-text-button" type="button" :disabled="!hasRead" @click="notifications.clearRead">清除已读</button><button class="notification-text-button clear-all" type="button" :disabled="!notifications.history.length" @click="notifications.clearAll"><Trash2 :size="14" />清空全部</button></div><p>{{ notifications.historyPersisted ? '仅保存在当前浏览器 · 按账号隔离 · 最多保留 100 条' : '浏览器存储不可用 · 当前消息仅在本次打开期间保留' }}</p></footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.notification-tone { --tone: var(--accent); }
.notification-tone.success { --tone: var(--success); }
.notification-tone.warning { --tone: var(--warning); }
.notification-tone.error { --tone: var(--danger); }
.toast-stack { position: fixed; z-index: 220; top: 82px; right: 24px; display: flex; flex-direction: column; gap: 12px; width: min(400px, calc(100vw - 32px)); pointer-events: none; }
.toast-card { position: relative; display: flex; align-items: flex-start; gap: 12px; width: 100%; padding: 17px 14px 15px 17px; overflow: hidden; pointer-events: auto; border: 1px solid color-mix(in srgb, var(--tone) 26%, var(--border)); border-radius: 14px; background: color-mix(in srgb, var(--tone) 5%, var(--panel)); box-shadow: 0 10px 36px color-mix(in srgb, var(--tone) 10%, transparent), var(--shadow-md); }
.toast-card::before { position: absolute; inset: 0 auto 0 0; width: 3px; background: var(--tone); content: ''; }
.notification-icon { display: grid; flex-shrink: 0; place-items: center; width: 34px; height: 34px; color: var(--tone); border-radius: 10px; background: color-mix(in srgb, var(--tone) 12%, transparent); }
.toast-copy { min-width: 0; flex: 1; }
.toast-copy > strong { display: block; color: var(--tone); font-size: 14px; }
.toast-copy small { margin-left: 4px; font-size: 11px; }
.toast-copy p { margin: 5px 0 7px; color: var(--text); font-size: 13px; line-height: 1.65; }
.toast-history { padding: 0; color: var(--muted); border: 0; background: transparent; cursor: pointer; font-size: 11px; }
.toast-history:hover { color: var(--tone); }
.notification-close { display: grid; flex-shrink: 0; place-items: center; width: 28px; height: 28px; padding: 0; color: var(--muted); border: 0; border-radius: 7px; background: transparent; cursor: pointer; }
.notification-close:hover { color: var(--text); background: var(--surface-raised); }
.toast-enter-active, .toast-leave-active, .toast-move { transition: opacity .24s ease, transform .3s cubic-bezier(.22,1,.36,1); }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateX(calc(100% + 30px)); }
.toast-leave-active { position: absolute; }
.notification-backdrop { position: fixed; z-index: 240; inset: 0; display: flex; justify-content: flex-end; background: rgba(8,15,29,.26); backdrop-filter: blur(3px); }
.notification-drawer { display: flex; flex-direction: column; width: min(460px, 100%); height: 100dvh; min-height: 0; border-left: 1px solid var(--border); background: var(--panel); box-shadow: -20px 0 70px rgba(0,0,0,.13); }
.notification-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; padding: 26px 22px 22px; }
.notification-heading { display: flex; align-items: center; gap: 12px; }
.center-icon { display: grid; flex-shrink: 0; place-items: center; width: 44px; height: 44px; color: var(--accent); border: 1px solid var(--accent-border); border-radius: 13px; background: var(--accent-bg); }
.notification-heading h2 { margin: 0; font-size: 20px; letter-spacing: -.4px; }
.notification-heading p { margin: 4px 0 0; color: var(--muted); font-size: 12px; }
.notification-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 0 22px 16px; border-bottom: 1px solid var(--border); }
.notification-filters { display: flex; gap: 3px; padding: 3px; border-radius: 9px; background: var(--surface-sunken); }
.notification-filters button { display: flex; align-items: center; gap: 6px; padding: 6px 10px; color: var(--muted); border: 0; border-radius: 7px; background: transparent; cursor: pointer; font-size: 12px; }
.notification-filters button.active { color: var(--accent); background: var(--panel); box-shadow: var(--shadow-sm); }
.notification-filters span { min-width: 16px; font-size: 10px; }
.notification-text-button { display: inline-flex; align-items: center; gap: 5px; padding: 5px 0; color: var(--muted); border: 0; background: transparent; cursor: pointer; font-size: 12px; }
.notification-text-button:hover:not(:disabled) { color: var(--accent); }
.notification-list { min-height: 0; flex: 1; overflow-y: auto; overscroll-behavior: auto; padding: 10px 14px; }
.notification-item { position: relative; display: flex; align-items: flex-start; gap: 10px; margin: 4px 0; padding: 16px 10px; border: 1px solid transparent; border-radius: 12px; }
.notification-item.unread { border-color: color-mix(in srgb, var(--tone) 14%, var(--border)); background: color-mix(in srgb, var(--tone) 4%, var(--panel)); }
.notification-item .notification-icon { width: 30px; height: 30px; border-radius: 9px; }
.notification-item-copy { flex: 1; min-width: 0; }
.notification-item-heading { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.notification-item-heading strong { font-size: 13px; }
.notification-kind { color: var(--tone); font-size: 10px; }
.notification-item-heading i { width: 5px; height: 5px; margin-left: auto; border-radius: 50%; background: var(--tone); }
.notification-item-copy > p { margin: 7px 0 9px; color: var(--subtle); font-size: 13px; line-height: 1.65; }
.notification-item-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; color: var(--muted); font-size: 10px; }
.notification-item-meta button { margin-left: auto; padding: 0; color: var(--accent); border: 0; background: transparent; cursor: pointer; font-size: 11px; }
.notification-item details { margin: 9px 0; color: var(--muted); font-size: 12px; overflow-wrap: anywhere; }
.notification-item summary { color: var(--tone); cursor: pointer; }
.notification-item ul { margin: 8px 0; padding-left: 16px; }
.notification-item li { margin: 5px 0; }
.remove-notification { width: 22px; height: 22px; }
.notification-empty { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 65%; padding: 40px 20px; text-align: center; }
.notification-empty > span { display: grid; place-items: center; width: 76px; height: 76px; margin-bottom: 16px; color: var(--muted); border: 1px dashed var(--border-strong); border-radius: 24px; background: var(--surface-sunken); }
.notification-empty h3 { margin: 0; font-size: 16px; }
.notification-empty p { margin: 8px 0; color: var(--muted); font-size: 12px; }
.notification-footer { padding: 16px 24px max(18px, env(safe-area-inset-bottom)); border-top: 1px solid var(--border); background: var(--surface-sunken); }
.notification-footer > div { display: flex; justify-content: space-between; }
.notification-footer .clear-all:hover:not(:disabled) { color: var(--danger); }
.notification-footer p { margin: 10px 0 0; color: var(--muted); font-size: 10px; }
.notification-drawer-enter-active, .notification-drawer-leave-active { transition: opacity .22s ease; }
.notification-drawer-enter-active .notification-drawer, .notification-drawer-leave-active .notification-drawer { transition: transform .28s cubic-bezier(.22,1,.36,1); }
.notification-drawer-enter-from, .notification-drawer-leave-to { opacity: 0; }
.notification-drawer-enter-from .notification-drawer, .notification-drawer-leave-to .notification-drawer { transform: translateX(100%); }
.notification-list-enter-active, .notification-list-leave-active, .notification-list-move { transition: opacity var(--motion-base) ease, transform var(--motion-slow) var(--ease-emphasized); }
.notification-list-enter-from { opacity: 0; transform: translateX(12px); }
.notification-list-leave-to { opacity: 0; transform: translateX(20px); }
@media (max-width: 720px) { .toast-stack { top: 78px; right: 16px; } .notification-header { padding: 22px 18px 18px; } .notification-toolbar { padding-inline: 18px; } }
</style>
