<script setup lang="ts">
import { ref } from 'vue'
import { ChevronRight } from 'lucide-vue-next'
import type { MarkdownHeadingTreeNode } from '../utils/markdown'

withDefaults(defineProps<{
  nodes: MarkdownHeadingTreeNode[]
  activeId: string
  root?: boolean
}>(), { root: true })

const emit = defineEmits<{ select: [id: string] }>()
const collapsedIds = ref(new Set<string>())

function isCollapsed(id: string) {
  return collapsedIds.value.has(id)
}

function toggle(id: string) {
  const next = new Set(collapsedIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  collapsedIds.value = next
}
</script>

<template>
  <ul class="outline-tree" :class="{ 'outline-tree--root': root }" :role="root ? 'tree' : 'group'">
    <li
      v-for="node in nodes"
      :key="node.id"
      role="treeitem"
      :aria-current="activeId === node.id ? 'location' : undefined"
      :aria-expanded="node.children.length ? !isCollapsed(node.id) : undefined"
    >
      <div class="outline-tree__row">
        <button
          v-if="node.children.length"
          class="outline-tree__branch"
          type="button"
          :aria-label="isCollapsed(node.id) ? `展开“${node.text}”的子标题` : `收起“${node.text}”的子标题`"
          :title="isCollapsed(node.id) ? '展开子标题' : '收起子标题'"
          @click="toggle(node.id)"
        >
          <ChevronRight :size="14" :class="{ expanded: !isCollapsed(node.id) }" />
        </button>
        <span v-else class="outline-tree__spacer" aria-hidden="true"></span>
        <button
          class="outline-tree__label"
          :class="{ active: activeId === node.id }"
          type="button"
          :title="node.text"
          @click="emit('select', node.id)"
        >{{ node.text }}</button>
      </div>
      <MarkdownOutlineTree
        v-if="node.children.length && !isCollapsed(node.id)"
        :nodes="node.children"
        :active-id="activeId"
        :root="false"
        @select="emit('select', $event)"
      />
    </li>
  </ul>
</template>

<style scoped>
.outline-tree { display: grid; gap: 2px; margin: 0; padding: 2px 0 2px 14px; list-style: none; border-left: 1px solid var(--border); }
.outline-tree--root { padding: 10px 0 0; border-left: 0; }
.outline-tree__row { min-width: 0; display: grid; grid-template-columns: 24px minmax(0, 1fr); align-items: center; }
.outline-tree__branch, .outline-tree__label { border: 0; background: transparent; cursor: pointer; }
.outline-tree__branch { width: 24px; height: 30px; display: grid; place-items: center; padding: 0; color: var(--muted); border-radius: 6px; }
.outline-tree__branch:hover { color: var(--accent); background: var(--accent-bg); }
.outline-tree__branch svg { transition: transform var(--motion-fast); }
.outline-tree__branch svg.expanded { transform: rotate(90deg); }
.outline-tree__spacer { width: 24px; }
.outline-tree__label { min-width: 0; min-height: 34px; overflow: hidden; padding: 7px 9px 7px 4px; color: var(--muted); border-radius: 7px; font-size: 11px; line-height: 1.45; text-align: left; text-overflow: ellipsis; white-space: nowrap; }
.outline-tree__label:hover, .outline-tree__label.active { color: var(--accent); background: var(--accent-bg); }
</style>
