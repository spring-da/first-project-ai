import type { MarkdownShareLink, MarkdownShareSecret, PublicMarkdownShare } from '../types'
import { apiRequest, jsonBody } from './api'

export function listMarkdownShares(documentId: string) {
  return apiRequest<MarkdownShareLink[]>(`/markdown-documents/${documentId}/shares`)
}

export function createMarkdownShare(documentId: string, expiresAt: string) {
  return apiRequest<MarkdownShareSecret>(`/markdown-documents/${documentId}/shares`, {
    method: 'POST',
    body: jsonBody({ expiresAt }),
  })
}

export function revokeMarkdownShare(documentId: string, shareId: string) {
  return apiRequest<void>(`/markdown-documents/${documentId}/shares/${shareId}`, { method: 'DELETE' })
}

export function getPublicMarkdownShare(token: string, signal?: AbortSignal) {
  return apiRequest<PublicMarkdownShare>(`/public/markdown-shares/${encodeURIComponent(token)}`, {
    authenticated: false,
    signal,
  })
}
