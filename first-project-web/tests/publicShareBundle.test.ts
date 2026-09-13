import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import * as vue from 'vue'
import ts from 'typescript'
import { compileScript, compileTemplate, parse } from '@vue/compiler-sfc'

// Exercise the production setup function with Vue reactivity and controlled HTTP/time.
// DOM rendering and image Blob disposal are also covered by the isolated UI fixture.
const filename = new URL('../src/views/PublicShareBundleView.vue', import.meta.url)
const { descriptor } = parse(readFileSync(filename, 'utf8'), { filename: filename.pathname })
const compiled = compileScript(descriptor, { id: 'public-bundle-lifecycle' })
const code = ts.transpileModule(compiled.content, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText
const initialTime = Date.now()
const items = [
  { id: 'a', resourceType: 'MARKDOWN', title: 'Article' },
  { id: 'b', resourceType: 'SNIPPET', title: 'Snippet' },
]
const directory = (entries = items, expiresAt = initialTime + 60_000) => ({
  title: 'Bundle', expiresAt: new Date(expiresAt).toISOString(), items: entries,
})
const body = (id: string) => ({
  id, resourceType: 'MARKDOWN', title: id, content: `Body ${id}`,
  language: null, category: null, tags: [], updatedAt: new Date(initialTime).toISOString(),
})
class FixtureApiError extends Error {
  status: number
  constructor(status: number) { super('Unavailable'); this.status = status }
}
type Services = {
  getPublicShareBundle: (token: string, signal: AbortSignal) => Promise<ReturnType<typeof directory>>
  getPublicBundleContent?: (token: string, id: string, signal: AbortSignal) => Promise<ReturnType<typeof body>>
}
const flush = async () => { for (let index = 0; index < 10; index++) await Promise.resolve(); await vue.nextTick() }
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (cause: unknown) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
function mount(services: Services, token = 'a'.repeat(43)) {
  const mounted: Array<() => void> = [], cleanup: Array<() => void> = []
  const timers = new Map<number, { fn: () => void; delay: number }>()
  const events = new Map<string, () => void>()
  const route = vue.reactive({ params: { token } })
  let clock = initialTime, timerId = 0
  const fakeDocument = {
    title: 'Original', visibilityState: 'visible',
    addEventListener: (key: string, fn: () => void) => events.set(key, fn),
    removeEventListener: (key: string) => events.delete(key),
  }
  const fakeWindow = { addEventListener: fakeDocument.addEventListener, removeEventListener: fakeDocument.removeEventListener }
  const exports: Record<string, any> = {}
  vm.runInNewContext(code, {
    exports, document: fakeDocument, window: fakeWindow, AbortController, Intl,
    Date: class extends Date { static now() { return clock } },
    setTimeout: (fn: () => void, delay: number) => { timers.set(++timerId, { fn, delay }); return timerId },
    clearTimeout: (id: number) => timers.delete(id),
    require: (name: string) => {
      if (name === 'vue') return { ...vue, onMounted: (fn: () => void) => mounted.push(fn), onBeforeUnmount: (fn: () => void) => cleanup.push(fn) }
      if (name === 'vue-router') return { useRoute: () => route }
      if (name === '../services/sharing') return services
      if (name === '../services/api') return { ApiError: FixtureApiError }
      if (name === '../stores/theme') return { useThemeStore: () => ({ isDark: false, toggleTheme() {} }) }
      return {}
    },
  })
  const scope = vue.effectScope()
  const state = scope.run(() => exports.default.setup({}, { expose() {} }))!
  mounted.forEach((fn) => fn())
  return {
    state, route, timers, events, document: fakeDocument,
    setTime: (value: number) => { clock = value },
    unmount: () => { cleanup.forEach((fn) => fn()); scope.stop() },
  }
}

test('public reader template compiles with its production setup bindings', () => {
  const result = compileTemplate({ source: descriptor.template!.content, filename: filename.pathname, id: 'public-bundle-lifecycle', compilerOptions: { bindingMetadata: compiled.bindings } })
  assert.deepEqual(result.errors, [])
})

test('public bundle opens a directory first and validates before every uncached body read', async () => {
  const calls: string[] = []
  const app = mount({ getPublicShareBundle: async () => { calls.push('directory'); return directory() }, getPublicBundleContent: async (_token, id) => { calls.push(id); return body(id) } })
  try {
    await flush()
    assert.deepEqual(calls, ['directory'])
    await app.state.loadBundle('a')
    await app.state.loadBundle('a')
    assert.deepEqual(calls, ['directory', 'directory', 'a', 'directory', 'a'])
  } finally { app.unmount() }
})

test('rapid selection rejects late bodies when the transport ignores cancellation', async () => {
  const reads = new Map<string, ReturnType<typeof deferred<ReturnType<typeof body>>> & { signal: AbortSignal }>()
  const app = mount({ getPublicShareBundle: async () => directory(), getPublicBundleContent: (_token, id, signal) => {
    const pending = deferred<ReturnType<typeof body>>(); reads.set(id, { ...pending, signal }); return pending.promise
  } })
  try {
    await flush()
    const first = app.state.loadBundle('a'); await flush()
    const second = app.state.loadBundle('b'); await flush()
    assert.equal(reads.get('a')!.signal.aborted, true)
    reads.get('b')!.resolve(body('b')); await second
    reads.get('a')!.resolve(body('a')); await first
    assert.equal(app.state.resource.value.id, 'b')
    assert.equal(app.state.loading.value, false)
  } finally { app.unmount() }
})

test('one removed item preserves the remaining directory while a revoked bundle clears it', async () => {
  for (const revoked of [false, true]) {
    let checks = 0
    const app = mount({ getPublicShareBundle: async () => {
      if (++checks > 2 && revoked) throw new FixtureApiError(404)
      return directory(checks > 2 ? [items[1]!] : items)
    }, getPublicBundleContent: async () => { throw new FixtureApiError(404) } })
    try {
      await flush(); await app.state.loadBundle('a')
      assert.equal(app.state.resource.value, null)
      assert.equal(app.state.unavailable.value, revoked)
      if (revoked) assert.equal(app.state.bundle.value, null)
      else {
        assert.equal(app.state.bundle.value.items.length, 1)
        assert.equal(app.state.contentUnavailable.value, true)
        assert.equal(app.state.pageError.value, '')
      }
    } finally { app.unmount() }
  }
})

test('expiry clears rendered content and aborts a pending body without accepting it later', async () => {
  const pending = deferred<ReturnType<typeof body>>()
  let signal: AbortSignal | undefined
  const app = mount({ getPublicShareBundle: async () => directory(items, initialTime + 1000), getPublicBundleContent: (_token, _id, nextSignal) => { signal = nextSignal; return pending.promise } })
  try {
    await flush(); const read = app.state.loadBundle('a'); await flush()
    app.setTime(initialTime + 1001)
    Array.from(app.timers.values()).forEach(({ fn }) => fn())
    assert.equal(signal!.aborted, true)
    assert.equal(app.state.resource.value, null)
    assert.equal(app.state.bundle.value, null)
    assert.equal(app.state.unavailable.value, true)
    assert.equal(app.timers.size, 0)
    pending.resolve(body('a')); await read
    assert.equal(app.state.resource.value, null)
  } finally { app.unmount() }
})

test('focus replaces in-flight reads, visibility revalidates, and route changes erase prior content', async () => {
  const reads: Array<ReturnType<typeof deferred<ReturnType<typeof body>>> & { signal: AbortSignal }> = []
  const app = mount({ getPublicShareBundle: async () => directory(), getPublicBundleContent: (_token, _id, signal) => {
    const pending = deferred<ReturnType<typeof body>>(); reads.push({ ...pending, signal }); return pending.promise
  } })
  try {
    await flush(); const first = app.state.loadBundle('a'); await flush()
    app.events.get('focus')!(); await flush()
    assert.equal(reads[0]!.signal.aborted, true)
    reads[1]!.resolve(body('a')); await flush()
    reads[0]!.resolve({ ...body('a'), content: 'Stale' }); await first
    assert.equal(app.state.resource.value.content, 'Body a')
    app.events.get('visibilitychange')!()
    assert.equal(app.state.resource.value, null)
    await flush()
    app.route.params.token = 'b'.repeat(43); await flush()
    assert.equal(reads[2]!.signal.aborted, true)
    reads[2]!.resolve(body('a')); await flush()
    assert.equal(app.state.resource.value, null)
    assert.equal(app.state.selectedId.value, null)
  } finally { app.unmount() }
  assert.equal(app.events.size, 0)
  assert.equal(app.timers.size, 0)
  assert.equal(app.document.title, 'Original')
})

test('invalid tokens never reach the API and long expiry values stay inside browser timer limits', async () => {
  let calls = 0
  const invalid = mount({ getPublicShareBundle: async () => { calls++; return directory() } }, 'invalid')
  try { await flush(); assert.equal(calls, 0); assert.equal(invalid.state.unavailable.value, true) } finally { invalid.unmount() }
  const long = mount({ getPublicShareBundle: async () => directory(items, initialTime + 365 * 86400000) })
  try { await flush(); assert.equal([...long.timers.values()][0]!.delay, 2_147_483_647) } finally { long.unmount() }
})

test('transient content errors leave retry available and a retry requests the current bundle again', async () => {
  let attempts = 0
  const app = mount({ getPublicShareBundle: async () => directory(), getPublicBundleContent: async (_token, id) => {
    if (++attempts === 1) throw new Error('Offline')
    return body(id)
  } })
  try {
    await flush(); await app.state.loadBundle('a')
    assert.ok(app.state.contentError.value)
    assert.equal(app.state.contentUnavailable.value, false)
    await app.state.loadBundle('a')
    assert.equal(app.state.contentError.value, '')
    assert.equal(app.state.resource.value.id, 'a')
  } finally { app.unmount() }
})
