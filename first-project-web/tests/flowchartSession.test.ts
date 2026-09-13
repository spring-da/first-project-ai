import assert from 'node:assert/strict'
import { test } from 'node:test'
import { FlowchartSession } from '../src/utils/flowchartSession.ts'
import { emptyDiagram, createTemplate } from '../src/utils/flowcharts.ts'
import type { FlowchartDraft, FlowchartDocument } from '../src/types/flowcharts.ts'
const draft = (): FlowchartDraft => ({ title: '初稿', domainId: null, favorite: false, diagram: emptyDiagram() })
const saved = (d: FlowchartDraft, version = 0): FlowchartDocument => ({ ...structuredClone(d), id: 'doc', createdAt: '', updatedAt: '', deletedAt: null, version, nodeCount: 0, edgeCount: 0, excerpt: '' })
test('server canonical property order is clean and does not trigger repeated automatic saves', async () => {
  const canonical = (value: unknown): unknown => Array.isArray(value) ? value.map(canonical) : value && typeof value === 'object' ? Object.fromEntries(Object.entries(value).sort(([a], [b]) => a.localeCompare(b)).map(([k, v]) => [k, canonical(v)])) : value
  let calls = 0
  const input = { ...draft(), diagram: createTemplate('decision') }
  const session = new FlowchartSession(input, null, 'key', {
    create: async d => { calls++; return canonical(saved(d)) as FlowchartDocument },
    update: async (_id, d) => { calls++; return canonical(saved(d, calls)) as FlowchartDocument },
  })
  await session.save('AUTO')
  assert.equal(session.dirty, false)
  await session.save('AUTO')
  assert.equal(calls, 1)
  session.edit({ ...input, title: '已修改' })
  await session.save('AUTO')
  assert.equal(session.dirty, false)
  assert.equal(calls, 2)
})
test('a save response preserves newer edits and the next queued save uses the new version', async () => {
  let release!: (d: FlowchartDocument) => void
  const updates: Array<{ expectedVersion: number; title: string }> = []
  const session = new FlowchartSession(draft(), null, 'creation', {
    create: () => new Promise(resolve => release = resolve),
    update: async (_id, input) => { updates.push(input); return saved(input, 1) },
  })
  const first = session.save('AUTO')
  session.edit({ ...session.draft, title: '保存期间的新输入' })
  const next = session.save('MANUAL')
  release(saved(draft()))
  await Promise.all([first, next])
  assert.equal(session.draft.title, '保存期间的新输入')
  assert.deepEqual(updates.map(i => [i.expectedVersion, i.title]), [[0, '保存期间的新输入']])
  assert.equal(session.dirty, false)
})
test('failed creation retries retain the same idempotency key and local content', async () => {
  const keys: string[] = []
  const session = new FlowchartSession(draft(), null, 'stable-key', {
    create: async input => { keys.push(input.creationKey); if (keys.length === 1) throw new Error('offline'); return saved(input) },
    update: async (_id, input) => saved(input),
  })
  await assert.rejects(session.save('AUTO'))
  assert.equal(session.dirty, true)
  await session.save('AUTO')
  assert.deepEqual(keys, ['stable-key', 'stable-key'])
})
test('idempotent creation response cannot erase edits made after a lost first response', async () => {
  let creates = 0
  const updated: string[] = []
  const session = new FlowchartSession(draft(), null, 'stable-key', {
    create: async () => { if (++creates === 1) throw new Error('response lost after commit'); return saved(draft()) },
    update: async (_id, input) => { updated.push(input.title); return saved(input, 1) },
  })
  await assert.rejects(session.save('AUTO'))
  session.edit({ ...draft(), title: '断网期间继续画图' })
  await session.save('AUTO')
  assert.equal(session.draft.title, '断网期间继续画图')
  assert.equal(session.dirty, true)
  await session.save('AUTO')
  assert.deepEqual(updated, ['断网期间继续画图'])
  assert.equal(session.dirty, false)
})
test('conflict pauses automatic writes; disposing ignores late responses', async () => {
  let calls = 0
  const session = new FlowchartSession(draft(), saved(draft()), 'key', {
    create: async input => saved(input),
    update: async () => { calls++; throw Object.assign(new Error('conflict'), { status: 409 }) },
  })
  session.edit({ ...draft(), title: '本机内容' })
  await assert.rejects(session.save('AUTO'))
  await assert.rejects(session.save('AUTO'))
  assert.equal(calls, 1)
  assert.equal(session.status, 'conflict')
  assert.equal(session.draft.title, '本机内容')
  let release!: (value: FlowchartDocument) => void
  const late = new FlowchartSession(draft(), null, 'key', { create: () => new Promise(r => release = r), update: async (_id, input) => saved(input) })
  const promise = late.save('AUTO')
  late.dispose(); release(saved(draft())); await promise
  assert.equal(late.document, null)
})
