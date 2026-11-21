import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const canvasSource = readFileSync(new URL('../src/components/flowcharts/FlowchartCanvas.vue', import.meta.url), 'utf8')
const adapterSource = readFileSync(new URL('../src/utils/flowchartAdapter.ts', import.meta.url), 'utf8')
const editorPluginsSource = readFileSync(new URL('../src/services/flowchartEditorPlugins.ts', import.meta.url), 'utf8')
const exportPluginSource = readFileSync(new URL('../src/services/flowchartExportPlugin.ts', import.meta.url), 'utf8')

test('heavy flowchart capabilities stay out of the read-only canvas chunk', () => {
  assert.match(canvasSource, /from ['"]@antv\/x6\/es\/graph['"]/)
  assert.match(canvasSource, /import\(['"]\.\.\/\.\.\/services\/flowchartEditorPlugins['"]\)/)
  assert.match(canvasSource, /import\(['"]\.\.\/\.\.\/services\/flowchartExportPlugin['"]\)/)
  assert.doesNotMatch(canvasSource, /import\s*\{[^}]*History[^}]*\}\s*from\s*['"]@antv\/x6['"]/s)
  assert.match(editorPluginsSource, /@antv\/x6\/es\/plugin\/history/)
  assert.match(exportPluginSource, /@antv\/x6\/es\/plugin\/export/)
})

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

test('canvas keeps document actions out of the viewport toolbar', () => {
  assert.match(canvasSource, /aria-label="画布工具"/)
  assert.doesNotMatch(canvasSource, /分享流程图/)
  assert.doesNotMatch(canvasSource, /历史版本/)
})

test('image export hides workbench overlays and restores them', () => {
  assert.match(canvasSource, /runCanvasExportCycle/)
  for (const overlay of ['flow-canvas__tools', 'flow-canvas__context', 'flow-canvas__edge-editor', 'flow-canvas__mini', 'flow-canvas__hint']) {
    assert.match(canvasSource, new RegExp(`v-(?:if|show)="[^\"]*!exportingOverlay[^\"]*"[^>]*class="${overlay}"|class="${overlay}"[^>]*v-(?:if|show)="[^\"]*!exportingOverlay`, 's'))
  }
})

test('the minimap stays mounted while export temporarily hides it', () => {
  assert.match(canvasSource, /v-show="showMini && !exportingOverlay"\s+ref="mini"\s+class="flow-canvas__mini"/)
  assert.doesNotMatch(canvasSource, /v-if="[^\"]*exportingOverlay[^\"]*"[^>]*ref="mini"/)
})

test('panel collapse state schedules a selected-cell viewport recompute', () => {
  assert.match(canvasSource, /panelCollapseState\?:/)
  assert.match(canvasSource, /watch\(\(\) => props\.panelCollapseState,[\s\S]*nextTick\([\s\S]*keepSelectedInView/)
})

test('resize observations are coalesced to one canvas layout per frame', () => {
  assert.match(canvasSource, /function scheduleObservedResize\(\)/)
  assert.match(canvasSource, /resizeFrame = requestAnimationFrame/)
  assert.match(canvasSource, /new ResizeObserver\(scheduleObservedResize\)/)
  assert.match(canvasSource, /cancelAnimationFrame\(resizeFrame\)/)
})
