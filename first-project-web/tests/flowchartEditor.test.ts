import assert from 'node:assert/strict'
import test from 'node:test'
import { FLOWCHART_SHAPES, filterFlowchartShapes, type FlowchartShapeCategory, classifyCanvasWheel, normalizeCanvasWheelDelta, shouldOpenProperties } from '../src/utils/flowchartEditor.ts'
import {
  createEdgeDragSnapshot,
  describeOrthogonalSegments,
  ensureEditableOrthogonalRoute,
  insertOrthogonalVertex,
  moveOrthogonalSegment,
  moveOrthogonalVertex,
  normalizeOrthogonalPoints,
  removeOrthogonalVertex,
  restoreEdgeDragSnapshot,
} from '../src/utils/flowchartEdgeEditor.ts'

test('canvas wheel classifies ordinary scrolling as pan and modified scrolling as zoom', () => {
  assert.equal(classifyCanvasWheel({ ctrlKey: false, metaKey: false }), 'pan')
  assert.equal(classifyCanvasWheel({ ctrlKey: true, metaKey: false }), 'zoom')
  assert.equal(classifyCanvasWheel({ ctrlKey: false, metaKey: true }), 'zoom')
})

test('canvas wheel normalizes line and page deltas without browser globals', () => {
  assert.deepEqual(normalizeCanvasWheelDelta({ deltaX: 2, deltaY: -3, deltaMode: 0 }), { x: 2, y: -3 })
  assert.deepEqual(normalizeCanvasWheelDelta({ deltaX: 1, deltaY: 2, deltaMode: 1 }), { x: 16, y: 32 })
  assert.deepEqual(normalizeCanvasWheelDelta({ deltaX: 1, deltaY: 2, deltaMode: 2 }), { x: 800, y: 1600 })
})

test('only explicit edit gestures open the properties panel', () => {
  assert.equal(shouldOpenProperties('single'), false)
  assert.equal(shouldOpenProperties('double'), true)
  assert.equal(shouldOpenProperties('context'), true)
})

test('shape palette groups all supported shapes into ProcessOn-style categories', () => {
  const categories = new Set(FLOWCHART_SHAPES.map(shape => shape.category))
  assert.deepEqual([...categories], ['flow', 'basic', 'data', 'annotation'])
  assert.equal(FLOWCHART_SHAPES.length, 7)
})

test('shape palette search matches labels and filters by category', () => {
  assert.deepEqual(filterFlowchartShapes('判断').map(shape => shape.kind), ['diamond'])
  assert.deepEqual(filterFlowchartShapes('', 'data').map(shape => shape.kind), ['database'])
  assert.deepEqual(filterFlowchartShapes('输入', 'basic').map(shape => shape.kind), ['io'])
  assert.equal(filterFlowchartShapes('不存在').length, 0)
})

test('shape palette category accepts all as a stable filter value', () => {
  const expected: Record<FlowchartShapeCategory, number> = { all: 7, flow: 3, basic: 2, data: 1, annotation: 1 }
  for (const category of Object.keys(expected) as FlowchartShapeCategory[]) assert.equal(filterFlowchartShapes('', category).length, expected[category])
})

test('orthogonal edge normalization removes duplicate and collinear route points', () => {
  assert.deepEqual(normalizeOrthogonalPoints([
    { x: 0, y: 0 },
    { x: 40, y: 0 },
    { x: 80, y: 0 },
    { x: 80, y: 40 },
    { x: 80, y: 40 },
  ]), [
    { x: 0, y: 0 },
    { x: 80, y: 0 },
    { x: 80, y: 40 },
  ])
})

test('editable route is built from endpoints without retaining router micro-bends', () => {
  assert.deepEqual(ensureEditableOrthogonalRoute([
    { x: 0, y: 0 }, { x: 0, y: 0 }, { x: 80, y: 0 }, { x: 80, y: 40 },
  ]), [
    { x: 0, y: 0 }, { x: 80, y: 0 }, { x: 80, y: 40 },
  ])
  assert.deepEqual(ensureEditableOrthogonalRoute([{ x: 0, y: 0 }, { x: 160, y: 0 }]), [
    { x: 0, y: 0 }, { x: 0, y: 32 }, { x: 160, y: 32 }, { x: 160, y: 0 },
  ])
  assert.deepEqual(ensureEditableOrthogonalRoute([{ x: 0, y: 0 }, { x: 160, y: 0 }], 32, false), [
    { x: 0, y: 0 }, { x: 160, y: 0 },
  ])
})

