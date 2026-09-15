import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const workspaceSource = readFileSync(new URL('../src/components/flowcharts/FlowchartWorkspace.vue', import.meta.url), 'utf8')
const inspectorSource = readFileSync(new URL('../src/components/flowcharts/InspectorPanel.vue', import.meta.url), 'utf8')
const shellSource = readFileSync(new URL('../src/components/flowcharts/FlowchartEditorShell.vue', import.meta.url), 'utf8')
const knowledgeViewSource = readFileSync(new URL('../src/views/KnowledgeView.vue', import.meta.url), 'utf8')

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
