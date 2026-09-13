import assert from 'node:assert/strict'
import { test } from 'node:test'
import { IDBFactory } from 'fake-indexeddb'
import { createFlowchartDraftStorage } from '../src/utils/flowchartDrafts.ts'
import { emptyDiagram } from '../src/utils/flowcharts.ts'

test('drafts isolate actors, workspace targets, documents and simultaneous sessions', async () => {
  const storage = createFlowchartDraftStorage(new IDBFactory())
  const draft = { title: '本机', favorite: false, domainId: null, diagram: emptyDiagram() }
  const record = { id: 'session-1', ownerKey: 'admin:member-1', documentId: 'doc', creationKey: 'creation', baseVersion: 2, savedAt: new Date().toISOString(), draft }
  await storage.write(record)
  await storage.write({ ...record, id: 'session-2', draft: { ...draft, title: '第二窗口' } })
  await storage.write({ ...record, ownerKey: 'admin:member-2', draft: { ...draft, title: '另一工作区' } })
  assert.deepEqual((await storage.list('admin:member-1')).map(i => i.draft.title).sort(), ['本机', '第二窗口'].sort())
  assert.equal((await storage.list('member-1')).length, 0)
  await storage.remove('admin:member-1', 'session-1')
  assert.equal((await storage.list('admin:member-1')).length, 1)
  assert.equal((await storage.list('admin:member-2')).length, 1)
  storage.close()
})
