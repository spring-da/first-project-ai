import { defineStore } from 'pinia'
import { ref } from 'vue'
import { apiRequest, jsonBody } from '../services/api'
import { captureImageContext, primeImageCache } from '../services/markdownImages'
import type {
  CommunityMessage,
  CommunityMessagePage,
  CommunityReplyPage,
  SystemAnnouncement,
} from '../types'

export interface ReplyState {
  items: CommunityMessage[]
  nextPage: number | null
  loaded: boolean
  loading: boolean
}

export const useCommunicationStore = defineStore('communication', () => {
  const unreadAnnouncements = ref<SystemAnnouncement[]>([])
  const announcements = ref<SystemAnnouncement[]>([])
  const messages = ref<CommunityMessage[]>([])
  const replies = ref<Record<string, ReplyState>>({})
  const nextCursor = ref<string | null>(null)
  const hasMore = ref(true)
  const announcementLoading = ref(false)
  const messageLoading = ref(false)
  const loadingMore = ref(false)
  const mutating = ref(false)

  async function loadUnreadAnnouncements() {
    announcementLoading.value = true
    try {
      unreadAnnouncements.value = await apiRequest<SystemAnnouncement[]>('/announcements/unread')
    } finally {
      announcementLoading.value = false
    }
  }

  async function acknowledgeAnnouncement(id: string) {
    await apiRequest<void>(`/announcements/${encodeURIComponent(id)}/read`, { method: 'POST' })
    unreadAnnouncements.value = unreadAnnouncements.value.filter((item) => item.id !== id)
    announcements.value = announcements.value.map((item) => item.id === id ? { ...item, read: true } : item)
    if (!unreadAnnouncements.value.length) await loadUnreadAnnouncements()
  }

  async function loadAnnouncements(admin = false) {
    announcementLoading.value = true
    try {
      announcements.value = await apiRequest<SystemAnnouncement[]>(
        admin ? '/admin/announcements?limit=20' : '/announcements?limit=10',
      )
    } finally {
      announcementLoading.value = false
    }
  }

  async function publishAnnouncement(title: string, content: string) {
    return mutate(async () => {
      const result = await apiRequest<SystemAnnouncement>('/admin/announcements', {
        method: 'POST',
        body: jsonBody({ title, content }),
      })
      announcements.value = [result, ...announcements.value]
      return result
    })
  }

  async function archiveAnnouncement(id: string) {
    await mutate(() => apiRequest<void>(`/admin/announcements/${encodeURIComponent(id)}`, { method: 'DELETE' }))
    announcements.value = announcements.value.map((item) => item.id === id ? { ...item, active: false } : item)
  }

  async function loadMessages(reset = false) {
    if ((reset ? messageLoading.value : loadingMore.value) || (!reset && !hasMore.value)) return
    if (reset) {
      messageLoading.value = true
      nextCursor.value = null
      hasMore.value = true
    } else loadingMore.value = true
    try {
      const cursor = !reset && nextCursor.value ? `&cursor=${encodeURIComponent(nextCursor.value)}` : ''
      const page = await apiRequest<CommunityMessagePage>(`/community/messages?size=8${cursor}`)
      const incomingIds = new Set(page.items.map((item) => item.id))
      messages.value = reset
        ? page.items
        : [...messages.value.filter((item) => !incomingIds.has(item.id)), ...page.items]
      nextCursor.value = page.nextCursor
      hasMore.value = page.nextCursor !== null
    } finally {
      messageLoading.value = false
      loadingMore.value = false
    }
  }

  async function createMessage(content: string, image: File | null) {
    const imageContext = captureImageContext()
    return mutate(async () => {
      const form = messageForm(content, image)
      const created = await apiRequest<CommunityMessage>('/community/messages', {
        method: 'POST', body: form, timeoutMs: 90_000,
      })
      if (image && created.imageUrl) primeImageCache(created.imageUrl, image, imageContext)
      messages.value = [created, ...messages.value.filter((item) => item.id !== created.id)]
      return created
    })
  }

  async function loadReplies(messageId: string, reset = false) {
    const current = replies.value[messageId]
    if (current?.loading || (!reset && current?.loaded && current.nextPage === null)) return
    const page = reset || !current?.loaded ? 0 : current.nextPage ?? 0
    setReplyState(messageId, { ...(current ?? emptyReplyState()), loading: true })
    try {
      const result = await apiRequest<CommunityReplyPage>(
        `/community/messages/${encodeURIComponent(messageId)}/replies?page=${page}&size=10`,
      )
      const previous = reset || !current?.loaded ? [] : current.items
      const incomingIds = new Set(result.items.map((item) => item.id))
      setReplyState(messageId, {
        items: [...previous.filter((item) => !incomingIds.has(item.id)), ...result.items],
        nextPage: result.nextPage,
        loaded: true,
        loading: false,
      })
    } catch (error) {
      setReplyState(messageId, { ...(replies.value[messageId] ?? emptyReplyState()), loading: false })
      throw error
    }
  }

  async function createReply(messageId: string, content: string, image: File | null) {
    const imageContext = captureImageContext()
    return mutate(async () => {
      const previousReplyCount = messages.value.find((item) => item.id === messageId)?.replyCount ?? 0
      const created = await apiRequest<CommunityMessage>(
        `/community/messages/${encodeURIComponent(messageId)}/replies`,
        { method: 'POST', body: messageForm(content, image), timeoutMs: 90_000 },
      )
      if (image && created.imageUrl) primeImageCache(created.imageUrl, image, imageContext)
      messages.value = messages.value.map((item) => item.id === messageId
        ? { ...item, replyCount: item.replyCount + 1 }
        : item)
      const state = replies.value[messageId]
      if (state?.loaded) setReplyState(messageId, {
        ...state,
        items: [...state.items.filter((item) => item.id !== created.id), created],
      })
      else setReplyState(messageId, {
        items: [created],
        nextPage: previousReplyCount > 0 ? 0 : null,
        loaded: true,
        loading: false,
      })
      return created
    })
  }

  async function deleteMessage(message: CommunityMessage) {
    await mutate(() => apiRequest<void>(`/community/messages/${encodeURIComponent(message.id)}`, { method: 'DELETE' }))
    if (message.parentId) {
      const state = replies.value[message.parentId]
      if (state) setReplyState(message.parentId, {
        ...state,
        items: state.items.filter((item) => item.id !== message.id),
      })
      messages.value = messages.value.map((item) => item.id === message.parentId
        ? { ...item, replyCount: Math.max(0, item.replyCount - 1) }
        : item)
    } else {
      messages.value = messages.value.filter((item) => item.id !== message.id)
      const next = { ...replies.value }
      delete next[message.id]
      replies.value = next
    }
  }

  function setReplyState(messageId: string, state: ReplyState) {
    replies.value = { ...replies.value, [messageId]: state }
  }

  function emptyReplyState(): ReplyState {
    return { items: [], nextPage: null, loaded: false, loading: false }
  }

  function messageForm(content: string, image: File | null) {
    const form = new FormData()
    form.append('content', content)
    if (image) form.append('image', image)
    return form
  }

  async function mutate<T>(operation: () => Promise<T>) {
    mutating.value = true
    try {
      return await operation()
    } finally {
      mutating.value = false
    }
  }

  function clear() {
    unreadAnnouncements.value = []
    announcements.value = []
    messages.value = []
    replies.value = {}
    nextCursor.value = null
    hasMore.value = true
    announcementLoading.value = false
    messageLoading.value = false
    loadingMore.value = false
    mutating.value = false
  }

  return {
    unreadAnnouncements, announcements, messages, replies, nextCursor, hasMore,
    announcementLoading, messageLoading, loadingMore, mutating,
    loadUnreadAnnouncements, acknowledgeAnnouncement, loadAnnouncements,
    publishAnnouncement, archiveAnnouncement, loadMessages, createMessage,
    loadReplies, createReply, deleteMessage, clear,
  }
})
