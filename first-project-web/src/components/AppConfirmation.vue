<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ArrowRight, Info, LogOut, ShieldAlert, Trash2, X } from 'lucide-vue-next'
import { useConfirmationStore } from '../stores/confirmation'
import { useNotificationStore } from '../stores/notifications'

const confirmation = useConfirmationStore()
const notifications = useNotificationStore()
const dialog = ref<HTMLElement | null>(null)
const cancelButton = ref<HTMLButtonElement | null>(null)
const icons = { delete: Trash2, logout: LogOut, leave: ShieldAlert, info: Info }
let returnFocus: HTMLElement | null = null

function onKeydown(event: KeyboardEvent) {
  if (!confirmation.current || notifications.isOpen) return
  if (event.key === 'Escape') {
    event.preventDefault()
    event.stopImmediatePropagation()
    confirmation.cancel()
  } else if (event.key === 'Tab') {
    const buttons = [...(dialog.value?.querySelectorAll<HTMLButtonElement>('button:not(:disabled)') ?? [])]
    const first = buttons[0]
    const last = buttons.at(-1)
    if (event.shiftKey && (document.activeElement === first || !dialog.value?.contains(document.activeElement))) {
      event.preventDefault(); last?.focus()
    } else if (!event.shiftKey && (document.activeElement === last || !dialog.value?.contains(document.activeElement))) {
      event.preventDefault(); first?.focus()
    }
  }
}

watch(() => confirmation.current, async (current) => {
  document.body.classList.toggle('confirmation-open', !!current)
  if (current) {
    returnFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
    window.addEventListener('keydown', onKeydown, true)
    await nextTick()
    cancelButton.value?.focus()
  } else {
    window.removeEventListener('keydown', onKeydown, true)
    if (returnFocus?.isConnected) returnFocus.focus({ preventScroll: true })
  }
})

onBeforeUnmount(() => {
  confirmation.cancel()
  document.body.classList.remove('confirmation-open')
  window.removeEventListener('keydown', onKeydown, true)
})
</script>

<template>
  <Teleport to="body">
    <Transition name="confirmation">
      <div v-if="confirmation.current" class="confirmation-backdrop" @mousedown.self="confirmation.cancel">
        <section ref="dialog" class="confirmation-card" :class="confirmation.current.tone" role="alertdialog" aria-modal="true" aria-labelledby="confirmation-title" aria-describedby="confirmation-message">
          <button class="confirmation-close" type="button" aria-label="取消并关闭确认弹窗" @click="confirmation.cancel"><X :size="18" /></button>
          <div class="confirmation-content">
            <span class="confirmation-symbol"><component :is="icons[confirmation.current.icon ?? 'info']" :size="24" :stroke-width="1.7" /></span>
            <h2 id="confirmation-title">{{ confirmation.current.title }}</h2>
            <p id="confirmation-message">{{ confirmation.current.message }}</p>
            <div v-if="confirmation.current.detail" class="confirmation-detail"><Info :size="15" /><span>{{ confirmation.current.detail }}</span></div>
          </div>
          <footer class="confirmation-actions">
            <button ref="cancelButton" type="button" class="button confirmation-cancel" @click="confirmation.cancel">{{ confirmation.current.cancelText }}</button>
            <button type="button" class="button confirmation-accept" @click="confirmation.answer(true)">{{ confirmation.current.confirmText }}<ArrowRight v-if="confirmation.current.icon === 'logout'" :size="16" /></button>
          </footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.confirmation-backdrop { position: fixed; z-index: 230; inset: 0; display: grid; place-items: center; padding: 24px; background: rgba(9,16,30,.38); backdrop-filter: blur(5px); }
.confirmation-card { --tone: var(--accent); --action: var(--brand); position: relative; width: min(430px, 100%); max-height: calc(100dvh - 48px); overflow: auto; color: var(--text); border: 1px solid var(--border-strong); border-radius: 22px; background: var(--panel); box-shadow: 0 24px 90px rgba(0,0,0,.24), 0 2px 8px rgba(0,0,0,.05); }
.confirmation-card.danger { --tone: var(--danger); --action: #c64b5b; }
.confirmation-card.warning { --tone: var(--warning); --action: #98671e; }
.confirmation-close { position: absolute; top: 16px; right: 16px; display: grid; place-items: center; width: 30px; height: 30px; padding: 0; color: var(--muted); border: 0; border-radius: 9px; background: transparent; cursor: pointer; }
.confirmation-close:hover { color: var(--text); background: var(--surface-raised); }
.confirmation-content { padding: 30px 30px 25px; }
.confirmation-symbol { display: grid; place-items: center; width: 52px; height: 52px; margin-bottom: 21px; color: var(--tone); border: 1px solid color-mix(in srgb, var(--tone) 20%, transparent); border-radius: 16px; background: color-mix(in srgb, var(--tone) 10%, var(--panel)); box-shadow: inset 0 1px 0 color-mix(in srgb, var(--panel) 70%, transparent); }
.confirmation-content h2 { margin: 0 26px 10px 0; font-size: 21px; line-height: 1.4; letter-spacing: -.4px; }
.confirmation-content > p { margin: 0; color: var(--subtle); font-size: 14px; line-height: 1.8; white-space: pre-line; }
.confirmation-detail { display: flex; align-items: flex-start; gap: 8px; margin-top: 18px; padding: 12px; color: var(--muted); border-radius: 10px; background: var(--surface-sunken); font-size: 12px; line-height: 1.65; }
.confirmation-detail svg { flex-shrink: 0; margin-top: 2px; color: var(--tone); }
.confirmation-actions { display: flex; justify-content: flex-end; gap: 10px; padding: 18px 30px; border-top: 1px solid var(--border); background: var(--surface-sunken); }
.confirmation-actions .button { min-width: 108px; min-height: 40px; font-size: 13px; }
.confirmation-cancel { color: var(--subtle); border-color: var(--border-strong); background: var(--panel); }
.confirmation-cancel:hover { color: var(--text); background: var(--surface-raised); }
.confirmation-accept { color: #fff; background: var(--action); box-shadow: 0 3px 8px color-mix(in srgb, var(--action) 20%, transparent); }
.confirmation-accept:hover { background: color-mix(in srgb, var(--action) 88%, #000); }
.confirmation-enter-active, .confirmation-leave-active { transition: opacity .18s ease; }
.confirmation-enter-active .confirmation-card, .confirmation-leave-active .confirmation-card { transition: opacity .18s ease, transform .22s cubic-bezier(.2,.8,.2,1); }
.confirmation-enter-from, .confirmation-leave-to { opacity: 0; }
.confirmation-enter-from .confirmation-card, .confirmation-leave-to .confirmation-card { opacity: 0; transform: translateY(12px) scale(.97); }
@media (max-width: 480px) { .confirmation-backdrop { padding: 18px; } .confirmation-content { padding: 26px 22px 22px; } .confirmation-actions { padding: 16px 22px; } .confirmation-actions .button { min-width: 0; flex: 1; } }
</style>
