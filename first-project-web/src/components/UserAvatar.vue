<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { loadImage } from '../services/markdownImages'
import { useAuthStore } from '../stores/auth'
import { IMAGE_TYPES } from '../utils/markdownEditing'

const props = withDefaults(defineProps<{
  name: string
  seed?: string
  url?: string | null
  size?: number
}>(), { seed: '', url: null })

const objectUrl = ref('')
const auth = useAuthStore()
const failed = ref(false)
let controller: AbortController | null = null
const initial = computed(() => Array.from(props.name.trim())[0]?.toUpperCase() || 'D')
const externalUrl = computed(() => /^https?:\/\//iu.test(props.url ?? '') ? props.url! : '')
const imageSource = computed(() => externalUrl.value || objectUrl.value)
const palette = [
  ['#315fca', '#6f8fe8'], ['#7250bd', '#aa7ce2'], ['#147d7a', '#4fb5a4'],
  ['#a55067', '#dc8393'], ['#9b6426', '#d8a256'], ['#3c6f91', '#6ba6c8'],
]
const colors = computed(() => {
  let hash = 0
  const value = props.seed || props.name
  for (const character of value) hash = ((hash << 5) - hash + character.codePointAt(0)!) | 0
  return palette[Math.abs(hash) % palette.length]!
})
const avatarStyle = computed(() => ({
  ...(props.size ? {
    width: `${props.size}px`, height: `${props.size}px`,
    fontSize: `${Math.max(11, Math.round(props.size * .36))}px`,
  } : {}),
  background: `linear-gradient(145deg, ${colors.value[0]}, ${colors.value[1]})`,
}))

function cleanup() {
  controller?.abort()
  controller = null
  if (objectUrl.value) URL.revokeObjectURL(objectUrl.value)
  objectUrl.value = ''
}

async function load() {
  cleanup()
  failed.value = false
  if (!props.url || externalUrl.value) return
  const request = new AbortController()
  controller = request
  try {
    const blob = await loadImage(props.url, request.signal)
    if (request.signal.aborted || controller !== request) return
    if (!IMAGE_TYPES.includes(blob.type) || blob.size > 5 * 1024 * 1024) throw new Error('头像响应无效')
    objectUrl.value = URL.createObjectURL(blob)
  } catch (error) {
    if (!request.signal.aborted && controller === request && !(error instanceof DOMException && error.name === 'AbortError')) failed.value = true
  }
}

watch(() => [props.url, auth.workspaceKey, auth.session?.accessToken], load, { immediate: true })
onBeforeUnmount(cleanup)
</script>

<template>
  <span class="user-avatar" :style="avatarStyle" role="img" :aria-label="`${name}的头像`">
    <img v-if="imageSource && !failed" :src="imageSource" alt="" decoding="async" @error="failed = true" />
    <span v-else>{{ initial }}</span>
  </span>
</template>

<style scoped>
.user-avatar { display: inline-grid; flex: 0 0 auto; place-items: center; overflow: hidden; color: #fff; border: 1px solid color-mix(in srgb, #fff 34%, transparent); border-radius: 28%; box-shadow: inset 0 0 0 1px rgba(255,255,255,.08); font-weight: 760; line-height: 1; }
.user-avatar img { width: 100%; height: 100%; object-fit: cover; }
</style>
