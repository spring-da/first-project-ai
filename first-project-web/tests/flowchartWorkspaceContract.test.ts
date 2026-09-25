import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { compileStyle, parse } from '@vue/compiler-sfc'

const workspaceSource = readFileSync(new URL('../src/components/flowcharts/FlowchartWorkspace.vue', import.meta.url), 'utf8')
const inspectorSource = readFileSync(new URL('../src/components/flowcharts/InspectorPanel.vue', import.meta.url), 'utf8')
const shellSource = readFileSync(new URL('../src/components/flowcharts/FlowchartEditorShell.vue', import.meta.url), 'utf8')
const knowledgeViewSource = readFileSync(new URL('../src/views/KnowledgeView.vue', import.meta.url), 'utf8')
const nodeLibrarySource = readFileSync(new URL('../src/components/flowcharts/NodeLibraryPanel.vue', import.meta.url), 'utf8')
const canvasSource = readFileSync(new URL('../src/components/flowcharts/FlowchartCanvas.vue', import.meta.url), 'utf8')

test('flowchart workspace uses a state-aware inspector and focused-editor chrome', () => {
  assert.match(workspaceSource, /import FlowchartEditorShell/)
  assert.match(workspaceSource, /<FlowchartEditorShell/)
  assert.match(shellSource, /<InspectorPanel/)
  assert.match(shellSource, /:collapsed="preferences\.inspectorCollapsed"/)
  assert.doesNotMatch(workspaceSource, /FlowchartProperties/)
  assert.match(workspaceSource, /:global\(\.writing-mode\)\s+\.flow-workspace/)
  assert.match(shellSource, /flowchart-editor-shell__actions/)
  assert.match(shellSource, /flowchart-editor-shell__export-menu/)
})

test('flowchart inspector keeps its header visible and exposes keyboard focus affordances', () => {
  assert.match(inspectorSource, /\.inspector-panel__header[^}]*position:\s*sticky/)
  assert.match(inspectorSource, /\.inspector-panel__rail:focus-visible/)
  assert.match(inspectorSource, /scrollbar-gutter:\s*stable/)
  assert.match(inspectorSource, /PanelRightClose/)
})

test('opening inspector preserves the selected object in the shrunken canvas viewport', () => {
  assert.match(workspaceSource, /keepSelectedInView/)
  assert.match(workspaceSource, /watch\(inspectorOpen/)
})

test('knowledge view delegates new flowchart choices to the workspace starter', () => {
  assert.match(knowledgeViewSource, /openCreate\('flowcharts'\)/)
  assert.doesNotMatch(knowledgeViewSource, /showCanvasStarter/)
  assert.doesNotMatch(knowledgeViewSource, /title="新建流程图"/)
})

test('a parsed imported draft bypasses the starter and opens the editable workbench', () => {
  assert.match(workspaceSource, /showStarter = ref\(!props\.documentId && !props\.initialDiagram\)/)
  assert.match(workspaceSource, /else if \(props\.initialDiagram\) \{\s*install\(/)
  assert.match(workspaceSource, /focusImportedTitle/)
})

test('starter Escape closes only the active starter without taking over editor shortcuts', () => {
  assert.match(workspaceSource, /watch\(showStarter, syncStarterEscapeListener, \{ immediate: true, flush: 'sync' \}\)/)
  assert.match(workspaceSource, /attachStarterEscapeListener\(document, \(\) => showStarter\.value, \(\) => \{ void close\(\) \}\)/)
  assert.match(workspaceSource, /removeStarterEscapeListener\?\.\(\)/)
  assert.doesNotMatch(workspaceSource, /@keydown\.capture="starterShortcut"/)
  assert.match(workspaceSource, /if \(!readOnly\.value && \(event\.ctrlKey \|\| event\.metaKey\)/)
})

test('hidden starter import input is inaccessible while its visible trigger remains operable', () => {
  assert.match(workspaceSource, /@import="chooseImport"/)
  assert.match(workspaceSource, /function chooseImport\(\) \{ starterImportError\.value = ''; importInput\.value\?\.click\(\) \}/)
  assert.match(workspaceSource, /<input ref="importInput" class="flow-import-input"[^>]*tabindex="-1"[^>]*aria-hidden="true"/)
})

test('compiled dark workspace CSS applies flow tokens to the mounted workbench', () => {
  const descriptor = parse(workspaceSource, { filename: 'FlowchartWorkspace.vue' }).descriptor
  const compiled = compileStyle({ filename: 'FlowchartWorkspace.vue', id: 'data-v-flowchart-theme', source: descriptor.styles.map(style => style.content).join('\n'), scoped: true })
  assert.deepEqual(compiled.errors, [])
  assert.match(compiled.code, /:root\[data-theme=(?:"dark"|'dark'|dark)\] \.flow-workspace\s*\{[^}]*--flow-bg:\s*#151a23[^}]*--flow-surface:\s*#1d2430[^}]*--flow-text:\s*#edf2fb/s)
})

test('workbench icon controls retain 32px desktop click targets', () => {
  const compile = (source: string, filename: string) => compileStyle({ filename, id: `data-v-${filename}`, source: parse(source, { filename }).descriptor.styles.map(style => style.content).join('\n'), scoped: true })
  const library = compile(nodeLibrarySource, 'NodeLibraryPanel.vue')
  const canvas = compile(canvasSource, 'FlowchartCanvas.vue')
  assert.deepEqual(library.errors, [])
  assert.deepEqual(canvas.errors, [])
  assert.match(library.code, /\.node-library__toggle[^\{]*\{[^}]*width:\s*32px[^}]*height:\s*32px/s)
  assert.match(canvas.code, /\.flow-canvas__context button[^\{]*\{[^}]*width:\s*32px[^}]*height:\s*32px/s)
})