test('orthogonal edge segments describe axis, midpoint and length while preserving source indexes', () => {
  assert.deepEqual(describeOrthogonalSegments([
    { x: 0, y: 0 },
    { x: 80, y: 0 },
    { x: 80, y: 40 },
  ]), [
    { index: 0, start: { x: 0, y: 0 }, end: { x: 80, y: 0 }, axis: 'x', midpoint: { x: 40, y: 0 }, length: 80 },
    { index: 1, start: { x: 80, y: 0 }, end: { x: 80, y: 40 }, axis: 'y', midpoint: { x: 80, y: 20 }, length: 40 },
  ])
  assert.deepEqual(describeOrthogonalSegments([
    { x: 0, y: 0 },
    { x: 2, y: 0 },
    { x: 80, y: 0 },
  ], 8).map(segment => segment.index), [1])
})

test('orthogonal edge segment movement locks the perpendicular axis, snaps, and moves adjacent vertices', () => {
  const points = [
    { x: 0, y: 0 },
    { x: 80, y: 0 },
    { x: 80, y: 80 },
    { x: 160, y: 80 },
  ]
  assert.deepEqual(moveOrthogonalSegment(points, 1, { x: 17, y: 13 }, { gridSize: 8 }), [
    { x: 0, y: 0 },
    { x: 96, y: 0 },
    { x: 96, y: 80 },
    { x: 160, y: 80 },
  ])
  assert.deepEqual(points[1], { x: 80, y: 0 })
})

test('orthogonal edge segment movement respects minimum gap and bounds', () => {
  const points = [
    { x: 0, y: 0 },
    { x: 80, y: 0 },
    { x: 80, y: 80 },
    { x: 160, y: 80 },
  ]
  assert.deepEqual(moveOrthogonalSegment(points, 1, { x: -100, y: 0 }, { minGap: 16, bounds: { minX: 24, maxX: 120 } }), [
    { x: 0, y: 0 },
    { x: 24, y: 0 },
    { x: 24, y: 80 },
    { x: 160, y: 80 },
  ])
})

test('moving a first or last segment keeps fixed endpoints and inserts a dogleg', () => {
  assert.deepEqual(moveOrthogonalSegment([
    { x: 0, y: 100 }, { x: 0, y: 40 }, { x: 80, y: 40 },
  ], 0, { x: 24, y: 0 }, { gridSize: 8 }), [
    { x: 0, y: 100 }, { x: 24, y: 100 }, { x: 24, y: 40 }, { x: 80, y: 40 },
  ])
  assert.deepEqual(moveOrthogonalSegment([
    { x: 0, y: 40 }, { x: 80, y: 40 }, { x: 80, y: 100 },
  ], 1, { x: 24, y: 0 }, { gridSize: 8 }), [
    { x: 0, y: 40 }, { x: 104, y: 40 }, { x: 104, y: 100 }, { x: 80, y: 100 },
  ])
})

test('moving an interior corner keeps both adjacent legs orthogonal', () => {
  assert.deepEqual(moveOrthogonalVertex([
    { x: 0, y: 0 }, { x: 80, y: 0 }, { x: 80, y: 80 }, { x: 160, y: 80 }, { x: 160, y: 160 },
  ], 2, { x: 17, y: 13 }, { gridSize: 8 }), [
    { x: 0, y: 0 }, { x: 96, y: 0 }, { x: 96, y: 96 }, { x: 160, y: 96 }, { x: 160, y: 160 },
  ])
})

test('orthogonal edge vertex insertion projects to the selected segment and removal protects endpoints', () => {
  const points = [{ x: 0, y: 0 }, { x: 80, y: 0 }, { x: 80, y: 80 }]
  assert.deepEqual(insertOrthogonalVertex(points, 0, { x: 32, y: 24 }), [
    { x: 0, y: 0 }, { x: 32, y: 0 }, { x: 32, y: 8 }, { x: 80, y: 8 }, { x: 80, y: 0 }, { x: 80, y: 80 },
  ])
  assert.deepEqual(removeOrthogonalVertex(points, 0), points)
  assert.deepEqual(removeOrthogonalVertex(points, 1), [{ x: 0, y: 0 }, { x: 80, y: 80 }])
})

test('clicking a straight orthogonal segment inserts a visible dogleg with minimum segment length', () => {
  const points = [{ x: 0, y: 40 }, { x: 160, y: 40 }]
  assert.deepEqual(insertOrthogonalVertex(points, 0, { x: 80, y: 40 }, { minGap: 16 }), [
    { x: 0, y: 40 },
    { x: 80, y: 40 },
    { x: 80, y: 56 },
    { x: 160, y: 56 },
    { x: 160, y: 40 },
  ])
})

test('edge drag snapshots clone vertices and router for cancel/restore', () => {
  const vertices = [{ x: 16, y: 24 }]
  const router = { name: 'manhattan', args: { padding: 24 } }
  const snapshot = createEdgeDragSnapshot(vertices, router)
  vertices[0].x = 99
  router.args.padding = 4
  assert.deepEqual(restoreEdgeDragSnapshot(snapshot), {
    vertices: [{ x: 16, y: 24 }],
    router: { name: 'manhattan', args: { padding: 24 } },
  })
  assert.notEqual(restoreEdgeDragSnapshot(snapshot).vertices, snapshot.vertices)
})
