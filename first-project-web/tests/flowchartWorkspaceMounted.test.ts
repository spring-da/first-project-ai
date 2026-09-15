import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { compileScript, compileTemplate, parse } from '@vue/compiler-sfc'
import ts from 'typescript'
import { createRenderer, defineComponent, h, nextTick } from 'vue'
import * as flowcharts from '../src/utils/flowcharts.ts'

type HostNode = { type: string; props: Record<string, unknown>; children: HostNode[]; parent?: HostNode; text?: string }
const workspacePath = new URL('../src/components/flowcharts/FlowchartWorkspace.vue', import.meta.url)
const starterPath = new URL('../src/components/flowcharts/FlowchartStarter.vue', import.meta.url)
const workspaceSource = readFileSync(workspacePath, 'utf8')
const starterSource = readFileSync(starterPath, 'utf8')
const vue = await import('vue')

const icon = defineComponent({ render: () => h('svg') })
const editorShell = defineComponent({
  props: { draft: { type: Object, required: true } },
  setup(props, { expose }) {
    expose({ canvas: { flush: () => {}, keepSelectedInView: () => {} }, focusTitle: () => {}, focusInspectorLabel: () => {} })
    return () => h('editor-shell', { draft: props.draft })
  },
})
const panelStub = defineComponent({ render: () => h('panel-stub') })

