import assert from 'node:assert/strict'
import test from 'node:test'
import { clearMarkdownImageCache, loadMarkdownImage } from '../src/services/markdownImages.ts'

test('reopening an authenticated pool image revalidates withdrawal instead of serving cached bytes', async context => {
  Object.defineProperty(globalThis, 'window', { configurable: true, value: { setTimeout, clearTimeout, dispatchEvent: () => {} } })
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: {
    getItem: () => JSON.stringify({ accessToken: 'test-token', tokenType: 'Bearer', expiresAt: Date.now() + 60_000, user: { id: 'member', email: 'member@example.invalid' } }),
    removeItem: () => {},
  } })
  let withdrawn = false
  const requests: RequestInit[] = []
  context.mock.method(globalThis, 'fetch', async (_url, options) => {
    requests.push(options!)
    return withdrawn ? Response.json({ detail: '已撤下' }, { status: 404 }) : new Response(new Blob(['image'], { type: 'image/png' }))
  })
  try {
    await loadMarkdownImage('image', new AbortController().signal, '/sharing/pool/entry/images')
    withdrawn = true
    await assert.rejects(loadMarkdownImage('image', new AbortController().signal, '/sharing/pool/entry/images'), { status: 404 })
    assert.equal(requests.length, 2)
    assert.equal(requests[0]?.cache, 'no-store')
    assert.equal(new Headers(requests[0]?.headers).get('Authorization'), 'Bearer test-token')
  } finally {
    clearMarkdownImageCache()
    Reflect.deleteProperty(globalThis, 'window'); Reflect.deleteProperty(globalThis, 'localStorage')
  }
})
