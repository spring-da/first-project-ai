<script setup lang="ts">
import ShareDialog from './ShareDialog.vue'
defineProps<{ kind: 'snippets' | 'flowcharts'; resourceId: string; resourceTitle: string }>()
defineEmits<{ close: [] }>()
</script>
<template>
  <ShareDialog :items="[{ type: kind === 'snippets' ? 'SNIPPET' : 'FLOWCHART', id: resourceId, title: resourceTitle }]" @close="$emit('close')" />
</template>
