import assert from 'node:assert/strict'
import { afterEach, beforeEach, test } from 'node:test'
import { registerHooks } from 'node:module'
import { createPinia, disposePinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'

// The app uses Vite's extensionless imports; resolve those same local modules in Node.
registerHooks({ resolve(specifier, context, nextResolve) {
  try { return nextResolve(specifier, context) }
  catch (error) {
    if (specifier.startsWith('.') && !/\.[a-z]+$/i.test(specifier)) return nextResolve(`${specifier}.ts`, context)
    throw error
  }
} })
const { useSharingStore } = await import('../src/stores/sharing.ts')
const { useAuthStore } = await import('../src/stores/auth.ts')
const { useNotificationStore } = await import('../src/stores/notifications.ts')

let pinia: ReturnType<typeof createPinia>
let store: ReturnType<typeof useSharingStore>
let auth: ReturnType<typeof useAuthStore>
const poolEntry = (id: string, mine = true) => ({ id, resourceType: 'MARKDOWN', title: `Note ${id}`, excerpt: 'Saved text', authorName: 'Owner', sharedAt: '2026-09-09T00:00:00Z', updatedAt: '2026-09-09T00:00:00Z', mine })
const poolPage = (id: string, mine = true) => ({ items: [poolEntry(id, mine)], total: 1, page: 0, size: 20 })
const link = { id: 'link', title: 'Collection', expiresAt: '2026-09-16T00:00:00Z', createdAt: '2026-09-09T00:00:00Z', revokedAt: null, active: true, itemCount: 1 }
const item = { id: 'item', resourceId: 'note', resourceType: 'MARKDOWN', title: 'Note', removedAt: null, available: true }
const content = { id: 'note', resourceType: 'MARKDOWN', title: 'Note', content: 'Sensitive saved text', language: null, category: null, tags: [], updatedAt: '2026-09-09T00:00:00Z' }

beforeEach(() => {
  const storage = { getItem: () => JSON.stringify({ accessToken: 'session', tokenType: 'Bearer', expiresAt: Date.now() + 60_000, user: { id: 'owner', email: 'owner@example.test', role: 'ADMIN' } }), removeItem: () => {}, setItem: () => {} }
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: storage })
  Object.defineProperty(globalThis, 'window', { configurable: true, value: { setTimeout, clearTimeout, localStorage: storage, dispatchEvent: () => {} } })
  pinia = createPinia()
  setActivePinia(pinia)
  auth = useAuthStore()
  store = useSharingStore()
})
afterEach(() => { auth.clear(); disposePinia(pinia); Reflect.deleteProperty(globalThis, 'window'); Reflect.deleteProperty(globalThis, 'localStorage') })

test('a newer search wins even if the old transport ignores cancellation', async (context) => {
  const pending: Array<{ finish: (response: Response) => void; signal: AbortSignal }> = []
  context.mock.method(globalThis, 'fetch', (_url, options) => new Promise<Response>((finish) => pending.push({ finish, signal: options.signal })))
  const oldSearch = store.loadPool({ q: 'old' })
  const newSearch = store.loadPool({ q: 'new' })
  assert.equal(pending[0]!.signal.aborted, true)
  pending[1]!.finish(Response.json(poolPage('new')))
  await newSearch
  pending[0]!.finish(Response.json(poolPage('old')))
  await oldSearch
  assert.deepEqual(store.pool.items.map((entry) => entry.id), ['new'])
  assert.equal(store.requests.pool.loading, false)
  assert.equal(store.requests.pool.error, '')
})

test('typing cancels the active query immediately and debounces the replacement query', async (context) => {
  context.mock.timers.enable({ apis: ['setTimeout'] })
  const urls: string[] = []
  let oldSignal: AbortSignal | undefined
  let finishOld!: (response: Response) => void
  context.mock.method(globalThis, 'fetch', (url, options) => {
    urls.push(String(url))
    if (urls.length === 1) { oldSignal = options.signal; return new Promise<Response>((resolve) => { finishOld = resolve }) }
    return Promise.resolve(Response.json(poolPage('new')))
  })
  const oldSearch = store.loadPool({ q: 'old' })
  store.searchPool({ q: 'n' })
  assert.equal(oldSignal?.aborted, true)
  context.mock.timers.tick(150)
  store.searchPool({ q: 'new' })
  context.mock.timers.tick(249)
  assert.equal(urls.length, 1)
  context.mock.timers.tick(1)
  await new Promise((resolve) => setImmediate(resolve))
  finishOld(Response.json(poolPage('old')))
  await oldSearch
  assert.equal(urls.length, 2)
  assert.match(urls[1]!, /q=new/)
  assert.equal(store.pool.items[0]?.id, 'new')
})

