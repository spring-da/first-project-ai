import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { compileStyle, compileTemplate, parse } from '@vue/compiler-sfc'

const fieldSource = readFileSync(new URL('../src/components/JsonFieldNode.vue', import.meta.url), 'utf8')
const editorSource = readFileSync(new URL('../src/components/JsonSnippetEditor.vue', import.meta.url), 'utf8')
const dialogSource = readFileSync(new URL('../src/components/JsonValueDialog.vue', import.meta.url), 'utf8')

test('long JSON values expose an accessible expand control and relay it from nested fields', () => {
  assert.match(fieldSource, /draft\.value\.length > 96/)
  assert.match(fieldSource, /`展开完整值 \$\{label\}`/)
  assert.match(fieldSource, /@preview="\(target, value\) => emit\('preview', target, value\)"/)
  assert.match(fieldSource, /<Maximize2 :size="13" \/>/)
})

test('JSON editor shares one value dialog across tools and snippet creation flows', () => {
  assert.match(editorSource, /import JsonValueDialog/)
  assert.match(editorSource, /@preview="previewValue"/)
  assert.match(editorSource, /<JsonValueDialog v-if="expandedValue"/)
})

test('expanded JSON values are selectable, copyable, and styled as a bounded dialog', () => {
  const descriptor = parse(dialogSource, { filename: 'JsonValueDialog.vue' }).descriptor
  const template = compileTemplate({ id: 'json-value-dialog-test', filename: 'JsonValueDialog.vue', source: descriptor.template!.content })
  const style = compileStyle({ id: 'json-value-dialog-test', filename: 'JsonValueDialog.vue', source: descriptor.styles.map(item => item.content).join('\n'), scoped: true })
  assert.deepEqual(template.errors, [])
  assert.deepEqual(style.errors, [])
  assert.match(dialogSource, /navigator\.clipboard\?\.writeText/)
  assert.match(dialogSource, /document\.execCommand\('copy'\)/)
  assert.match(dialogSource, /@focus="selectValue"/)
  assert.match(style.code, /max-height:\s*min\(46vh, 360px\)/)
})
