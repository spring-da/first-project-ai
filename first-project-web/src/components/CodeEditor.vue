<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ChevronDown, ChevronUp, Search, X } from 'lucide-vue-next'
import { highlightCode, highlightEscapedJson } from '../utils/codeHighlight'
import { codeKeyEdit, measureCode } from '../utils/codeEditing'
import { findCodeMatches } from '../utils/codeSearch'

const props = withDefaults(defineProps<{
  modelValue: string
  language: string
  placeholder?: string
  ariaLabel?: string
  maxlength?: number
  tabSize?: number
  required?: boolean
  escaped?: boolean
  searchable?: boolean
}>(), {
  placeholder: '',
  ariaLabel: '代码内容',
  maxlength: 200000,
  tabSize: 2,
  required: false,
  escaped: false,
  searchable: false,
})

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const textarea = ref<HTMLTextAreaElement | null>(null)
const pre = ref<HTMLElement | null>(null)
const gutter = ref<HTMLElement | null>(null)
const searchInput = ref<HTMLInputElement | null>(null)
const searchLayer = ref<HTMLElement | null>(null)
const searchMark = ref<HTMLElement | null>(null)
const searchOpen = ref(false)
const searchQuery = ref('')
const activeIndex = ref(0)
// Textarea values normalize line endings; search and its overlay must use the same offsets.
const searchSource = computed(() => props.modelValue.replace(/\r\n?/g, '\n'))
const matches = computed(() => searchOpen.value ? findCodeMatches(searchSource.value, searchQuery.value) : [])
const activeMatch = computed(() => matches.value[activeIndex.value])
const searchStatus = computed(() => !searchQuery.value ? '输入关键词' : matches.value.length ? `${activeIndex.value + 1} / ${matches.value.length}` : '无匹配')
let releaseTab = false
const metrics = computed(() => measureCode(props.modelValue))
const lineNumbers = computed(() => metrics.value.large ? '' : Array.from({ length: metrics.value.lines }, (_, index) => index + 1).join('\n'))

const value = computed<string>({
  get: () => props.modelValue,
  set: (next: string) => emit('update:modelValue', next),
})

const highlighted = computed(() => `${props.escaped ? highlightEscapedJson(props.modelValue) : highlightCode(props.modelValue, props.language)}\n`)

function syncScroll() {
  const ta = textarea.value
  if (!ta) return
  for (const layer of [pre.value, searchLayer.value]) {
    if (!layer) continue
    layer.scrollTop = ta.scrollTop
    layer.scrollLeft = ta.scrollLeft
  }
  if (gutter.value) gutter.value.scrollTop = ta.scrollTop
}

async function resetScroll() {
  // The new source must render before resetting the textarea and its mirrored layers.
  await nextTick()
  const ta = textarea.value
  if (!ta) return
  ta.scrollTop = 0
  ta.scrollLeft = 0
  syncScroll()
}

defineExpose({ resetScroll })

async function revealMatch() {
  await nextTick()
  const ta = textarea.value
  const match = activeMatch.value
  if (!ta || !match || !searchMark.value) return
  ta.setSelectionRange(match.start, match.end)
  syncScroll()
  // Measure the rendered text so tabs, CJK and long lines scroll to the actual match.
  const rect = searchMark.value.getClientRects()[0]
  if (!rect) return
  const viewport = ta.getBoundingClientRect()
  if (rect.top < viewport.top + 60 || rect.bottom > viewport.top + ta.clientHeight - 16) {
    ta.scrollTop += rect.top - viewport.top - Math.max(60, (ta.clientHeight - rect.height) / 2)
  }
  const leftInset = metrics.value.large ? 18 : 60
  if (rect.left < viewport.left + leftInset || rect.right > viewport.left + ta.clientWidth - 18) {
    ta.scrollLeft += rect.left - viewport.left - leftInset
  }
  syncScroll()
}

function selectFirstMatch() {
  const caret = textarea.value?.selectionStart ?? 0
  const index = matches.value.findIndex((match) => match.start >= caret)
  activeIndex.value = index < 0 ? 0 : index
  void revealMatch()
}

async function openSearch() {
  const ta = textarea.value
  if (ta && document.activeElement === ta && ta.selectionStart !== ta.selectionEnd) {
    const selection = ta.value.slice(ta.selectionStart, ta.selectionEnd)
    if (!selection.includes('\n')) searchQuery.value = selection
  }
  searchOpen.value = true
  releaseTab = false
  await nextTick()
  searchInput.value?.focus({ preventScroll: true })
  searchInput.value?.select()
  selectFirstMatch()
}

async function closeSearch() {
  searchOpen.value = false
  releaseTab = false
  await nextTick()
  textarea.value?.focus({ preventScroll: true })
  syncScroll()
}

function moveMatch(direction: number) {
  if (!matches.value.length) return
  activeIndex.value = (activeIndex.value + direction + matches.value.length) % matches.value.length
  void revealMatch()
}

