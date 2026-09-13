<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, watch, nextTick, reactive, shallowRef } from 'vue'
import { Graph, History, Selection, Snapline, Clipboard, Transform, MiniMap, Export, Dnd } from '@antv/x6'
import type { Cell, Edge as X6Edge, EdgeView } from '@antv/x6'
import { Minus, Plus, Scan, Map, Grid2X2, MousePointer2, Hand, Undo2, Redo2, Maximize2, PanelRightClose, PanelRightOpen, Copy, ClipboardPaste, Trash2, ArrowUpToLine, ArrowDownToLine } from 'lucide-vue-next'
import type { FlowchartDiagram, FlowNodeKind, FlowNode, FlowEdge } from '../../types/flowcharts'
import { nodeToCell, edgeToCell, graphDiagram } from '../../utils/flowchartAdapter'
import { makeNode, makeEdge, validateDiagram, downloadBlob, flowchartFingerprint } from '../../utils/flowcharts'
import { classifyCanvasWheel, normalizeCanvasWheelDelta } from '../../utils/flowchartEditor'
import { describeOrthogonalSegments, ensureEditableOrthogonalRoute, insertOrthogonalVertex, moveOrthogonalSegment, moveOrthogonalVertex, normalizeOrthogonalPoints, removeOrthogonalVertex, createEdgeDragSnapshot, restoreEdgeDragSnapshot, type EdgePoint, type OrthogonalSegment } from '../../utils/flowchartEdgeEditor'

const props = withDefaults(defineProps<{ diagram: FlowchartDiagram; readonly?: boolean; viewportKey?: string; propertiesOpen?: boolean }>(), { readonly: false })
const emit = defineEmits<{ change: [diagram: FlowchartDiagram]; select: [cell: FlowNode | FlowEdge | null]; 'edit-label': []; 'toggle-properties': []; error: [message: string] }>()
const root = ref<HTMLElement>(), host = ref<HTMLElement>(), mini = ref<HTMLElement>(), contextBar = ref<HTMLElement>()
const zoom = ref(100), showMini = ref(true), grid = ref(true), pan = ref(false), canUndo = ref(false), canRedo = ref(false), selectedId = ref<string | null>(null), cellCount = ref(0)
const editorViewport = reactive({ width: 0, height: 0 })
const contextStyle = ref<Record<string, string>>({ left: '8px', top: '66px', transform: 'none' })
interface EdgeEditorHandle {
  id: string
  kind: 'vertex' | 'segment'
  index: number
  x: number
  y: number
  width: number
  height: number
  angle: number
  axis?: 'x' | 'y'
  label: string
}
interface EdgeEditorState {
  edgeId: string
  points: EdgePoint[]
  path: string
  handles: EdgeEditorHandle[]
  dragging: boolean
}
interface ActiveEdgeDrag {
  edgeId: string
  kind: 'vertex' | 'segment'
  index: number
  axis?: 'x' | 'y'
  pointerId: number
  start: EdgePoint
  basePoints: EdgePoint[]
  baseVertices: EdgePoint[]
  router: ReturnType<X6Edge['getRouter']>
  currentPoints: EdgePoint[]
}
const edgeEditor = shallowRef<EdgeEditorState | null>(null)
let graph: Graph | undefined, dnd: Dnd | undefined, transform: Transform | undefined, observer: ResizeObserver | undefined
let disposed = false, applying = false, lastJson = '', frame = 0
let cancelPendingDrag: (() => void) | undefined
let activeEdgeDrag: ActiveEdgeDrag | undefined
let edgeDragListeners: { move: (event: PointerEvent) => void; up: (event: PointerEvent) => void; cancel: (event: PointerEvent) => void; keydown: (event: KeyboardEvent) => void } | undefined
let edgeDragActive = false
const inputFocused = () => document.activeElement instanceof HTMLElement && Boolean(document.activeElement.closest('input,textarea,select,[contenteditable="true"],[role="dialog"]'))

function handleWheel(event: WheelEvent) {
  if (!graph || classifyCanvasWheel(event) === 'zoom') return
  const delta = normalizeCanvasWheelDelta(event)
  if (!delta.x && !delta.y) return
  event.preventDefault()
  graph.translateBy(-delta.x, -delta.y)
  saveViewport()
}

function edgePoint(value: { x: number; y: number }): EdgePoint { return { x: Number(value.x), y: Number(value.y) } }
function clonePoints(points: readonly EdgePoint[]): EdgePoint[] { return points.map(point => edgePoint(point)) }

/** Turn an X6 route into a predictable orthogonal editing skeleton. */
function orthogonalRoute(edge: X6Edge): EdgePoint[] {
  const view = graph?.findViewByCell(edge) as EdgeView | null
  const source = view?.sourcePoint ?? edge.getSourcePoint()
  const target = view?.targetPoint ?? edge.getTargetPoint()
  const routed = view?.routePoints?.map(point => edgePoint(point))
  const vertices = edge.getVertices().map(point => edgePoint(point))
  const points = routed?.length ? routed : [edgePoint(source), ...vertices, edgePoint(target)]
  return ensureEditableOrthogonalRoute(points, 32, false)
}

