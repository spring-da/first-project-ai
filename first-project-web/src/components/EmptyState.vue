<script setup lang="ts">
import { Inbox } from 'lucide-vue-next'

withDefaults(defineProps<{ title: string; description: string; compact?: boolean }>(), { compact: false })
</script>

<template>
  <div class="empty-state" :class="{ compact }">
    <span><Inbox :size="22" /></span>
    <h3>{{ title }}</h3>
    <p>{{ description }}</p>
    <slot />
  </div>
</template>

<style scoped>
.empty-state { min-height: 210px; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: clamp(28px, 2vw, 36px); text-align: center; border: 1px dashed var(--border); border-radius: var(--radius-lg); background: color-mix(in srgb, var(--panel) 56%, transparent); }
.empty-state.compact { min-height: 150px; }
.empty-state > span { width: 40px; height: 40px; display: grid; place-items: center; color: var(--accent); border-radius: var(--radius-md); background: var(--accent-bg); }
.empty-state h3 { margin: 13px 0 0; font-size: var(--font-lg); }
.empty-state p { max-width: 390px; margin: 6px 0 16px; color: var(--muted); font-size: var(--font-sm); line-height: 1.65; }
@media (max-width: 720px) {
  .empty-state { min-height: 170px; padding: 26px 20px; }
  .empty-state.compact { min-height: 132px; }
}
</style>
