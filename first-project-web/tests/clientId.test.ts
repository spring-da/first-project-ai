import assert from 'node:assert/strict'
import { afterEach, test } from 'node:test'
import { createClientId } from '../src/utils/clientId.ts'

const originalCrypto = Object.getOwnPropertyDescriptor(globalThis, 'crypto')
const setCrypto = (value: unknown) => Object.defineProperty(globalThis, 'crypto', { configurable: true, value })
afterEach(() => {
  if (originalCrypto) Object.defineProperty(globalThis, 'crypto', originalCrypto)
  else Reflect.deleteProperty(globalThis, 'crypto')
})

test('uses the native UUID when available', () => {
  setCrypto({ randomUUID: () => '86e404b1-90b5-4eac-a213-8ac0541d0806' })
  assert.equal(createClientId(), '86e404b1-90b5-4eac-a213-8ac0541d0806')
})

test('ordinary HTTP without randomUUID still produces a version 4 UUID', () => {
  setCrypto({ getRandomValues: (bytes: Uint8Array) => bytes.fill(255) })
  assert.equal(createClientId(), 'ffffffff-ffff-4fff-bfff-ffffffffffff')
})

test('a rejected native UUID falls through to getRandomValues', () => {
  setCrypto({ randomUUID: () => { throw new Error('NotAllowedError') }, getRandomValues: (bytes: Uint8Array) => bytes.fill(0) })
  assert.equal(createClientId(), '00000000-0000-4000-8000-000000000000')
})

test('missing Web Crypto does not break UI and repeated IDs remain distinct', (context) => {
  setCrypto(undefined)
  context.mock.method(Date, 'now', () => 1000)
  context.mock.method(Math, 'random', () => 0.5)
  const ids = Array.from({ length: 100 }, createClientId)
  assert.equal(new Set(ids).size, 100)
  assert.ok(ids.every((id) => id.startsWith('local-')))
})

test('a rejected random source still falls back to a local identifier', () => {
  setCrypto({ getRandomValues: () => { throw new Error('NotSupportedError') } })
  assert.match(createClientId(), /^local-/)
})
