import assert from 'node:assert/strict'
import test from 'node:test'
import { defaultWorkbenchPreferences, rememberRecentShape, toggleWorkbenchPanel, parseWorkbenchPreferences, visibleRecentKinds } from '../src/utils/flowchartEditorState.ts'

test('workbench preferences toggle only the requested panel', () => {
  const state = defaultWorkbenchPreferences()
  assert.deepEqual(toggleWorkbenchPanel(state, 'library'), { ...state, libraryCollapsed: true })
})

test('recent shapes deduplicate, prepend and cap at five entries', () => {
  let state = defaultWorkbenchPreferences()
  for (const kind of ['rectangle', 'diamond', 'rectangle', 'database', 'io', 'text'] as const) state = rememberRecentShape(state, kind)
  assert.deepEqual(state.recentKinds, ['text', 'io', 'database', 'rectangle', 'diamond'])
})

test('visible recent shapes only includes supported kinds', () => {
  assert.deepEqual(visibleRecentKinds(['diamond', 'unknown' as never, 'diamond', 'text']), ['diamond', 'text'])
})

test('malformed persisted preferences fall back to defaults', () => {
  assert.deepEqual(parseWorkbenchPreferences('{not json'), defaultWorkbenchPreferences())
  assert.deepEqual(parseWorkbenchPreferences(null), defaultWorkbenchPreferences())
})

test('persisted preferences are normalized while retaining valid values', () => {
  assert.deepEqual(parseWorkbenchPreferences(JSON.stringify({ libraryCollapsed: true, inspectorCollapsed: false, recentKinds: ['text', 'invalid', 'text', 'io'] })), {
    libraryCollapsed: true,
    inspectorCollapsed: false,
    recentKinds: ['text', 'io'],
  })
})
