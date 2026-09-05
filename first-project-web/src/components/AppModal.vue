<script setup lang="ts">
import { onActivated, onBeforeUnmount, onDeactivated, onMounted } from 'vue'
import { X } from 'lucide-vue-next'

withDefaults(defineProps<{ title: string; description?: string; wide?: boolean; workspace?: boolean }>(), {
  description: '',
  wide: false,
  workspace: false,
})
const emit = defineEmits<{ close: [] }>()

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') emit('close')
}

function activateModal() {
  document.body.classList.add('modal-open')
  window.addEventListener('keydown', onKeydown)
}

function deactivateModal() {
  document.body.classList.remove('modal-open')
  window.removeEventListener('keydown', onKeydown)
}

onMounted(activateModal)
onActivated(activateModal)
onDeactivated(deactivateModal)
onBeforeUnmount(deactivateModal)
</script>

<template>
  <Teleport to="body">
    <div class="modal-backdrop" :class="{ 'workspace-backdrop': workspace }" role="presentation" @mousedown.self="emit('close')">
      <section class="modal-card" :class="{ wide, workspace }" role="dialog" aria-modal="true" :aria-label="title">
        <header class="modal-header">
          <div><h2>{{ title }}</h2><p v-if="description">{{ description }}</p></div>
          <button class="icon-btn" type="button" aria-label="关闭" @click="emit('close')"><X :size="19" /></button>
        </header>
        <div class="modal-body"><slot /></div>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.modal-backdrop { position: fixed; z-index: 100; inset: 0; display: grid; place-items: center; padding: 30px; background: rgba(5,7,11,.74); backdrop-filter: blur(5px); animation: modal-backdrop-in var(--motion-base) ease both; }
.modal-card { width: min(650px, 100%); max-height: calc(100vh - 60px); overflow: auto; border: 1px solid var(--border-strong); border-radius: 20px; background: var(--modal-bg); box-shadow: 0 24px 80px rgba(0,0,0,.45); animation: modal-card-in var(--motion-slow) var(--ease-emphasized) both; }
.modal-card.wide { width: min(920px, 100%); }
.modal-backdrop.workspace-backdrop { padding: 12px; }
.modal-card.workspace { width: min(1720px, 100%); height: calc(100dvh - 24px); max-height: calc(100dvh - 24px); display: flex; flex-direction: column; overflow: hidden; }
.modal-card.workspace .modal-header { flex: 0 0 auto; padding: 18px 24px; }
.modal-card.workspace .modal-body { min-height: 0; flex: 1; overflow: hidden; padding: 16px 20px 18px; }
.modal-header { position: sticky; z-index: 2; top: 0; display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; padding: clamp(24px, 1.4vw, 32px); border-bottom: 1px solid var(--border); background: var(--modal-bg); }
.modal-header h2 { margin: 0; font-size: var(--font-lg); }
.modal-header p { margin: 7px 0 0; color: var(--muted); font-size: var(--font-sm); }
.modal-body { padding: clamp(24px, 1.4vw, 32px); }
@keyframes modal-backdrop-in { from { opacity: 0; backdrop-filter: blur(0); } }
@keyframes modal-card-in { from { opacity: 0; transform: translateY(16px) scale(.975); } }
@media (max-width: 720px) {
  .modal-backdrop { place-items: end stretch; padding: 0; }
  .modal-backdrop.workspace-backdrop { padding: 0; }
  .modal-card, .modal-card.wide, .modal-card.workspace { width: 100%; height: auto; max-height: min(92vh, 92svh); border-right: 0; border-bottom: 0; border-left: 0; border-radius: 18px 18px 0 0; }
  .modal-header { padding: 20px 18px 16px; }
  .modal-body { padding: 20px 18px calc(22px + env(safe-area-inset-bottom)); }
}
</style>
