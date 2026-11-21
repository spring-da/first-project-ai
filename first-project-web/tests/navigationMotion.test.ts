import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { resolveNavigationTransition } from '../src/utils/navigationMotion.ts'

const paths = ['/', '/projects', '/knowledge', '/shared-pool', '/tools', '/profile']
const layoutSource = readFileSync(new URL('../src/layouts/AppLayout.vue', import.meta.url), 'utf8')
const styleSource = readFileSync(new URL('../src/style.css', import.meta.url), 'utf8')

test('navigation motion follows the visual order of the sidebar', () => {
  assert.equal(resolveNavigationTransition(paths, '/knowledge', '/'), 'page-forward')
  assert.equal(resolveNavigationTransition(paths, '/projects', '/tools'), 'page-back')
})

test('navigation motion crossfades within a route or outside the primary sidebar', () => {
  assert.equal(resolveNavigationTransition(paths, '/knowledge', '/knowledge'), 'page-crossfade')
  assert.equal(resolveNavigationTransition(paths, '/unknown', '/knowledge'), 'page-crossfade')
  assert.equal(resolveNavigationTransition(paths, '/projects'), 'page-crossfade')
})

test('app layout uses one shared menu indicator and overlapping route transitions', () => {
  assert.match(layoutSource, /class="nav-active-indicator"/)
  assert.match(layoutSource, /<Transition :name="pageTransitionName" appear>/)
  assert.doesNotMatch(layoutSource, /<Transition :name="pageTransitionName" mode="out-in"/)
  assert.match(styleSource, /\.nav-active-indicator[^}]+translate3d/)
  assert.match(styleSource, /\.page-forward-leave-active[^}]+position: absolute/)
})
