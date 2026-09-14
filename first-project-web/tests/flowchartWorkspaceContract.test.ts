import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const workspaceSource = readFileSync(new URL('../src/components/flowcharts/FlowchartWorkspace.vue', import.meta.url), 'utf8')
const inspectorSource = readFileSync(new URL('../src/components/flowcharts/InspectorPanel.vue', import.meta.url), 'utf8')

test('flowchart workspace uses a state-aware inspector and focused-editor chrome', () => {
  assert.match(workspaceSource, /import InspectorPanel/)
  assert.match(workspaceSource, /<InspectorPanel/)
  assert.match(workspaceSource, /:collapsed="!inspectorOpen"/)
  assert.doesNotMatch(workspaceSource, /FlowchartProperties/)
  assert.match(workspaceSource, /:global\(\.writing-mode\)\s+\.flow-workspace/)
  assert.match(workspaceSource, /flow-header__actions[^}]*overflow:\s*visible/)
  assert.match(workspaceSource, /flow-header__actions \.button span[^}]*white-space:\s*nowrap/)
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
