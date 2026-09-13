import assert from 'node:assert/strict'
import { afterEach, beforeEach, test } from 'node:test'
import {
  createShareBundle, getPoolContent, getPublicBundleContent, getPublicShareBundle,
  getShareBundle, listShareBundles, listSharedPool, publishToPool,
  removeShareBundleItem, revokeShareBundles, withdrawFromPool,
} from '../src/services/sharing.ts'

beforeEach(() => {
  Object.defineProperty(globalThis, 'window', { configurable: true, value: { setTimeout, clearTimeout, dispatchEvent: () => {} } })
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: {
    getItem: () => JSON.stringify({ accessToken: 'test-session', tokenType: 'Bearer', expiresAt: Date.now() + 60_000, user: { id: 'owner', email: 'owner@example.test' } }),
    removeItem: () => {},
  } })
})
afterEach(() => { Reflect.deleteProperty(globalThis, 'window'); Reflect.deleteProperty(globalThis, 'localStorage') })

test('sharing creation sends only resource references and preserves the one-time token response', async (context) => {
  const calls: Array<{ url: string; method: string; body: unknown }> = []
  context.mock.method(globalThis, 'fetch', async (url, options) => {
    calls.push({ url: String(url), method: options.method, body: JSON.parse(options.body) })
    return Response.json(String(url).endsWith('/pool') ? { publishedCount: 2, existingCount: 1 }
      : { id: 'link', token: 'one-time-secret', title: '合集', expiresAt: '2026-09-16T00:00:00Z', createdAt: '2026-09-09T00:00:00Z' })
  })
  const items = [{ type: 'MARKDOWN' as const, id: 'a', title: 'Local title' }, { type: 'SNIPPET' as const, id: 'b' }]
  assert.deepEqual(await publishToPool(items), { publishedCount: 2, existingCount: 1 })
  assert.equal((await createShareBundle('合集', items, '2026-09-16T00:00:00Z')).token, 'one-time-secret')
  assert.deepEqual(calls, [
    { url: '/api/v1/sharing/pool', method: 'POST', body: { items: [{ type: 'MARKDOWN', id: 'a' }, { type: 'SNIPPET', id: 'b' }] } },
    { url: '/api/v1/sharing/links', method: 'POST', body: { title: '合集', items: [{ type: 'MARKDOWN', id: 'a' }, { type: 'SNIPPET', id: 'b' }], expiresAt: '2026-09-16T00:00:00Z' } },
  ])
})

test('sharing list requests encode filters and all reads bypass browser cache', async (context) => {
  const urls: string[] = []
  context.mock.method(globalThis, 'fetch', async (url, options) => {
    urls.push(String(url))
    assert.equal(options.cache, 'no-store')
    assert.equal(options.headers.get('Authorization'), 'Bearer test-session')
    return Response.json({ items: [], total: 0, page: 0, size: 20 })
  })
  await listSharedPool({ page: 2, size: 10, q: 'Vue & 权限', type: 'FLOWCHART', mine: true })
  await listShareBundles({ page: 1, q: 'my links' })
  await getPoolContent('id/with slash')
  await getShareBundle('link/id')
  assert.deepEqual(urls, [
    '/api/v1/sharing/pool?page=2&size=10&q=Vue+%26+%E6%9D%83%E9%99%90&type=FLOWCHART&mine=true',
    '/api/v1/sharing/links?page=1&size=20&q=my+links',
    '/api/v1/sharing/pool/id%2Fwith%20slash', '/api/v1/sharing/links/link%2Fid',
  ])
})

test('public bundle reads require no session and never send account authorization', async (context) => {
  context.mock.method(localStorage, 'getItem', () => null)
  const urls: string[] = []
  context.mock.method(globalThis, 'fetch', async (url, options) => {
    urls.push(String(url))
    assert.equal(options.headers.get('Authorization'), null)
    assert.equal(options.headers.get('X-Workspace-Owner'), null)
    assert.equal(options.cache, 'no-store')
    return Response.json({ title: '公开合集', expiresAt: '2026-09-16T00:00:00Z', items: [] })
  })
  await getPublicShareBundle('token/segment')
  await getPublicBundleContent('token/segment', 'item/id')
  assert.deepEqual(urls, ['/api/v1/public/share-bundles/token%2Fsegment', '/api/v1/public/share-bundles/token%2Fsegment/items/item%2Fid'])
})

test('withdraw and revoke submit one batch request and removing one bundle item uses the scoped route', async (context) => {
  const calls: Array<[string, string, unknown]> = []
  context.mock.method(globalThis, 'fetch', async (url, options) => {
    calls.push([String(url), options.method, options.body ? JSON.parse(options.body) : null])
    return new Response(null, { status: 204 })
  })
  await withdrawFromPool(['a', 'b'])
  await revokeShareBundles(['x', 'y'])
  await removeShareBundleItem('link/id', 'item/id')
  assert.deepEqual(calls, [
    ['/api/v1/sharing/pool/revoke', 'POST', { ids: ['a', 'b'] }],
    ['/api/v1/sharing/links/revoke', 'POST', { ids: ['x', 'y'] }],
    ['/api/v1/sharing/links/link%2Fid/items/item%2Fid', 'DELETE', null],
  ])
})

test('an abandoned sharing search aborts its underlying network request', async (context) => {
  let networkSignal: AbortSignal | undefined
  context.mock.method(globalThis, 'fetch', (_url, options) => {
    networkSignal = options.signal
    return new Promise<Response>((_resolve, reject) => options.signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError'))))
  })
  const controller = new AbortController()
  const pending = listSharedPool({ q: 'old search' }, controller.signal)
  controller.abort()
  await assert.rejects(pending, { name: 'AbortError' })
  assert.equal(networkSignal?.aborted, true)
})
