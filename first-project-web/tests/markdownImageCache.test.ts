import assert from 'node:assert/strict'
import { test } from 'node:test'
import { MarkdownImageCache } from '../src/services/markdownImageCache.ts'

test('Markdown image cache promotes hits and evicts the least recently used blob', () => {
  const cache = new MarkdownImageCache(6, 3)
  const first = new Blob(['aa'])
  const second = new Blob(['bb'])
  const third = new Blob(['cc'])
  const fourth = new Blob(['dd'])
  cache.set('first', first)
  cache.set('second', second)
  cache.set('third', third)

  assert.equal(cache.get('first'), first)
  cache.set('fourth', fourth)

  assert.equal(cache.get('second'), undefined)
  assert.equal(cache.get('first'), first)
  assert.equal(cache.get('fourth'), fourth)
  assert.equal(cache.bytes, 6)
})

test('Markdown image cache respects its byte limit and can be cleared on account changes', () => {
  const cache = new MarkdownImageCache(4, 10)
  cache.set('too-large', new Blob(['12345']))
  assert.equal(cache.size, 0)

  cache.set('one', new Blob(['12']))
  cache.set('two', new Blob(['34']))
  assert.equal(cache.size, 2)
  cache.clear()
  assert.equal(cache.size, 0)
  assert.equal(cache.bytes, 0)
})

test('expired cached images are discarded and release their byte budget', (context) => {
  let now = 100
  context.mock.method(Date, 'now', () => now)
  const cache = new MarkdownImageCache(10, 3)
  cache.set('image', new Blob(['pixels']), 50)
  now = 149
  assert.ok(cache.get('image'))
  now = 150
  assert.equal(cache.get('image'), undefined)
  assert.equal(cache.bytes, 0)
})
