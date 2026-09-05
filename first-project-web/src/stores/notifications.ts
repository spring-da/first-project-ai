import { computed, onScopeDispose, ref } from 'vue'
import { defineStore } from 'pinia'
import { createClientId } from '../utils/clientId.ts'

export type NotificationType = 'success' | 'info' | 'warning' | 'error'
export interface Notification {
  id: string
  type: NotificationType
  title: string
  message: string
  details: string[]
  createdAt: number
  updatedAt: number
  occurrences: number
  read: boolean
}

interface NotificationOptions {
  type?: NotificationType
  title?: string
  details?: string[]
}

const TITLES: Record<NotificationType, string> = { success: '操作成功', info: '温馨提示', warning: '请留意', error: '操作未完成' }
const DURATIONS: Record<NotificationType, number> = { success: 4000, info: 4500, warning: 5500, error: 6000 }
const MAX_HISTORY = 100
const MAX_TOASTS = 3
const MERGE_WINDOW = 15_000

function isNotification(value: unknown): value is Notification {
  if (!value || typeof value !== 'object') return false
  const item = value as Notification
  return typeof item.id === 'string' && !!item.id && Object.hasOwn(TITLES, item.type)
    && typeof item.title === 'string' && typeof item.message === 'string'
    && Array.isArray(item.details) && item.details.every((detail) => typeof detail === 'string')
    && Number.isFinite(item.createdAt) && Number.isFinite(item.updatedAt)
    && Number.isFinite(new Date(item.createdAt).getTime()) && Number.isFinite(new Date(item.updatedAt).getTime())
    && Number.isSafeInteger(item.occurrences) && item.occurrences > 0 && typeof item.read === 'boolean'
}

export const useNotificationStore = defineStore('notifications', () => {
  const history = ref<Notification[]>([])
  const activeIds = ref<string[]>([])
  const isOpen = ref(false)
  const historyPersisted = ref(true)
  const unreadCount = computed(() => history.value.filter((item) => !item.read).length)
  const toasts = computed(() => activeIds.value.flatMap((id) => {
    const item = history.value.find((notification) => notification.id === id)
    return item ? [item] : []
  }))
  const timers = new Map<string, { handle?: ReturnType<typeof setTimeout>; startedAt: number; remaining: number }>()
  let storageKey = ''

  function persist() {
    if (!storageKey) return
    try {
      if (history.value.length) window.localStorage.setItem(storageKey, JSON.stringify(history.value))
      else window.localStorage.removeItem(storageKey)
      historyPersisted.value = true
    } catch {
      // Never turn a storage failure into another notification (or evict a user's drafts).
      historyPersisted.value = false
    }
  }

  function dismiss(id: string) {
    clearTimeout(timers.get(id)?.handle)
    timers.delete(id)
    activeIds.value = activeIds.value.filter((item) => item !== id)
  }

  function dismissAll() { [...activeIds.value].forEach(dismiss) }

  function setOwner(ownerId: string | null) {
    const nextKey = `devnest_notifications_v1:${ownerId ? `user:${encodeURIComponent(ownerId)}` : 'guest'}`
    if (storageKey === nextKey) return
    dismissAll()
    isOpen.value = false
    storageKey = nextKey
    history.value = []
    try {
      const stored: unknown = JSON.parse(window.localStorage.getItem(storageKey) ?? '[]')
      if (Array.isArray(stored)) {
        const ids = new Set<string>()
        history.value = stored.filter(isNotification).filter((item) => {
          if (ids.has(item.id)) return false
          ids.add(item.id)
          return true
        }).sort((a, b) => b.updatedAt - a.updatedAt).slice(0, MAX_HISTORY)
      }
      historyPersisted.value = true
    } catch { historyPersisted.value = false }
  }

  function resume(id: string) {
    const timer = timers.get(id)
    if (!timer || timer.handle !== undefined) return
    timer.startedAt = Date.now()
    timer.handle = setTimeout(() => dismiss(id), timer.remaining)
  }

  function pause(id: string) {
    const timer = timers.get(id)
    if (!timer || timer.handle === undefined) return
    clearTimeout(timer.handle)
    timer.handle = undefined
    timer.remaining = Math.max(0, timer.remaining - (Date.now() - timer.startedAt))
  }

  function notify(message: string, options: NotificationOptions = {}) {
    if (!message.trim()) return
    const type = options.type ?? 'info'
    const details = (options.details ?? []).slice(0, 100)
    const now = Date.now()
    const existing = history.value.find((item) => item.type === type && item.message === message
      && now - item.updatedAt < MERGE_WINDOW && JSON.stringify(item.details) === JSON.stringify(details))
    const item: Notification = existing
      ? { ...existing, updatedAt: now, occurrences: existing.occurrences + 1, read: false }
      : { id: createClientId(), type, title: options.title ?? TITLES[type], message, details, createdAt: now, updatedAt: now, occurrences: 1, read: false }
    history.value = [item, ...history.value.filter((entry) => entry.id !== item.id)].slice(0, MAX_HISTORY)
    // Repeated errors merge without extending the lifetime of an already visible toast.
    if (!activeIds.value.includes(item.id)) {
      while (activeIds.value.length >= MAX_TOASTS) dismiss(activeIds.value[0]!)
      activeIds.value = [...activeIds.value, item.id]
      timers.set(item.id, { startedAt: now, remaining: DURATIONS[type] })
      resume(item.id)
    }
    persist()
    return item.id
  }

  function markRead(id: string) {
    const item = history.value.find((entry) => entry.id === id)
    if (item) item.read = true
    persist()
  }

  function markAllRead() { history.value.forEach((item) => { item.read = true }); persist() }

  function remove(id: string) {
    dismiss(id)
    history.value = history.value.filter((item) => item.id !== id)
    persist()
  }

  function clearRead() {
    history.value.filter((item) => item.read).forEach((item) => dismiss(item.id))
    history.value = history.value.filter((item) => !item.read)
    persist()
  }

  function clearAll() { dismissAll(); history.value = []; persist() }
  function open() { isOpen.value = true; dismissAll() }
  function close() { isOpen.value = false }
  onScopeDispose(dismissAll)

  return { history, toasts, isOpen, historyPersisted, unreadCount, notify, dismiss, pause, resume, setOwner, markRead, markAllRead, remove, clearRead, clearAll, open, close }
})
