<script setup lang="ts">
defineProps<{ eyebrow: string; title: string; description: string }>()
</script>

<template>
  <header class="page-header">
    <div><p class="eyebrow">{{ eyebrow }}</p><h1>{{ title }}</h1><p class="description">{{ description }}</p></div>
    <div class="page-header__actions"><slot /></div>
  </header>
</template>

<style scoped>
.page-header { display: flex; align-items: center; justify-content: space-between; gap: clamp(24px, 2vw, 36px); margin-bottom: clamp(24px, 1.5vw, 32px); }
.page-header > div:first-child { min-width: 0; max-width: 720px; }
.page-header h1 { margin: 0; font-size: var(--font-xl); line-height: 1.18; letter-spacing: -.65px; }
.description { margin: 8px 0 0; color: var(--muted); font-size: var(--font-sm); line-height: 1.65; }
.page-header__actions { display: flex; flex: 0 0 auto; align-items: center; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }
@media (max-width: 720px) {
  .page-header { align-items: stretch; flex-direction: column; gap: 16px; margin-bottom: 22px; }
  .page-header h1 { font-size: 26px; letter-spacing: -.5px; }
  .description { margin-top: 7px; line-height: 1.6; }
  .page-header__actions { display: grid; grid-template-columns: minmax(0, 1fr); justify-content: stretch; }
  .page-header__actions :deep(.button) { width: 100%; }
}
</style>
