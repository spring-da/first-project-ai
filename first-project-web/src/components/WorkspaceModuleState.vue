<script setup lang="ts">
import { AlertTriangle, LoaderCircle, RefreshCw } from 'lucide-vue-next'
import { computed } from 'vue'
import { useWorkspaceStore, type WorkspaceModuleKey } from '../stores/workspace'

const props = withDefaults(defineProps<{
  module: WorkspaceModuleKey
  title: string
  hasData?: boolean
  compact?: boolean
}>(), { hasData: false, compact: false })

const workspace = useWorkspaceStore()
const state = computed(() => workspace.moduleStates[props.module])
</script>

<template>
  <div v-if="state.error" class="module-state error" :class="{ compact }" role="alert">
    <AlertTriangle :size="compact ? 16 : 19" />
    <span><strong>{{ title }}加载失败</strong><small>{{ state.error }}{{ hasData ? '；当前仍显示上次成功加载的数据。' : '' }}</small></span>
    <button type="button" :disabled="state.loading" @click="workspace.loadModule(module)">
      <RefreshCw :size="15" :class="{ spin: state.loading }" />重试
    </button>
  </div>
  <div v-else-if="state.loading && !hasData" class="module-state loading" :class="{ compact }" role="status">
    <LoaderCircle class="spin" :size="compact ? 16 : 19" />
    <span><strong>正在加载{{ title }}</strong><small>其他模块会继续独立加载。</small></span>
  </div>
</template>

<style scoped>
.module-state { display: flex; align-items: center; gap: 11px; min-height: 74px; margin: 12px 0; padding: 14px 15px; color: var(--muted); border: 1px solid var(--border); border-radius: 11px; background: var(--surface-raised); }
.module-state.error { color: var(--danger); border-color: color-mix(in srgb, var(--danger) 27%, var(--border)); background: color-mix(in srgb, var(--danger) 5%, var(--panel)); }
.module-state.compact { min-height: 54px; margin-block: 8px; padding: 10px 11px; }
.module-state > svg { flex-shrink: 0; }
.module-state > span { min-width: 0; flex: 1; }
.module-state strong, .module-state small { display: block; }
.module-state strong { color: var(--text); font-size: var(--font-xs); }
.module-state small { margin-top: 4px; color: var(--muted); font-size: var(--font-2xs); line-height: 1.5; overflow-wrap: anywhere; }
.module-state button { display: inline-flex; flex-shrink: 0; align-items: center; gap: 6px; padding: 7px 9px; color: var(--accent); border: 1px solid var(--accent-border); border-radius: 8px; background: var(--accent-bg); cursor: pointer; font-size: var(--font-2xs); }
@media (max-width: 560px) { .module-state { align-items: flex-start; flex-wrap: wrap; } .module-state button { margin-left: 27px; } }
</style>
