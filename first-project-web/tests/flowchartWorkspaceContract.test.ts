import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const workspaceSource = readFileSync(new URL('../src/components/flowcharts/FlowchartWorkspace.vue', import.meta.url), 'utf8')
const propertiesSource = readFileSync(new URL('../src/components/flowcharts/FlowchartProperties.vue', import.meta.url), 'utf8')

test('flowchart workspace uses a non-jumping properties-panel transition and focused-editor chrome', () => {
  assert.match(workspaceSource, /flow-properties-enter-active[^}]*width/)
  assert.match(workspaceSource, /flow-properties-enter-from[^}]*flex-basis/)
  assert.match(workspaceSource, /:global\(\.writing-mode\)\s+\.flow-workspace/)
  assert.match(workspaceSource, /flow-header__actions[^}]*overflow:\s*visible/)
  assert.match(workspaceSource, /flow-header__actions \.button span[^}]*white-space:\s*nowrap/)
})

test('flowchart properties keeps its header visible and exposes keyboard focus affordances', () => {
  assert.match(propertiesSource, /\.flow-properties header[^}]*position:\s*sticky/)
  assert.match(propertiesSource, /\.flow-property-actions button:focus-visible/)
  assert.match(propertiesSource, /scrollbar-gutter:\s*stable/)
})

test('opening properties preserves the selected object in the shrunken canvas viewport', () => {
  assert.match(workspaceSource, /keepSelectedInView/)
  assert.match(workspaceSource, /watch\(showProperties/)
})