function distance(a: EdgePoint, b: EdgePoint): number { return Math.hypot(a.x - b.x, a.y - b.y) }
function moveToward(from: EdgePoint, to: EdgePoint, amount: number): EdgePoint {
  const length = distance(from, to)
  if (!length || amount <= 0) return edgePoint(from)
  const ratio = Math.min(0.45, amount / length)
  return { x: from.x + (to.x - from.x) * ratio, y: from.y + (to.y - from.y) * ratio }
}
function roundedPath(points: readonly EdgePoint[], radius = 8): string {
  if (!points.length) return ''
  let path = `M ${points[0]!.x} ${points[0]!.y}`
  for (let index = 1; index < points.length; index += 1) {
    const current = points[index]!
    const previous = points[index - 1]!
    const next = points[index + 1]
    if (!next) { path += ` L ${current.x} ${current.y}`; continue }
    const before = moveToward(current, previous, radius)
    const after = moveToward(current, next, radius)
    path += ` L ${before.x} ${before.y} Q ${current.x} ${current.y} ${after.x} ${after.y}`
  }
  return path
}
function localToEditor(point: EdgePoint): EdgePoint {
  const rect = host.value?.getBoundingClientRect()
  const client = graph?.localToClient(point)
  return { x: (client?.x ?? point.x) - (rect?.left ?? 0), y: (client?.y ?? point.y) - (rect?.top ?? 0) }
}
function buildEdgeEditor(edge: X6Edge, points = orthogonalRoute(edge), dragging = Boolean(activeEdgeDrag)): EdgeEditorState {
  const screen = points.map(localToEditor)
  const segments = describeOrthogonalSegments(points, 28)
  const handles: EdgeEditorHandle[] = []
  for (let index = 1; index < points.length - 1; index += 1) {
    const previous = points[index - 1]!, current = points[index]!, next = points[index + 1]!
    if ((previous.x === current.x && current.x === next.x) || (previous.y === current.y && current.y === next.y)) continue
    const position = screen[index]!
    handles.push({ id: `vertex-${index}`, kind: 'vertex', index, x: position.x, y: position.y, width: 14, height: 14, angle: 0, label: `移动第 ${index} 个拐点` })
  }
  for (const segment of segments) {
    const position = localToEditor(segment.midpoint)
    handles.push({ id: `segment-${segment.index}`, kind: 'segment', index: segment.index, x: position.x, y: position.y, width: segment.axis === 'x' ? 30 : 10, height: segment.axis === 'x' ? 10 : 30, angle: 0, axis: segment.axis, label: segment.axis === 'x' ? '上下移动线段' : '左右移动线段' })
  }
  return { edgeId: edge.id, points: clonePoints(points), path: roundedPath(screen), handles, dragging }
}
function syncEdgeEditor(cell?: Cell | null) {
  if (!graph || props.readonly || activeEdgeDrag) return
  const candidate = cell ?? (selectedId.value ? graph.getCellById(selectedId.value) : null)
  if (!candidate?.isEdge() || candidate.getData<FlowEdge>()?.kind !== 'orthogonal') { edgeEditor.value = null; return }
  edgeEditor.value = buildEdgeEditor(candidate as X6Edge)
  editorViewport.width = host.value?.clientWidth ?? 0; editorViewport.height = host.value?.clientHeight ?? 0
}
function syncContextToolbar(cell?: Cell | null) {
  if (!host.value || props.readonly || !selectedId.value) return
  const selected = cell ?? graph?.getCellById(selectedId.value)
  if (!selected || selected.id !== selectedId.value) return
  const bounds = selected.getBBox()
  const topLeft = graph?.localToClient({ x: bounds.x, y: bounds.y })
  const bottomRight = graph?.localToClient({ x: bounds.x + bounds.width, y: bounds.y + bounds.height })
  const hostRect = host.value.getBoundingClientRect()
  if (!topLeft || !bottomRight) return
  const barWidth = contextBar.value?.offsetWidth ?? 174
  const barHeight = contextBar.value?.offsetHeight ?? 40
  const left = ((topLeft.x + bottomRight.x) / 2) - hostRect.left
  const above = topLeft.y - hostRect.top - barHeight - 10
  const below = bottomRight.y - hostRect.top + 10
  const minTop = 66
  const top = above >= minTop ? above : below
  const maxLeft = Math.max(8, hostRect.width - barWidth - 8)
  contextStyle.value = {
    left: `${Math.max(8, Math.min(maxLeft, left - barWidth / 2))}px`,
    top: `${Math.max(8, Math.min(Math.max(8, hostRect.height - barHeight - 8), top))}px`,
    transform: 'none',
  }
}
function resetContextToolbar() { contextStyle.value = { left: '8px', top: '66px', transform: 'none' } }
function keepSelectedInView() {
  if (!graph || !host.value || !selectedId.value) return
  const cell = graph.getCellById(selectedId.value)
  if (!cell) return
  const screen = graph.localToClient(cell.getBBox())
  const viewport = host.value.getBoundingClientRect()
  const margin = 36
  let dx = 0
  let dy = 0
  if (screen.width <= viewport.width - margin * 2) {
    if (screen.x < viewport.x + margin) dx = viewport.x + margin - screen.x
    else if (screen.x + screen.width > viewport.right - margin) dx = viewport.right - margin - (screen.x + screen.width)
  }
  if (screen.height <= viewport.height - margin * 2) {
    if (screen.y < viewport.y + margin) dy = viewport.y + margin - screen.y
    else if (screen.y + screen.height > viewport.bottom - margin) dy = viewport.bottom - margin - (screen.y + screen.height)
  }
  if (dx || dy) {
    graph.translateBy(dx, dy)
    saveViewport()
    nextTick(() => syncContextToolbar(cell))
  }
}
function nearestSegment(points: readonly EdgePoint[], point: EdgePoint): OrthogonalSegment | undefined {
  return describeOrthogonalSegments(points, 1).reduce<OrthogonalSegment | undefined>((best, segment) => {
    const distanceTo = segment.axis === 'x'
      ? Math.abs(point.y - segment.start.y) + (point.x < Math.min(segment.start.x, segment.end.x) ? Math.min(segment.start.x, segment.end.x) - point.x : point.x > Math.max(segment.start.x, segment.end.x) ? point.x - Math.max(segment.start.x, segment.end.x) : 0)
      : Math.abs(point.x - segment.start.x) + (point.y < Math.min(segment.start.y, segment.end.y) ? Math.min(segment.start.y, segment.end.y) - point.y : point.y > Math.max(segment.start.y, segment.end.y) ? point.y - Math.max(segment.start.y, segment.end.y) : 0)
    if (!best) return segment
    const bestDistance = best.axis === 'x'
      ? Math.abs(point.y - best.start.y) + (point.x < Math.min(best.start.x, best.end.x) ? Math.min(best.start.x, best.end.x) - point.x : point.x > Math.max(best.start.x, best.end.x) ? point.x - Math.max(best.start.x, best.end.x) : 0)
      : Math.abs(point.x - best.start.x) + (point.y < Math.min(best.start.y, best.end.y) ? Math.min(best.start.y, best.end.y) - point.y : point.y > Math.max(best.start.y, best.end.y) ? point.y - Math.max(best.start.y, best.end.y) : 0)
    return distanceTo < bestDistance ? segment : best
  }, undefined)
}