function compileSfc(source: string, path: URL, require: (id: string) => unknown) {
  const descriptor = parse(source, { filename: path.pathname }).descriptor
  const script = compileScript(descriptor, { id: `mounted-${path.pathname}` })
  const template = compileTemplate({ id: `mounted-${path.pathname}`, filename: path.pathname, source: descriptor.template!.content, compilerOptions: { bindingMetadata: script.bindings } })
  assert.deepEqual(template.errors, [])
  const program = ts.transpileModule(`${script.content}\n${template.code}\nexports.default.render = exports.render`, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText
  const module = { exports: {} as Record<string, unknown> }
  new Function('require', 'exports', 'module', program)(require, module.exports, module)
  return module.exports.default as ReturnType<typeof defineComponent>
}

function compileStarter() {
  return compileSfc(starterSource, starterPath, id => id === 'vue' ? vue : new Proxy({}, { get: () => icon }))
}

const renderer = createRenderer<HostNode, HostNode>({
  patchProp(node, key, _previous, next) { node.props[key] = next },
  insert(child, parent, anchor) { const index = anchor ? parent.children.indexOf(anchor) : -1; if (index >= 0) parent.children.splice(index, 0, child); else parent.children.push(child); child.parent = parent },
  remove(child) { const parent = child.parent; if (parent) parent.children.splice(parent.children.indexOf(child), 1) },
  createElement(type) { return { type, props: {}, children: [] } },
  createText(text) { return { type: '#text', props: {}, children: [], text } },
  createComment(text) { return { type: '#comment', props: {}, children: [], text } },
  setText(node, text) { node.text = text },
  setElementText(node, text) { node.children = [{ type: '#text', props: {}, children: [], text, parent: node }] },
  parentNode(node) { return node.parent ?? null },
  nextSibling(node) { const siblings = node.parent?.children ?? []; return siblings[siblings.indexOf(node) + 1] ?? null },
  setScopeId() {},
  cloneNode(node) { return { ...node, props: { ...node.props }, children: [...node.children] } },
  insertStaticContent(content, parent, anchor) { const node: HostNode = { type: '#static', props: {}, children: [], text: content }; const index = anchor ? parent.children.indexOf(anchor) : -1; if (index >= 0) parent.children.splice(index, 0, node); else parent.children.push(node); node.parent = parent; return [node, node] },
})

function find(node: HostNode, predicate: (candidate: HostNode) => boolean): HostNode | undefined {
  if (predicate(node)) return node
  return node.children.flatMap(child => [find(child, predicate)]).find((candidate): candidate is HostNode => !!candidate)
}
function text(node: HostNode): string { return `${node.text ?? ''}${node.children.map(text).join('')}` }
async function flush() { await Promise.resolve(); await nextTick(); await Promise.resolve(); await nextTick() }

function mountWorkspace(documentId?: string) {
  const closeCalls: string[] = [], getCalls: string[] = []
  const originalWindow = globalThis.window, originalDocument = globalThis.document
  Object.assign(globalThis, {
    window: { matchMedia: () => ({ matches: false, addEventListener: () => {}, removeEventListener: () => {} }), addEventListener: () => {}, removeEventListener: () => {} },
    document: { addEventListener: () => {}, removeEventListener: () => {}, body: { classList: { contains: () => false } } },
  })
  class Session {
    dirty = false; status = 'idle'; error = ''; creationKey = 'mounted-session'
    draft: ReturnType<typeof flowcharts.copyDraft>
    document: { id: string; version: number } | null
    constructor(draft: ReturnType<typeof flowcharts.copyDraft>, document: { id: string; version: number } | null) {
      this.draft = draft
      this.document = document
    }
    dispose() {}; edit(next: ReturnType<typeof flowcharts.copyDraft>) { this.draft = next }; async save() { return this.document }
  }
  const existing = { id: 'existing-flowchart', title: '旧流程图', domainId: null, favorite: false, diagram: flowcharts.emptyDiagram(), createdAt: '', updatedAt: '', deletedAt: null, version: 1, nodeCount: 0, edgeCount: 0, excerpt: '' }
  const storage = { list: async () => [], write: async () => {}, remove: async () => {}, close: () => {} }
  const component = compileSfc(workspaceSource, workspacePath, id => {
    if (id === 'vue') return vue
    if (id === 'vue-router') return { onBeforeRouteLeave: () => {}, onBeforeRouteUpdate: () => {} }
    if (id === 'lucide-vue-next') return new Proxy({}, { get: () => icon })
    if (id.endsWith('FlowchartStarter.vue')) return { __esModule: true, default: compileStarter() }
    if (id.endsWith('FlowchartEditorShell.vue')) return { __esModule: true, default: editorShell }
    if (id.endsWith('.vue')) return { __esModule: true, default: panelStub }
    if (id.endsWith('stores/auth')) return { useAuthStore: () => ({ workspaceKey: 'fixture-workspace' }) }
    if (id.endsWith('stores/notifications')) return { useNotificationStore: () => ({ notify: () => {} }) }
    if (id.endsWith('stores/confirmation')) return { useConfirmationStore: () => ({ current: null, ask: async () => true }) }
    if (id.endsWith('services/flowcharts')) return { flowchartApi: { get: async (id: string) => { getCalls.push(id); return existing }, create: async () => existing, update: async () => existing } }
    if (id.endsWith('flowchartSession')) return { FlowchartSession: Session }
    if (id.endsWith('flowchartDrafts')) return { createFlowchartDraftStorage: () => storage }
    if (id.endsWith('clientId')) return { createClientId: () => 'mounted-client-id' }
    if (id.endsWith('utils/flowcharts')) return flowcharts
    if (id.endsWith('flowchartEditorState')) return { createNodeLibraryController: () => ({ preferences: { libraryCollapsed: false, inspectorCollapsed: false, recentKinds: [] }, create: () => {}, dragStart: () => {}, toggleLibrary: () => {}, toggleInspector: () => {} }) }
    if (id.endsWith('flowchartStarterEscape')) return { attachStarterEscapeListener: () => () => {} }
    throw new Error(`Unexpected workspace dependency: ${id}`)
  })
  const root: HostNode = { type: 'root', props: {}, children: [] }
  const app = renderer.createApp(component, { documentId, domainId: null, domains: [], onClose: () => closeCalls.push('close') })
  app.mount(root)
  return { root, closeCalls, getCalls, unmount: () => { app.unmount(); Object.assign(globalThis, { window: originalWindow, document: originalDocument }) } }
}

test('mounted workspace starts new flowcharts in the starter, handles blank/template/close, and bypasses it for existing documents', async () => {
  const blank = mountWorkspace()
  try {
    assert.ok(find(blank.root, node => node.props['aria-label'] === '新建流程图起始面板'))
    const blankButton = find(blank.root, node => node.type === 'button' && text(node).includes('从空白画布开始'))!
    ;(blankButton.props.onClick as () => void)()
    await flush()
    const editor = find(blank.root, node => node.type === 'editor-shell')!
    assert.equal((editor.props.draft as { diagram: { nodes: unknown[] } }).diagram.nodes.length, 0)
  } finally { blank.unmount() }

  const template = mountWorkspace()
  try {
    const templateButton = find(template.root, node => node.type === 'button' && text(node).includes('条件分支'))!
    ;(templateButton.props.onClick as () => void)()
    await flush()
    const editor = find(template.root, node => node.type === 'editor-shell')!
    assert.ok((editor.props.draft as { diagram: { nodes: unknown[] } }).diagram.nodes.length > 0)
  } finally { template.unmount() }

  const close = mountWorkspace()
  try {
    const cancel = find(close.root, node => node.type === 'button' && text(node) === '取消')!
    ;(cancel.props.onClick as () => void)()
    await flush()
    assert.deepEqual(close.closeCalls, ['close'])
  } finally { close.unmount() }

  const existing = mountWorkspace('existing-flowchart')
  try {
    await flush()
    assert.equal(find(existing.root, node => node.props['aria-label'] === '新建流程图起始面板'), undefined)
    assert.ok(find(existing.root, node => node.type === 'editor-shell'))
    assert.deepEqual(existing.getCalls, ['existing-flowchart'])
  } finally { existing.unmount() }
})
