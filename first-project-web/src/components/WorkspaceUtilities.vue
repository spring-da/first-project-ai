<script setup lang="ts">
import { Bell, Moon, Sun } from 'lucide-vue-next'
import { useNotificationStore } from '../stores/notifications'
import { useThemeStore } from '../stores/theme'

const notifications = useNotificationStore()
const theme = useThemeStore()
</script>

<template>
  <div class="workspace-utilities">
    <button class="utility-button notification-bell" type="button" :aria-label="`消息通知${notifications.unreadCount ? `，${notifications.unreadCount} 条未读` : ''}`" title="消息通知" :aria-expanded="notifications.isOpen" aria-controls="notification-center" @click="notifications.open">
      <Bell :size="18" />
      <Transition name="badge"><span v-if="notifications.unreadCount" class="notification-count">{{ notifications.unreadCount > 99 ? '99+' : notifications.unreadCount }}</span></Transition>
    </button>
    <button class="utility-button" type="button" :aria-label="theme.isDark ? '切换到浅色主题' : '切换到深色主题'" :title="theme.isDark ? '切换到浅色主题' : '切换到深色主题'" @click="theme.toggleTheme">
      <Transition name="icon-swap" mode="out-in"><Sun v-if="theme.isDark" key="sun" :size="18" /><Moon v-else key="moon" :size="18" /></Transition>
    </button>
  </div>
</template>

<style scoped>
.workspace-utilities { display: flex; flex-shrink: 0; align-items: center; gap: 4px; }
.utility-button { position: relative; display: grid; place-items: center; width: 36px; height: 36px; padding: 0; color: var(--muted); border: 0; border-radius: 9px; background: transparent; cursor: pointer; transition: color var(--motion-fast), background var(--motion-fast), transform var(--motion-base) var(--ease-emphasized); }
.utility-button:hover { color: var(--text); background: var(--surface-raised); transform: translateY(-1px); }
.utility-button:active { transform: scale(.9); }
.notification-count { position: absolute; top: -2px; right: -3px; min-width: 17px; height: 17px; display: grid; place-items: center; padding: 0 4px; color: #fff; border: 2px solid var(--panel); border-radius: 20px; background: var(--brand); font-size: 9px; font-weight: 700; line-height: 1; }
.badge-enter-active, .badge-leave-active { transition: opacity var(--motion-fast), transform var(--motion-base) var(--ease-emphasized); }
.badge-enter-from, .badge-leave-to { opacity: 0; transform: scale(.45); }
@media (max-width: 720px) { .workspace-utilities { gap: 0; } .utility-button { width: 32px; height: 34px; } }
</style>
