import assert from 'node:assert/strict'
import test from 'node:test'
import { createFlowchartFixture, fixtureDiagram, seedFlowcharts } from './flowchart-fixture.ts'
import { validateDiagram } from '../../src/utils/flowcharts.ts'

test('large fixture is deterministic, valid and contains exactly 300 nodes and 500 edges', () => {
  const diagram = validateDiagram(fixtureDiagram(true))
  assert.equal(diagram.nodes.length, 300)
  assert.equal(diagram.edges.length, 500)
  assert.deepEqual(diagram, fixtureDiagram(true))
})

test('fixture API keeps bodies lazy, searches labels, enforces versions and preserves trash share invalidation', async () => {
  const source = seedFlowcharts(1), invalidations: string[] = []
  const fixture = createFlowchartFixture({ scenario: new URLSearchParams('flowchart-create-retry=1'), sources: () => source, saveFailed: () => false, historyFailed: () => false, changed: () => {}, invalidateShares: (owner, id) => invalidations.push(`${owner}:${id}`) })
  const call = async (path: string, method = 'GET', body?: unknown) => (await fixture.handle(path, { method, ...(body ? { body: JSON.stringify(body) } : {}) }, 'owner'))!
  const list = await (await call('/flowcharts?q=批准')).json()
  assert.equal(list.length, 1)
  assert.equal('diagram' in list[0], false)
  const original = await (await call('/flowcharts/fixture-flowchart')).json()
  const draft = { title: '已编辑流程图', domainId: original.domainId, favorite: true, diagram: original.diagram, expectedVersion: 0, saveMode: 'MANUAL' }
  const saved = await (await call('/flowcharts/fixture-flowchart', 'PUT', draft)).json()
  assert.equal(saved.version, 1)
  assert.equal((await call('/flowcharts/fixture-flowchart', 'PUT', draft)).status, 409)
  assert.equal((await (await call('/flowcharts/fixture-flowchart/revisions?page=0')).json()).length, 2)
  const revisions = await (await call('/flowcharts/fixture-flowchart/revisions?page=0')).json()
  assert.equal('diagram' in revisions[0], false)
  const restored = await (await call(`/flowcharts/fixture-flowchart/revisions/${revisions[1].id}/restore`, 'POST', { expectedVersion: 1 })).json()
  assert.equal(restored.version, 2)
  assert.equal(restored.title, original.title)
  const create = { ...draft, creationKey: '11111111-2222-4333-8444-555555555555' }
  await assert.rejects(call('/flowcharts', 'POST', create), /首次响应丢失/)
  const retry = await (await call('/flowcharts', 'POST', create)).json()
  assert.equal(source.flowcharts.length, 2)
  assert.equal((await (await call('/flowcharts', 'POST', create)).json()).id, retry.id)
  assert.equal((await call('/flowcharts/fixture-flowchart', 'DELETE')).status, 204)
  assert.equal((await (await call('/flowcharts/trash')).json()).length, 1)
  assert.equal((await call('/flowcharts/fixture-flowchart')).status, 404)
  assert.equal((await call('/flowcharts/fixture-flowchart/restore', 'POST')).status, 200)
  assert.deepEqual(invalidations, ['owner:fixture-flowchart'])
  assert.equal((await (await call('/flowcharts/trash')).json()).length, 0)
})