function routeVertices(points: readonly EdgePoint[]): EdgePoint[] { return clonePoints(points.slice(1, -1)) }
function currentEditorEdge(): X6Edge | undefined {
  const cell = selectedId.value ? graph?.getCellById(selectedId.value) : undefined
  return cell?.isEdge() ? cell as X6Edge : undefined
}
function applyPreviewRoute(edge: X6Edge, points: readonly EdgePoint[]) {
  const next = clonePoints(points)
  edge.setVertices(routeVertices(next), { ui: true, toolId: 'flow-edge-editor' })
  edgeEditor.value = buildEdgeEditor(edge, next, true)
}
function refreshEdgeView(edge: X6Edge) {
  const view = graph?.findViewByCell(edge) as EdgeView | null
  // Silent setters are intentional while cancelling a drag (they must not
  // create a second history entry), so explicitly refresh the mounted view.
  view?.update({ async: false, ui: true, toolId: 'flow-edge-editor' })
}
function removeEdgeDragListeners() {
  if (!edgeDragListeners) return
  document.removeEventListener('pointermove', edgeDragListeners.move)
  document.removeEventListener('pointerup', edgeDragListeners.up)
  document.removeEventListener('pointercancel', edgeDragListeners.cancel)
  document.removeEventListener('keydown', edgeDragListeners.keydown)
  edgeDragListeners = undefined
}
function finishEdgeDrag(cancel = false) {
  const drag = activeEdgeDrag
  if (!drag || !graph) return
  removeEdgeDragListeners()
  const cell = graph.getCellById(drag.edgeId)
  const edge = cell?.isEdge() ? cell as X6Edge : undefined
  if (!edge) { activeEdgeDrag = undefined; edgeEditor.value = null; return }
  if (cancel) {
    // Restore the persisted route state captured at pointer-down. The
    // editable skeleton may contain derived doglegs that Manhattan would
    // route differently when persisted, so only the original vertices belong
    // in the cancelled model state.
    const snapshot = restoreEdgeDragSnapshot({ vertices: drag.baseVertices, router: drag.router })
    edge.setVertices(snapshot.vertices, { ui: true, toolId: 'flow-edge-editor', silent: true })
    edge.setRouter(snapshot.router, { ui: true, toolId: 'flow-edge-editor', silent: true })
    refreshEdgeView(edge)
  } else {
    const finalPoints = normalizeOrthogonalPoints(drag.currentPoints, 8)
    edge.setVertices(routeVertices(finalPoints), { ui: true, toolId: 'flow-edge-editor' })
    edge.setRouter(drag.router, { ui: true, toolId: 'flow-edge-editor' })
  }
  edge.stopBatch('move-segment', { ui: true, toolId: 'flow-edge-editor' })
  activeEdgeDrag = undefined
  edgeDragActive = false
  syncEdgeEditor(edge)
  if (!cancel) flush()
}
function cancelEdgeDrag() { finishEdgeDrag(true) }
function beginEdgeHandleDrag(handle: EdgeEditorHandle, event: PointerEvent) {
  if (props.readonly || !graph || activeEdgeDrag) return
  const edge = currentEditorEdge()
  if (!edge || edge.id !== edgeEditor.value?.edgeId) return
  const points = clonePoints(edgeEditor.value?.points ?? orthogonalRoute(edge))
  const start = graph.clientToLocal(event.clientX, event.clientY)
  const snapshot = createEdgeDragSnapshot(edge.getVertices().map(point => edgePoint(point)), edge.getRouter())
  activeEdgeDrag = { edgeId: edge.id, kind: handle.kind, index: handle.index, axis: handle.axis, pointerId: event.pointerId, start, basePoints: points, baseVertices: snapshot.vertices, router: snapshot.router, currentPoints: points }
  edgeDragActive = true
  edge.startBatch('move-segment', { ui: true, toolId: 'flow-edge-editor' })
  edge.setRouter('normal', undefined, { ui: true, toolId: 'flow-edge-editor' })
  edge.setVertices(routeVertices(points), { ui: true, toolId: 'flow-edge-editor' })
  edgeEditor.value = buildEdgeEditor(edge, points, true)
  const move = (next: PointerEvent) => {
    if (!activeEdgeDrag || next.pointerId !== activeEdgeDrag.pointerId || !graph) return
    const local = graph.clientToLocal(next.clientX, next.clientY)
    const delta = { x: local.x - activeEdgeDrag.start.x, y: local.y - activeEdgeDrag.start.y }
    const moved = activeEdgeDrag.kind === 'segment'
      ? moveOrthogonalSegment(activeEdgeDrag.basePoints, activeEdgeDrag.index, delta, { gridSize: 16, minGap: 16 })
      : moveOrthogonalVertex(activeEdgeDrag.basePoints, activeEdgeDrag.index, { x: Math.round(delta.x / 8) * 8, y: Math.round(delta.y / 8) * 8 }, { gridSize: 8, minGap: 16 })
    const preview = normalizeOrthogonalPoints(moved, 8)
    activeEdgeDrag.currentPoints = preview
    applyPreviewRoute(edge, preview)
  }
  const up = (next: PointerEvent) => { if (next.pointerId === event.pointerId) finishEdgeDrag(false) }
  const cancel = (next: PointerEvent) => { if (next.pointerId === event.pointerId) finishEdgeDrag(true) }
  const onKeydown = (next: KeyboardEvent) => {
    if (next.key !== 'Escape') return
    next.preventDefault()
    next.stopPropagation()
    finishEdgeDrag(true)
  }
  edgeDragListeners = { move, up, cancel, keydown: onKeydown }
  document.addEventListener('pointermove', move)
  document.addEventListener('pointerup', up, { once: false })
  document.addEventListener('pointercancel', cancel, { once: false })
  document.addEventListener('keydown', onKeydown)
  event.preventDefault(); event.stopPropagation()
}
function insertEdgeVertex(event: MouseEvent) {
  if (props.readonly || activeEdgeDrag || !graph) return
  const edge = currentEditorEdge()
  const state = edgeEditor.value
  if (!edge || !state || edge.id !== state.edgeId) return
  const point = graph.snapToGrid(event.clientX, event.clientY)
  const segment = nearestSegment(state.points, edgePoint(point))
  if (!segment) return
  const next = insertOrthogonalVertex(state.points, segment.index, edgePoint(point), { minGap: 16 })
  if (next.length === state.points.length) return
  graph.batchUpdate('add-vertex', () => edge.setVertices(routeVertices(next), { ui: true, toolId: 'flow-edge-editor' }))
  edgeEditor.value = buildEdgeEditor(edge, next)
  flush()
  event.preventDefault(); event.stopPropagation()
}
function removeEdgeVertex(handle: EdgeEditorHandle, event: MouseEvent) {
  if (props.readonly || activeEdgeDrag || handle.kind !== 'vertex' || !graph) return
  const edge = currentEditorEdge()
  const state = edgeEditor.value
  if (!edge || !state || edge.id !== state.edgeId) return
  const next = removeOrthogonalVertex(state.points, handle.index, 8)
  if (next.length === state.points.length) return
  graph.batchUpdate('remove-vertex', () => edge.setVertices(routeVertices(next), { ui: true, toolId: 'flow-edge-editor' }))
  edgeEditor.value = buildEdgeEditor(edge, next)
  flush()
  event.preventDefault(); event.stopPropagation()
}
function flush() {
  if (!graph || props.readonly || applying || disposed || edgeDragActive) return
  const diagram = graphDiagram(graph), json = flowchartFingerprint(diagram)
  if (json !== lastJson) { lastJson = json; emit('change', diagram) }
  canUndo.value = graph.canUndo(); canRedo.value = graph.canRedo()
  const cell = graph.getSelectedCells()[0]
  if (cell) emit('select', [...diagram.nodes, ...diagram.edges].find(i => i.id === cell.id) ?? null)
}
function schedule() { if (!frame && !applying && !edgeDragActive) frame = requestAnimationFrame(() => { frame = 0; flush() }) }
function select(cell?: Cell) {
  if (!graph || props.readonly) return
  selectedId.value = cell?.id ?? null
  graph.getEdges().forEach(edge => edge.removeTools())
  syncEdgeEditor(cell)
  if (cell) nextTick(() => syncContextToolbar(cell)); else resetContextToolbar()
  const diagram = graphDiagram(graph)
  emit('select', cell ? [...diagram.nodes, ...diagram.edges].find(i => i.id === cell.id) ?? null : null)
}
function replace(diagram: FlowchartDiagram, fit = false) {
  if (!graph) return
  cancelEdgeDrag()
  applying = true
  const safe = validateDiagram(diagram)
  graph.resetCells([...safe.nodes.map(n => graph!.createNode(nodeToCell(n, props.readonly))), ...safe.edges.map(e => graph!.createEdge(edgeToCell(e)))])
  cellCount.value = graph.getCellCount()
  lastJson = flowchartFingerprint(safe); applying = false
  selectedId.value = null
  edgeEditor.value = null
  resetContextToolbar()
  if (!props.readonly) { graph.cleanHistory(); canUndo.value = canRedo.value = false }
  if (fit) nextTick(fitContent)
}
function fitContent() { if (graph?.getCellCount()) graph.zoomToFit({ padding: 32, maxScale: 1.25 }); else { graph?.zoomTo(1); graph?.centerPoint(300, 180) } }
function add(kind: FlowNodeKind) {
  if (!graph || props.readonly) return
  if (graph.getNodes().length >= 1000) { emit('error', '单图最多支持 1,000 个图形。'); return }
  const rect = host.value!.getBoundingClientRect(), center = graph.clientToLocal(rect.x + rect.width / 2, rect.y + rect.height / 2)
  const node = graph.addNode(nodeToCell(makeNode(kind, center.x - 80, center.y - 32)))
  graph.resetSelection([node]); select(node); flush()
}
function startDrag(kind: FlowNodeKind, event: MouseEvent) {
  if (!graph || !dnd || props.readonly || graph.getNodes().length >= 1000) return
  cancelPendingDrag?.()
  const cancel = () => { document.removeEventListener('mousemove', move); document.removeEventListener('mouseup', cancel); cancelPendingDrag = undefined }
  const move = (next: MouseEvent) => {
    if (Math.hypot(next.clientX - event.clientX, next.clientY - event.clientY) < 5) return
    cancel()
    if (graph && dnd) dnd.start(graph.createNode(nodeToCell(makeNode(kind, 0, 0))), next)
  }
  // Starting X6 Dnd on mouse-down consumes the click; wait for actual dragging.
  cancelPendingDrag = cancel
  document.addEventListener('mousemove', move); document.addEventListener('mouseup', cancel, { once: true })
}
function updateCell(value: FlowNode | FlowEdge) {
  const cell = graph?.getCellById(value.id)
  if (!cell || props.readonly) return
  const diagram = graphDiagram(graph!); const collection = 'width' in value ? diagram.nodes : diagram.edges
  const index = collection.findIndex(i => i.id === value.id); if (index < 0) return
  ;(collection as Array<FlowNode | FlowEdge>)[index] = structuredClone(value)
  try { validateDiagram(diagram) } catch (error) { emit('error', (error as Error).message); return }
  graph!.batchUpdate('properties', () => {
    const config = 'width' in value ? nodeToCell(value) : edgeToCell(value)
    // Preserve the shape's built-in geometry/connection attributes when styling.
    cell.setData(structuredClone(value)); cell.setAttrs(config.attrs!); cell.setZIndex(value.zIndex)
    if (cell.isNode() && 'width' in value) { cell.position(value.x, value.y); cell.resize(value.width, value.height) }
    else if (cell.isEdge() && !('width' in value)) { const configEdge = edgeToCell(value); cell.setRouter(value.kind === 'orthogonal' ? configEdge.router! : { name: 'normal' }, { ui: true, toolId: 'properties' }); cell.setConnector(value.kind === 'orthogonal' ? { name: 'rounded', args: { radius: 8 } } : { name: 'normal' }); cell.setLabels(configEdge.labels ?? []) }
  })
  syncEdgeEditor(cell)
  flush()
}
function action(name: 'undo' | 'redo' | 'delete' | 'copy' | 'paste' | 'front' | 'back') {
  if (!graph || props.readonly) return
  if (name === 'undo') graph.undo()
  if (name === 'redo') graph.redo()
  if (name === 'delete') { const selected = graph.getSelectedCells(); graph.batchUpdate('delete', () => graph!.removeCells(selected)); select() }
  if (name === 'copy') graph.copy(graph.getSelectedCells(), { useLocalStorage: false })
  if (name === 'paste') {
    const previous = graphDiagram(graph)
    graph.batchUpdate('paste', () => { const cells = graph!.paste({ offset: 24, useLocalStorage: false }); graph!.resetSelection(cells) })
    try { validateDiagram(graphDiagram(graph)) } catch { replace(previous); emit('error', '粘贴后图形数量超出限制。') }
  }
  if (name === 'front' || name === 'back') graph.batchUpdate('layer', () => graph!.getSelectedCells().forEach(cell => cell.setZIndex(name === 'front' ? Math.min(10000, Math.max(0, ...graph!.getCells().map(c => c.getZIndex() ?? 0)) + 1) : Math.max(-10000, Math.min(0, ...graph!.getCells().map(c => c.getZIndex() ?? 0)) - 1))))
  flush()
}
function keydown(event: KeyboardEvent) {
  if (props.readonly || inputFocused()) return
  const command = event.ctrlKey || event.metaKey, key = event.key.toLowerCase()
  const op = command && key === 'z' ? event.shiftKey ? 'redo' : 'undo' : command && key === 'y' ? 'redo' : command && key === 'c' ? 'copy' : command && key === 'v' ? 'paste' : key === 'delete' || key === 'backspace' ? 'delete' : null
  if (op) { event.preventDefault(); event.stopPropagation(); action(op) }
  if (command && key === 'a') { event.preventDefault(); graph?.resetSelection(graph.getCells()) }
  if (event.key === 'Escape') { if (activeEdgeDrag) cancelEdgeDrag(); else { graph?.cleanSelection(); transform?.clearWidgets(); select() } }
  if (event.code === 'Space') { event.preventDefault(); graph?.enablePanning() }
  const offsets: Record<string, [number, number]> = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] }
  const offset = offsets[event.key]
  if (offset && graph) {
    event.preventDefault()
    const edge = currentEditorEdge()
    if (edge && edgeEditor.value) {
      const delta = { x: offset[0] * (event.shiftKey ? 16 : 8), y: offset[1] * (event.shiftKey ? 16 : 8) }
      const next = edgeEditor.value.points.map(point => ({ x: point.x + delta.x, y: point.y + delta.y }))
      graph.batchUpdate('nudge', () => edge.setVertices(routeVertices(next), { ui: true, toolId: 'flow-edge-editor' }))
      edgeEditor.value = buildEdgeEditor(edge, next)
      flush()
    } else graph.batchUpdate('nudge', () => graph!.getSelectedCells().filter(c => c.isNode()).forEach(c => c.translate(offset[0] * (event.shiftKey ? 10 : 1), offset[1] * (event.shiftKey ? 10 : 1))))
    if (!edge) flush()
  }
}
function keyup(event: KeyboardEvent) { if (event.code === 'Space' && !pan.value && !props.readonly) graph?.disablePanning() }
function togglePan() { pan.value = !pan.value; if (pan.value) graph?.enablePanning(); else graph?.disablePanning() }
function toggleGrid() { grid.value = !grid.value; graph?.drawGrid(grid.value ? { type: 'dot', args: { color: '#cbd5e1', thickness: 1 } } : { type: 'dot', args: { color: 'transparent' } }) }
async function fullscreen() {
  try {
    if (document.fullscreenElement) await document.exitFullscreen()
    // Workspace dialogs and confirmations teleport to body. Keep them inside
    // the fullscreen tree so templates, history and saving remain usable.
    else await (root.value?.closest('.flow-workspace') ? document.documentElement : root.value)?.requestFullscreen()
  } catch { emit('error', '浏览器暂不支持全屏显示。') }
}
function saveViewport() {
  if (!graph || !props.viewportKey) return
  try { localStorage.setItem(`devnest-flowchart-view:${props.viewportKey}`, JSON.stringify({ scale: graph.zoom(), ...graph.translate() })) } catch { /* Viewport persistence is optional. */ }
}
async function exportImage(format: 'png' | 'svg', title: string) {
  if (!graph) return
  flush(); validateDiagram(graphDiagram(graph))
  graph.getCells().forEach(cell => graph!.findViewByCell(cell)?.hideTools()); transform?.clearWidgets()
  try {
    const options = { preserveDimensions: true, copyStyles: false, serializeImages: false, beforeSerialize(svg: SVGSVGElement) {
      svg.querySelectorAll('.x6-port, .x6-cell-tools, .x6-edge-tools').forEach(el => el.remove())
      svg.style.background = '#ffffff'
      const background = document.createElementNS('http://www.w3.org/2000/svg', 'rect')
      const box = svg.getAttribute('viewBox')?.split(/\s+/).map(Number)
      background.setAttribute('x', String(box?.[0] ?? 0)); background.setAttribute('y', String(box?.[1] ?? 0)); background.setAttribute('width', String(box?.[2] ?? '100%')); background.setAttribute('height', String(box?.[3] ?? '100%')); background.setAttribute('fill', '#ffffff'); svg.insertBefore(background, svg.firstChild)
    } }
    if (format === 'svg') downloadBlob(new Blob([await graph.toSVGAsync(options)], { type: 'image/svg+xml' }), `${title}.svg`)
    else {
      const data = await graph.toPNGAsync({ ...options, backgroundColor: '#ffffff', padding: 24, ratio: 2 })
      const bytes = Uint8Array.from(atob(data.split(',')[1]!), c => c.charCodeAt(0))
      downloadBlob(new Blob([bytes], { type: 'image/png' }), `${title}.png`)
    }
  } finally { graph.getCells().forEach(cell => graph!.findViewByCell(cell)?.showTools()) }
}
onMounted(() => {
  if (!host.value) return
  try {
    graph = new Graph({ container: host.value, autoResize: true, background: { color: '#f8fafc' }, grid: { visible: true, size: 16, type: 'dot', args: { color: '#cbd5e1' } },
      scaling: { min: 0.1, max: 3 }, panning: { enabled: props.readonly, eventTypes: ['leftMouseDown', 'rightMouseDown'] }, mousewheel: { enabled: true, modifiers: ['ctrl', 'meta'], zoomAtMousePosition: true, minScale: 0.1, maxScale: 3 }, interacting: !props.readonly,
      preventDefaultContextMenu: true,
      connecting: { allowBlank: false, allowEdge: false, allowNode: false, allowLoop: true, snap: { radius: 20 }, highlight: true,
        validateConnection: ({ targetMagnet }) => !props.readonly && !!targetMagnet && (graph?.getEdges().length ?? 0) <= 2000,
        createEdge: () => graph!.createEdge(edgeToCell(makeEdge('', ''))),
      },
    })
    graph.use(new Export())
    if (!props.readonly) {
      graph.use(new History({ stackSize: 100, beforeAddCommand: (event: string) => !event.includes('tools') && !event.includes('ports') }))
      graph.use(new Selection({ enabled: true, multiple: true, rubberband: true, showNodeSelectionBox: true, showEdgeSelectionBox: false, modifiers: ['shift'] }))
      graph.use(new Snapline({ enabled: true })); graph.use(new Clipboard({ enabled: true, useLocalStorage: false }))
      transform = new Transform({ resizing: { enabled: true, minWidth: 10, minHeight: 10, maxWidth: 10000, maxHeight: 10000 }, rotating: false }); graph.use(transform)
      dnd = new Dnd({ target: graph, scaled: true })
      graph.on('selection:changed', ({ selected }) => select(selected[0]))
      graph.on('cell:dblclick', ({ cell }) => { graph!.resetSelection([cell]); select(cell); emit('edit-label') })
      graph.on('cell:contextmenu', ({ cell, e }) => { e.preventDefault(); e.stopPropagation(); graph!.resetSelection([cell]); select(cell); emit('edit-label') })
      graph.on('blank:click', () => { graph!.cleanSelection(); select() })
      graph.on('blank:contextmenu', ({ e }) => { e.preventDefault(); e.stopPropagation(); if (!props.readonly) { graph!.cleanSelection(); select() } })
      graph.on('cell:added', () => { cellCount.value = graph?.getCellCount() ?? 0; schedule() }); graph.on('cell:removed', () => { cellCount.value = graph?.getCellCount() ?? 0; edgeEditor.value = null; schedule() })
      graph.on('cell:change:*', ({ cell, key }) => {
        if (!['position', 'size', 'data', 'attrs', 'source', 'target', 'vertices', 'zIndex', 'router', 'connector'].includes(String(key))) return
        if (!edgeDragActive) { syncEdgeEditor(); nextTick(() => syncContextToolbar(cell)); schedule() }
      })
      graph.on('edge:connected', ({ edge }) => { const d = graphDiagram(graph!).edges.find(e => e.id === edge.id); if (d) edge.setData(d); schedule() })
    }
    if (mini.value) graph.use(new MiniMap({ container: mini.value, width: 160, height: 100, padding: 10 }))
    replace(props.diagram, true)
    nextTick(() => {
      try { const view = props.viewportKey && JSON.parse(localStorage.getItem(`devnest-flowchart-view:${props.viewportKey}`) ?? 'null'); if (view && Number.isFinite(view.scale) && Number.isFinite(view.tx) && Number.isFinite(view.ty)) { graph?.zoomTo(Math.max(.1, Math.min(3, view.scale))); graph?.translate(view.tx, view.ty) } } catch { /* Ignore malformed view preferences. */ }
    })
    graph.on('scale', ({ sx }) => { zoom.value = Math.round(sx * 100); saveViewport(); syncEdgeEditor(); nextTick(() => syncContextToolbar()) }); graph.on('translate', () => { saveViewport(); syncEdgeEditor(); nextTick(() => syncContextToolbar()) })
    host.value.addEventListener('wheel', handleWheel, { passive: false })
    observer = new ResizeObserver(() => { graph?.resize(host.value!.clientWidth, host.value!.clientHeight); editorViewport.width = host.value?.clientWidth ?? 0; editorViewport.height = host.value?.clientHeight ?? 0; keepSelectedInView(); syncEdgeEditor(); nextTick(() => syncContextToolbar()) }); observer.observe(host.value)
  } catch (error) { emit('error', `画布加载失败：${(error as Error).message}`) }
})
watch(() => props.diagram, value => { if (flowchartFingerprint(value) !== lastJson) replace(value) })
onBeforeUnmount(() => { cancelEdgeDrag(); flush(); saveViewport(); disposed = true; cancelPendingDrag?.(); removeEdgeDragListeners(); cancelAnimationFrame(frame); observer?.disconnect(); host.value?.removeEventListener('wheel', handleWheel); dnd?.dispose(); graph?.dispose(); graph = undefined; edgeEditor.value = null })
function selectId(id: string) { const cell = graph?.getCellById(id); if (cell && !props.readonly) { graph!.resetSelection([cell]); select(cell) } }
defineExpose({ add, startDrag, updateCell, selectId, action, fit: fitContent, keepSelectedInView, exportImage, flush, getDiagram: () => graph ? graphDiagram(graph) : props.diagram })
</script>

