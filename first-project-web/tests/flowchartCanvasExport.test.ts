import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { compileScript, compileTemplate, parse } from '@vue/compiler-sfc'
import ts from 'typescript'
import { createRenderer, defineComponent, h, nextTick } from 'vue'
import { emptyDiagram } from '../src/utils/flowcharts.ts'

type HostNode = {
  type: string
  props: Record<string, unknown>
  children: HostNode[]
  parent?: HostNode
  text?: string
  style: Record<string, string>
  addEventListener: () => void
  removeEventListener: () => void
  focus: () => void
}

const canvasPath = new URL('../src/components/flowcharts/FlowchartCanvas.vue', import.meta.url)
const canvasSource = readFileSync(canvasPath, 'utf8')
const vue = await import('vue')
const editor = await import('../src/utils/flowchartEditor.ts')
const icon = defineComponent({ render: () => h('svg') })

function compileCanvas(require: (id: string) => unknown) {
  const descriptor = parse(canvasSource, { filename: canvasPath.pathname }).descriptor
  const script = compileScript(descriptor, { id: 'flowchart-canvas-export-test' })
  const template = compileTemplate({ id: 'flowchart-canvas-export-test', filename: canvasPath.pathname, source: descriptor.template!.content, compilerOptions: { bindingMetadata: script.bindings } })
  assert.deepEqual(template.errors, [])
  const program = ts.transpileModule(`${script.content}\n${template.code}\nexports.default.render = exports.render`, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText
  const module = { exports: {} as Record<string, unknown> }
  new Function('require', 'exports', 'module', program)(require, module.exports, module)
  return module.exports.default as ReturnType<typeof defineComponent>
}

const renderer = createRenderer<HostNode, HostNode>({
  patchProp(node, key, _previous, next) { node.props[key] = next },
  insert(child, parent, anchor) { const index = anchor ? parent.children.indexOf(anchor) : -1; if (index >= 0) parent.children.splice(index, 0, child); else parent.children.push(child); child.parent = parent },
  remove(child) { const parent = child.parent; if (parent) parent.children.splice(parent.children.indexOf(child), 1) },
  createElement(type) { return { type, props: {}, children: [], style: {}, addEventListener() {}, removeEventListener() {}, focus() {} } },
  createText(text) { return { type: '#text', props: {}, children: [], text, style: {}, addEventListener() {}, removeEventListener() {}, focus() {} } },
  createComment(text) { return { type: '#comment', props: {}, children: [], text, style: {}, addEventListener() {}, removeEventListener() {}, focus() {} } },
  setText(node, text) { node.text = text },
  setElementText(node, text) { node.children = [{ type: '#text', props: {}, children: [], text, parent: node, style: {}, addEventListener() {}, removeEventListener() {}, focus() {} }] },
  parentNode(node) { return node.parent ?? null },
  nextSibling(node) { const siblings = node.parent?.children ?? []; return siblings[siblings.indexOf(node) + 1] ?? null },
  setScopeId() {},
  cloneNode(node) { return { ...node, props: { ...node.props }, children: [...node.children] } },
  insertStaticContent(content, parent, anchor) { const node: HostNode = { type: '#static', props: {}, children: [], text: content, style: {}, addEventListener() {}, removeEventListener() {}, focus() {} }; const index = anchor ? parent.children.indexOf(anchor) : -1; if (index >= 0) parent.children.splice(index, 0, node); else parent.children.push(node); node.parent = parent; return [node, node] },
})

test('canvas SVG export downloads sanitized X6 output rather than editor chrome', async () => {
  const downloads: Array<{ blob: Blob; filename: string }> = []
  let svgExports = 0
  class Graph {
    use() {}; on() {}; dispose() {}; resetCells() {}; cleanHistory() {}; zoomTo() {}; centerPoint() {}
    getNodes() { return [] }; getEdges() { return [] }; getCells() { return [] }; getCellCount() { return 0 }; getSelectedCells() { return [] }
    canUndo() { return false }; canRedo() { return false }
    async toSVGAsync(options: { beforeSerialize: (svg: SVGSVGElement) => void }) {
      svgExports += 1
      options.beforeSerialize({ querySelectorAll: () => [], style: {}, getAttribute: () => '0 0 100 80', insertBefore: () => {}, firstChild: null } as unknown as SVGSVGElement)
      return '<svg><g class="x6-node"><rect /></g><g class="flow-canvas__tools"><rect /></g><g class="flow-canvas__context"><rect /></g><g class="flow-canvas__edge-editor"><rect /></g><g class="flow-canvas__mini"><rect /></g><text class="flow-canvas__hint">hint</text><g class="inspector-panel"><rect /></g></svg>'
    }
  }
  const x6 = { Graph, History: class {}, Selection: class {}, Snapline: class {}, Clipboard: class {}, Transform: class { clearWidgets() {} }, MiniMap: class {}, Export: class {}, Dnd: class {} }
  const flows = await import('../src/utils/flowcharts.ts')
  const adapter = { nodeToCell: (node: unknown) => node, edgeToCell: (edge: unknown) => edge, graphDiagram: () => emptyDiagram() }
  const component = compileCanvas(id => {
    if (id === 'vue') return vue
    if (id === '@antv/x6') return x6
    if (id === 'lucide-vue-next') return new Proxy({}, { get: () => icon })
    if (id.endsWith('flowchartAdapter')) return adapter
    if (id.endsWith('utils/flowcharts')) return { ...flows, downloadBlob: (blob: Blob, filename: string) => downloads.push({ blob, filename }) }
    if (id.endsWith('flowchartEditor')) return editor
    if (id.endsWith('flowchartEditorState')) return { readNativeFlowNodeKind: () => null, replaceFlowchartSelection: () => null }
    if (id.endsWith('flowchartEdgeEditor')) return {}
    throw new Error(`Unexpected canvas dependency: ${id}`)
  })
  const originalDocument = globalThis.document
  const originalResizeObserver = globalThis.ResizeObserver
  const originalCancelAnimationFrame = globalThis.cancelAnimationFrame
  Object.assign(globalThis, {
    document: { createElementNS: () => ({ setAttribute: () => {} }), addEventListener: () => {}, removeEventListener: () => {} },
    ResizeObserver: class { observe() {}; disconnect() {} },
    cancelAnimationFrame: () => {},
  })
  const root: HostNode = { type: 'root', props: {}, children: [], style: {}, addEventListener() {}, removeEventListener() {}, focus() {} }
  const app = renderer.createApp(component, { diagram: emptyDiagram(), readonly: true })
  try {
    const canvas = app.mount(root) as { exportImage: (format: 'svg', title: string) => Promise<void> }
    await nextTick()
    await canvas.exportImage('svg', '导出流程图')
    assert.equal(svgExports, 1)
    assert.equal(downloads.length, 1)
    assert.equal(downloads[0]!.filename, '导出流程图.svg')
    const serialized = await downloads[0]!.blob.text()
    assert.match(serialized, /class="x6-node"/)
    for (const overlay of ['flow-canvas__edge-editor', 'flow-canvas__tools', 'flow-canvas__context', 'flow-canvas__mini', 'flow-canvas__hint', 'inspector-panel']) assert.doesNotMatch(serialized, new RegExp(overlay))
  } finally {
    app.unmount()
    Object.assign(globalThis, { document: originalDocument, ResizeObserver: originalResizeObserver, cancelAnimationFrame: originalCancelAnimationFrame })
  }
})
