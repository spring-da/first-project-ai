<script setup lang="ts">
import { computed, ref } from 'vue'
import { Check, ChevronsUpDown } from 'lucide-vue-next'

const props = withDefaults(defineProps<{
  modelValue: string
  options: { id: string; label: string }[]
  ariaLabel?: string
  placeholder?: string
  required?: boolean
}>(), { ariaLabel: '代码语言', placeholder: '', required: false })

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const open = ref(false)
const query = ref('')
const highlight = ref(0)
const input = ref<HTMLInputElement | null>(null)

const filtered = computed(() => {
  const q = query.value.trim().toLocaleLowerCase()
  if (!q) return props.options
  return props.options.filter(
    (option) => option.label.toLocaleLowerCase().includes(q) || option.id.toLocaleLowerCase().includes(q),
  )
})

const displayValue = computed(() => (open.value ? query.value : props.modelValue))

function openList() {
  open.value = true
  query.value = props.modelValue
  const index = filtered.value.findIndex((option) => option.label === props.modelValue)
  highlight.value = index >= 0 ? index : 0
}

function choose(label: string) {
  emit('update:modelValue', label)
  open.value = false
  query.value = ''
}

function commit() {
  const target = filtered.value[highlight.value]
  choose(target ? target.label : query.value.trim() || props.modelValue)
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Tab' || event.key === 'Escape') {
    open.value = false
    query.value = ''
    if (event.key === 'Escape') input.value?.blur()
    return
  }
  if (!open.value) {
    if (event.key === 'ArrowDown' || event.key === 'Enter') {
      event.preventDefault()
      openList()
    }
    return
  }
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    highlight.value = (highlight.value + 1) % Math.max(1, filtered.value.length)
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    highlight.value = (highlight.value - 1 + filtered.value.length) % Math.max(1, filtered.value.length)
  } else if (event.key === 'Enter') {
    event.preventDefault()
    commit()
  }
}

function onInput(event: Event) {
  query.value = (event.target as HTMLInputElement).value
  highlight.value = 0
}

function onBlur() {
  open.value = false
  query.value = ''
}
</script>

<template>
  <div class="language-select">
    <input
      ref="input"
      class="language-select__input"
      :value="displayValue"
      :aria-label="ariaLabel"
      :placeholder="placeholder"
      :required="required"
      role="combobox"
      aria-autocomplete="list"
      :aria-expanded="open"
      autocomplete="off"
      spellcheck="false"
      @focus="openList"
      @input="onInput"
      @keydown="onKeydown"
      @blur="onBlur"
    />
    <ChevronsUpDown class="language-select__chevron" :size="14" aria-hidden="true" />
    <div v-if="open" class="language-select__list" role="listbox">
      <button
        v-for="(option, index) in filtered"
        :key="option.id"
        type="button"
        role="option"
        :aria-selected="option.label === modelValue"
        :class="{ active: index === highlight, selected: option.label === modelValue }"
        @mousedown.prevent="choose(option.label)"
        @mouseenter="highlight = index"
      >
        <span>{{ option.label }}</span>
        <Check v-if="option.label === modelValue" :size="14" aria-hidden="true" />
      </button>
      <p v-if="!filtered.length" class="language-select__empty">没有匹配的语言，回车可自定义</p>
    </div>
  </div>
</template>

<style scoped>
.language-select {
  position: relative;
  min-width: 0;
}
.language-select__input {
  width: clamp(120px, 22vw, 200px);
  height: 32px;
  padding: 0 28px 0 9px;
  color: var(--text);
  border: 1px solid var(--border);
  border-radius: 7px;
  outline: 0;
  background: var(--panel);
  font-size: var(--font-xs);
}
.language-select__input:focus {
  border-color: var(--accent);
  box-shadow: 0 0 0 2px var(--accent-soft);
}
.language-select__input::placeholder {
  color: var(--muted);
}
.language-select__chevron {
  position: absolute;
  top: 50%;
  right: 8px;
  transform: translateY(-50%);
  color: var(--muted);
  pointer-events: none;
}
.language-select__list {
  position: absolute;
  z-index: 30;
  top: calc(100% + 6px);
  left: 0;
  width: max(240px, min(320px, 62vw));
  max-height: 264px;
  overflow-y: auto;
  padding: 6px;
  border: 1px solid var(--border-strong);
  border-radius: 10px;
  background: var(--modal-bg);
  box-shadow: var(--shadow-md);
}
.language-select__list button {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 7px 9px;
  color: var(--text);
  border: 0;
  border-radius: 7px;
  background: transparent;
  cursor: pointer;
  font-size: var(--font-xs);
  text-align: left;
}
.language-select__list button.active {
  color: var(--accent);
  background: var(--accent-bg);
}
.language-select__list button.selected svg {
  flex: 0 0 auto;
  color: var(--accent);
}
.language-select__list button.selected:not(.active) svg {
  color: var(--accent);
}
.language-select__empty {
  margin: 0;
  padding: 10px;
  color: var(--muted);
  font-size: var(--font-2xs);
}
</style>
