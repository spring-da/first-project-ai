<script setup lang="ts">
import { computed } from 'vue'
import { linkifyText } from '../utils/linkify'

const props = defineProps<{ text: string }>()
const segments = computed(() => linkifyText(props.text))
</script>

<template>
  <span class="linked-text">
    <template v-for="(segment, index) in segments" :key="`${index}-${segment.text}`">
      <a
        v-if="segment.type === 'link'"
        :href="segment.href"
        target="_blank"
        rel="noopener noreferrer nofollow"
      >{{ segment.text }}</a>
      <template v-else>{{ segment.text }}</template>
    </template>
  </span>
</template>

<style scoped>
.linked-text { white-space: pre-wrap; overflow-wrap: anywhere; }
.linked-text a { color: var(--accent); text-decoration: underline; text-decoration-color: color-mix(in srgb, var(--accent) 38%, transparent); text-underline-offset: 3px; }
.linked-text a:hover { text-decoration-color: currentColor; }
</style>
