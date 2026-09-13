import assert from 'node:assert/strict'
import { afterEach, beforeEach, test } from 'node:test'
import { captureImageContext, clearMarkdownImageCache, loadImage, loadMarkdownImage, primeImageCache } from '../src/services/markdownImages.ts'
import { requestPendingCount } from '../src/services/requestActivity.ts'
import { apiRequest } from '../src/services/api.ts'
import { resetWorkspaceRequests, setRequestWorkspace } from '../src/services/workspaceContext.ts'

let account = 'one'
beforeEach(() => {
  account = 'one'
  Object.defineProperty(globalThis, 'window', { configurable: true, value: { setTimeout, clearTimeout, dispatchEvent: () => {} } })
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: {
    getItem: () => JSON.stringify({ accessToken: `token-${account}`, tokenType: 'Bearer', expiresAt: Date.now() + 60000, user: { id: account, email: `${account}@example.invalid` } }),
    removeItem: () => {},
  } })
  clearMarkdownImageCache()
})
afterEach(() => {
  clearMarkdownImageCache()
  resetWorkspaceRequests()
  Reflect.deleteProperty(globalThis, 'window')
  Reflect.deleteProperty(globalThis, 'localStorage')
})

test('simultaneous previews of one image share one download', async (context) => {
  const fetch = context.mock.method(globalThis, 'fetch', async () => new Response(new Blob(['pixels'], { type: 'image/png' })))
  const [first, second] = await Promise.all([
    loadMarkdownImage('same', new AbortController().signal),
    loadMarkdownImage('same', new AbortController().signal),
  ])
  assert.equal(await first.text(), 'pixels')
  assert.equal(await second.text(), 'pixels')
  assert.equal(fetch.mock.callCount(), 1)
})

test('public share images are revalidated when reopened instead of held in the private cache', async (context) => {
  let revoked = false
  context.mock.method(globalThis, 'fetch', async () => revoked
    ? Response.json({ detail: 'Revoked' }, { status: 404 })
    : new Response(new Blob(['shared pixels'], { type: 'image/png' })))
  await loadMarkdownImage('one', new AbortController().signal, '/public/markdown-shares/token/images', false)
  revoked = true
  await assert.rejects(loadMarkdownImage('one', new AbortController().signal, '/public/markdown-shares/token/images', false), { status: 404 })
})

test('community images reuse cached bytes and normalize stable API paths', async (context) => {
  const fetch = context.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, '/api/v1/community/messages/one/image')
    assert.equal(new Headers(options?.headers).get('Authorization'), 'Bearer token-one')
    return new Response(new Blob(['community pixels'], { type: 'image/png' }))
  })
  await loadImage('/community/messages/one/image', new AbortController().signal)
  const again = await loadImage('/api/v1/community/messages/one/image', new AbortController().signal)
  assert.equal(await again.text(), 'community pixels')
  assert.equal(fetch.mock.callCount(), 1)
})

test('cancelling one subscriber leaves the shared transfer available to the other', async (context) => {
  let finish!: (response: Response) => void
  let transferSignal!: AbortSignal
  const fetch = context.mock.method(globalThis, 'fetch', (_url, options) => {
    transferSignal = options!.signal!
    return new Promise<Response>((resolve) => { finish = resolve })
  })
  const controller = new AbortController()
  const first = loadImage('/user-avatars/one', controller.signal)
  const second = loadImage('/user-avatars/one', new AbortController().signal)
  const cancelled = assert.rejects(first, { name: 'AbortError' })
  controller.abort()
  assert.equal(transferSignal.aborted, false)
  finish(new Response(new Blob(['avatar'], { type: 'image/png' })))
  await cancelled
  assert.equal(await (await second).text(), 'avatar')
  assert.equal(fetch.mock.callCount(), 1)
})

test('last subscriber cancellation stops the network transfer and permits a fresh retry', async (context) => {
  let calls = 0
  let transferSignal!: AbortSignal
  context.mock.method(globalThis, 'fetch', (_url, options) => {
    if (++calls === 2) return Promise.resolve(new Response(new Blob(['retry'], { type: 'image/png' })))
    transferSignal = options!.signal!
    return new Promise<Response>((_resolve, reject) => transferSignal.addEventListener('abort', () => reject(transferSignal.reason), { once: true }))
  })
  const controller = new AbortController()
  const pending = loadImage('/community/messages/one/image', controller.signal)
  const cancelled = assert.rejects(pending, { name: 'AbortError' })
  controller.abort()
  assert.equal(transferSignal.aborted, true)
  await cancelled
  assert.equal(await (await loadImage('/community/messages/one/image', new AbortController().signal)).text(), 'retry')
  assert.equal(calls, 2)
})