<template>
  <div ref="root" class="flow-canvas" :class="{ 'flow-canvas--readonly': readonly }" tabindex="0" aria-label="流程图画布" @keydown="keydown" @keyup="keyup" @blur="() => { if (!pan && !readonly) graph?.disablePanning() }">
    <div class="flow-canvas__tools" role="toolbar" aria-label="画布工具">
      <template v-if="!readonly">
        <button type="button" :class="{ active: !pan }" aria-label="选择工具" :aria-pressed="!pan" @click="pan && togglePan()"><MousePointer2 :size="17" /></button>
        <button type="button" :class="{ active: pan }" aria-label="平移工具" :aria-pressed="pan" @click="togglePan"><Hand :size="17" /></button><i />
        <button type="button" :disabled="!canUndo" aria-label="撤销" title="撤销 Ctrl+Z" @click="action('undo')"><Undo2 :size="17" /></button>
        <button type="button" :disabled="!canRedo" aria-label="重做" title="重做 Ctrl+Shift+Z" @click="action('redo')"><Redo2 :size="17" /></button><i />
      </template>
      <button type="button" aria-label="缩小" @click="graph?.zoom(-.1)"><Minus :size="17" /></button><span>{{ zoom }}%</span>
      <button type="button" aria-label="放大" @click="graph?.zoom(.1)"><Plus :size="17" /></button>
      <button type="button" aria-label="适应全部内容" title="适应全部内容" @click="fitContent"><Scan :size="17" /></button><i />
      <button type="button" aria-label="显示网格" :aria-pressed="grid" @click="toggleGrid"><Grid2X2 :size="17" /></button>
      <button type="button" aria-label="显示小地图" :aria-pressed="showMini" @click="showMini = !showMini"><Map :size="17" /></button>
      <button type="button" aria-label="全屏画布" @click="fullscreen"><Maximize2 :size="17" /></button>
      <button v-if="!readonly" type="button" aria-label="切换属性面板" :aria-expanded="propertiesOpen" @click="emit('toggle-properties')"><PanelRightClose v-if="propertiesOpen" :size="17" /><PanelRightOpen v-else :size="17" /></button>
    </div>
    <div v-if="selectedId && !readonly" ref="contextBar" class="flow-canvas__context" :style="contextStyle" role="toolbar" aria-label="选中图形操作">
      <button type="button" title="复制" aria-label="复制选中图形" @click="action('copy')"><Copy :size="15" /></button>
      <button type="button" title="粘贴" aria-label="粘贴图形" @click="action('paste')"><ClipboardPaste :size="15" /></button>
      <i aria-hidden="true" />
      <button type="button" title="置于顶层" aria-label="置于顶层" @click="action('front')"><ArrowUpToLine :size="15" /></button>
      <button type="button" title="置于底层" aria-label="置于底层" @click="action('back')"><ArrowDownToLine :size="15" /></button>
      <button type="button" class="danger" title="删除" aria-label="删除选中图形" @click="action('delete')"><Trash2 :size="15" /></button>
    </div>
    <div ref="host" class="flow-canvas__surface" @mousedown="root?.focus({ preventScroll: true })"></div>
    <div v-if="cellCount === 0 && !readonly" class="flow-canvas__empty" aria-label="空白画布提示">
      <div class="flow-canvas__empty-card">
        <span class="flow-canvas__empty-icon" aria-hidden="true"><Plus :size="19" /></span>
        <strong>从一个步骤开始</strong>
        <p>从左侧拖入图形，或直接添加第一个处理步骤。</p>
        <button type="button" class="flow-canvas__empty-action" @click="add('rectangle')"><Plus :size="15" />添加处理步骤</button>
      </div>
    </div>
    <div v-if="edgeEditor && !readonly" class="flow-canvas__edge-editor" :class="{ 'is-dragging': edgeEditor.dragging }" :style="{ width: `${editorViewport.width}px`, height: `${editorViewport.height}px` }" aria-label="连线编辑工具">
      <svg class="flow-edge-editor__svg" :width="editorViewport.width" :height="editorViewport.height" :viewBox="`0 0 ${editorViewport.width} ${editorViewport.height}`" aria-hidden="true">
        <path class="flow-edge-editor__hit" :d="edgeEditor.path" @click="insertEdgeVertex" />
        <path v-if="edgeEditor.dragging" class="flow-edge-editor__preview" :d="edgeEditor.path" />
      </svg>
      <button v-for="handle in edgeEditor.handles" :key="handle.id" type="button" class="flow-edge-editor__handle" :class="[`is-${handle.kind}`, handle.axis ? `axis-${handle.axis}` : '']" :aria-label="handle.label" :title="handle.label" :data-handle-index="handle.index" :data-handle-kind="handle.kind" :style="{ left: `${handle.x}px`, top: `${handle.y}px`, width: `${handle.width}px`, height: `${handle.height}px`, transform: `translate(-50%, -50%) rotate(${handle.angle}deg)` }" @pointerdown="beginEdgeHandleDrag(handle, $event)" @dblclick="removeEdgeVertex(handle, $event)"><span aria-hidden="true"></span></button>
    </div>
    <div v-show="showMini" ref="mini" class="flow-canvas__mini" aria-label="画布小地图"></div>
    <span class="flow-canvas__hint">{{ readonly ? '滚轮平移 · Ctrl + 滚轮缩放' : edgeEditor ? '点击线段插入拐点 · 双击拐点删除 · 拖动线段调整 · Esc 取消' : '拖动连接点连线 · 滚轮平移 · Ctrl + 滚轮缩放 · Shift 框选' }}</span>
  </div>
