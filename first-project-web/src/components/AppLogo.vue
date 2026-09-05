<script setup lang="ts">
import { BookOpen } from 'lucide-vue-next'

withDefaults(defineProps<{ compact?: boolean }>(), { compact: false })
</script>

<template>
  <span class="app-logo" :class="{ compact }">
    <span class="app-logo__mark"><BookOpen :size="compact ? 17 : 21" :stroke-width="1.8" /></span>
    <span v-if="!compact" class="app-logo__copy">
      <strong>DevNest</strong>
      <small>个人知识空间</small>
    </span>
  </span>
</template>

<style scoped>
.app-logo { display: inline-flex; align-items: center; gap: clamp(12px, .65vw, 16px); }
.app-logo__mark { width: 38px; height: 38px; flex-shrink: 0; display: grid; place-items: center; color: #fff; background: var(--brand); border-radius: 11px; box-shadow: 0 4px 10px rgba(65,105,225,.16); }
.app-logo.compact .app-logo__mark { width: 34px; height: 34px; }
.app-logo__copy strong, .app-logo__copy small { display: block; }
.app-logo__copy strong { font-size: clamp(19px, 1.05vw, 24px); line-height: 1; letter-spacing: -.35px; }
.app-logo__copy small { margin-top: 7px; color: var(--muted); font-size: 10px; font-weight: 500; letter-spacing: .12em; white-space: nowrap; }
</style>
