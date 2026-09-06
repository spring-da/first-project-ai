<script setup lang="ts">
import { ImageIcon, ImageOff, LoaderCircle } from 'lucide-vue-next'
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { apiDownload } from '../services/api'
import { IMAGE_TYPES, MAX_IMAGE_BYTES } from '../utils/markdownEditing'

const props = defineProps<{ url: string; alt: string }>()
const root = ref<HTMLElement | null>(null)
const objectUrl = ref('')
const loading = ref(false)
const failed = ref(false)
const queued = ref(true)
let controller: AbortController | null = null
let observer: IntersectionObserver | null = null

function cleanup() {
  controller?.abort()
  controller = null
  if (objectUrl.value) URL.revokeObjectURL(objectUrl.value)
  objectUrl.value = ''
}

async function load() {
  observer?.disconnect()
  queued.value = false
  cleanup()
  failed.value = false
  loading.value = true
  const request = new AbortController()
  controller = request
  try {
    const { blob } = await apiDownload(props.url, {
      signal: request.signal,
      headers: { Accept: 'image/*, application/problem+json' },
    })
    if (request.signal.aborted || controller !== request) return
    if (!IMAGE_TYPES.includes(blob.type) || blob.size > MAX_IMAGE_BYTES) throw new Error('图片响应无效')
    objectUrl.value = URL.createObjectURL(blob)
  } catch (error) {
    if (!request.signal.aborted && controller === request
      && !(error instanceof DOMException && error.name === 'AbortError')) failed.value = true
  } finally {
    if (controller === request) {
      controller = null
      loading.value = false
    }
  }
}

async function deferLoad() {
  observer?.disconnect()
  cleanup()
  failed.value = false
  loading.value = false
  queued.value = true
  await nextTick()
  if (!root.value) return
  if (typeof IntersectionObserver === 'undefined') {
    await load()
    return
  }
  observer = new IntersectionObserver((entries) => {
    if (entries.some((entry) => entry.isIntersecting)) void load()
  }, { rootMargin: '320px 0px' })
  observer.observe(root.value)
}

watch(() => props.url, deferLoad)
onMounted(deferLoad)
onBeforeUnmount(() => { observer?.disconnect(); cleanup() })
</script>

<template>
  <div ref="root" class="authenticated-image" :class="{ loading, failed, queued }">
    <LoaderCircle v-if="loading" class="spin" :size="20" />
    <button v-else-if="failed" type="button" @click="load"><ImageOff :size="19" />图片暂时无法加载，点击重试</button>
    <img v-else-if="objectUrl" :src="objectUrl" :alt="alt" loading="lazy" />
    <span v-else><ImageIcon :size="19" />滚动到这里时加载图片</span>
  </div>
</template>

<style scoped>
.authenticated-image { min-height: 120px; display: grid; place-items: center; overflow: hidden; color: var(--muted); border: 1px solid var(--border); border-radius: 12px; background: var(--surface-sunken); }
.authenticated-image.queued { border-style: dashed; }
.authenticated-image img { display: block; width: 100%; max-height: 520px; object-fit: contain; }
.authenticated-image > span, .authenticated-image > button { display: inline-flex; align-items: center; gap: 7px; padding: 24px; color: inherit; border: 0; background: transparent; font: inherit; font-size: var(--font-xs); }
.authenticated-image > button { color: var(--accent); cursor: pointer; }
</style>
