import assert from 'node:assert/strict'
import { test } from 'node:test'
import { findScrollDestinationIndex } from '../src/utils/scrollChaining.ts'

const scrollState = (scrollTop: number, scrollHeight = 1000, clientHeight = 300) => ({ scrollTop, scrollHeight, clientHeight })

test('wheel scrolling stays in the nearest region while it can move', () => {
  assert.equal(findScrollDestinationIndex([scrollState(200), scrollState(100)], 120), 0)
  assert.equal(findScrollDestinationIndex([scrollState(200), scrollState(100)], -120), 0)
})

test('wheel scrolling recursively falls back to a scrollable ancestor at boundaries', () => {
  assert.equal(findScrollDestinationIndex([scrollState(700), scrollState(100)], 120), 1)
  assert.equal(findScrollDestinationIndex([scrollState(0), scrollState(100)], -120), 1)
  assert.equal(findScrollDestinationIndex([scrollState(0, 300, 300), scrollState(100)], 120), 1)
  assert.equal(findScrollDestinationIndex([scrollState(700), scrollState(700)], 120), -1)
})
