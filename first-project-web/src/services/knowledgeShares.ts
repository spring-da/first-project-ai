import type { KnowledgeShareLink, KnowledgeShareSecret, PublicKnowledgeShare } from '../types'
import { apiRequest, jsonBody } from './api'

export type ShareableKnowledgeKind = 'snippets' | 'logs'

export function listKnowledgeShares(kind: ShareableKnowledgeKind, resourceId: string) {
  return apiRequest<KnowledgeShareLink[]>(`/${kind}/${resourceId}/shares`)
}

export function createKnowledgeShare(kind: ShareableKnowledgeKind, resourceId: string, expiresAt: string) {
  return apiRequest<KnowledgeShareSecret>(`/${kind}/${resourceId}/shares`, {
    method: 'POST',
    body: jsonBody({ expiresAt }),
  })
}

export function revokeKnowledgeShare(kind: ShareableKnowledgeKind, resourceId: string, shareId: string) {
  return apiRequest<void>(`/${kind}/${resourceId}/shares/${shareId}`, { method: 'DELETE' })
}

export function getPublicKnowledgeShare(token: string, signal?: AbortSignal) {
  return apiRequest<PublicKnowledgeShare>(`/public/knowledge-shares/${encodeURIComponent(token)}`, {
    authenticated: false,
    signal,
  })
}
