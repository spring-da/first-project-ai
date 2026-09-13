import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const canvasSource = readFileSync(new URL('../src/components/flowcharts/FlowchartCanvas.vue', import.meta.url), 'utf8')
const adapterSource = readFileSync(new URL('../src/utils/flowchartAdapter.ts', import.meta.url), 'utf8')

test('canvas installs the custom edge editor layer instead of native segment blocks', () => {
  assert.match(canvasSource, /flow-canvas__edge-editor/)
  assert.match(canvasSource, /beginEdgeHandleDrag/)
  assert.doesNotMatch(canvasSource, /name:\s*['"]segments['"]\s*as const/)
})

test('canvas copy documents insertion, removal and cancel gestures', () => {
  assert.match(canvasSource, /insertEdgeVertex/)
  assert.match(canvasSource, /removeEdgeVertex/)
  assert.match(canvasSource, /cancelEdgeDrag/)
  assert.match(canvasSource, /Esc 取消/)
  assert.match(canvasSource, /document\.addEventListener\(['"]keydown['"]/) 
})

test('canvas inserts clicked edge vertices with the minimum dogleg gap', () => {
  assert.match(canvasSource, /insertOrthogonalVertex\(state\.points,\s*segment\.index,\s*edgePoint\(point\),\s*\{\s*minGap:\s*16\s*\}\)/s)
})

test('cancel restores persisted route vertices and refreshes the silent view', () => {
  assert.match(canvasSource, /restoreEdgeDragSnapshot\(\{ vertices: drag\.baseVertices/)
  assert.match(canvasSource, /requestViewUpdate|\.update\(\{\s*async:\s*false/)
})

test('selection actions use a context position anchored to the selected cell', () => {
  assert.match(canvasSource, /syncContextToolbar/)
  assert.match(canvasSource, /contextStyle/)
  assert.doesNotMatch(canvasSource, /\.flow-canvas__context\s*\{[^}]*left:\s*50%/)
})

test('empty canvas offers a compact first action instead of an unbounded blank area', () => {
  assert.match(canvasSource, /flow-canvas__empty/)
  assert.match(canvasSource, /cellCount/)
})

test('orthogonal routes honor port direction constraints and avoid terminal obstacles', () => {
  assert.match(adapterSource, /startDirections:\s*\[edge\.source\.port\]/)
  assert.match(adapterSource, /endDirections:\s*\[edge\.target\.port\]/)
  assert.match(adapterSource, /excludeTerminals:\s*\[['"]source['"],\s*['"]target['"]\]/)
})

test('edge previews normalize transient collinear points while dragging', () => {
  assert.match(canvasSource, /const moved = activeEdgeDrag\.kind[\s\S]*normalizeOrthogonalPoints\(moved,\s*8\)/)
})

test('fit view uses the available canvas more efficiently for compact diagrams', () => {
  assert.match(canvasSource, /zoomToFit\(\{\s*padding:\s*32,\s*maxScale:\s*1\.25\s*\}/)
})