</template>

<style scoped>
.flow-canvas { position: relative; min-width: 0; min-height: 0; height: 100%; overflow: hidden; background: var(--surface-sunken); outline: 0; outline-offset: -2px; }
.flow-canvas:focus-visible { outline: 2px solid var(--accent); }
.flow-canvas:fullscreen { width: 100vw; height: 100dvh; }
.flow-canvas__surface { position: absolute; inset: 0; touch-action: none; overscroll-behavior: contain; background: var(--surface-sunken); }

/* X6 paints its own SVG background. Keep the canvas surface and grid in sync
   with the app theme so the editor stays light and crisp in both modes. */
.flow-canvas__surface :deep(.x6-graph),
.flow-canvas__surface :deep(.x6-graph-svg),
.flow-canvas__surface :deep(.x6-graph-background) { background-color: var(--surface-sunken) !important; }
.flow-canvas__surface :deep(.x6-graph-background) { fill: var(--surface-sunken) !important; }
.flow-canvas__surface :deep(.x6-graph-grid) { opacity: .72; }
.flow-canvas__empty { position: absolute; inset: 0; z-index: 1; display: grid; place-items: center; pointer-events: none; }
.flow-canvas__empty-card { display: flex; flex-direction: column; align-items: center; gap: 8px; max-width: 280px; padding: 20px 24px; border: 1px solid color-mix(in srgb, var(--border) 84%, transparent); border-radius: 14px; background: color-mix(in srgb, var(--panel) 88%, transparent); box-shadow: 0 12px 30px color-mix(in srgb, var(--bg) 10%, transparent); text-align: center; backdrop-filter: blur(8px); }
.flow-canvas__empty-icon { display: grid; place-items: center; width: 34px; height: 34px; border-radius: 10px; color: var(--accent-strong); background: var(--accent-bg); }
.flow-canvas__empty-card strong { color: var(--text); font-size: 14px; font-weight: 650; }
.flow-canvas__empty-card p { max-width: 230px; margin: 0; color: var(--muted); font-size: 11px; line-height: 1.55; }
.flow-canvas__empty-action { display: inline-flex; align-items: center; gap: 5px; min-height: 32px; margin-top: 4px; padding: 6px 11px; border: 1px solid var(--accent-border); border-radius: 7px; background: var(--accent-bg); color: var(--accent-strong); font: inherit; font-size: 11px; cursor: pointer; pointer-events: auto; transition: background var(--motion-fast), border-color var(--motion-fast), color var(--motion-fast); }
.flow-canvas__empty-action:hover { border-color: var(--accent); background: color-mix(in srgb, var(--accent) 14%, var(--panel)); color: var(--accent-strong); }
.flow-canvas__empty-action:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }

