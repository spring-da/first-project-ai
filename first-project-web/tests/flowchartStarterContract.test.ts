import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = readFileSync(new URL('../src/components/flowcharts/FlowchartStarter.vue', import.meta.url), 'utf8')
const workspaceSource = readFileSync(new URL('../src/components/flowcharts/FlowchartWorkspace.vue', import.meta.url), 'utf8')

test('starter exposes blank, template and import paths with typed actions', () => {
  assert.match(source, /emit\('start-blank'\)/)
  assert.match(source, /emit\('start-template',\s*template\.kind\)/)
  assert.match(source, /emit\('import'\)/)
  assert.match(source, /aria-label="新建流程图起始面板"/)
})

test('workspace shows starter before creating a new session', () => {
  assert.match(workspaceSource, /v-if="showStarter"/)
  assert.match(workspaceSource, /@start-blank="startBlank"/)
})
