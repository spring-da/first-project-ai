<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { highlightCode, highlightEscapedJson } from '../utils/codeHighlight'
import { codeKeyEdit, measureCode } from '../utils/codeEditing'

const props = withDefaults(defineProps<{
  modelValue: string
  language: string
  placeholder?: string
  ariaLabel?: string
  maxlength?: number
  tabSize?: number
  required?: boolean
  escaped?: boolean
}>(), {
  placeholder: '',
  ariaLabel: '代码内容',
  maxlength: 200000,
  tabSize: 2,
  required: false,
  escaped: false,
})

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const textarea = ref<HTMLTextAreaElement | null>(null)
const pre = ref<HTMLElement | null>(null)
const gutter = ref<HTMLElement | null>(null)
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
  const p = pre.value
  if (ta && p) {
    p.scrollTop = ta.scrollTop
    p.scrollLeft = ta.scrollLeft
    if (gutter.value) gutter.value.scrollTop = ta.scrollTop
  }
}

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
  <div class="snippet-code-editor" :class="{ 'snippet-code-editor--large': metrics.large }">
    <pre v-if="!metrics.large" ref="gutter" class="snippet-code-editor__gutter" aria-hidden="true">{{ lineNumbers }}</pre>
    <pre v-if="!metrics.large" ref="pre" class="snippet-code-editor__pre" aria-hidden="true"><code v-html="highlighted"></code></pre>
    <textarea
      ref="textarea"
      v-model="value"
      class="snippet-code-editor__textarea"
      :placeholder="placeholder"
      :maxlength="maxlength"
      :aria-label="ariaLabel"
      title="Enter 自动缩进，Tab / Shift+Tab 调整缩进；按 Esc 后按 Tab 可离开编辑区"
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
.snippet-code-editor__pre {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
  color: var(--code-text);
}
.snippet-code-editor__gutter {
  position: absolute;
  z-index: 1;
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
.snippet-code-editor--large .snippet-code-editor__textarea {
  padding-left: 18px;
  color: var(--code-text);
  -webkit-text-fill-color: var(--code-text);
}
.snippet-code-editor--large .snippet-code-editor__textarea::selection {
  color: var(--code-text);
  -webkit-text-fill-color: var(--code-text);
}
@media (max-width: 720px) {
  .snippet-code-editor__pre,
  .snippet-code-editor__textarea {
    padding: 16px 12px 24px 54px;
    font-size: 14px;
  }
  .snippet-code-editor__gutter { font-size: 14px; width: 42px; }
}
</style>
