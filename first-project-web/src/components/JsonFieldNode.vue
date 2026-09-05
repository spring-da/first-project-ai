<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ChevronRight, Plus, Trash2 } from 'lucide-vue-next'
import type { JsonEdit, JsonNode, JsonType } from '../utils/jsonStructure'

defineOptions({ name: 'JsonFieldNode' })
const props = defineProps<{ node: JsonNode; path: number[]; label: string; siblings?: string[]; expansion: { open: boolean; revision: number } }>()
const emit = defineEmits<{ edit: [path: number[], edit: JsonEdit] }>()
const open = ref(props.path.length === 0 ? true : props.expansion.open)
const draft = ref('')
const keyDraft = ref('')
const error = ref('')
const valueInput = ref<HTMLInputElement | HTMLTextAreaElement | null>(null)
const keyInput = ref<HTMLInputElement | null>(null)
const visibleCount = ref(80)
const childNames = computed(() => props.node.children.flatMap(item => item.key === undefined ? [] : [item.key]))
const container = computed(() => props.node.type === 'object' || props.node.type === 'array')
const defaults: Record<JsonType, string> = { object: '{}', array: '[]', string: '""', number: '0', boolean: 'false', null: 'null' }
watch(() => props.expansion, (value) => { open.value = value.open })
watch(() => props.node.children.length, () => { visibleCount.value = 80 })
watch(() => props.node.raw, () => { draft.value = props.node.type === 'string' ? JSON.parse(props.node.raw) as string : props.node.raw; error.value = ''; valueInput.value?.setCustomValidity('') }, { immediate: true })
watch(() => props.node.key, (value) => { keyDraft.value = value ?? ''; keyInput.value?.setCustomValidity('') }, { immediate: true })

function editValue(event: Event) {
  const input = event.target as HTMLInputElement
  if ((event as InputEvent).isComposing) return
  const raw = props.node.type === 'string' ? JSON.stringify(draft.value) : draft.value
  const valid = props.node.type !== 'number' || /^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?$/.test(raw)
  error.value = valid ? '' : '请输入完整的 JSON 数字'
  input.setCustomValidity(error.value)
  if (valid) emit('edit', props.path, { kind: 'value', value: raw })
}

function rename(event: Event) {
  const input = event.target as HTMLInputElement
  if ((event as InputEvent).isComposing) return
  const duplicate = props.siblings?.includes(keyDraft.value) && keyDraft.value !== props.node.key
  error.value = duplicate ? '已存在同名字段' : ''
  input.setCustomValidity(error.value)
  if (!duplicate) emit('edit', props.path, { kind: 'rename', name: keyDraft.value })
}

function changeType(event: Event) {
  error.value = ''
  emit('edit', props.path, { kind: 'value', value: defaults[(event.target as HTMLSelectElement).value as JsonType] })
}
</script>

<template>
  <div class="json-field">
    <div class="json-field__row" :class="{ 'is-container': container }">
      <button v-if="container" class="json-field__toggle" type="button" :aria-label="`${open ? '折叠' : '展开'} ${label}`" :aria-expanded="open" @click="open = !open"><ChevronRight :size="14" :class="{ open }" /></button>
      <span v-else class="json-field__spacer"></span>
      <input v-if="node.key !== undefined" ref="keyInput" v-model="keyDraft" class="json-field__key" :aria-label="`字段名 ${label}`" spellcheck="false" @input="rename" @keydown.enter.prevent />
      <span v-else class="json-field__index">{{ path.length ? `[${path[path.length - 1]}]` : '$' }}</span>
      <select :value="node.type" class="json-field__type" :class="`type-${node.type}`" :aria-label="`类型 ${label}`" @change="changeType">
        <option v-for="(_, type) in defaults" :key="type" :value="type">{{ type }}</option>
      </select>
      <span v-if="container" class="json-field__count">{{ node.children.length }} {{ node.type === 'object' ? '个字段' : '项' }}</span>
      <select v-else-if="node.type === 'boolean'" class="json-field__value" :value="node.raw" :aria-label="`值 ${label}`" @change="emit('edit', path, { kind: 'value', value: ($event.target as HTMLSelectElement).value })"><option value="true">true</option><option value="false">false</option></select>
      <span v-else-if="node.type === 'null'" class="json-field__count">null</span>
      <textarea v-else-if="node.type === 'string'" ref="valueInput" v-model="draft" class="json-field__value" rows="1" :aria-label="`值 ${label}`" spellcheck="false" @input="editValue"></textarea>
      <input v-else ref="valueInput" v-model="draft" class="json-field__value" :aria-label="`值 ${label}`" spellcheck="false" @input="editValue" @keydown.enter.prevent />
      <div class="json-field__actions">
        <button v-if="container" type="button" :aria-label="`添加字段 ${label}`" title="添加字段" @click="open = true; emit('edit', path, { kind: 'add' })"><Plus :size="14" /></button>
        <button v-if="path.length" type="button" class="remove" :aria-label="`删除字段 ${label}`" title="删除字段（可撤销）" @click="emit('edit', path, { kind: 'remove' })"><Trash2 :size="13" /></button>
      </div>
    </div>
    <p v-if="error" class="json-field__error" role="status">{{ error }}</p>
    <div v-if="container && open" class="json-field__children">
      <JsonFieldNode v-for="(child, index) in node.children.slice(0, visibleCount)" :key="index" :node="child" :path="[...path, index]" :label="node.type === 'array' ? `${label}[${index}]` : `${label}.${child.key}`" :siblings="childNames" :expansion="expansion" @edit="(target, edit) => emit('edit', target, edit)" />
      <button v-if="node.children.length > visibleCount" class="json-field__empty" type="button" @click="visibleCount += 80">显示更多（剩余 {{ node.children.length - visibleCount }} 项）</button>
      <button v-if="!node.children.length" class="json-field__empty" type="button" @click="emit('edit', path, { kind: 'add' })"><Plus :size="13" />{{ node.type === 'object' ? '添加第一个字段' : '添加第一个数组项' }}</button>
    </div>
  </div>
