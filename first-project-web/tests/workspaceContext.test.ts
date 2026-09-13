import assert from 'node:assert/strict'
import { afterEach, beforeEach, test } from 'node:test'
import { apiDownload, apiRequest, readStoredSession, writeStoredSession } from '../src/services/api.ts'
import { resetWorkspaceRequests, setRequestWorkspace } from '../src/services/workspaceContext.ts'

const events: string[] = []
beforeEach(() => {
  events.length = 0
  Object.defineProperty(globalThis, 'window', { configurable: true, value: { setTimeout, clearTimeout, dispatchEvent: (event: Event) => events.push(event.type) } })
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: {
    getItem: () => JSON.stringify({
      accessToken: 'admin-token', tokenType: 'Bearer', expiresAt: Date.now() + 60_000,
      user: { id: 'admin', email: 'admin@example.com', role: 'ADMIN' },
    }),
    setItem: () => {}, removeItem: () => {},
  } })
})
afterEach(() => { resetWorkspaceRequests(); Reflect.deleteProperty(globalThis, 'window'); Reflect.deleteProperty(globalThis, 'localStorage') })

test('member context applies only to workspace data; authentication and system communication keep the real identity', async (context) => {
  setRequestWorkspace('member')
  const calls: Array<[string, string | null]> = []
  context.mock.method(globalThis, 'fetch', async (url, options) => {
    calls.push([String(url), options.headers.get('X-Workspace-Owner')])
    if (String(url).includes('/public/')) assert.equal(options.headers.get('Authorization'), null)
    else assert.equal(options.headers.get('Authorization'), 'Bearer admin-token')
    return Response.json({})
  })
  await apiRequest('/tasks', { method: 'POST', body: '{}' })
  await apiRequest('/profile/avatar', { method: 'POST', body: new FormData() })
  await apiRequest('/markdown-documents/id/shares')
  await apiRequest('/flowcharts?q=approval')
  await apiRequest('/flowcharts/id', { method: 'PUT', body: '{}' })
  await apiDownload('/markdown-documents/export', { method: 'POST', body: '{}' })
  await apiDownload('/markdown-images/image')
  await apiRequest('/auth/me')
  await apiRequest('/admin/accounts')
  await apiRequest('/announcements/unread')
  await apiRequest('/community/messages')
  await apiDownload('/community/messages/message/image')
  await apiDownload('/user-avatars/user')
  await apiDownload('/public/markdown-shares/token/images/image', { authenticated: false })
  await apiRequest('/public/knowledge-shares/token', { authenticated: false })
  assert.deepEqual(calls.map(([, owner]) => owner), [
    'member', 'member', 'member', 'member', 'member', 'member', 'member', null, null, null, null, null, null, null, null,
  ])
  setRequestWorkspace(null)
  await apiRequest('/tasks')
  assert.equal(calls.at(-1)?.[1], null)
})

test('switching workspace aborts writes and rejects late responses even if transport ignores abort', async (context) => {
  setRequestWorkspace('member-a')
  let finish!: (response: Response) => void
  let signal!: AbortSignal
  context.mock.method(globalThis, 'fetch', (_url, options) => {
    signal = options.signal
    return new Promise<Response>((resolve) => { finish = resolve })
  })
  const pending = apiRequest('/flowcharts/id', { method: 'PUT', body: '{}' })
  setRequestWorkspace('member-b')
  assert.equal(signal.aborted, true)
  finish(Response.json({ id: 'old-task' }))
  await assert.rejects(pending, { code: 'WORKSPACE_CHANGED' })
})

test('late image bodies cannot be cached in a newly selected workspace', async (context) => {
  let finish!: (blob: Blob) => void
  const response = new Response('image')
  context.mock.method(response, 'blob', () => new Promise<Blob>((resolve) => { finish = resolve }))
  context.mock.method(globalThis, 'fetch', async () => response)
  const pending = apiDownload('/markdown-images/id')
  await new Promise((resolve) => setTimeout(resolve, 0))
  setRequestWorkspace('member')
  finish(new Blob(['old image']))
  await assert.rejects(pending, { code: 'WORKSPACE_CHANGED' })
})

test('local expiry triggers logout without making an HTTP request', async (context) => {
  context.mock.method(localStorage, 'getItem', () => null)
  const fetch = context.mock.method(globalThis, 'fetch', async () => Response.json({}))
  await assert.rejects(apiRequest('/tasks'), { status: 401 })
  assert.equal(fetch.mock.callCount(), 0)
  assert.deepEqual(events, ['devnest:unauthorized'])
})

test('download errors enforce password changes while public 401 errors preserve the account session', async (context) => {
  context.mock.method(globalThis, 'fetch', async () => Response.json({ detail: 'Change password', code: 'PASSWORD_CHANGE_REQUIRED' }, { status: 403 }))
  await assert.rejects(apiDownload('/markdown-images/id'), { code: 'PASSWORD_CHANGE_REQUIRED' })
  assert.deepEqual(events, ['devnest:password-change-required'])
  events.length = 0
  context.mock.method(globalThis, 'fetch', async () => Response.json({}, { status: 401 }))
  await assert.rejects(apiDownload('/public/markdown-shares/invalid/images/id', { authenticated: false }), { status: 401 })
  assert.deepEqual(events, [])
})

test('unavailable browser storage does not crash session initialization', (context) => {
  context.mock.method(localStorage, 'getItem', () => { throw new Error('storage disabled') })
  context.mock.method(localStorage, 'removeItem', () => { throw new Error('storage disabled') })
  assert.equal(readStoredSession(), null)
})

test('session persistence failures return a useful error instead of a browser exception', (context) => {
  context.mock.method(localStorage, 'setItem', () => { throw new Error('QuotaExceededError') })
  assert.throws(() => writeStoredSession({
    accessToken: 'token', tokenType: 'Bearer', expiresInSeconds: 60, expiresAt: Date.now() + 60_000,
    user: { id: 'user', email: 'user@example.com', displayName: 'User', role: 'USER', mustChangePassword: false },
  }), { code: 'SESSION_STORAGE_UNAVAILABLE' })
})

test('malformed JSON keeps the HTTP failure status instead of reporting a network outage', async (context) => {
  context.mock.method(globalThis, 'fetch', async () => new Response('{', {
    status: 502,
    headers: { 'Content-Type': 'application/problem+json' },
  }))
  await assert.rejects(apiRequest('/tasks'), { status: 502, message: '请求失败（502）' })
})