.flow-canvas__tools { position: absolute; z-index: 2; display: flex; align-items: center; flex-wrap: wrap; gap: 3px; left: 16px; top: 16px; max-width: calc(100% - 32px); padding: 5px; border: 1px solid color-mix(in srgb, var(--border-strong) 78%, transparent); border-radius: 9px; background: color-mix(in srgb, var(--panel) 94%, transparent); box-shadow: 0 6px 18px color-mix(in srgb, var(--bg) 14%, transparent), 0 1px 2px color-mix(in srgb, var(--bg) 10%, transparent); backdrop-filter: blur(10px); }
.flow-canvas__tools button { display: grid; place-items: center; width: 32px; height: 32px; padding: 0; border: 1px solid transparent; border-radius: 6px; background: transparent; color: var(--subtle); cursor: pointer; transition: color var(--motion-fast), background var(--motion-fast), border-color var(--motion-fast), box-shadow var(--motion-fast); }
.flow-canvas__tools button:hover:not(:disabled) { color: var(--text); border-color: var(--border); background: var(--surface-raised); }
.flow-canvas__tools button.active { color: var(--accent-strong); border-color: var(--accent-border); background: var(--accent-bg); box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--accent) 12%, transparent); }
.flow-canvas__tools button:focus-visible { outline: 2px solid var(--accent); outline-offset: 1px; }
.flow-canvas__tools button:disabled { opacity: .35; cursor: default; }
.flow-canvas__tools span { min-width: 45px; padding: 0 4px; text-align: center; color: var(--text); font-size: 12px; font-variant-numeric: tabular-nums; }
.flow-canvas__tools i { height: 18px; margin: 0 4px; border-left: 1px solid var(--border); }
.flow-canvas__context { position: absolute; z-index: 3; display: flex; align-items: center; gap: 2px; padding: 4px; border: 1px solid var(--border-strong); border-radius: 8px; background: color-mix(in srgb, var(--panel) 96%, transparent); box-shadow: 0 6px 18px color-mix(in srgb, var(--bg) 16%, transparent); backdrop-filter: blur(10px); transition: left .16s ease, top .16s ease; }
.flow-canvas__context button { display: grid; place-items: center; width: 30px; height: 30px; padding: 0; border: 0; border-radius: 5px; background: transparent; color: var(--subtle); cursor: pointer; }
.flow-canvas__context button:hover { color: var(--accent-strong); background: var(--accent-bg); }
.flow-canvas__context button.danger:hover { color: var(--danger); background: color-mix(in srgb, var(--danger) 10%, transparent); }
.flow-canvas__context i { height: 18px; margin: 0 3px; border-left: 1px solid var(--border); }

