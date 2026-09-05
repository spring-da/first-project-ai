import assert from 'node:assert/strict'
import { afterEach, beforeEach, test } from 'node:test'
import { createPinia, setActivePinia } from 'pinia'
import { useConfirmationStore } from '../src/stores/confirmation.ts'

let store: ReturnType<typeof useConfirmationStore>
beforeEach(() => { setActivePinia(createPinia()); store = useConfirmationStore() })
afterEach(() => store.$dispose())
const question = { title: '删除项目？', message: '此操作无法撤销。', tone: 'danger' as const }

test('actions wait for explicit acceptance and run only once', async () => {
  let actions = 0
  const action = (async () => { if (await store.ask(question)) actions++ })()
  await Promise.resolve()
  assert.equal(actions, 0)
  assert.equal(store.current?.title, question.title)
  store.answer(true)
  store.answer(true)
  await action
  assert.equal(actions, 1)
  assert.equal(store.current, null)
})

test('cancel, Escape or closing the dialog never accepts the action', async () => {
  const pending = store.ask(question)
  store.cancel()
  assert.equal(await pending, false)
  assert.equal(store.current, null)
})

test('a repeated request cannot replace or accept the first action', async () => {
  const first = store.ask(question)
  assert.equal(await store.ask({ title: '退出？', message: '退出账户' }), false)
  assert.equal(store.current?.title, question.title)
  store.answer(true)
  assert.equal(await first, true)
  const next = store.ask(question)
  store.cancel()
  assert.equal(await next, false)
})

test('disposing the store cancels pending work instead of leaving a promise hanging', async () => {
  const pending = store.ask(question)
  store.$dispose()
  assert.equal(await pending, false)
})
