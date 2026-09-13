<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId } from 'vue'
import { Check, ChevronDown, Globe2, ListFilter } from 'lucide-vue-next'
import type { SearchScope } from '../stores/search'

const props = defineProps<{ modelValue: SearchScope }>()
const emit = defineEmits<{
  'update:modelValue': [value: SearchScope]
  change: []
  'open-change': [open: boolean]
}>()
const options = [
  { value: 'page' as const, label: '当前列表', description: '只筛选正在查看的内容', icon: ListFilter },
  { value: 'global' as const, label: '全局', description: '搜索项目、文章、代码与流程图', icon: Globe2 },
]
const selected = computed(() => options.find((option) => option.value === props.modelValue)!)
const root = ref<HTMLElement | null>(null)
const trigger = ref<HTMLButtonElement | null>(null)
const isOpen = ref(false)
const activeIndex = ref(0)
const listId = `search-scope-${useId()}`

function focusOption(index: number) {
  activeIndex.value = index
  nextTick(() => root.value?.querySelectorAll<HTMLButtonElement>('[role="option"]')[index]?.focus())
}

function open() {
  isOpen.value = true
  emit('open-change', true)
  focusOption(options.findIndex((option) => option.value === props.modelValue))
}

function close(restoreFocus = false) {
  if (!isOpen.value) return
  isOpen.value = false
  emit('open-change', false)
  if (restoreFocus) trigger.value?.focus()
}

function select(value: SearchScope) {
  emit('update:modelValue', value)
  close(true)
  emit('change')
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Tab' && isOpen.value) {
    // Restore the trigger before the browser advances to the next/previous control.
    close(true)
    return
  }
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    event.stopPropagation()
    if (!isOpen.value) open()
    else if (event.target === trigger.value) close(true)
    else select(options[activeIndex.value]!.value)
    return
  }
  if (event.key === 'Escape' && isOpen.value) {
    event.preventDefault()
    event.stopPropagation()
    close(true)
    return
  }
  if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  event.stopPropagation()
  if (!isOpen.value) { open(); return }
  const index = event.key === 'Home' ? 0 : event.key === 'End' ? options.length - 1
    : (activeIndex.value + (event.key === 'ArrowDown' ? 1 : options.length - 1)) % options.length
  focusOption(index)
}

function onFocusOut(event: FocusEvent) {
  if (!root.value?.contains(event.relatedTarget as Node | null)) close()
}
function onPointerDown(event: PointerEvent) {
  if (!root.value?.contains(event.target as Node)) close()
}
onMounted(() => window.addEventListener('pointerdown', onPointerDown))
onBeforeUnmount(() => window.removeEventListener('pointerdown', onPointerDown))
</script>

<template>
  <div ref="root" class="scope-select" @keydown="onKeydown" @focusout="onFocusOut">
    <button ref="trigger" class="scope-trigger" :class="{ open: isOpen }" type="button" :title="`搜索范围：${selected.label}`" :aria-label="`搜索范围：${selected.label}`" aria-haspopup="listbox" :aria-expanded="isOpen" :aria-controls="isOpen ? listId : undefined" @click="isOpen ? close() : open()">
      <component :is="selected.icon" class="scope-trigger__compact-icon" :size="16" aria-hidden="true" /><span>{{ selected.label }}</span><ChevronDown :size="13" :class="{ rotated: isOpen }" />
    </button>
    <Transition name="scope-menu">
      <div v-if="isOpen" :id="listId" class="scope-menu" role="listbox" aria-label="选择搜索范围">
        <div class="scope-caption" aria-hidden="true">搜索范围</div>
        <button v-for="(option, index) in options" :key="option.value" class="scope-option" :class="{ selected: modelValue === option.value }" type="button" role="option" :aria-label="option.label" :aria-selected="modelValue === option.value" :tabindex="index === activeIndex ? 0 : -1" @focus="activeIndex = index" @click="select(option.value)">
          <span class="scope-icon"><component :is="option.icon" :size="17" /></span>
          <span class="scope-copy"><strong>{{ option.label }}</strong><small>{{ option.description }}</small></span>
          <Check v-if="modelValue === option.value" class="scope-check" :size="15" />
        </button>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.scope-select { position: relative; flex-shrink: 0; padding-right: 9px; border-right: 1px solid var(--border); }
.scope-trigger { display: flex; align-items: center; justify-content: space-between; gap: 7px; min-width: 80px; padding: 5px 7px; color: var(--subtle); border: 1px solid transparent; border-radius: 7px; background: transparent; cursor: pointer; font-size: 12px; white-space: nowrap; transition: color .15s, background .15s; }
.scope-trigger:hover, .scope-trigger.open { color: var(--accent); background: var(--accent-soft); }
.scope-trigger svg { flex-shrink: 0; transition: transform .18s; }
.scope-trigger .scope-trigger__compact-icon { display: none; }
.scope-trigger .rotated { transform: rotate(180deg); }
.scope-menu { position: absolute; z-index: 30; top: calc(100% + 14px); left: -8px; width: 270px; padding: 7px; border: 1px solid var(--border-strong); border-radius: 14px; background: var(--panel); box-shadow: 0 12px 36px rgba(12,23,46,.14), 0 2px 8px rgba(12,23,46,.04); transform-origin: top left; }
.scope-caption { padding: 5px 9px 9px; color: var(--muted); font-size: 11px; letter-spacing: .6px; }
.scope-option { display: flex; align-items: center; gap: 11px; width: 100%; padding: 11px 9px; color: var(--subtle); text-align: left; border: 0; border-radius: 9px; background: transparent; cursor: pointer; }
.scope-option + .scope-option { margin-top: 4px; }
.scope-option:hover, .scope-option:focus-visible { background: var(--surface-raised); }
.scope-option.selected { color: var(--accent); background: var(--accent-soft); }
.scope-option:focus-visible { outline: 2px solid var(--accent); outline-offset: -2px; }
.scope-icon { display: grid; place-items: center; flex-shrink: 0; width: 32px; height: 32px; border: 1px solid var(--border); border-radius: 9px; background: var(--panel); }
.scope-copy { flex: 1; min-width: 0; }
.scope-copy strong, .scope-copy small { display: block; }
.scope-copy strong { font-size: 13px; font-weight: 650; }
.scope-copy small { margin-top: 4px; color: var(--muted); font-size: 11px; line-height: 1.4; white-space: nowrap; }
.scope-check { flex-shrink: 0; }
.scope-menu-enter-active, .scope-menu-leave-active { transition: opacity .15s, transform .15s; }
.scope-menu-enter-from, .scope-menu-leave-to { opacity: 0; transform: translateY(-5px) scale(.98); }
@media (max-width: 720px) {
  .scope-select { padding-right: 4px; }
  .scope-trigger { min-width: 72px; padding-inline: 4px; gap: 4px; font-size: 11px; }
  .scope-menu { position: fixed; top: 60px; left: 16px; width: min(270px, calc(100vw - 32px)); }
}
@media (max-width: 400px) {
  .scope-trigger { min-width: 40px; justify-content: center; }
  .scope-trigger > span { display: none; }
  .scope-trigger .scope-trigger__compact-icon { display: block; }
}
</style>
