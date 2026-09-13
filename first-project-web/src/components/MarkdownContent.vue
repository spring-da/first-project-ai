<script setup lang="ts">
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue'
import { renderMarkdown } from '../utils/markdown'
import { loadMarkdownImage } from '../services/markdownImages'
import { useAuthStore } from '../stores/auth'

const props = withDefaults(defineProps<{
  source: string
  imageBasePath?: string
  authenticatedImages?: boolean
}>(), {
  imageBasePath: '/markdown-images',
  authenticatedImages: true,
})
const auth = useAuthStore()
const root = ref<HTMLElement | null>(null)
const html = computed(() => renderMarkdown(props.source))
type ImageEntry = { controller: AbortController; url?: string; failed?: boolean }
const images = new Map<string, ImageEntry>()
let active = true

function paint(id: string, entry: ImageEntry) {
  for (const frame of root.value?.querySelectorAll<HTMLElement>('[data-image-id]') ?? []) {
    if (frame.dataset.imageId !== id) continue
    const image = frame.querySelector('img')!
    const status = frame.querySelector('button')!
    image.hidden = !entry.url
    status.hidden = !!entry.url
    status.disabled = !entry.failed
    status.textContent = entry.failed ? '图片加载失败，点击重试' : '正在加载图片…'
    if (entry.url) image.src = entry.url
    else image.removeAttribute('src')
  }
}

function release(entry: ImageEntry) {
  entry.controller.abort()
  if (entry.url) URL.revokeObjectURL(entry.url)
}

async function load(id: string) {
  const entry: ImageEntry = { controller: new AbortController() }
  images.set(id, entry)
  paint(id, entry)
  try {
    const blob = await loadMarkdownImage(id, entry.controller.signal, props.imageBasePath, props.authenticatedImages)
    if (entry.controller.signal.aborted || images.get(id) !== entry) return
    entry.url = URL.createObjectURL(blob)
  } catch {
    if (entry.controller.signal.aborted || images.get(id) !== entry) return
    entry.failed = true
  }
  paint(id, entry)
}

function refreshImages() {
  if (!active || !root.value || (props.authenticatedImages && !auth.session)) return
  const ids = new Set(Array.from(root.value.querySelectorAll<HTMLElement>('[data-image-id]'), (frame) => frame.dataset.imageId!))
  for (const [id, entry] of images) if (!ids.has(id)) { release(entry); images.delete(id) }
  for (const id of ids) {
    const entry = images.get(id)
    if (entry) paint(id, entry)
    else void load(id)
  }
}

function retry(event: MouseEvent) {
  const button = (event.target as HTMLElement).closest<HTMLButtonElement>('.markdown-image-status')
  const id = button?.closest<HTMLElement>('[data-image-id]')?.dataset.imageId
  if (id && images.get(id)?.failed) { release(images.get(id)!); void load(id) }
}

function clearImages() {
  for (const [id, entry] of images) {
    release(entry)
    paint(id, { controller: entry.controller })
  }
  images.clear()
}
watch(html, refreshImages, { flush: 'post' })
watch(() => props.imageBasePath, () => { clearImages(); refreshImages() })
watch(() => props.authenticatedImages, () => { clearImages(); refreshImages() })
watch(() => [auth.workspaceKey, auth.session?.accessToken], () => {
  clearImages()
  refreshImages()
})
onMounted(refreshImages)
onActivated(() => { active = true; refreshImages() })
onDeactivated(() => { active = false; clearImages() })
onBeforeUnmount(() => { active = false; clearImages() })
</script>

<template>
  <!-- Raw HTML is escaped by the renderer. Private images never send JWTs in URLs. -->
  <article ref="root" class="markdown-body" @click="retry" v-html="html"></article>
</template>

<style>
.markdown-private-image { display: block; margin: 14px 0; }
.markdown-private-image img { max-width: 100%; height: auto; border-radius: 8px; }
.markdown-image-status { width: 100%; min-height: 72px; padding: 14px; color: var(--muted); border: 1px dashed var(--border-strong); border-radius: 10px; background: var(--surface-sunken); font: inherit; font-size: 12px; }
.markdown-image-status:not(:disabled) { cursor: pointer; color: var(--accent); }
.markdown-private-image [hidden] { display: none; }
</style>
