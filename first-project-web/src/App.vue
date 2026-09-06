<script setup lang="ts">
import { watch } from 'vue'
import AppNotifications from './components/AppNotifications.vue'
import AppConfirmation from './components/AppConfirmation.vue'
import AppRequestProgress from './components/AppRequestProgress.vue'
import { useConfirmationStore } from './stores/confirmation'
import { useRoute } from 'vue-router'
import { useAuthStore } from './stores/auth'
import { useNotificationStore } from './stores/notifications'
import { useWorkspaceStore } from './stores/workspace'
import { useCommunicationStore } from './stores/communication'

const auth = useAuthStore()
const workspace = useWorkspaceStore()
const notifications = useNotificationStore()
const confirmation = useConfirmationStore()
const communication = useCommunicationStore()
const route = useRoute()
watch(() => route.fullPath, () => confirmation.cancel())
watch(() => auth.workspaceKey, () => confirmation.cancel(), { flush: 'sync' })
watch(() => auth.workspaceKey, (id) => notifications.setOwner(id ?? null), { immediate: true, flush: 'sync' })
watch(() => auth.workspaceKey, () => workspace.clear(), { flush: 'sync' })
watch(() => auth.session?.user.id, () => communication.clear(), { flush: 'sync' })
watch(() => workspace.error, (message) => { if (message) notifications.notify(message, { type: 'error' }) })
watch(() => auth.error, (message) => { if (message) notifications.notify(message, { type: 'error', title: '账户验证失败' }) })
</script>

<template>
  <AppRequestProgress />
  <RouterView v-slot="{ Component, route: activeRoute }">
    <Transition name="app-route" mode="out-in">
      <component :is="Component" :key="`${auth.workspaceKey}:${activeRoute.matched[0]?.path ?? activeRoute.path}`" />
    </Transition>
  </RouterView>
  <AppNotifications />
  <AppConfirmation />
</template>
