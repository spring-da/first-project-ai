import { captureWorkspaceRequest, WORKSPACE_OWNER_HEADER } from './workspaceContext.ts'
import type { AuthSession } from '../types'
import { beginRequest } from './requestActivity.ts'

export const SESSION_KEY = 'devnest_web_session_v1'

const configuredBaseUrl = import.meta.env?.VITE_API_BASE_URL?.trim()
const API_BASE_URL = (configuredBaseUrl || '/api/v1').replace(/\/$/, '')

export class ApiError extends Error {
  readonly status?: number
  readonly code?: string

  constructor(message: string, status?: number, code?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

export function readStoredSession(): AuthSession | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY)
    if (!raw) return null
    const session = JSON.parse(raw) as AuthSession
    if (!session.accessToken || !session.user) throw new Error('invalid session')
    if (session.expiresAt && session.expiresAt <= Date.now()) {
      localStorage.removeItem(SESSION_KEY)
      return null
    }
    return {
      ...session,
      user: {
        ...session.user,
        role: session.user.role === 'ADMIN' ? 'ADMIN' : 'USER',
        mustChangePassword: session.user.mustChangePassword === true,
      },
    }
  } catch {
    try { localStorage.removeItem(SESSION_KEY) } catch { /* Storage may be disabled. */ }
    return null
  }
}

function expireSession() {
  try { localStorage.removeItem(SESSION_KEY) } catch { /* Storage may be disabled. */ }
  window.dispatchEvent(new CustomEvent('devnest:unauthorized'))
}

function handleAuthError(status: number, code: string | undefined, authenticated: boolean) {
  if (!authenticated) return
  if (status === 401) expireSession()
  if (status === 403 && code === 'PASSWORD_CHANGE_REQUIRED') {
    window.dispatchEvent(new CustomEvent('devnest:password-change-required'))
  }
}

export async function apiRequest<T>(
  path: string,
  options: RequestInit & { authenticated?: boolean; timeoutMs?: number } = {},
): Promise<T> {
  const context = captureWorkspaceRequest(path, options.authenticated !== false)
  const controller = new AbortController()
  const timer = window.setTimeout(() => controller.abort(), options.timeoutMs ?? 12_000)
  const onAbort = () => controller.abort()
  if (options.signal?.aborted) controller.abort()
  else options.signal?.addEventListener('abort', onAbort, { once: true })
  context?.signal.addEventListener('abort', onAbort, { once: true })
  const headers = new Headers(options.headers)
  headers.delete(WORKSPACE_OWNER_HEADER)
  if (context?.ownerId) headers.set(WORKSPACE_OWNER_HEADER, context.ownerId)
  let releaseRequest = () => {}
  headers.set('Accept', 'application/json')
  if (options.body && !(options.body instanceof FormData)) headers.set('Content-Type', 'application/json')

  try {
    if (options.authenticated !== false) {
      const session = readStoredSession()
      if (!session) { expireSession(); throw new ApiError('登录状态已失效，请重新登录', 401) }
      headers.set('Authorization', `${session.tokenType} ${session.accessToken}`)
    }
    releaseRequest = beginRequest()
    const response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      headers,
      signal: controller.signal,
    })
    context?.signal.throwIfAborted()
    if (response.status === 204) return undefined as T

    const contentType = response.headers.get('content-type') ?? ''
    const payload = contentType.includes('json') ? await response.json() : null
    context?.signal.throwIfAborted()
    if (!response.ok) {
      const message = payload?.detail || (response.status === 401 && options.authenticated === false ? '邮箱或密码不正确，请重试。' : `请求失败（${response.status}）`)
      handleAuthError(response.status, payload?.code, options.authenticated !== false)
      throw new ApiError(message, response.status, payload?.code)
    }
    return payload as T
  } catch (error) {
    if (error instanceof ApiError) throw error
    if (context?.signal.aborted) throw new ApiError('工作区已切换，请重新操作', undefined, 'WORKSPACE_CHANGED')
    if (options.signal?.aborted) throw error
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new ApiError('连接服务器超时，请稍后重试')
    }
    throw new ApiError('暂时无法连接服务器，请确认后端服务已启动')
  } finally {
    releaseRequest()
    window.clearTimeout(timer)
    options.signal?.removeEventListener('abort', onAbort)
    context?.signal.removeEventListener('abort', onAbort)
  }
}

export async function apiDownload(
  path: string,
  options: RequestInit & { authenticated?: boolean } = {},
): Promise<{ blob: Blob; fileName: string | null }> {
  const context = captureWorkspaceRequest(path, options.authenticated !== false)
  const controller = new AbortController()
  const timer = window.setTimeout(() => controller.abort(), 30_000)
  const onAbort = () => controller.abort()
  if (options.signal?.aborted) controller.abort()
  else options.signal?.addEventListener('abort', onAbort, { once: true })
  context?.signal.addEventListener('abort', onAbort, { once: true })
  const headers = new Headers(options.headers)
  headers.delete(WORKSPACE_OWNER_HEADER)
  if (context?.ownerId) headers.set(WORKSPACE_OWNER_HEADER, context.ownerId)
  let releaseRequest = () => {}
  if (!headers.has('Accept')) headers.set('Accept', 'application/zip, text/markdown, application/octet-stream, application/problem+json, application/json')
  if (options.body) headers.set('Content-Type', 'application/json')

  try {
    if (options.authenticated !== false) {
      const session = readStoredSession()
      if (!session) { expireSession(); throw new ApiError('登录状态已失效，请重新登录', 401) }
      headers.set('Authorization', `${session.tokenType} ${session.accessToken}`)
    }
    releaseRequest = beginRequest()
    const response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      headers,
      signal: controller.signal,
    })
    context?.signal.throwIfAborted()
    if (!response.ok) {
      const contentType = response.headers.get('content-type') ?? ''
      const payload = contentType.includes('json') ? await response.json() : null
      context?.signal.throwIfAborted()
      handleAuthError(response.status, payload?.code, options.authenticated !== false)
      throw new ApiError(payload?.detail || `下载失败（${response.status}）`, response.status, payload?.code)
    }

    const disposition = response.headers.get('content-disposition') ?? ''
    const encodedName = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
    const plainName = disposition.match(/filename="?([^";]+)"?/i)?.[1]
    let fileName: string | null = encodedName || plainName || null
    if (fileName && encodedName) {
      try { fileName = decodeURIComponent(fileName) } catch { /* Keep the server value. */ }
    }
    const blob = await response.blob()
    context?.signal.throwIfAborted()
    return { blob, fileName }
  } catch (error) {
    if (error instanceof ApiError) throw error
    if (context?.signal.aborted) throw new ApiError('工作区已切换，请重新操作', undefined, 'WORKSPACE_CHANGED')
    if (options.signal?.aborted) throw error
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new ApiError('文件生成超时，请缩小导出范围后重试')
    }
    throw new ApiError('暂时无法下载文件，请确认后端服务已启动')
  } finally {
    releaseRequest()
    window.clearTimeout(timer)
    options.signal?.removeEventListener('abort', onAbort)
    context?.signal.removeEventListener('abort', onAbort)
  }
}

export function jsonBody(value: unknown): string {
  return JSON.stringify(value)
}
