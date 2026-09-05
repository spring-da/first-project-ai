import assert from 'node:assert/strict'
import { afterEach, beforeEach, test } from 'node:test'
import { createPinia, setActivePinia } from 'pinia'
import { useNotificationStore } from '../src/stores/notifications.ts'

let store: ReturnType<typeof useNotificationStore>
let values: Map<string, string>
beforeEach(() => {
  values = new Map()
  Object.defineProperty(globalThis, 'window', { configurable: true, value: { localStorage: {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => { values.set(key, value) },
    removeItem: (key: string) => { values.delete(key) },
  } } })
  setActivePinia(createPinia())
  store = useNotificationStore()
  store.setOwner('alice')
})
afterEach(() => { store.$dispose(); Reflect.deleteProperty(globalThis, 'window') })

test('toasts expire automatically while unread history stays available', (context) => {
  context.mock.timers.enable({ apis: ['setTimeout', 'Date'] })
  const success = store.notify('已保存', { type: 'success' })!
  store.notify('网络不可用', { type: 'error' })
  context.mock.timers.tick(4000)
  assert.equal(store.toasts.length, 1)
  assert.equal(store.toasts[0]?.type, 'error')
  context.mock.timers.tick(2000)
  assert.equal(store.toasts.length, 0)
  assert.equal(store.unreadCount, 2)
  assert.equal(store.history.find((item) => item.id === success)?.message, '已保存')
})

test('hover and keyboard focus can pause the remaining toast lifetime', (context) => {
  context.mock.timers.enable({ apis: ['setTimeout', 'Date'] })
  const id = store.notify('草稿未备份', { type: 'warning' })!
  context.mock.timers.tick(2500)
  store.pause(id)
  context.mock.timers.tick(10_000)
  assert.equal(store.toasts.length, 1)
  store.resume(id)
  context.mock.timers.tick(2999)
  assert.equal(store.toasts.length, 1)
  context.mock.timers.tick(1)
  assert.equal(store.toasts.length, 0)
})

test('repeated errors merge without restarting an active toast timer', (context) => {
  context.mock.timers.enable({ apis: ['setTimeout', 'Date'] })
  const id = store.notify('请求失败', { type: 'error' })
  context.mock.timers.tick(5000)
  assert.equal(store.notify('请求失败', { type: 'error' }), id)
  assert.equal(store.history.length, 1)
  assert.equal(store.history[0]?.occurrences, 2)
  context.mock.timers.tick(1000)
  assert.equal(store.toasts.length, 0)
  context.mock.timers.tick(15_000)
  assert.notEqual(store.notify('请求失败', { type: 'error' }), id)
})

test('history is bounded, survives reopening, and never leaks across accounts', () => {
  for (let i = 0; i < 105; i++) store.notify(`消息 ${i}`)
  assert.equal(store.history.length, 100)
  assert.equal(store.toasts.length, 3)
  store.setOwner('bob')
  assert.equal(store.history.length, 0)
  assert.equal(store.toasts.length, 0)
  store.notify('Bob 的消息')
  store.setOwner('alice')
  assert.equal(store.history.length, 100)
  assert.equal(store.history.some((item) => item.message === 'Bob 的消息'), false)
  assert.equal(store.toasts.length, 0)
  store.$dispose()
  setActivePinia(createPinia())
  store = useNotificationStore()
  store.setOwner('alice')
  assert.equal(store.history.length, 100)
  assert.equal(store.toasts.length, 0)
})

test('dismiss, mark read, delete and clear have distinct persistent behavior', () => {
  const first = store.notify('第一条')!
  const second = store.notify('第二条')!
  store.dismiss(first)
  assert.equal(store.history.length, 2)
  store.markRead(first)
  assert.equal(store.unreadCount, 1)
  store.clearRead()
  assert.deepEqual(store.history.map((item) => item.id), [second])
  store.remove(second)
  assert.equal(store.history.length, 0)
  store.notify('第三条')
  store.markAllRead()
  assert.equal(store.unreadCount, 0)
  store.clearAll()
  store.setOwner('bob')
  store.setOwner('alice')
  assert.equal(store.history.length, 0)
  assert.equal(store.toasts.length, 0)
})

test('unavailable storage falls back to memory without recursive errors or deleting drafts', () => {
  window.localStorage.setItem = () => { throw new Error('QuotaExceededError') }
  assert.doesNotThrow(() => store.notify('本机草稿保存失败', { type: 'warning' }))
  assert.equal(store.history.length, 1)
  assert.equal(store.toasts.length, 1)
  assert.equal(store.historyPersisted, false)
})

test('login errors become toasts and history without crypto.randomUUID', () => {
  const descriptor = Object.getOwnPropertyDescriptor(globalThis, 'crypto')
  const cryptoApi = globalThis.crypto
  Object.defineProperty(globalThis, 'crypto', { configurable: true, value: {
    getRandomValues: (bytes: Uint8Array) => cryptoApi.getRandomValues(bytes),
  } })
  try {
    const id = store.notify('邮箱或密码不正确，请重试。', { type: 'error', title: '账户验证失败' })
    assert.ok(id)
    assert.equal(store.toasts[0]?.id, id)
    assert.equal(store.history[0]?.message, '邮箱或密码不正确，请重试。')
    assert.equal(store.historyPersisted, true)
  } finally {
    if (descriptor) Object.defineProperty(globalThis, 'crypto', descriptor)
    else Reflect.deleteProperty(globalThis, 'crypto')
  }
})

test('corrupt persisted items are ignored and valid details remain readable', () => {
  store.notify('导入未完成', { type: 'warning', details: ['empty.md：空文件'] })
  const valid = { ...store.history[0] }
  values.set('devnest_notifications_v1:user:alice', JSON.stringify([null, {}, { ...valid, type: 'invalid' }, { ...valid, updatedAt: 1e100 }, valid, valid]))
  store.setOwner('bob')
  store.setOwner('alice')
  assert.equal(store.history.length, 1)
  assert.deepEqual(store.history[0]?.details, ['empty.md：空文件'])
  store.open()
  assert.equal(store.isOpen, true)
  assert.equal(store.toasts.length, 0)
})
