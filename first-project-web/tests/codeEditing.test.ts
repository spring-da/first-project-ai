import assert from 'node:assert/strict'
import { test } from 'node:test'
import { appendBoundedHistory, codeKeyEdit, measureCode } from '../src/utils/codeEditing.ts'

test('large editor metrics avoid line arrays and history stays within its character budget', () => {
  assert.deepEqual(measureCode('one\ntwo\nthree'), { characters: 13, lines: 3, large: false })
  assert.equal(measureCode('x'.repeat(40_001)).large, true)
  assert.equal(measureCode(Array.from({ length: 1_501 }, () => 'x').join('\n')).large, true)
  const history = ['a'.repeat(200_000), 'b'.repeat(200_000), 'c'.repeat(200_000)]
  const bounded = appendBoundedHistory(history, 'd'.repeat(200_000))
  assert.deepEqual(bounded.map(value => value[0]), ['b', 'c', 'd'])
})

test('Enter inside matching braces adds an indented line and aligns the closing brace', () => {
  assert.deepEqual(codeKeyEdit('{}', 1, 1, 'Enter'), { value: '{\n  \n}', start: 4, end: 4 })
  assert.deepEqual(codeKeyEdit('  []', 3, 3, 'Enter'), { value: '  [\n    \n  ]', start: 8, end: 8 })
  assert.equal(codeKeyEdit('  "a": 1,', 9, 9, 'Enter')?.value, '  "a": 1,\n  ')
  assert.equal(codeKeyEdit('{"str":"{"}', 9, 9, 'Enter')?.value, '{"str":"{\n"}')
})

test('pair insertion, skipping closers, deletion and selected wrapping keep caret positions', () => {
  assert.deepEqual(codeKeyEdit('', 0, 0, '{'), { value: '{}', start: 1, end: 1 })
  assert.deepEqual(codeKeyEdit('{}', 1, 1, '}'), { value: '{}', start: 2, end: 2 })
  assert.deepEqual(codeKeyEdit('{}', 1, 1, 'Backspace'), { value: '', start: 0, end: 0 })
  assert.deepEqual(codeKeyEdit('text', 0, 4, '"'), { value: '"text"', start: 1, end: 5 })
  assert.equal(codeKeyEdit('"{', 2, 2, '['), null)
})

test('Tab and Shift+Tab indent selections without consuming the next line', () => {
  const input = 'one\ntwo\nthree'
  const result = codeKeyEdit(input, 0, 8, 'Tab')!
  assert.equal(result.value, '  one\n  two\nthree')
  assert.equal(result.start, 2)
  assert.equal(result.end, 12)
  assert.equal(codeKeyEdit(result.value, result.start, result.end, 'Tab', true)?.value, input)
  assert.deepEqual(codeKeyEdit('  a', 2, 2, 'Tab', true), { value: 'a', start: 0, end: 0 })
})

test('escaped JSON strings distinguish structural braces from braces inside values', () => {
  const text = '{\n  \\"data\\": {}\n}'
  const position = text.indexOf('{}') + 1
  assert.ok(codeKeyEdit(text, position, position, 'Enter', false, 2, true)?.value.includes('{}') === false)
  const string = '{\\"note\\": \\"{'
  assert.equal(codeKeyEdit(string, string.length, string.length, '[', false, 2, true), null)
})
