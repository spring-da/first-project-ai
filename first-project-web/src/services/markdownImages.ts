import { getRequestWorkspaceOwner } from './workspaceContext'
import { apiDownload, apiRequest, readStoredSession } from './api'
import { IMAGE_TYPES, MAX_IMAGE_BYTES } from '../utils/markdownEditing'
import { markdownImageCache } from './markdownImageCache'

export interface UploadedImage { id: string; url: string; contentType: string; size: number }

export function uploadMarkdownImage(file: File, signal: AbortSignal) {
  const form = new FormData()
  form.append('file', file)
  return apiRequest<UploadedImage>('/markdown-images', { method: 'POST', body: form, signal, timeoutMs: 90_000 })
}

function imageCacheKey(id: string, basePath: string, authenticated: boolean) {
  const scope = authenticated ? `account:${readStoredSession()?.user.id ?? 'signed-out'}:workspace:${getRequestWorkspaceOwner() ?? 'self'}` : 'public'
  return `${scope}:${basePath}/${id}`
}

export function primeMarkdownImageCache(
  id: string,
  blob: Blob,
  basePath = '/markdown-images',
  authenticated = true,
) {
  if (!IMAGE_TYPES.includes(blob.type) || blob.size > MAX_IMAGE_BYTES) return
  markdownImageCache.set(imageCacheKey(id, basePath, authenticated), blob)
}

export function clearMarkdownImageCache() {
  markdownImageCache.clear()
}

export async function loadMarkdownImage(
  id: string,
  signal: AbortSignal,
  basePath = '/markdown-images',
  authenticated = true,
) {
  const cacheKey = imageCacheKey(id, basePath, authenticated)
  const cached = markdownImageCache.get(cacheKey)
  if (cached) return cached
  const { blob } = await apiDownload(`${basePath}/${id}`, {
    signal,
    authenticated,
    headers: { Accept: 'image/*, application/problem+json' },
  })
  if (!IMAGE_TYPES.includes(blob.type) || blob.size > MAX_IMAGE_BYTES) throw new Error('图片响应无效')
  markdownImageCache.set(cacheKey, blob)
  return blob
}
