<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Check, Copy } from 'lucide-vue-next'
import AppModal from './AppModal.vue'
import { useNotificationStore } from '../stores/notifications'

defineOptions({ name: 'JsonValueDialog' })
const props = defineProps<{ label: string; value: string }>()
const emit = defineEmits<{ close: [] }>()
const notifications = useNotificationStore()
const copied = ref(false)
const copyButton = ref<HTMLButtonElement | null>(null)
const valueInput = ref<HTMLTextAreaElement | null>(null)
let copiedTimer: number | undefined

watch(() => props.value, () => { copied.value = false })

onMounted(() => { void nextTick(() => copyButton.value?.focus()) })
onBeforeUnmount(() => { if (copiedTimer !== undefined) window.clearTimeout(copiedTimer) })

async function copyValue() {
  try {
    if (window.isSecureContext && navigator.clipboard?.writeText) await navigator.clipboard.writeText(props.value)
    else {
      const field = valueInput.value
      if (!field) throw new Error('copy field unavailable')
      field.focus()
      field.select()
      if (!document.execCommand('copy')) throw new Error('copy failed')
    }
    copied.value = true
    if (copiedTimer !== undefined) window.clearTimeout(copiedTimer)
    copiedTimer = window.setTimeout(() => { copied.value = false }, 1_500)
    notifications.notify('字段值已复制。', { type: 'success' })
  } catch {
    notifications.notify('自动复制失败，请手动选择内容。', { type: 'warning' })
  }
}

function selectValue(event: FocusEvent) {
  if (event.target instanceof HTMLTextAreaElement) event.target.select()
}
</script>

<template>
  <AppModal title="完整字段值" description="可选择完整内容，或一键复制到剪贴板。" @close="emit('close')">
    <div class="json-value-dialog">
      <p class="json-value-dialog__field"><span>字段</span><code>{{ label }}</code></p>
      <label class="sr-only" for="json-value-dialog-content">完整字段值</label>
      <textarea id="json-value-dialog-content" ref="valueInput" class="json-value-dialog__content" :value="value" readonly spellcheck="false" @focus="selectValue" />
      <div class="json-value-dialog__actions">
        <button ref="copyButton" class="button button-primary" type="button" @click="copyValue"><Check v-if="copied" :size="16" /><Copy v-else :size="16" />{{ copied ? '已复制' : '复制内容' }}</button>
        <button class="button button-secondary" type="button" @click="emit('close')">关闭</button>
      </div>
    </div>
  </AppModal>
</template>

<style scoped>
.json-value-dialog { display: grid; gap: 14px; min-width: 0; }
.json-value-dialog__field { display: flex; align-items: baseline; gap: 8px; min-width: 0; margin: 0; color: var(--muted); font-size: var(--font-xs); }
.json-value-dialog__field code { min-width: 0; overflow-wrap: anywhere; color: var(--accent); font: 600 12px/1.5 "Cascadia Code", Consolas, monospace; }
.json-value-dialog__content { width: 100%; min-height: 180px; max-height: min(46vh, 360px); padding: 12px; border: 1px solid var(--border); border-radius: 9px; resize: vertical; background: var(--code-bg); color: var(--code-text); font: 12px/1.65 "Cascadia Code", Consolas, monospace; overflow-wrap: anywhere; }
.json-value-dialog__content:focus { outline: 2px solid var(--accent-border); outline-offset: 1px; border-color: var(--accent-border); }
.json-value-dialog__actions { display: flex; justify-content: flex-end; gap: 8px; }
@media (max-width: 520px) {
  .json-value-dialog__content { min-height: 150px; }
  .json-value-dialog__actions { flex-direction: column-reverse; }
  .json-value-dialog__actions .button { justify-content: center; }
}
</style>
