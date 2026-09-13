<script setup lang="ts">
import { ref } from 'vue'
import { ArrowUpToLine, ArrowDownToLine, Copy, ClipboardPaste, Trash2, SlidersHorizontal, PanelRightClose } from 'lucide-vue-next'
import type { FlowNode, FlowEdge } from '../../types/flowcharts'
const props = defineProps<{ selected: FlowNode | FlowEdge | null; nodes: FlowNode[]; edges?: FlowEdge[] }>()
const emit = defineEmits<{ change: [cell: FlowNode | FlowEdge]; action: [name: 'copy' | 'paste' | 'delete' | 'front' | 'back']; select: [id: string]; close: [] }>()
const labelInput = ref<HTMLTextAreaElement>()
function update(key: string, value: unknown, style = false) {
  if (!props.selected) return
  const next = structuredClone(props.selected)
  if (style) Object.assign(next.style, { [key]: value }); else Object.assign(next, { [key]: value })
  emit('change', next)
}
const value = (event: Event) => (event.target as HTMLInputElement).value
const checked = (event: Event) => (event.target as HTMLInputElement).checked
defineExpose({ focusLabel: () => labelInput.value?.focus() })
</script>
<template>
  <aside class="flow-properties" aria-label="图形属性">
    <header><SlidersHorizontal :size="15" /><strong>属性</strong><span>{{ selected ? ('width' in selected ? '图形' : '连线') : '画布' }}</span><button type="button" class="flow-properties__close" aria-label="关闭属性面板" title="关闭属性面板" @click="emit('close')"><PanelRightClose :size="16" /></button></header>
    <label class="flow-field"><span>选择图形</span><select :value="selected?.id ?? ''" @change="emit('select', value($event))"><option value="">选择画布中的图形</option><optgroup v-if="nodes.length" label="图形"><option v-for="node in nodes" :key="node.id" :value="node.id">{{ node.label || '无文字图形' }}</option></optgroup><optgroup v-if="edges?.length" label="连线"><option v-for="edge in edges" :key="edge.id" :value="edge.id">{{ edge.label || '未命名连线' }}</option></optgroup></select></label>
    <template v-if="selected">
      <label class="flow-field"><span>文字</span><textarea ref="labelInput" :value="selected.label" maxlength="10000" rows="4" aria-label="图形或连线文字" @input="update('label', value($event))" /></label>
      <div class="flow-fields">
        <label class="flow-field"><span>文字颜色</span><input type="color" :value="selected.style.textColor.slice(0, 7)" @input="update('textColor', value($event), true)" /></label>
        <label class="flow-field"><span>字号</span><input type="number" :value="selected.style.fontSize" min="8" max="96" @change="update('fontSize', Number(value($event)), true)" /></label>
      </div>
      <template v-if="'width' in selected">
        <div class="flow-fields"><label class="flow-field"><span>填充色</span><input type="color" :value="selected.style.fill === 'transparent' ? '#ffffff' : selected.style.fill.slice(0, 7)" @input="update('fill', value($event), true)" /></label><label class="flow-field"><span>文字对齐</span><select :value="selected.style.textAlign" @change="update('textAlign', value($event), true)"><option value="left">左对齐</option><option value="center">居中</option><option value="right">右对齐</option></select></label></div>
        <label class="flow-check"><input type="checkbox" :checked="selected.style.fill === 'transparent'" @change="update('fill', checked($event) ? 'transparent' : '#eef2ff', true)" />透明填充</label>
        <div class="flow-fields"><label v-for="field in ['x', 'y', 'width', 'height'] as const" :key="field" class="flow-field"><span>{{ { x: '横坐标', y: '纵坐标', width: '宽度', height: '高度' }[field] }}</span><input type="number" :value="Math.round(selected[field])" :min="field === 'width' || field === 'height' ? 10 : -1000000" :max="field === 'width' || field === 'height' ? 10000 : 1000000" @change="update(field, Number(value($event)))" /></label></div>
      </template>
      <div class="flow-fields"><label class="flow-field"><span>线条颜色</span><input type="color" :value="selected.style.stroke.slice(0, 7)" @input="update('stroke', value($event), true)" /></label><label class="flow-field"><span>线条粗细</span><input type="number" :value="selected.style.strokeWidth" min="0" max="12" step="0.5" @change="update('strokeWidth', Number(value($event)), true)" /></label></div>
      <label class="flow-check"><input type="checkbox" :checked="selected.style.dash" @change="update('dash', checked($event), true)" />虚线</label>
      <template v-if="!('width' in selected)">
        <label class="flow-field"><span>连线方式</span><select :value="selected.kind" @change="update('kind', value($event))"><option value="orthogonal">折线</option><option value="straight">直线</option></select></label>
        <label class="flow-check"><input type="checkbox" :checked="selected.sourceArrow" @change="update('sourceArrow', checked($event))" />起点箭头</label><label class="flow-check"><input type="checkbox" :checked="selected.targetArrow" @change="update('targetArrow', checked($event))" />终点箭头</label>
      </template>
      <div class="flow-property-actions"><button type="button" @click="emit('action', 'front')"><ArrowUpToLine :size="15" />置于顶层</button><button type="button" @click="emit('action', 'back')"><ArrowDownToLine :size="15" />置于底层</button><button type="button" @click="emit('action', 'copy')"><Copy :size="15" />复制</button><button type="button" @click="emit('action', 'paste')"><ClipboardPaste :size="15" />粘贴</button><button class="danger" type="button" @click="emit('action', 'delete')"><Trash2 :size="15" />删除选中</button></div>
    </template>
    <div v-else class="flow-properties__empty"><SlidersHorizontal :size="28" /><p>选择一个图形或连线<br />在这里调整文字和样式</p><small>可从上方列表选择图形，<br />用方向键微调位置。</small></div>
  </aside>
