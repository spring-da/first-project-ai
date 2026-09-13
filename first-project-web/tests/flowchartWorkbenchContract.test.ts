import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const panelPath = new URL('../src/components/flowcharts/NodeLibraryPanel.vue', import.meta.url)

test('node library offers search, categories, click creation, drag creation and collapse', () => {
  const source = readFileSync(panelPath, 'utf8')

  assert.match(source, /@click="emit\('create', shape\.kind\)"/)
  assert.match(source, /@dragstart="emit\('drag-start', shape\.kind, \$event\)"/)
  assert.match(source, /aria-label="折叠节点库"/)
  assert.match(source, /function dragStart\(kind: FlowNodeKind, event: DragEvent\)/)
  assert.match(source, /setData\('application\/x-devnest-flow-node', kind\)/)
})
