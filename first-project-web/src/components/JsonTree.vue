<script setup lang="ts">
import { computed, ref } from 'vue'
import { ChevronsDownUp, ChevronsUpDown, Braces } from 'lucide-vue-next'
import JsonTreeNode from './JsonTreeNode.vue'
import { parseJsonSource } from '../utils/codeJson'
import { buildJsonTree, countJsonNodes, type JsonNode } from '../utils/jsonStructure'

const props = withDefaults(defineProps<{ source: string; fill?: boolean }>(), { fill: false })

const parsed = computed(() => parseJsonSource(props.source))
const root = computed(() => {
  if (!parsed.value.valid) return null
  try { return buildJsonTree(parsed.value.text, true) } catch { return null }
})
const valid = computed(() => root.value !== null)
const nodeCount = computed(() => root.value ? countJsonNodes(root.value, 2_001) : 0)
const canExpandAll = computed(() => nodeCount.value <= 2_000)
const expanded = ref(new Set(['']))

const rootSummary = computed(() => {
  const value = root.value
  if (value?.type === 'array') return `数组 · ${value.children.length} 项`
  if (value?.type === 'object') return `对象 · ${value.children.length} 个键`
  return value?.type ?? ''
})

function collectPaths(value: JsonNode, prefix = ''): string[] {
  const paths: string[] = []
  if (value.type === 'object' || value.type === 'array') {
    paths.push(prefix)
    value.children.forEach((child, index) => paths.push(...collectPaths(child, `${prefix}/${index}`)))
  }
  return paths
}

function expandAll() {
  if (root.value === null || !canExpandAll.value) return
  expanded.value = new Set(collectPaths(root.value))
}

function collapseAll() {
  expanded.value = new Set()
}

function toggle(path: string) {
  const next = new Set(expanded.value)
  next.has(path) ? next.delete(path) : next.add(path)
  expanded.value = next
}
</script>

<template>
  <div class="json-tree" :class="{ 'json-tree--fill': fill }">
    <div v-if="valid" class="json-tree__bar">
      <span class="json-tree__meta"><Braces :size="14" />{{ rootSummary }}</span>
      <span class="json-tree__actions">
        <button type="button" :disabled="!canExpandAll" :title="canExpandAll ? '展开全部' : '字段过多，请按需展开'" @click="expandAll"><ChevronsDownUp :size="14" />{{ canExpandAll ? '展开全部' : '大型结构' }}</button>
        <button type="button" @click="collapseAll"><ChevronsUpDown :size="14" />折叠全部</button>
      </span>
    </div>
    <div v-if="valid" class="json-tree__body">
      <JsonTreeNode v-if="root" name="" :value="root" path="" :expanded="expanded" @toggle="toggle" />
    </div>
    <p v-else class="json-tree__invalid">内容不是有效的 JSON，暂时无法折叠查看。</p>
  </div>
</template>

<style scoped>
.json-tree {
  min-width: 0;
  margin-top: 16px;
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: var(--code-bg);
}
.json-tree--fill {
  margin-top: 0;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 0;
  border-radius: 0;
}
.json-tree--fill .json-tree__body {
  flex: 1;
  min-height: 0;
  max-height: none;
}
.json-tree__bar {
  min-height: 38px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 12px;
  color: var(--muted);
  border-bottom: 1px solid var(--border);
  background: color-mix(in srgb, var(--surface-sunken) 55%, var(--panel));
  font-size: var(--font-2xs);
}
.json-tree__meta {
  display: flex;
  align-items: center;
  gap: 6px;
}
.json-tree__actions {
  display: flex;
  gap: 2px;
}
.json-tree__actions button {
  height: 26px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 0 9px;
  color: var(--muted);
  border: 1px solid transparent;
  border-radius: 7px;
  background: transparent;
  cursor: pointer;
  font-size: 11px;
  white-space: nowrap;
}
.json-tree__actions button:hover {
  color: var(--accent);
  border-color: var(--accent-border);
  background: var(--accent-bg);
}
.json-tree__actions button:disabled { opacity: .55; cursor: not-allowed; }
.json-tree__body {
  max-height: min(62vh, 640px);
  overflow: auto;
  padding: 14px 16px 18px;
  overscroll-behavior: auto;
}
.json-tree__placeholder {
  width: 100%;
  padding: 14px;
  color: var(--accent);
  border: 1px dashed var(--accent-border);
  border-radius: 9px;
  background: transparent;
  cursor: pointer;
  font-size: var(--font-2xs);
}
.json-tree__invalid {
  margin: 0;
  padding: 16px;
  color: var(--muted);
  font-size: var(--font-xs);
}
</style>
