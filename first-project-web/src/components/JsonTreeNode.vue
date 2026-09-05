<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ChevronRight, ChevronDown } from 'lucide-vue-next'
import type { JsonNode } from '../utils/jsonStructure'

defineOptions({ name: 'JsonTreeNode' })

const props = defineProps<{
  name: string
  value: JsonNode
  path: string
  expanded: Set<string>
}>()

const emit = defineEmits<{ toggle: [path: string] }>()

const isContainer = computed(() => props.value.type === 'object' || props.value.type === 'array')
const isOpen = computed(() => props.expanded.has(props.path))
const isArray = computed(() => props.value.type === 'array')
const size = computed(() => props.value.children.length)
const visibleCount = ref(160)
watch(() => props.value, () => { visibleCount.value = 160 })

const displayInfo = computed<{ cls: string; text: string } | null>(() => {
  if (isContainer.value) return null
  const value = props.value
  const classes = { string: 'tok-string', number: 'tok-number', boolean: 'tok-boolean', null: 'tok-constant', array: '', object: '' }
  return { cls: classes[value.type], text: value.raw }
})

const preview = computed(() => {
  if (!isContainer.value) return ''
  if (size.value === 0) return isArray.value ? '[]' : '{}'
  return isArray.value ? `[ ${size.value} 项 ]` : `{ ${size.value} 项 }`
})
</script>

<template>
  <div class="json-node" :class="{ open: isOpen }">
    <button
      v-if="isContainer"
      type="button"
      class="json-node__row json-node__container"
      :aria-expanded="isOpen"
      @click="emit('toggle', path)"
    >
      <span class="json-node__chevron"><ChevronRight v-if="!isOpen" :size="13" /><ChevronDown v-else :size="13" /></span>
      <span v-if="name !== ''" class="tok-property json-node__key">{{ name }}<span class="json-node__colon">: </span></span>
      <span class="json-node__preview" :class="{ empty: size === 0 }">{{ preview }}</span>
    </button>

    <div v-else class="json-node__row json-node__leaf">
      <span v-if="name !== ''" class="tok-property json-node__key">{{ name }}<span class="json-node__colon">: </span></span>
      <span v-if="displayInfo" class="json-node__value" :class="displayInfo.cls">{{ displayInfo.text }}</span>
    </div>

    <div v-if="isContainer && isOpen && size" class="json-node__children">
      <JsonTreeNode
        v-for="(child, index) in value.children.slice(0, visibleCount)"
        :key="index"
        :name="isArray ? `[${index}]` : JSON.stringify(child.key)"
        :value="child"
        :path="`${path}/${index}`"
        :expanded="expanded"
        @toggle="(path) => emit('toggle', path)"
      />
      <button v-if="value.children.length > visibleCount" class="json-node__more" type="button" @click="visibleCount += 160">显示更多（剩余 {{ value.children.length - visibleCount }} 项）</button>
    </div>
  </div>
</template>

<style scoped>
.json-node { min-width: 0; }
.json-node__row { display: flex; align-items: baseline; gap: 6px; min-width: 0; padding: 1px 0; text-align: left; border: 0; background: transparent; color: var(--code-text); font-family: "Cascadia Code", Consolas, monospace; font-size: 12px; line-height: 1.7; }
.json-node__container { cursor: pointer; border-radius: 5px; }
.json-node__container:hover { background: color-mix(in srgb, var(--surface-raised) 80%, transparent); }
.json-node__chevron { flex: 0 0 auto; display: grid; place-items: center; align-self: center; width: 15px; height: 15px; color: var(--muted); }
.json-node__key { flex: 0 0 auto; overflow-wrap: anywhere; }
.json-node__colon { color: var(--muted); }
.json-node__preview { color: var(--muted); font-style: italic; }
.json-node__preview.empty { color: var(--muted); font-style: normal; }
.json-node__value { min-width: 0; overflow-wrap: anywhere; }
.json-node__children { margin-left: 14px; padding-left: 8px; border-left: 1px solid color-mix(in srgb, var(--border) 70%, transparent); }
.json-node__more { margin: 5px 0 7px; padding: 5px 8px; color: var(--accent); border: 1px dashed var(--accent-border); border-radius: 6px; background: transparent; cursor: pointer; font-size: 11px; }
</style>
