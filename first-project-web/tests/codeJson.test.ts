import assert from 'node:assert/strict'
import { test } from 'node:test'
import { escapeJsonText, extractEmbeddedJson, formatJsonText, parseJsonSource, preflightJson, unescapeJsonText } from '../src/utils/codeJson.ts'
import { buildJsonTree, countJsonNodes, editJsonSource } from '../src/utils/jsonStructure.ts'
import { highlightCode, highlightEscapedJson } from '../src/utils/codeHighlight.ts'

const source = '{"id":9223372036854775807,"nested":{"path":"C:\\\\tmp","note":"第一行\\n第二行","quote":"\\\""},"list":[true,null,1.2300e+4],"__proto__":{"safe":true}}'

test('escaped highlighting preserves exact text and escapes HTML while recognizing keys', () => {
  const escaped = escapeJsonText(source).value
  const html = highlightEscapedJson(escaped)
  assert.equal(html.replace(/<\/?span[^>]*>/g, ''), escaped)
  assert.ok(html.includes('class="tok-property">\\"id\\"</span>'))
  assert.ok(html.includes('class="tok-number">9223372036854775807</span>'))
  const unsafe = highlightEscapedJson(escapeJsonText('{"html":"<img src=x onerror=alert(1)>"}').value)
  assert.equal(unsafe.includes('<img'), false)
  assert.ok(unsafe.includes('&lt;img'))
})

test('escaping keeps physical lines and indentation and round trips all inner escapes', () => {
  const formatted = formatJsonText(source)
  const escaped = escapeJsonText(source).value
  assert.equal(escaped.split('\n').length, formatted.split('\n').length)
  assert.ok(escaped.includes('  \\"id\\": 9223372036854775807'))
  assert.equal(unescapeJsonText(escaped).value, formatted)
  assert.equal(escapeJsonText(escaped).value, escaped)
  assert.equal(formatJsonText(escaped), escaped)
  assert.equal(unescapeJsonText(JSON.stringify(formatted)).value, formatted)
  assert.equal(formatJsonText(JSON.stringify(formatted)), escaped)
  assert.ok(formatted.includes('1.2300e+4'))
})

test('invalid JSON cannot be silently rewritten and valid scalar null stays valid', () => {
  for (const input of ['{"bad":', '{"bad":"\\q"}', '{\\"bad\\":', '']) {
    assert.equal(parseJsonSource(input).valid, false)
    assert.throws(() => formatJsonText(input))
    assert.throws(() => escapeJsonText(input))
    assert.throws(() => unescapeJsonText(input))
  }
  for (const input of ['null', 'false', '123', '"hello"', '""', '[]', '{}']) {
    assert.equal(parseJsonSource(input).valid, true)
    assert.equal(formatJsonText(input), input)
  }
})

test('preflight keeps incomplete and noisy text away from the full parser', () => {
  assert.equal(preflightJson(''), 'empty')
  assert.equal(preflightJson('{"rows": [1, 2]}'), 'candidate')
  assert.equal(preflightJson('{"message":"brace } stays text"}'), 'candidate')
  assert.equal(preflightJson('{"rows": ['), 'incomplete')
  assert.equal(preflightJson('{"rows": [1,]} trailing'), 'invalid')
  assert.equal(preflightJson('2026-09-04 INFO {"ok":true}'), 'invalid')
  assert.equal(preflightJson('{\\"ok\\": true}'), 'candidate')
  assert.equal(preflightJson('false'), 'candidate')
})

test('malformed log text safely falls back through JSON syntax highlighting', () => {
  const log = '2026-09-04 INFO body: {"id": -1, bad } trailing'
  const html = highlightCode(log, 'JSON')
  assert.equal(html.replace(/<[^>]+>/g, ''), log)
  assert.ok(html.includes('class="tok-number">-1</span>'))
})