test('account switches reject late image bytes and do not reuse the previous cache', async (context) => {
  let finish!: (response: Response) => void
  let calls = 0
  context.mock.method(globalThis, 'fetch', () => ++calls === 1
    ? new Promise<Response>((resolve) => { finish = resolve })
    : Promise.resolve(new Response(new Blob(['new account'], { type: 'image/png' }))))
  const old = loadImage('/community/messages/one/image', new AbortController().signal)
  const rejected = assert.rejects(old, { name: 'AbortError' })
  account = 'two'
  const current = loadImage('/community/messages/one/image', new AbortController().signal)
  finish(new Response(new Blob(['old account'], { type: 'image/png' })))
  await rejected
  assert.equal(await (await current).text(), 'new account')
  assert.equal(await (await loadImage('/community/messages/one/image', new AbortController().signal)).text(), 'new account')
  assert.equal(calls, 2)
})

test('workspace changes and explicit clearing invalidate completed image caches', async (context) => {
  const fetch = context.mock.method(globalThis, 'fetch', async () => new Response(new Blob(['pixels'], { type: 'image/png' })))
  await loadMarkdownImage('one', new AbortController().signal)
  setRequestWorkspace('member')
  await loadMarkdownImage('one', new AbortController().signal)
  clearMarkdownImageCache()
  await loadMarkdownImage('one', new AbortController().signal)
  assert.equal(fetch.mock.callCount(), 3)
})

test('new uploads preview locally and old-session uploads cannot prime a new session', async (context) => {
  const originalContext = captureImageContext()
  const file = new Blob(['uploaded'], { type: 'image/png' })
  primeImageCache('/community/messages/new/image', file, originalContext)
  const fetch = context.mock.method(globalThis, 'fetch', async () => new Response(new Blob(['server'], { type: 'image/png' })))
  assert.equal(await (await loadImage('/community/messages/new/image', new AbortController().signal)).text(), 'uploaded')
  assert.equal(fetch.mock.callCount(), 0)
  account = 'two'
  primeImageCache('/community/messages/new/image', file, originalContext)
  assert.equal(await (await loadImage('/community/messages/new/image', new AbortController().signal)).text(), 'server')
})

test('background image reads do not keep the page progress bar active', async (context) => {
  let completeImage!: (response: Response) => void
  let completePage!: (response: Response) => void
  context.mock.method(globalThis, 'fetch', (url) => new Promise<Response>((resolve) => {
    if (String(url).endsWith('/image')) completeImage = resolve
    else completePage = resolve
  }))
  const image = loadImage('/community/messages/one/image', new AbortController().signal)
  assert.equal(requestPendingCount.value, 0)
  const page = apiRequest('/community/messages')
  assert.equal(requestPendingCount.value, 1)
  completePage(Response.json({ items: [] }))
  await page
  assert.equal(requestPendingCount.value, 0)
  completeImage(new Response(new Blob(['pixels'], { type: 'image/png' })))
  await image
})

test('failed and invalid image responses are not cached and remain retryable', async (context) => {
  let calls = 0
  context.mock.method(globalThis, 'fetch', async () => ++calls === 1
    ? new Response('<html>error</html>', { headers: { 'Content-Type': 'text/html' } })
    : new Response(new Blob(['valid'], { type: 'image/png' })))
  await assert.rejects(loadImage('/community/messages/one/image', new AbortController().signal), /图片响应无效/)
  assert.equal(await (await loadImage('/community/messages/one/image', new AbortController().signal)).text(), 'valid')
})

test('explicit retry replaces undecodable cached bytes and reloads the HTTP cache', async (context) => {
  let calls = 0
  context.mock.method(globalThis, 'fetch', async (_url, options) => {
    calls++
    if (calls === 2) assert.equal(options?.cache, 'reload')
    return new Response(new Blob([calls === 1 ? 'truncated PNG' : 'recovered pixels'], { type: 'image/png' }))
  })
  const path = '/community/messages/one/image'
  await loadImage(path, new AbortController().signal)
  const recovered = await loadImage(path, new AbortController().signal, true, { reload: true })
  assert.equal(await recovered.text(), 'recovered pixels')
  assert.equal(await (await loadImage(path, new AbortController().signal)).text(), 'recovered pixels')
  assert.equal(calls, 2)
})
