import { apiRequest, jsonBody } from './api.ts'
import type {
  PublicShareBundle, ShareBundleDetail, ShareBundleQuery, ShareBundleSecret, ShareBundleSummary,
  SharedContent, SharedPoolEntry, SharedPoolQuery, SharePage, ShareReference,
} from '../types/sharing.ts'

const references = (items: ShareReference[]) => items.map(({ type, id }) => ({ type, id }))
const segment = encodeURIComponent

function listQuery(query: ShareBundleQuery) {
  const params = new URLSearchParams({ page: String(query.page ?? 0), size: String(query.size ?? 20) })
  if (query.q?.trim()) params.set('q', query.q.trim())
  return params
}

export function publishToPool(items: ShareReference[]) {
  return apiRequest<{ publishedCount: number; existingCount: number }>('/sharing/pool', {
    method: 'POST', body: jsonBody({ items: references(items) }),
  })
}

export function createShareBundle(title: string, items: ShareReference[], expiresAt: string) {
  return apiRequest<ShareBundleSecret>('/sharing/links', {
    method: 'POST', body: jsonBody({ title, items: references(items), expiresAt }),
  })
}

export function listSharedPool(query: SharedPoolQuery = {}, signal?: AbortSignal) {
  const params = listQuery(query)
  if (query.type) params.set('type', query.type)
  if (query.mine !== undefined) params.set('mine', String(query.mine))
  return apiRequest<SharePage<SharedPoolEntry>>(`/sharing/pool?${params}`, { signal, cache: 'no-store' })
}

export function getPoolContent(id: string, signal?: AbortSignal) {
  return apiRequest<SharedContent>(`/sharing/pool/${segment(id)}`, { signal, cache: 'no-store' })
}

export function withdrawFromPool(ids: string[]) {
  return apiRequest<void>('/sharing/pool/revoke', { method: 'POST', body: jsonBody({ ids }) })
}

export function listShareBundles(query: ShareBundleQuery = {}, signal?: AbortSignal) {
  return apiRequest<SharePage<ShareBundleSummary>>(`/sharing/links?${listQuery(query)}`, { signal, cache: 'no-store' })
}

export function getShareBundle(id: string, signal?: AbortSignal) {
  return apiRequest<ShareBundleDetail>(`/sharing/links/${segment(id)}`, { signal, cache: 'no-store' })
}

export function revokeShareBundles(ids: string[]) {
  return apiRequest<void>('/sharing/links/revoke', { method: 'POST', body: jsonBody({ ids }) })
}

export function removeShareBundleItem(linkId: string, itemId: string) {
  return apiRequest<void>(`/sharing/links/${segment(linkId)}/items/${segment(itemId)}`, { method: 'DELETE' })
}

export function getPublicShareBundle(token: string, signal?: AbortSignal) {
  return apiRequest<PublicShareBundle>(`/public/share-bundles/${segment(token)}`, { authenticated: false, signal, cache: 'no-store' })
}

export function getPublicBundleContent(token: string, itemId: string, signal?: AbortSignal) {
  return apiRequest<SharedContent>(`/public/share-bundles/${segment(token)}/items/${segment(itemId)}`, { authenticated: false, signal, cache: 'no-store' })
}