function onSearchKeydown(event: KeyboardEvent) {
  if (!props.searchable || event.isComposing || event.defaultPrevented) return
  if ((event.ctrlKey || event.metaKey) && !event.altKey && !event.shiftKey && event.key.toLowerCase() === 'f') {
    event.preventDefault()
    event.stopPropagation()
    void openSearch()
  } else if (searchOpen.value && event.key === 'Escape') {
    event.preventDefault()
    event.stopPropagation()
    void closeSearch()
  } else if (searchOpen.value && event.target === searchInput.value && event.key === 'Enter' && !event.ctrlKey && !event.metaKey && !event.altKey) {
    event.preventDefault()
    event.stopPropagation()
    moveMatch(event.shiftKey ? -1 : 1)
  }
}

watch(searchQuery, selectFirstMatch, { flush: 'post' })
watch(searchSource, () => {
  activeIndex.value = Math.min(activeIndex.value, Math.max(0, matches.value.length - 1))
  // Editing updates counts/highlighting without moving the user's caret.
  void nextTick(syncScroll)
})

function insert(next: string, start: number, end: number) {
  if (next.length > props.maxlength) return
  emit('update:modelValue', next)
  void nextTick(() => {
    const ta = textarea.value
    if (ta) ta.setSelectionRange(start, end)
    syncScroll()
  })
}

function onKeydown(event: KeyboardEvent) {
  if (event.isComposing || event.ctrlKey || event.metaKey || event.altKey) return
  if (event.key === 'Escape') { releaseTab = true; return }
  if (event.key === 'Tab' && releaseTab) { releaseTab = false; return }
  releaseTab = false
  const ta = event.currentTarget as HTMLTextAreaElement
  const start = ta.selectionStart
  const end = ta.selectionEnd

  const edit = codeKeyEdit(props.modelValue, start, end, event.key, event.shiftKey, props.tabSize, props.escaped)
  if (edit) {
    event.preventDefault()
    insert(edit.value, edit.start, edit.end)
  }
}
</script>

<template>
  <div class="snippet-code-editor" :class="{ 'snippet-code-editor--large': metrics.large, 'snippet-code-editor--searching': searchOpen }" @keydown="onSearchKeydown">
    <pre v-if="!metrics.large" ref="gutter" class="snippet-code-editor__gutter" aria-hidden="true">{{ lineNumbers }}</pre>
    <pre v-if="!metrics.large" ref="pre" class="snippet-code-editor__pre" aria-hidden="true"><code v-html="highlighted"></code></pre>
    <pre v-if="activeMatch" ref="searchLayer" class="snippet-code-editor__search-layer" aria-hidden="true"><code>{{ searchSource.slice(0, activeMatch.start) }}<mark ref="searchMark">{{ searchSource.slice(activeMatch.start, activeMatch.end) }}</mark>{{ searchSource.slice(activeMatch.end) }}{{ '\n' }}</code></pre>
    <textarea
      ref="textarea"
      v-model="value"
      class="snippet-code-editor__textarea"
      :placeholder="placeholder"
      :maxlength="maxlength"
      :aria-label="ariaLabel"
      :aria-keyshortcuts="searchable ? 'Control+F Meta+F' : undefined"
      :title="`${searchable ? 'Ctrl / ⌘ F 搜索，' : ''}Enter 自动缩进，Tab / Shift+Tab 调整缩进；按 Esc 后按 Tab 可离开编辑区`"
      :required="required"
      spellcheck="false"
      autocapitalize="off"
      autocomplete="off"
      autocorrect="off"
      wrap="off"
      @input="syncScroll"
      @scroll="syncScroll"
      @keydown="onKeydown"
    ></textarea>
    <div v-if="searchOpen" class="snippet-code-editor__search" data-code-search role="search" aria-label="代码区域搜索">
      <Search :size="15" aria-hidden="true" />
      <input ref="searchInput" v-model="searchQuery" type="text" aria-label="搜索代码" placeholder="查找…" autocomplete="off" spellcheck="false" />
      <span class="snippet-code-editor__search-status" :class="{ 'is-empty': searchQuery && !matches.length }" role="status" aria-live="polite" aria-atomic="true">{{ searchStatus }}</span>
      <button type="button" aria-label="上一个匹配" title="上一个匹配 (Shift+Enter)" :disabled="!matches.length" @click="moveMatch(-1)"><ChevronUp :size="16" aria-hidden="true" /></button>
      <button type="button" aria-label="下一个匹配" title="下一个匹配 (Enter)" :disabled="!matches.length" @click="moveMatch(1)"><ChevronDown :size="16" aria-hidden="true" /></button>
      <button type="button" aria-label="关闭搜索" title="关闭 (Esc)" @click="closeSearch"><X :size="16" aria-hidden="true" /></button>
    </div>
  </div>
</template>

