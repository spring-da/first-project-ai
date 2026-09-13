<script setup lang="ts">
import { ImageIcon, ImageOff, LoaderCircle } from 'lucide-vue-next'
import { nextTick, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue'
import { loadImage } from '../services/markdownImages'
import { useAuthStore } from '../stores/auth'

const props = defineProps<{ url: string; alt: string }>()
const auth = useAuthStore()
const root = ref<HTMLElement | null>(null)
const objectUrl = ref('')
const loading = ref(false)
const failed = ref(false)
const queued = ref(true)
let controller: AbortController | null = null
let observer: IntersectionObserver | null = null
let active = true
let revision = 0

function cleanup() {
  controller?.abort()
  controller = null
  if (objectUrl.value) URL.revokeObjectURL(objectUrl.value)
  objectUrl.value = ''
}

async function load(reload = false) {
  observer?.disconnect()
  queued.value = false
  cleanup()
  failed.value = false
  loading.value = true
  const request = new AbortController()
  controller = request
  try {
    const blob = await loadImage(props.url, request.signal, true, { reload })
    if (request.signal.aborted || controller !== request) return
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
  const current = ++revision
  observer?.disconnect()
  cleanup()
  failed.value = false
  loading.value = false
  queued.value = true
  await nextTick()
  if (!active || current !== revision || !root.value) return
  if (typeof IntersectionObserver === 'undefined') {
    await load()
    return
  }
  observer = new IntersectionObserver((entries) => {
    if (active && current === revision && entries.some((entry) => entry.isIntersecting)) void load()
  }, { rootMargin: '320px 0px' })
  observer.observe(root.value)
}

watch(() => [props.url, auth.workspaceKey, auth.session?.accessToken], deferLoad)
onMounted(deferLoad)
onActivated(() => { if (!active) { active = true; void deferLoad() } })
function deactivate() { active = false; revision++; observer?.disconnect(); cleanup() }
onDeactivated(deactivate)
onBeforeUnmount(deactivate)
</script>

<template>
  <div ref="root" class="authenticated-image" :class="{ loading, failed, queued }">
    <LoaderCircle v-if="loading" class="spin" :size="20" />
    <button v-else-if="failed" type="button" @click="load(true)"><ImageOff :size="19" />图片暂时无法加载，点击重试</button>
    <img v-else-if="objectUrl" :src="objectUrl" :alt="alt" decoding="async" @error="failed = true" />
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
