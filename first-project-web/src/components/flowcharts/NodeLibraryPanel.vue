<script setup lang="ts">
import { computed, ref } from 'vue'
import { ChevronLeft, ChevronRight, Search } from 'lucide-vue-next'
import type { FlowNodeKind } from '../../types/flowcharts'
import { FLOWCHART_SHAPES, filterFlowchartShapes, type FlowchartShapeCategory } from '../../utils/flowchartEditor'
import { visibleRecentKinds } from '../../utils/flowchartEditorState'

const props = defineProps<{ collapsed: boolean; recentKinds: FlowNodeKind[] }>()
const dispatch = defineEmits<{ create: [kind: FlowNodeKind]; 'drag-start': [kind: FlowNodeKind, event: DragEvent]; toggle: [] }>()

const query = ref('')
const category = ref<FlowchartShapeCategory>('all')
const categories: Array<{ key: FlowchartShapeCategory; label: string }> = [
  { key: 'all', label: '全部' }, { key: 'flow', label: '流程' }, { key: 'basic', label: '基础' }, { key: 'data', label: '数据' }, { key: 'annotation', label: '标注' },
]
const shapes = computed(() => filterFlowchartShapes(query.value, category.value))
const recentShapes = computed(() => visibleRecentKinds(props.recentKinds)
  .map(kind => FLOWCHART_SHAPES.find(shape => shape.kind === kind))
  .filter((shape): shape is typeof FLOWCHART_SHAPES[number] => !!shape))

function emit(event: 'create', kind: FlowNodeKind): void
function emit(event: 'drag-start', kind: FlowNodeKind, nativeEvent: DragEvent): void
function emit(event: 'create' | 'drag-start', kind: FlowNodeKind, nativeEvent?: DragEvent) {
  if (event === 'drag-start' && nativeEvent) return dragStart(kind, nativeEvent)
  dispatch('create', kind)
}

function dragStart(kind: FlowNodeKind, event: DragEvent) {
  event.dataTransfer?.setData('application/x-devnest-flow-node', kind)
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'copy'
  dispatch('drag-start', kind, event)
}
</script>

<template>
  <aside class="node-library" :class="{ 'node-library--collapsed': collapsed }" aria-label="图形库">
    <button v-if="collapsed" type="button" class="node-library__toggle" aria-label="展开节点库" title="展开节点库" @click="dispatch('toggle')"><ChevronRight :size="16" /></button>
    <template v-else>
      <header class="node-library__heading"><div><h2>图形库</h2><p>拖入画布，或点击添加</p></div><button type="button" class="node-library__toggle" aria-label="折叠节点库" title="折叠节点库" @click="dispatch('toggle')"><ChevronLeft :size="16" /></button></header>
      <label class="node-library__search"><Search :size="14" aria-hidden="true" /><input v-model="query" type="search" placeholder="搜索图形" aria-label="搜索图形" /></label>
      <div class="node-library__tabs" role="tablist" aria-label="图形分类"><button v-for="item in categories" :key="item.key" type="button" role="tab" :aria-selected="category === item.key" :class="{ active: category === item.key }" @click="category = item.key">{{ item.label }}</button></div>
      <section v-if="recentShapes.length" class="node-library__recent" aria-label="最近使用"><h3>最近使用</h3><div class="node-library__recent-list"><button v-for="shape in recentShapes" :key="shape.kind" type="button" :title="shape.hint" @click="emit('create', shape.kind)">{{ shape.label }}</button></div></section>
      <div v-if="shapes.length" class="node-library__grid"><button v-for="shape in shapes" :key="shape.kind" type="button" draggable="true" :aria-label="`添加${shape.label}`" :title="shape.hint" @click="emit('create', shape.kind)" @dragstart="emit('drag-start', shape.kind, $event)"><strong>{{ shape.label }}</strong><span>{{ shape.hint }}</span></button></div>
      <p v-else class="node-library__empty">没有匹配的图形</p>
    </template>
  </aside>
</template>

<style scoped>
.node-library { width: 212px; flex: 0 0 212px; min-height: 0; padding: 16px 12px; overflow: auto; overscroll-behavior: contain; border-right: 1px solid var(--flow-border); background: var(--flow-surface); color: var(--flow-text); }
.node-library--collapsed { width: 44px; flex-basis: 44px; padding: 6px; overflow: hidden; }
.node-library__heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; margin-bottom: 12px; }.node-library h2,.node-library h3 { margin: 0; color: var(--flow-text); }.node-library h2 { font-size: 12px; }.node-library h3 { font-size: 10px; }.node-library p { margin: 4px 0 0; font-size: 10px; color: var(--flow-muted); }
.node-library__toggle { display: grid; place-items: center; width: 32px; height: 32px; padding: 0; border: 0; border-radius: 5px; background: transparent; color: var(--flow-muted); cursor: pointer; }.node-library__toggle:hover { color: var(--flow-accent); background: var(--flow-accent-soft); }.node-library__toggle:focus-visible,.node-library button:focus-visible { outline: 2px solid var(--flow-accent); outline-offset: 2px; }
.node-library__search { display: flex; align-items: center; gap: 7px; height: 32px; margin-bottom: 10px; padding: 0 9px; border: 1px solid var(--flow-border); border-radius: 6px; background: var(--flow-surface-alt); color: var(--flow-muted); }.node-library__search:focus-within { border-color: var(--flow-accent); box-shadow: 0 0 0 2px color-mix(in srgb, var(--flow-accent) 14%, transparent); }.node-library__search input { width: 100%; min-width: 0; border: 0; outline: 0; background: transparent; color: var(--flow-text); font: inherit; font-size: 11px; }
.node-library__tabs { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 2px; margin-bottom: 12px; padding: 2px; border-radius: 6px; background: var(--flow-surface-alt); }.node-library__tabs button { min-height: 28px; padding: 4px 2px; border: 0; border-radius: 4px; background: transparent; color: var(--flow-muted); font-size: 10px; cursor: pointer; }.node-library__tabs button.active { background: var(--flow-surface); color: var(--flow-accent); box-shadow: 0 1px 3px color-mix(in srgb, var(--flow-text) 10%, transparent); }
.node-library__recent { margin: 0 0 12px; }.node-library__recent-list { display: flex; flex-wrap: wrap; gap: 5px; margin-top: 7px; }.node-library__recent-list button { border: 1px solid var(--flow-border); border-radius: 4px; padding: 5px 6px; background: var(--flow-surface-alt); color: var(--flow-text); font-size: 10px; cursor: pointer; }.node-library__recent-list button:hover { border-color: var(--flow-accent); color: var(--flow-accent); }
.node-library__grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 6px; }.node-library__grid button { display: flex; flex-direction: column; align-items: stretch; min-height: 68px; gap: 4px; padding: 8px; border: 1px solid var(--flow-border); border-radius: 6px; background: var(--flow-surface); color: var(--flow-text); text-align: left; cursor: grab; }.node-library__grid button:hover { border-color: #8bbcff; background: var(--flow-accent-soft); }.node-library__grid strong { font-size: 10px; }.node-library__grid span { color: var(--flow-muted); font-size: 9px; line-height: 1.35; }.node-library__empty { margin: 24px 0; text-align: center; }
@media (max-width: 1100px) { .node-library { width: 154px; flex-basis: 154px; padding: 14px 10px; }.node-library__tabs { grid-template-columns: repeat(3, minmax(0, 1fr)); }.node-library__grid { grid-template-columns: 1fr; }.node-library--collapsed { width: 44px; flex-basis: 44px; } }
</style>
