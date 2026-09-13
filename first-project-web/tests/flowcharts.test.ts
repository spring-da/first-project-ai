import assert from 'node:assert/strict'
import { test } from 'node:test'
import { reactive } from 'vue'
import { emptyDiagram, makeNode, makeEdge, validateDiagram, exportFlowchartSource, importFlowchartSource, createTemplate } from '../src/utils/flowcharts.ts'
import { edgeToCell } from '../src/utils/flowchartAdapter.ts'

test('edge adapter uses rounded orthogonal runtime styling for editable paths', () => {
  const edge = makeEdge('source', 'target')
  const cell = edgeToCell(edge)
  assert.deepEqual(cell.router, { name: 'manhattan', args: { padding: 24, step: 16 } })
  assert.deepEqual(cell.connector, { name: 'rounded', args: { radius: 8 } })
})

test('reactive shared diagrams validate into detached plain data', () => {
  const original = reactive(createTemplate('sequence'))
  const detached = validateDiagram(original)
  assert.deepEqual(detached, original)
  detached.nodes[0]!.label = 'detached'
  assert.notEqual(original.nodes[0]!.label, 'detached')
})

test('source roundtrip preserves Chinese multiline labels, shapes, ports and bends without server identity', () => {
  const a = makeNode('diamond', 10, 20, '判断\n是否通过'), b = makeNode('database', 300, 20, '数据库')
  const edge = makeEdge(a.id, b.id); edge.vertices = [{ x: 150, y: 70 }]; edge.label = '是'
  const diagram = { schemaVersion: 1 as const, nodes: [a, b], edges: [edge] }
  const source = exportFlowchartSource('审批图', diagram)
  assert.deepEqual(importFlowchartSource(source), { title: '审批图', diagram })
  assert.equal(source.includes('ownerId'), false)
})
test('unsafe configuration, broken endpoints, duplicate ids and non-finite coordinates cannot reach engine', () => {
  const a = makeNode('rectangle', 0, 0, '<script>literal text</script>')
  assert.equal(validateDiagram({ ...emptyDiagram(), nodes: [a] }).nodes[0]!.label, a.label)
  for (const invalid of [
    { ...emptyDiagram(), markup: '<script/>' },
    { ...emptyDiagram(), nodes: [{ ...a, x: Infinity }] },
    { ...emptyDiagram(), nodes: [a, a] },
    { ...emptyDiagram(), nodes: [{ ...a, attrs: { href: 'https://example.com' } }] },
    { ...emptyDiagram(), nodes: [a], edges: [makeEdge(a.id, 'missing')] },
  ]) assert.throws(() => validateDiagram(invalid))
})
test('limits reject oversized graphs and invalid source versions without changing active diagram', () => {
  assert.throws(() => validateDiagram({ ...emptyDiagram(), nodes: Array.from({ length: 1001 }, () => makeNode('text', 0, 0)) }))
  assert.throws(() => importFlowchartSource('{"format":"devnest-flowchart","schemaVersion":2}'))
  assert.throws(() => validateDiagram({ ...emptyDiagram(), nodes: [{ ...makeNode('text', 0, 0), width: 0 }] }))
  for (const kind of ['sequence', 'decision', 'architecture'] as const) {
    const diagram = createTemplate(kind)
    assert.ok(validateDiagram(diagram).nodes.length >= 3)
    assert.ok(diagram.edges.every(e => diagram.nodes.some(n => n.id === e.source.nodeId) && diagram.nodes.some(n => n.id === e.target.nodeId)))
  }
})
