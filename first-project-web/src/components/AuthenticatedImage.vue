<script setup lang="ts">
import { ImageOff, LoaderCircle } from 'lucide-vue-next'
import { onBeforeUnmount, ref, watch } from 'vue'
import { apiDownload } from '../services/api'
import { IMAGE_TYPES, MAX_IMAGE_BYTES } from '../utils/markdownEditing'

const props = defineProps<{ url: string; alt: string }>()
const objectUrl = ref('')
const loading = ref(false)
const failed = ref(false)
let controller: AbortController | null = null

function cleanup() {
  controller?.abort()
  controller = null
  if (objectUrl.value) URL.revokeObjectURL(objectUrl.value)
  objectUrl.value = ''
}

async function load() {
  cleanup()
  failed.value = false
  loading.value = true
  controller = new AbortController()
  try {
    const { blob } = await apiDownload(props.url, {
      signal: controller.signal,
      headers: { Accept: 'image/*, application/problem+json' },
    })
    if (!IMAGE_TYPES.includes(blob.type) || blob.size > MAX_IMAGE_BYTES) throw new Error('图片响应无效')
    objectUrl.value = URL.createObjectURL(blob)
  } catch (error) {
    if (!(error instanceof DOMException && error.name === 'AbortError')) failed.value = true
  } finally {
    loading.value = false
  }
}

watch(() => props.url, load, { immediate: true })
onBeforeUnmount(cleanup)
</script>

<template>
  <div class="authenticated-image" :class="{ loading, failed }">
    <LoaderCircle v-if="loading" class="spin" :size="20" />
    <span v-else-if="failed"><ImageOff :size="19" />图片暂时无法加载</span>
    <img v-else-if="objectUrl" :src="objectUrl" :alt="alt" loading="lazy" />
  </div>
</template>

<style scoped>
.authenticated-image { min-height: 120px; display: grid; place-items: center; overflow: hidden; color: var(--muted); border: 1px solid var(--border); border-radius: 12px; background: var(--surface-sunken); }
.authenticated-image img { display: block; width: 100%; max-height: 520px; object-fit: contain; }
.authenticated-image > span { display: inline-flex; align-items: center; gap: 7px; padding: 24px; font-size: var(--font-xs); }
</style>
