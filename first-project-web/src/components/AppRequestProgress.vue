<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { requestActive } from '../services/requestActivity'

const visible = ref(false)
let shownAt = 0
let showTimer: number | undefined
let hideTimer: number | undefined

function clearTimers() {
  if (showTimer !== undefined) window.clearTimeout(showTimer)
  if (hideTimer !== undefined) window.clearTimeout(hideTimer)
  showTimer = undefined
  hideTimer = undefined
}

watch(requestActive, (active) => {
  clearTimers()
  if (active) {
    showTimer = window.setTimeout(() => {
      visible.value = true
      shownAt = Date.now()
    }, 140)
    return
  }
  if (!visible.value) return
  hideTimer = window.setTimeout(() => { visible.value = false }, Math.max(0, 260 - (Date.now() - shownAt)))
}, { immediate: true })

onBeforeUnmount(clearTimers)
</script>

<template>
  <Transition name="request-progress">
    <div v-if="visible" class="request-progress" role="progressbar" aria-label="正在与服务器同步"><i></i></div>
  </Transition>
</template>

<style scoped>
.request-progress { position: fixed; z-index: 1200; top: 0; right: 0; left: 0; height: 3px; overflow: hidden; pointer-events: none; background: color-mix(in srgb, var(--accent) 14%, transparent); }
.request-progress i { width: 38%; height: 100%; display: block; border-radius: 999px; background: linear-gradient(90deg, transparent, var(--accent), var(--accent-strong)); animation: request-loading 1.05s ease-in-out infinite; }
@keyframes request-loading { from { transform: translateX(-110%); } to { transform: translateX(365%); } }
.request-progress-enter-active, .request-progress-leave-active { transition: opacity .16s ease; }.request-progress-enter-from, .request-progress-leave-to { opacity: 0; }
@media (prefers-reduced-motion: reduce) { .request-progress i { animation-duration: 2.2s; } }
</style>
