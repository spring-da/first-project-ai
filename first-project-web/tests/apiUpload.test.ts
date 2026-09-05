import assert from 'node:assert/strict'
import { afterEach, beforeEach, test } from 'node:test'
import { apiDownload, apiRequest } from '../src/services/api.ts'

beforeEach(() => {
  Object.defineProperty(globalThis, 'window', { configurable: true, value: { setTimeout, clearTimeout } })
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: { getItem: () => JSON.stringify({ accessToken: 'fake', tokenType: 'Bearer', user: { id: 'test' } }) } })
})
afterEach(() => { Reflect.deleteProperty(globalThis, 'window'); Reflect.deleteProperty(globalThis, 'localStorage') })

test('multipart upload keeps the browser boundary and authenticates the request', async (context) => {
  const form = new FormData()
  form.append('file', new Blob(['test'], { type: 'image/png' }), 'test.png')
  context.mock.method(globalThis, 'fetch', async (_url, options) => {
    assert.equal(options.body, form)
    assert.equal(options.headers.has('Content-Type'), false)
    assert.equal(options.headers.get('Authorization'), 'Bearer fake')
    return Response.json({ id: 'test' })
  })
  assert.deepEqual(await apiRequest('/markdown-images', { method: 'POST', body: form }), { id: 'test' })
})

test('image reads carry authorization and honor image Accept headers', async (context) => {
  context.mock.method(globalThis, 'fetch', async (_url, options) => {
    assert.equal(options.headers.get('Authorization'), 'Bearer fake')
    assert.equal(options.headers.get('Accept'), 'image/*')
    return new Response(new Blob(['png'], { type: 'image/png' }))
  })
  const response = await apiDownload('/markdown-images/test', { headers: { Accept: 'image/*' } })
  assert.equal(response.blob.type, 'image/png')
})

test('public Markdown shares and their images never send account authorization', async (context) => {
  const calls: string[] = []
  context.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(options.headers.has('Authorization'), false)
    calls.push(String(url))
    return calls.length === 1
      ? Response.json({ title: 'Shared', fileName: 'shared.md', content: '# Shared', createdAt: '', updatedAt: '', expiresAt: '' })
      : new Response(new Blob(['png'], { type: 'image/png' }))
  })
  const token = 's'.repeat(43)
  const share = await apiRequest<{ title: string }>(`/public/markdown-shares/${token}`, { authenticated: false })
  const image = await apiDownload(`/public/markdown-shares/${token}/images/image-id`, {
    authenticated: false,
    headers: { Accept: 'image/*' },
  })
  assert.equal(share.title, 'Shared')
  assert.equal(image.blob.type, 'image/png')
  assert.deepEqual(calls, [
    `/api/v1/public/markdown-shares/${token}`,
    `/api/v1/public/markdown-shares/${token}/images/image-id`,
  ])
})

test('cancelling an image upload aborts the underlying request', async (context) => {
  context.mock.method(globalThis, 'fetch', (_url, options) => new Promise((_resolve, reject) => {
    options.signal.addEventListener('abort', () => reject(new DOMException('cancelled', 'AbortError')))
  }))
  const controller = new AbortController()
  const pending = apiRequest('/markdown-images', { method: 'POST', body: new FormData(), signal: controller.signal })
  controller.abort()
  await assert.rejects(pending, { name: 'AbortError' })
})