</template>
<style scoped>
.flow-properties { position: relative; z-index: 1; width: 288px; flex: 0 0 288px; min-height: 0; overflow: auto; overscroll-behavior: contain; scrollbar-gutter: stable; padding: 16px 15px; border-left: 1px solid var(--flow-border, var(--border)); background: var(--flow-surface, var(--panel)); color: var(--flow-text, var(--text)); box-shadow: -8px 0 20px color-mix(in srgb, var(--flow-text, var(--text)) 8%, transparent); color-scheme: light; }
:global(:root:not([data-theme='light'])) .flow-properties { color-scheme: dark; }
.flow-properties header { position: sticky; top: -16px; z-index: 2; display: flex; align-items: center; gap: 7px; margin: -16px 0 18px; padding: 16px 0 12px; border-bottom: 1px solid var(--flow-border, var(--border)); color: var(--flow-text, var(--text)); background: var(--flow-surface, var(--panel)); font-size: 13px; }.flow-properties header span { margin-left: auto; color: var(--flow-muted, var(--muted)); font-size: 11px; }.flow-properties__close { width: 28px; height: 28px; display: grid; place-items: center; flex: 0 0 auto; padding: 0; color: var(--flow-muted, var(--muted)); border: 1px solid transparent; border-radius: 6px; background: transparent; cursor: pointer; transition: color .15s ease, border-color .15s ease, background .15s ease; }.flow-properties__close:hover { color: var(--flow-accent, var(--accent)); border-color: var(--flow-border, var(--border)); background: var(--flow-surface-alt, var(--surface-sunken)); }.flow-properties__close:focus-visible { outline: 2px solid var(--flow-accent, var(--accent)); outline-offset: 2px; }
.flow-field { display: flex; flex-direction: column; gap: 6px; margin-bottom: 13px; min-width: 0; font-size: 12px; color: var(--flow-muted, var(--subtle)); }.flow-field input,.flow-field select,.flow-field textarea { min-width: 0; width: 100%; padding: 7px 8px; border: 1px solid var(--flow-border, var(--border)); border-radius: 5px; color: var(--flow-text, var(--text)); background: var(--flow-surface-alt, var(--surface-sunken)); font: inherit; box-sizing: border-box; transition: border-color .15s ease, box-shadow .15s ease; }.flow-field input:focus,.flow-field select:focus,.flow-field textarea:focus { outline: none; border-color: var(--flow-accent, var(--accent)); box-shadow: 0 0 0 2px color-mix(in srgb, var(--flow-accent, var(--accent)) 16%, transparent); }.flow-field input[type=color] { height: 33px; padding: 3px; cursor: pointer; }.flow-field textarea { resize: vertical; line-height: 1.6; }
.flow-fields { display: grid; grid-template-columns: 1fr 1fr; gap: 9px; }.flow-check { display: flex; align-items: center; gap: 7px; margin-bottom: 15px; font-size: 12px; color: var(--flow-muted, var(--subtle)); }.flow-check input { accent-color: var(--flow-accent, var(--accent)); }.flow-property-actions { border-top: 1px solid var(--flow-border, var(--border)); padding-top: 13px; display: grid; grid-template-columns: 1fr 1fr; gap: 7px; }.flow-property-actions button { display: flex; align-items: center; justify-content: center; gap: 5px; min-height: 34px; padding: 7px 2px; font-size: 11px; background: var(--flow-surface, var(--panel)); color: var(--flow-muted, var(--subtle)); border: 1px solid var(--flow-border, var(--border)); border-radius: 5px; cursor: pointer; transition: color .15s ease, border-color .15s ease, background .15s ease; }.flow-property-actions button:hover { color: var(--flow-accent, var(--accent)); border-color: var(--flow-accent, var(--accent)); background: var(--flow-accent-soft, var(--accent-bg)); }.flow-property-actions button:focus-visible { outline: 2px solid var(--flow-accent, var(--accent)); outline-offset: 2px; }.flow-property-actions button:active { transform: translateY(1px); }.flow-property-actions .danger { color: var(--danger); }
.flow-properties__empty { max-width: 220px; margin-inline: auto; padding-top: 40px; text-align: center; color: var(--flow-muted, var(--muted)); line-height: 1.8; font-size: 12px; }.flow-properties__empty small { font-size: 11px; }
@media (max-width: 1100px) { .flow-properties { width: 244px; flex-basis: 244px; padding-inline: 12px; }.flow-properties header { top: -12px; margin-top: -12px; padding-top: 12px; } }
@media (prefers-reduced-motion: reduce) { .flow-properties__close, .flow-property-actions button, .flow-field input, .flow-field select, .flow-field textarea { transition-duration: 0.01ms; } }
</style>
