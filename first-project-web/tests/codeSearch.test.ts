import assert from 'node:assert/strict'
import test from 'node:test'
import { findCodeMatches } from '../src/utils/codeSearch.ts'

test('finds literal JSON text without interpreting regex punctuation', () => {
  assert.deepEqual(findCodeMatches('{"a.b": "[x]", "aXb": "[x]"}', 'a.b'), [{ start: 2, end: 5 }])
  assert.deepEqual(findCodeMatches('[x] [x]', '[x]'), [{ start: 0, end: 3 }, { start: 4, end: 7 }])
  assert.deepEqual(findCodeMatches('a\\b a/b', '\\'), [{ start: 1, end: 2 }])
})

test('matches case insensitively while keeping original UTF-16 selection offsets', () => {
  assert.deepEqual(findCodeMatches('İ 😀 NAME name 中文', 'name'), [{ start: 5, end: 9 }, { start: 10, end: 14 }])
  assert.deepEqual(findCodeMatches('İ 😀 NAME name 中文', '中文'), [{ start: 15, end: 17 }])
})

test('handles empty, missing, whitespace and non-overlapping queries', () => {
  assert.deepEqual(findCodeMatches('aaa', ''), [])
  assert.deepEqual(findCodeMatches('', 'a'), [])
  assert.deepEqual(findCodeMatches('aaa', 'z'), [])
  assert.deepEqual(findCodeMatches('aaa', 'aa'), [{ start: 0, end: 2 }])
  assert.deepEqual(findCodeMatches('a  b', '  '), [{ start: 1, end: 3 }])
})

test('searches the entire large source including the final match', () => {
  const matches = findCodeMatches('x '.repeat(100_000), 'x')
  assert.equal(matches.length, 100_000)
  assert.deepEqual(matches.at(-1), { start: 199_998, end: 199_999 })
})

test('accepts a long selected JSON value as a literal query', () => {
  const query = 'X'.repeat(100_000)
  assert.deepEqual(findCodeMatches(`{"value":"${query}"}`, query.toLowerCase()), [{ start: 10, end: 100_010 }])
})