.flow-canvas__mini { position: absolute; right: 16px; bottom: 34px; width: 160px; height: 100px; overflow: hidden; border: 1px solid var(--border-strong); border-radius: 8px; background: var(--panel); box-shadow: 0 8px 22px color-mix(in srgb, var(--bg) 16%, transparent); }
.flow-canvas__mini :deep(.x6-widget-minimap) { border: 0; background: var(--panel); }
.flow-canvas__mini :deep(.x6-widget-minimap-viewport) { border: 1px solid var(--accent); background: var(--accent-soft); }
.flow-canvas__hint { position: absolute; left: 16px; bottom: 11px; pointer-events: none; padding: 3px 8px; color: var(--muted); border: 1px solid color-mix(in srgb, var(--border) 80%, transparent); border-radius: 999px; background: color-mix(in srgb, var(--panel) 86%, transparent); font-size: 11px; line-height: 1.35; box-shadow: 0 2px 8px color-mix(in srgb, var(--bg) 8%, transparent); }

/* ProcessOn-like interaction polish for the underlying X6 primitives. */
:deep(.x6-node) { cursor: move; }
.flow-canvas--readonly :deep(.x6-node) { cursor: grab; }
:deep(.x6-node-selected .x6-node-body) { stroke: var(--accent) !important; stroke-width: 2px !important; filter: drop-shadow(0 0 0.5px color-mix(in srgb, var(--accent) 80%, transparent)); }
:deep(.x6-edge-selected .x6-edge-line) { stroke: var(--accent) !important; stroke-width: 2.25px !important; }
:deep(.x6-edge:hover .x6-edge-line) { stroke: color-mix(in srgb, var(--accent) 58%, var(--muted)) !important; stroke-width: 1.75px !important; opacity: .9; }
:deep(.x6-port-body) { stroke: var(--accent) !important; fill: var(--panel) !important; stroke-width: 1.5px; transition: fill var(--motion-fast), transform var(--motion-fast); }
:deep(.x6-port:hover .x6-port-body), :deep(.x6-port-body:hover) { fill: var(--accent) !important; }
:deep(.x6-widget-selection-box), :deep(.x6-widget-selection-inner) { border-color: var(--accent) !important; border-radius: 3px; box-shadow: 0 2px 8px color-mix(in srgb, var(--accent) 16%, transparent); }
/* X6 gives the visual selection box an inline pointer-events:auto; keep it
   transparent to hit-testing so selected cells still receive double/context clicks. */
