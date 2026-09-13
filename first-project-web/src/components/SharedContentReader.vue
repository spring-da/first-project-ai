<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue'
import { Workflow, Code2, FileText } from 'lucide-vue-next'
import MarkdownContent from './MarkdownContent.vue'
import type { SharedContent } from '../types/sharing'
import { highlightCode } from '../utils/codeHighlight'

const FlowchartCanvas = defineAsyncComponent(() => import('./flowcharts/FlowchartCanvas.vue'))
const props = withDefaults(defineProps<{
  resource: SharedContent
  imageBasePath: string
  authenticatedImages?: boolean
}>(), { authenticatedImages: true })

const typeLabels = { MARKDOWN: 'Markdown 文章', SNIPPET: '代码片段', FLOWCHART: '流程图' }
const highlightedCode = computed(() => props.resource.resourceType === 'SNIPPET'
  ? highlightCode(props.resource.content, props.resource.language)
  : '')
const markdownSource = computed(() => {
  if (props.resource.resourceType !== 'MARKDOWN') return ''
  const lines = props.resource.content.split(/\r?\n/)
  const firstContentLine = lines.findIndex((line) => line.trim())
  if (firstContentLine >= 0) {
    const heading = lines[firstContentLine]?.match(/^#\s+(.+)$/)?.[1]?.trim()
    if (heading === props.resource.title.trim()) lines.splice(firstContentLine, 1)
  }
  return lines.join('\n')
})
const updatedAt = computed(() => {
  const date = new Date(props.resource.updatedAt)
  return Number.isFinite(date.getTime())
    ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(date)
    : '时间未知'
})
</script>

<template>
  <article class="shared-content-reader">
    <header class="shared-content-reader__header">
      <p class="shared-content-reader__kind">
        <FileText v-if="resource.resourceType === 'MARKDOWN'" :size="16" aria-hidden="true" />
        <Code2 v-else-if="resource.resourceType === 'SNIPPET'" :size="16" aria-hidden="true" />
        <Workflow v-else :size="16" aria-hidden="true" />
        {{ typeLabels[resource.resourceType] }}<span>只读</span>
      </p>
      <h2>{{ resource.title }}</h2>
      <div class="shared-content-reader__meta">
        <code v-if="resource.resourceType === 'SNIPPET'">{{ resource.language || 'Plain Text' }}</code>
        <span>更新于 <time :datetime="resource.updatedAt">{{ updatedAt }}</time></span>
      </div>
      <ul v-if="resource.tags.length" class="shared-content-reader__tags" aria-label="标签">
        <li v-for="(tag, index) in resource.tags" :key="`${index}-${tag}`">#{{ tag }}</li>
      </ul>
    </header>

    <FlowchartCanvas v-if="resource.resourceType === 'FLOWCHART' && resource.diagram" :diagram="resource.diagram" readonly style="height: min(70vh, 700px); margin-top: 20px" />
    <template v-else-if="resource.resourceType === 'MARKDOWN'">
      <MarkdownContent
        v-if="markdownSource.trim()"
        :key="`${resource.id}:${imageBasePath}`"
        class="shared-content-reader__markdown"
        :source="markdownSource"
        :image-base-path="imageBasePath"
        :authenticated-images="authenticatedImages"
      />
      <p v-else class="shared-content-reader__empty">这篇文章暂时没有正文。</p>
    </template>
    <p v-else-if="!resource.content.trim()" class="shared-content-reader__empty">这项内容暂时没有正文。</p>
    <pre v-else-if="resource.resourceType === 'SNIPPET'" class="shared-content-reader__code" tabindex="0" role="region" aria-label="代码内容，可横向滚动"><code v-html="highlightedCode"></code></pre>

  </article>
</template>

<style scoped>
.shared-content-reader { min-width: 0; color: var(--text); }
.shared-content-reader__header { padding-bottom: 24px; border-bottom: 1px solid var(--border); }
.shared-content-reader__kind { display: flex; align-items: center; flex-wrap: wrap; gap: 7px; margin: 0; color: var(--accent); font-size: var(--font-xs); font-weight: 650; }
.shared-content-reader__kind > span { margin-left: 3px; padding: 2px 7px; color: var(--muted); border: 1px solid var(--border); border-radius: 5px; font-size: 12px; font-weight: 500; }
.shared-content-reader__header h2 { margin: 14px 0 16px; font-size: clamp(24px, 2.4vw, 36px); line-height: 1.3; letter-spacing: -.025em; overflow-wrap: anywhere; }
.shared-content-reader__meta { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 16px; color: var(--muted); font-size: var(--font-xs); overflow-wrap: anywhere; }
.shared-content-reader__meta > * { min-width: 0; }
.shared-content-reader__meta code { padding: 4px 8px; color: var(--accent); border-radius: 6px; background: var(--accent-bg); font: inherit; }
.shared-content-reader__tags { display: flex; flex-wrap: wrap; gap: 6px 12px; margin: 15px 0 0; padding: 0; list-style: none; color: var(--accent); font-size: var(--font-xs); }
.shared-content-reader__tags li { max-width: 100%; overflow-wrap: anywhere; }
.shared-content-reader__markdown { min-width: 0; padding-top: 28px; font-size: 16px; line-height: 1.85; overflow-wrap: anywhere; }
.shared-content-reader__markdown :deep(h1), .shared-content-reader__markdown :deep(h2), .shared-content-reader__markdown :deep(h3), .shared-content-reader__markdown :deep(h4) { margin: 1.5em 0 .6em; line-height: 1.35; letter-spacing: -.015em; }
.shared-content-reader__markdown :deep(h1:first-child), .shared-content-reader__markdown :deep(h2:first-child), .shared-content-reader__markdown :deep(h3:first-child) { margin-top: 0; }
.shared-content-reader__markdown :deep(h1) { padding-bottom: .3em; border-bottom: 1px solid var(--border); font-size: 1.8em; }
.shared-content-reader__markdown :deep(h2) { padding-bottom: .3em; border-bottom: 1px solid var(--border); font-size: 1.5em; }
.shared-content-reader__markdown :deep(h3) { font-size: 1.25em; }
.shared-content-reader__markdown :deep(p) { margin: 0 0 1.1em; }
.shared-content-reader__markdown :deep(a) { color: var(--accent); text-decoration: underline; text-underline-offset: 3px; }
.shared-content-reader__markdown :deep(pre) { max-width: 100%; overflow: auto; margin: 1.3em 0; padding: 16px; border: 1px solid var(--border); border-radius: var(--radius-md); background: var(--code-bg); }
.shared-content-reader__markdown :deep(code) { padding: .15em .35em; color: var(--code-text); border-radius: 4px; background: var(--code-bg); font: 13px/1.7 "Cascadia Code", Consolas, monospace; }
.shared-content-reader__markdown :deep(pre code) { padding: 0; background: transparent; white-space: pre; }
.shared-content-reader__markdown :deep(blockquote) { margin: 1.2em 0; padding: .25em 1em; color: var(--subtle); border-left: 3px solid var(--accent); background: var(--surface-sunken); }
.shared-content-reader__markdown :deep(ul), .shared-content-reader__markdown :deep(ol) { padding-left: 1.5em; }
.shared-content-reader__markdown :deep(li) { margin: .35em 0; }
.shared-content-reader__markdown :deep(.markdown-table-wrap) { max-width: 100%; overflow-x: auto; margin: 1.3em 0; }
.shared-content-reader__markdown :deep(table) { width: 100%; border-collapse: collapse; font-size: 14px; }
.shared-content-reader__markdown :deep(th), .shared-content-reader__markdown :deep(td) { padding: 9px 11px; text-align: left; border: 1px solid var(--border); }
.shared-content-reader__markdown :deep(th) { background: var(--surface-sunken); }
.shared-content-reader__markdown :deep(img) { max-width: 100%; height: auto; }
.shared-content-reader__code { max-width: 100%; overflow: auto; margin: 28px 0 0; padding: clamp(16px, 2vw, 24px); color: var(--code-text); border: 1px solid var(--border); border-radius: var(--radius-md); background: var(--code-bg); font: 14px/1.75 "Cascadia Code", Consolas, monospace; tab-size: 2; }
.shared-content-reader__code code { font: inherit; white-space: pre; }
.shared-content-reader__empty { min-height: 180px; display: grid; place-items: center; padding: 24px 0; color: var(--muted); font-size: var(--font-sm); text-align: center; }
@media (max-width: 600px) {
  .shared-content-reader__header { padding-bottom: 20px; }
  .shared-content-reader__markdown { padding-top: 24px; }
}
</style>
