import assert from 'node:assert/strict'
import test from 'node:test'
import { prepareShareSelection, shareExpiration } from '../src/utils/shareSelection.ts'
import { captureWorkspaceRequest, setRequestWorkspace, resetWorkspaceRequests } from '../src/services/workspaceContext.ts'

test('mixed selections preserve order, deduplicate by type and id, and strip content', () => {
  assert.deepEqual(prepareShareSelection([
    { type: 'MARKDOWN', id: 'same', title: '笔记' },
    { type: 'SNIPPET', id: 'same', title: '代码' },
    { type: 'MARKDOWN', id: 'same', title: '重复' },
  ]), [{ type: 'MARKDOWN', id: 'same' }, { type: 'SNIPPET', id: 'same' }])
})

test('empty and excessive selections fail instead of silently sharing a subset', () => {
  assert.throws(() => prepareShareSelection([]), /选择/)
  assert.throws(() => prepareShareSelection(Array.from({ length: 101 }, (_, id) => ({ type: 'FLOWCHART', id: String(id), title: '流程图' }))), /100/)
})

test('expiration accepts full day boundaries and rejects invalid input', () => {
  const now = Date.parse('2026-09-09T00:00:00Z')
  assert.equal(shareExpiration(1, now), '2026-09-10T00:00:00.000Z')
  assert.equal(shareExpiration(365, now), '2027-09-09T00:00:00.000Z')
  for (const days of [0, -1, 366, 1.5, NaN, Infinity]) assert.throws(() => shareExpiration(days, now), /1–365/)
})

test('sharing operations and images follow workspace lifetime while public bundles do not', () => {
  setRequestWorkspace('member-one')
  const request = captureWorkspaceRequest('/sharing/pool?mine=true')
  assert.equal(request?.ownerId, 'member-one')
  assert.equal(captureWorkspaceRequest('/sharing/pool/id/images/image')?.ownerId, 'member-one')
  assert.equal(captureWorkspaceRequest('/public/share-bundles/token', false), null)
  setRequestWorkspace('member-two')
  assert.equal(request?.signal.aborted, true)
  resetWorkspaceRequests()
})