:deep(.x6-widget-selection-box), :deep(.x6-widget-selection-inner) { pointer-events: none !important; }
:deep(.x6-widget-selection-rubberband) { border: 1px solid var(--accent) !important; background: color-mix(in srgb, var(--accent) 9%, transparent) !important; }
:deep(.x6-cell-tools), :deep(.x6-edge-tools) { color: var(--accent); }
:deep(.x6-edge-selected .x6-edge-line), :deep(.x6-edge:hover .x6-edge-line) { stroke-linecap: round; stroke-linejoin: round; }
.flow-canvas__edge-editor { position: absolute; inset: 0; z-index: 4; overflow: visible; pointer-events: none; touch-action: none; }
.flow-edge-editor__svg { position: absolute; inset: 0; display: block; overflow: visible; pointer-events: none; }
.flow-edge-editor__hit { fill: none; stroke: transparent; stroke-width: 20px; stroke-linecap: round; stroke-linejoin: round; pointer-events: stroke; cursor: crosshair; }
.flow-edge-editor__preview { fill: none; stroke: var(--accent); stroke-width: 2px; stroke-dasharray: 6 5; stroke-linecap: round; stroke-linejoin: round; opacity: .78; pointer-events: none; filter: drop-shadow(0 1px 2px color-mix(in srgb, var(--accent) 18%, transparent)); }
.flow-edge-editor__handle { position: absolute; display: grid; place-items: center; padding: 0; border: 1px solid color-mix(in srgb, var(--accent) 84%, #fff); border-radius: 999px; background: color-mix(in srgb, var(--panel) 96%, transparent); box-shadow: 0 2px 5px color-mix(in srgb, var(--bg) 20%, transparent), 0 0 0 2px color-mix(in srgb, var(--accent) 10%, transparent); color: var(--accent); pointer-events: auto; cursor: grab; transform-origin: center; transition: width var(--motion-fast), height var(--motion-fast), background var(--motion-fast), border-color var(--motion-fast), box-shadow var(--motion-fast); }
.flow-edge-editor__handle span { display: block; width: 4px; height: 4px; border-radius: 50%; background: currentColor; opacity: .78; }
.flow-edge-editor__handle.is-segment { border-width: 1.5px; }
.flow-edge-editor__handle.is-segment span { width: 10px; height: 2px; border-radius: 999px; }
.flow-edge-editor__handle:hover, .flow-edge-editor__handle:focus-visible { border-color: var(--accent-strong); background: var(--accent-bg); box-shadow: 0 3px 8px color-mix(in srgb, var(--accent) 25%, transparent), 0 0 0 3px color-mix(in srgb, var(--accent) 14%, transparent); outline: none; }
.flow-edge-editor__handle:active { cursor: grabbing; background: var(--accent-bg); }
.flow-canvas__edge-editor.is-dragging .flow-edge-editor__handle { opacity: .48; }
.flow-canvas__edge-editor.is-dragging .flow-edge-editor__handle:active { opacity: 1; }
:deep(.x6-edge .connection) { transition: stroke .14s ease, stroke-width .14s ease, opacity .14s ease; stroke-linecap: round; stroke-linejoin: round; }
:deep(.x6-port-body) { opacity: 0; transition: opacity var(--motion-fast), r var(--motion-fast), fill var(--motion-fast); }
:deep(.x6-node:hover .x6-port-body), :deep(.x6-port:hover .x6-port-body), :deep(.x6-node-selected .x6-port-body), :deep(.x6-connecting .x6-port-body) { opacity: 1; }
:deep(.x6-edge-label rect) { fill: var(--panel) !important; stroke: var(--border) !important; stroke-width: 1px; }
.flow-edge-editor__handle.axis-x { cursor: ns-resize; }
.flow-edge-editor__handle.axis-y { cursor: ew-resize; }
@media (prefers-reduced-motion: reduce) { .flow-edge-editor__handle, :deep(.x6-edge .connection) { transition: none; } }

@media (max-width: 767px) { .flow-canvas { min-height: 440px; } .flow-canvas__tools { left: 8px; top: 8px; max-width: calc(100% - 16px); } .flow-canvas__tools button { width: 36px; height: 36px; } .flow-canvas__mini { display: none; } .flow-canvas__hint { left: 8px; bottom: 8px; max-width: calc(100% - 16px); overflow: hidden; white-space: nowrap; text-overflow: ellipsis; font-size: 10px; } }
</style>
