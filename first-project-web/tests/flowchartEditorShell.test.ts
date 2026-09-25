import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { compileScript, compileTemplate, parse } from '@vue/compiler-sfc'
import ts from 'typescript'
import { createRenderer, defineComponent, h } from 'vue'

type HostNode = { type: string; props: Record<string, unknown>; children: HostNode[]; parent?: HostNode; text?: string; focused?: boolean; focus?: () => void }

const shellPath = new URL('../src/components/flowcharts/FlowchartEditorShell.vue', import.meta.url)
const shellSource = readFileSync(shellPath, 'utf8')
const icon = defineComponent({ render: () => h('svg') })
const panelStub = defineComponent({ render: () => h('panel-stub') })
const canvasStub = defineComponent({ setup: (_, { expose }) => { expose({ updateCell: () => {}, selectId: () => {} }); return () => h('canvas-stub') } })

function compileShell() {
  const descriptor = parse(shellSource, { filename: shellPath.pathname }).descriptor
  const script = compileScript(descriptor, { id: 'flowchart-editor-shell-test' })
  const template = compileTemplate({ id: 'flowchart-editor-shell-test', filename: shellPath.pathname, source: descriptor.template!.content, compilerOptions: { bindingMetadata: script.bindings } })
  assert.deepEqual(template.errors, [])
  const program = ts.transpileModule(`${script.content}\n${template.code}\nexports.default.render = exports.render`, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText
  const module = { exports: {} as Record<string, unknown> }
  const require = (id: string) => {
    if (id === 'vue') return awaitableVue
    if (id === 'lucide-vue-next') return new Proxy({}, { get: () => icon })
    if (id.endsWith('FlowchartCanvas.vue')) return { __esModule: true, default: canvasStub }
    return { __esModule: true, default: panelStub }
  }
  new Function('require', 'exports', 'module', program)(require, module.exports, module)
  return module.exports.default as ReturnType<typeof defineComponent>
}

const awaitableVue = await import('vue')
const renderer = createRenderer<HostNode, HostNode>({
  patchProp(node, key, _previous, next) { node.props[key] = next },
  insert(child, parent, anchor) { const index = anchor ? parent.children.indexOf(anchor) : -1; if (index >= 0) parent.children.splice(index, 0, child); else parent.children.push(child); child.parent = parent },
  remove(child) { const parent = child.parent; if (parent) parent.children.splice(parent.children.indexOf(child), 1) },
  createElement(type) { const node: HostNode = { type, props: {}, children: [] }; node.focus = () => { node.focused = true }; return node },
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
  return node.children.flatMap(child => [find(child, predicate)]).find((value): value is HostNode => !!value)
}

function mount(readOnly: boolean, onTitle = (_value: string) => {}) {
  const root: HostNode = { type: 'root', props: {}, children: [] }
  const component = compileShell()
  const app = renderer.createApp(component, {
    draft: { title: '流程图标题', domainId: null, favorite: false, diagram: { schemaVersion: 1, nodes: [], edges: [] } },
    statusLabel: '所有修改已保存', statusTone: 'default', selected: null,
    preferences: { libraryCollapsed: false, inspectorCollapsed: false, recentKinds: [] }, readOnly,
    onTitle,
  })
  return { root, exposed: app.mount(root) as { focusTitle: () => void }, unmount: () => app.unmount() }
}

test('mounted read-only editor uses a single canvas column instead of blank workbench rails', () => {
  const view = mount(true)
  try {
    const body = find(view.root, node => String(node.props.class).includes('flowchart-editor-shell__body'))
    assert.ok(body)
    assert.match(String(body.props.class), /flowchart-editor-shell__body--readonly/)
    assert.equal(find(body, node => String(node.props.class).includes('library-rail')), undefined)
    assert.equal(find(body, node => String(node.props.class).includes('inspector-rail')), undefined)
    assert.equal(body.children.filter(node => node.type === 'canvas-stub').length, 1)
  } finally { view.unmount() }
})

test('mounted editable editor forwards title input and exposes a focusable title control', () => {
  const titles: string[] = []
  const view = mount(false, value => titles.push(value))
  try {
    const input = find(view.root, node => node.type === 'input')
    assert.ok(input)
    ;(input.props.onInput as (event: { target: { value: string } }) => void)({ target: { value: '新的流程图标题' } })
    assert.deepEqual(titles, ['新的流程图标题'])
    view.exposed.focusTitle()
    assert.equal(input.focused, true)
    assert.match(shellSource, /flowchart-editor-shell__identity input:focus-visible/)
  } finally { view.unmount() }
})