test('workspace identity changes synchronously clear every cache without an app reset hook', async (context) => {
  context.mock.method(globalThis, 'fetch', async (url) => {
    const path = String(url)
    return Response.json(path.includes('/links/link') ? { link, items: [item] } : path.includes('/links?') ? { items: [link], total: 1, page: 0, size: 20 } : path.includes('/pool/note') ? content : poolPage('note'))
  })
  await store.loadPool({})
  await store.loadLinks({})
  await store.openPoolContent('note')
  await store.openBundleDetail('link')
  auth.setWorkspaceMember({ id: 'member', displayName: 'Member', email: 'member@example.test' })
  assert.equal(store.pool.items.length, 0)
  assert.equal(store.links.items.length, 0)
  assert.equal(store.content, null)
  assert.equal(store.bundleDetail, null)
  assert.equal(store.busy, false)
  await nextTick()
})

test('reset cancels queued searches and late details cannot reopen a closed reader', async (context) => {
  context.mock.timers.enable({ apis: ['setTimeout'] })
  let finish!: (response: Response) => void
  let calls = 0
  context.mock.method(globalThis, 'fetch', () => { calls++; return new Promise<Response>((resolve) => { finish = resolve }) })
  const opening = store.openPoolContent('note')
  store.closePoolContent()
  finish(Response.json(content))
  await opening
  assert.equal(store.content, null)
  store.searchLinks({ q: 'pending' })
  store.reset()
  context.mock.timers.tick(1000)
  assert.equal(calls, 1)
})

test('withdraw rejects any non-owned selection before sending a mutation', async (context) => {
  const methods: string[] = []
  context.mock.method(globalThis, 'fetch', async (_url, options) => { methods.push(options.method ?? 'GET'); return Response.json(poolPage('other', false)) })
  await store.loadPool({})
  assert.equal(await store.withdraw(['other']), false)
  assert.deepEqual(methods, ['GET'])
  assert.equal(store.pool.items.length, 1)
})

test('failed withdrawal preserves the visible selection data and reports the server failure', async (context) => {
  context.mock.method(globalThis, 'fetch', async (_url, options) => options.method === 'POST'
    ? Response.json({ detail: 'Permission changed' }, { status: 403 }) : Response.json(poolPage('mine')))
  await store.loadPool({})
  assert.equal(await store.withdraw(['mine']), false)
  assert.equal(store.pool.items[0]?.id, 'mine')
  assert.equal(store.busy, false)
  assert.equal(useNotificationStore().history[0]?.message, 'Permission changed')
})

test('late mutation results produce no state or success notification in the next workspace', async (context) => {
  let finish!: (response: Response) => void
  context.mock.method(globalThis, 'fetch', async (_url, options) => options.method === 'POST'
    ? new Promise<Response>((resolve) => { finish = resolve }) : Response.json(poolPage('mine')))
  await store.loadPool({})
  const mutation = store.withdraw(['mine'])
  auth.setWorkspaceMember({ id: 'member', displayName: 'Member', email: 'member@example.test' })
  finish(new Response(null, { status: 204 }))
  assert.equal(await mutation, false)
  assert.equal(store.pool.items.length, 0)
  assert.equal(useNotificationStore().history.length, 0)
})

test('removing a bundle item refreshes its availability and the link summary', async (context) => {
  let removed = false
  context.mock.method(globalThis, 'fetch', async (url, options) => {
    if (options.method === 'DELETE') { removed = true; return new Response(null, { status: 204 }) }
    const updatedLink = { ...link, itemCount: removed ? 0 : 1, active: !removed }
    return Response.json(String(url).includes('/links/link') ? { link: updatedLink, items: [{ ...item, removedAt: removed ? '2026-09-09T01:00:00Z' : null, available: !removed }] } : { items: [updatedLink], total: 1, page: 0, size: 20 })
  })
  await store.loadLinks({})
  await store.openBundleDetail('link')
  assert.equal(await store.removeItem('link', 'item'), true)
  assert.equal(store.bundleDetail?.items[0]?.available, false)
  assert.equal(store.links.items[0]?.itemCount, 0)
})