<style scoped>
.snippet-code-editor {
  position: relative;
  min-height: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--code-bg);
}
.snippet-code-editor__pre,
.snippet-code-editor__search-layer,
.snippet-code-editor__textarea {
  margin: 0;
  border: 0;
  padding: 16px 18px 24px 60px;
  font: var(--font-sm)/1.75 "Cascadia Code", Consolas, monospace;
  tab-size: 2;
  white-space: pre;
  word-break: normal;
  overflow-wrap: normal;
  text-align: left;
}
.snippet-code-editor__pre,
.snippet-code-editor__search-layer {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
  color: var(--code-text);
}
.snippet-code-editor__search-layer { z-index: 1; color: transparent; }
.snippet-code-editor__search-layer code { font: inherit; color: inherit; }
.snippet-code-editor__search-layer mark { color: transparent; background: color-mix(in srgb, var(--warning) 35%, transparent); outline: 1px solid var(--warning); border-radius: 2px; }
.snippet-code-editor__gutter {
  position: absolute;
  z-index: 3;
  inset: 0 auto 0 0;
  width: 46px;
  margin: 0;
  padding: 16px 9px 24px 0;
  overflow: hidden;
  color: var(--muted);
  background: var(--code-bg);
  border-right: 1px solid var(--border);
  font: var(--font-sm)/1.75 "Cascadia Code", Consolas, monospace;
  text-align: right;
  pointer-events: none;
  user-select: none;
}
.snippet-code-editor:focus-within { box-shadow: inset 0 0 0 1px var(--accent-border); }
.snippet-code-editor__pre code {
  font: inherit;
  color: inherit;
}
.snippet-code-editor__textarea {
  position: relative;
  z-index: 2;
  width: 100%;
  min-height: 0;
  flex: 1 1 auto;
  resize: none;
  outline: none;
  color: transparent;
  caret-color: var(--text);
  background: transparent;
  overflow: auto;
}
.snippet-code-editor__textarea::placeholder {
  color: var(--muted);
  opacity: 0.55;
}
.snippet-code-editor__textarea::selection {
  color: transparent;
  background: color-mix(in srgb, var(--accent) 32%, transparent);
}
.snippet-code-editor--large .snippet-code-editor__search-layer { padding-left: 18px; }
.snippet-code-editor--large .snippet-code-editor__textarea {
  padding-left: 18px;
  color: var(--code-text);
  -webkit-text-fill-color: var(--code-text);
}
.snippet-code-editor--large .snippet-code-editor__textarea::selection {
  color: var(--code-text);
  -webkit-text-fill-color: var(--code-text);
}
.snippet-code-editor--searching .snippet-code-editor__pre,
.snippet-code-editor--searching .snippet-code-editor__search-layer,
.snippet-code-editor--searching .snippet-code-editor__textarea,
.snippet-code-editor--searching .snippet-code-editor__gutter { padding-top: 60px; }
.snippet-code-editor__search { position: absolute; z-index: 4; top: 8px; right: 8px; display: flex; align-items: center; gap: 4px; width: 380px; max-width: calc(100% - 16px); padding: 5px; border: 1px solid var(--border); border-radius: 8px; background: var(--panel); color: var(--subtle); box-shadow: 0 4px 16px rgba(0,0,0,.12); }
.snippet-code-editor__search > svg { flex-shrink: 0; margin-left: 4px; }
.snippet-code-editor__search input { width: 0; min-width: 0; flex: 1; height: 30px; padding: 4px 6px; border: 1px solid transparent; border-radius: 4px; outline: none; background: var(--surface-sunken); color: var(--text); font: inherit; font-size: 12px; }
.snippet-code-editor__search input:focus { border-color: var(--accent); box-shadow: 0 0 0 1px var(--accent-border); }
.snippet-code-editor__search-status { flex-shrink: 0; padding-inline: 4px; white-space: nowrap; font-size: 11px; font-variant-numeric: tabular-nums; }
.snippet-code-editor__search-status.is-empty { color: var(--danger); }
.snippet-code-editor__search button { display: inline-flex; align-items: center; justify-content: center; flex-shrink: 0; width: 28px; height: 30px; padding: 0; border: 0; border-radius: 4px; background: transparent; color: var(--subtle); cursor: pointer; }
.snippet-code-editor__search button:hover:not(:disabled) { color: var(--accent); background: var(--accent-bg); }
.snippet-code-editor__search button:focus-visible { outline: 2px solid var(--accent); outline-offset: 1px; }
.snippet-code-editor__search button:disabled { opacity: .35; cursor: default; }
@media (max-width: 720px) {
  .snippet-code-editor__pre,
  .snippet-code-editor__search-layer,
  .snippet-code-editor__textarea {
    padding: 16px 12px 24px 54px;
    font-size: 14px;
  }
  .snippet-code-editor__gutter { font-size: 14px; width: 42px; }
}
</style>