</template>

<style scoped>
.json-field { min-width: 0; }
.json-field__row { display: flex; align-items: center; gap: 6px; min-height: 38px; padding: 3px 5px 3px 0; border-bottom: 1px solid color-mix(in srgb, var(--border) 55%, transparent); }
.json-field__row:hover, .json-field__row:focus-within { background: color-mix(in srgb, var(--accent-bg) 45%, transparent); }
.json-field__toggle, .json-field__actions button { display: grid; place-items: center; width: 24px; height: 27px; flex-shrink: 0; border: 0; border-radius: 5px; background: transparent; color: var(--muted); cursor: pointer; }
.json-field__toggle svg { transition: transform .15s; }
.json-field__toggle svg.open { transform: rotate(90deg); }
.json-field__spacer { width: 24px; flex-shrink: 0; }
.json-field__key, .json-field__index { width: 130px; min-width: 60px; flex: 1; color: var(--accent); }
.json-field input, .json-field textarea, .json-field select { padding: 5px 6px; border: 1px solid transparent; border-radius: 5px; background: transparent; font: 12px/1.5 "Cascadia Code", Consolas, monospace; }
.json-field input:hover, .json-field textarea:hover, .json-field select:hover { border-color: var(--border); }
.json-field input:focus, .json-field textarea:focus, .json-field select:focus { outline: 2px solid var(--accent-border); outline-offset: -1px; background: var(--panel); }
.json-field__type { width: 83px; flex: 0 0 83px; color: var(--violet); }
.json-field__type.type-string { color: var(--success); }
.json-field__type.type-number { color: var(--accent); }
.json-field select option { color: var(--text); background: var(--panel); }
.json-field__value { width: 150px; min-width: 65px; flex: 1.4; color: var(--code-text); resize: vertical; }
.json-field__count { width: 150px; min-width: 65px; flex: 1.4; color: var(--muted); font: 11px/1.5 "Cascadia Code", Consolas, monospace; }
.json-field__actions { width: 52px; display: flex; justify-content: flex-end; flex-shrink: 0; }
.json-field__actions button:hover, .json-field__toggle:hover { background: var(--accent-bg); color: var(--accent); }
.json-field__actions button.remove:hover { color: var(--danger); }
.json-field__children { margin-left: 12px; padding-left: 8px; border-left: 1px solid var(--border); }
.json-field__empty { display: flex; align-items: center; gap: 5px; margin: 6px 0 8px 25px; padding: 6px; color: var(--accent); border: 1px dashed var(--accent-border); border-radius: 6px; background: transparent; cursor: pointer; font-size: 11px; }
.json-field__error { margin: 3px 0 3px 30px; color: var(--danger); font-size: 11px; }
@media (max-width: 600px) {
  .json-field__row { flex-wrap: wrap; gap: 4px; }
  .json-field__key, .json-field__index { width: 70px; }
  .json-field__value, .json-field__count { flex-basis: calc(100% - 90px); margin-left: 28px; }
  .json-field__children { margin-left: 4px; padding-left: 3px; }
}
</style>
