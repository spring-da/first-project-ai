import { getRequestWorkspaceOwner } from './workspaceContext.ts'
import { apiDownload, apiRequest, readStoredSession } from './api.ts'
import { IMAGE_TYPES, MAX_IMAGE_BYTES } from '../utils/markdownEditing.ts'
import { markdownImageCache } from './markdownImageCache.ts'

export interface UploadedImage { id: string; url: string; contentType: string; size: number }

export function uploadMarkdownImage(file: File, signal: AbortSignal) {
  const form = new FormData()
  form.append('file', file)
  return apiRequest<UploadedImage>('/markdown-images', { method: 'POST', body: form, signal, timeoutMs: 90_000 })
}

type PendingImage = { controller: AbortController; promise: Promise<Blob>; subscribers: number }
const pendingImages = new Map<string, PendingImage>()
let cacheContext = ''
let cacheRevision = 0

/** An opaque in-memory context; never persist or expose this value. */
export function captureImageContext() {
  const session = readStoredSession()
  return JSON.stringify([session?.user.id, session?.accessToken, getRequestWorkspaceOwner()])
}

function normalizeImagePath(path: string) {
  return path.startsWith('/api/v1/') ? path.slice('/api/v1'.length) : path
}

function ensureImageContext() {
  const context = captureImageContext()
  if (context !== cacheContext) {
    clearMarkdownImageCache()
    cacheContext = context
  }
  return context
}

function validateImage(blob: Blob) {
  if (!IMAGE_TYPES.includes(blob.type) || !blob.size || blob.size > MAX_IMAGE_BYTES) throw new Error('图片响应无效')
  return blob
}

export function primeImageCache(path: string, blob: Blob, context = captureImageContext()) {
  if (context !== captureImageContext() || !IMAGE_TYPES.includes(blob.type) || !blob.size || blob.size > MAX_IMAGE_BYTES) return
  ensureImageContext()
  markdownImageCache.set(normalizeImagePath(path), blob)
}

export function primeMarkdownImageCache(
  id: string,
  blob: Blob,
  basePath = '/markdown-images',
  authenticated = true,
) {
  if (authenticated) primeImageCache(`${basePath}/${id}`, blob)
}

export function clearMarkdownImageCache() {
  cacheRevision++
  for (const pending of pendingImages.values()) pending.controller.abort()
  pendingImages.clear()
  markdownImageCache.clear()
  cacheContext = ''
}

async function downloadImage(path: string, signal: AbortSignal, authenticated: boolean, reload = false) {
  const { blob } = await apiDownload(path, {
    signal, authenticated, trackActivity: false,
    ...(!authenticated || path.startsWith('/sharing/pool/') ? { cache: 'no-store' as const } : reload ? { cache: 'reload' as const } : {}),
    headers: { Accept: 'image/*, application/problem+json' },
  })
  signal.throwIfAborted()
  return validateImage(blob)
}

/** Share one transfer across visible consumers; cancelling one does not cancel the others. */
export async function loadImage(
  path: string,
  signal: AbortSignal,
  authenticated = true,
  options: { reload?: boolean } = {},
) {
  signal.throwIfAborted()
  path = normalizeImagePath(path)
  // Shared images must check withdrawal/expiry on every new read, including the internal pool.
  if (!authenticated || path.startsWith('/sharing/pool/')) return downloadImage(path, signal, authenticated)
  const context = ensureImageContext()
  // A valid MIME type does not guarantee that the browser can decode the bytes.
  if (options.reload) markdownImageCache.delete(path)
  const cached = markdownImageCache.get(path)
  if (cached) return cached

  let pending = pendingImages.get(path)
  if (!pending) {
    const controller = new AbortController()
    const revision = cacheRevision
    const entry: PendingImage = {
      controller, subscribers: 0,
      promise: downloadImage(path, controller.signal, true, options.reload).then((blob) => {
        if (controller.signal.aborted || revision !== cacheRevision || context !== captureImageContext()) {
          throw new DOMException('Image context changed', 'AbortError')
        }
        markdownImageCache.set(path, blob)
        return blob
      }).finally(() => { if (pendingImages.get(path) === entry) pendingImages.delete(path) }),
    }
    pendingImages.set(path, entry)
    pending = entry
  }
  const request = pending
  request.subscribers++
  return new Promise<Blob>((resolve, reject) => {
    let settled = false
    const release = () => {
      if (settled) return
      settled = true
      signal.removeEventListener('abort', abort)
      request.subscribers--
      if (!request.subscribers && pendingImages.get(path) === request) {
        pendingImages.delete(path)
        request.controller.abort()
      }
    }
    const abort = () => { release(); reject(signal.reason) }
    signal.addEventListener('abort', abort, { once: true })
    request.promise.then((blob) => {
      if (settled) return
      release()
      resolve(blob)
    }, (error: unknown) => {
      if (settled) return
      release()
      reject(error)
    })
    if (signal.aborted) abort()
  })
}

export function loadMarkdownImage(id: string, signal: AbortSignal, basePath = '/markdown-images', authenticated = true) {
  return loadImage(`${basePath}/${id}`, signal, authenticated)
}