test('a complete JSON payload can be extracted from noisy log text explicitly', () => {
  const log = '2026-09-04 INFO [worker-1] Request: {"reqId":"abc","data":{"note":"brace } in text","rows":[1,2,3]}} elapsed=12ms'
  assert.equal(parseJsonSource(log).valid, false)
  const extracted = extractEmbeddedJson(log)
  assert.ok(extracted)
  assert.deepEqual(JSON.parse(extracted.value), { reqId: 'abc', data: { note: 'brace } in text', rows: [1, 2, 3] } })
  assert.equal(log.slice(extracted.start, extracted.end), extracted.value)
  assert.equal(extractEmbeddedJson('INFO [worker-1] no payload here'), null)
})

test('structure edits preserve unrelated numeric lexemes and unsafe-looking keys as data', () => {
  const changed = editJsonSource(source, [1, 0], { kind: 'value', value: '"D:\\\\notes"' })
  assert.ok(changed.includes('9223372036854775807'))
  assert.ok(changed.includes('1.2300e+4'))
  assert.equal(JSON.parse(changed).nested.path, 'D:\\notes')
  const renamed = editJsonSource(changed, [1], { kind: 'rename', name: 'details' })
  assert.equal(JSON.parse(renamed).details.path, 'D:\\notes')
  assert.equal(JSON.parse(renamed).__proto__.safe, true)
  assert.throws(() => editJsonSource(source, [1], { kind: 'rename', name: 'id' }), /同名/)
  assert.equal(buildJsonTree('{"a":1,"a":2}').children.length, 2)
  assert.equal(JSON.parse(editJsonSource('{"a":1,"a":2}', [1], { kind: 'rename', name: 'b' })).b, 2)
})

test('structure trees do not duplicate container source and can count with a safety cap', () => {
  const tree = buildJsonTree('{"outer":{"items":[{"value":1},{"value":2}]}}')
  assert.equal(tree.raw, '')
  assert.equal(tree.children[0]?.raw, '')
  assert.equal(tree.children[0]?.children[0]?.raw, '')
  assert.equal(countJsonNodes(tree), 7)
  assert.equal(countJsonNodes(tree, 2), 3)
})

test('add, remove and change types work at every array/object boundary, including empty root', () => {
  for (let index = 0; index < 3; index++) {
    assert.deepEqual(JSON.parse(editJsonSource('[0,1,2]', [index], { kind: 'remove' })), [0, 1, 2].filter((_, i) => i !== index))
    const object = JSON.parse(editJsonSource('{"a":0,"b":1,"c":2}', [index], { kind: 'remove' }))
    assert.equal(Object.keys(object).length, 2)
    assert.equal(Object.values(object).includes(index), false)
  }
  assert.equal(editJsonSource('{"a":1}', [0], { kind: 'remove' }), '{}')
  assert.equal(editJsonSource('[1]', [0], { kind: 'remove' }), '[]')
  assert.deepEqual(JSON.parse(editJsonSource('{}', [], { kind: 'add' })), { field: null })
  assert.deepEqual(JSON.parse(editJsonSource('{"field":0}', [], { kind: 'add' })), { field: 0, field1: null })
  assert.deepEqual(JSON.parse(editJsonSource('[]', [], { kind: 'add' })), [null])
  assert.equal(editJsonSource('null', [], { kind: 'value', value: '[]' }), '[]')
  assert.throws(() => editJsonSource('{}', [], { kind: 'remove' }))
})

test('escaped JSON remains escaped when changed in structure view', () => {
  const escaped = escapeJsonText(source).value
  const changed = editJsonSource(escaped, [2, 0], { kind: 'value', value: 'false' })
  const parsed = parseJsonSource(changed)
  assert.ok(parsed.valid)
  assert.equal(parsed.encoding, 'escaped')
  assert.equal(JSON.parse(parsed.text).list[0], false)
  assert.ok(changed.includes('9223372036854775807'))
})
