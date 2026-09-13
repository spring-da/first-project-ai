import { defineStore } from 'pinia'
import { onScopeDispose, reactive, ref, watch } from 'vue'
import * as api from '../services/sharing.ts'
import type { ShareBundleDetail, ShareBundleQuery, ShareBundleSummary, SharedContent, SharedPoolEntry, SharedPoolQuery, SharePage } from '../types/sharing.ts'
import { useAuthStore } from './auth.ts'
import { useNotificationStore } from './notifications.ts'

type ReadSlot = 'pool' | 'links' | 'content' | 'detail'
const emptyPage = <T>(): SharePage<T> => ({ items: [], total: 0, page: 0, size: 20 })

export const useSharingStore = defineStore('sharing', () => {
  const auth = useAuthStore()
  const notifications = useNotificationStore()
  const pool = ref<SharePage<SharedPoolEntry>>(emptyPage())
  const links = ref<SharePage<ShareBundleSummary>>(emptyPage())
  const content = ref<SharedContent | null>(null)
  const contentPoolId = ref('')
  const bundleDetail = ref<ShareBundleDetail | null>(null)
  const detailLinkId = ref('')
  const busy = ref(false)
  const requests = reactive<Record<ReadSlot, { loading: boolean; error: string }>>({
    pool: { loading: false, error: '' }, links: { loading: false, error: '' },
    content: { loading: false, error: '' }, detail: { loading: false, error: '' },
  })
  const controllers = new Map<ReadSlot, AbortController>()
  const timers = new Map<ReadSlot, ReturnType<typeof setTimeout>>()
  let lifetime = 0
  let poolQuery: SharedPoolQuery = {}
  let linksQuery: ShareBundleQuery = {}

  function cancel(slot: ReadSlot) {
    controllers.get(slot)?.abort()
    controllers.delete(slot)
    clearTimeout(timers.get(slot))
    timers.delete(slot)
    requests[slot].loading = false
  }

  function closePoolContent() {
    cancel('content')
    content.value = null
    contentPoolId.value = ''
    requests.content.error = ''
  }

  function closeBundleDetail() {
    cancel('detail')
    bundleDetail.value = null
    detailLinkId.value = ''
    requests.detail.error = ''
  }

  function cancelLists() { cancel('pool'); cancel('links') }

  function reset() {
    lifetime++
    cancelLists()
    closePoolContent()
    closeBundleDetail()
    pool.value = emptyPage()
    links.value = emptyPage()
    poolQuery = {}
    linksQuery = {}
    requests.pool.error = ''
    requests.links.error = ''
    busy.value = false
  }

  async function read<T>(slot: ReadSlot, request: (signal: AbortSignal) => Promise<T>, commit: (value: T) => void) {
    cancel(slot)
    if (!auth.workspaceKey) return false
    const owner = auth.workspaceKey
    const controller = new AbortController()
    controllers.set(slot, controller)
    requests[slot].loading = true
    requests[slot].error = ''
    const current = () => controllers.get(slot) === controller && !controller.signal.aborted && auth.workspaceKey === owner
    try {
      const value = await request(controller.signal)
      if (!current()) return false
      commit(value)
      return true
    } catch (cause) {
      if (!current()) return false
      requests[slot].error = cause instanceof Error ? cause.message : '共享内容加载失败，请重试'
      notifications.notify(requests[slot].error, { type: 'error' })
      return false
    } finally {
      if (current()) { controllers.delete(slot); requests[slot].loading = false }
    }
  }

  function loadPool(query: SharedPoolQuery = poolQuery) {
    poolQuery = { ...query }
    return read('pool', (signal) => api.listSharedPool(query, signal), (value) => { pool.value = value })
  }

  function loadLinks(query: ShareBundleQuery = linksQuery) {
    linksQuery = { ...query }
    return read('links', (signal) => api.listShareBundles(query, signal), (value) => { links.value = value })
  }

  function searchPool(query: SharedPoolQuery) {
    cancel('pool')
    poolQuery = { ...query }
    requests.pool.loading = true
    timers.set('pool', setTimeout(() => { void loadPool(query) }, 250))
  }

  function searchLinks(query: ShareBundleQuery) {
    cancel('links')
    linksQuery = { ...query }
    requests.links.loading = true
    timers.set('links', setTimeout(() => { void loadLinks(query) }, 250))
  }

  function openPoolContent(id: string) {
    closePoolContent()
    contentPoolId.value = id
    return read('content', (signal) => api.getPoolContent(id, signal), (value) => { content.value = value })
  }

  function openBundleDetail(id: string) {
    closeBundleDetail()
    detailLinkId.value = id
    return read('detail', (signal) => api.getShareBundle(id, signal), (value) => { bundleDetail.value = value })
  }

  async function mutate(action: () => Promise<void>, update: () => Promise<unknown>, message: string) {
    if (busy.value || !auth.workspaceKey) return false
    const owner = auth.workspaceKey
    const version = lifetime
    const current = () => owner === auth.workspaceKey && version === lifetime
    busy.value = true
    try {
      await action()
      if (!current()) return false
      notifications.notify(message, { type: 'success' })
      await update()
      return current()
    } catch (cause) {
      if (current()) notifications.notify(cause instanceof Error ? cause.message : '操作失败，请重试', { type: 'error' })
      return false
    } finally {
      if (current()) busy.value = false
    }
  }

  async function withdraw(ids: string[]) {
    const selected = [...new Set(ids)]
    if (!selected.length || busy.value) return false
    if (selected.some((id) => !pool.value.items.some((entry) => entry.id === id && entry.mine))) {
      notifications.notify('只能撤下当前列表中由你分享的内容', { type: 'warning' })
      return false
    }
    cancel('pool')
    return mutate(() => api.withdrawFromPool(selected), async () => {
      if (selected.includes(contentPoolId.value)) closePoolContent()
      pool.value.items = pool.value.items.filter((entry) => !selected.includes(entry.id))
      pool.value.total = Math.max(0, pool.value.total - selected.length)
      await loadPool({ ...poolQuery, page: Math.min(poolQuery.page ?? 0, Math.max(0, Math.ceil(pool.value.total / pool.value.size) - 1)) })
    }, `已从知识广场撤下 ${selected.length} 项内容`)
  }

  async function revoke(ids: string[]) {
    const selected = [...new Set(ids)]
    if (!selected.length || busy.value) return false
    cancel('links')
    if (selected.includes(detailLinkId.value)) cancel('detail')
    return mutate(() => api.revokeShareBundles(selected), async () => {
      if (selected.includes(detailLinkId.value)) closeBundleDetail()
      links.value.items = links.value.items.map((entry) => selected.includes(entry.id)
        ? { ...entry, active: false, revokedAt: new Date().toISOString() } : entry)
      await loadLinks()
    }, `已撤销 ${selected.length} 个外部链接`)
  }

  async function removeItem(linkId: string, itemId: string) {
    if (busy.value) return false
    cancel('links')
    if (detailLinkId.value === linkId) cancel('detail')
    return mutate(() => api.removeShareBundleItem(linkId, itemId), async () => {
      if (detailLinkId.value === linkId && bundleDetail.value) {
        bundleDetail.value.items = bundleDetail.value.items.map((entry) => entry.id === itemId
          ? { ...entry, available: false, removedAt: new Date().toISOString() } : entry)
      }
      await Promise.all([loadLinks(), detailLinkId.value === linkId ? openBundleDetail(linkId) : Promise.resolve()])
    }, '已从此链接移除所选内容')
  }

  watch(() => auth.workspaceKey, reset, { flush: 'sync' })
  onScopeDispose(reset)
  return { pool, links, content, contentPoolId, bundleDetail, detailLinkId, requests, busy,
    loadPool, loadLinks, searchPool, searchLinks, openPoolContent, closePoolContent, openBundleDetail, closeBundleDetail,
    withdraw, revoke, removeItem, cancelLists, reset }
})
