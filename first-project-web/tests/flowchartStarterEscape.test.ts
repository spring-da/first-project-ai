import assert from 'node:assert/strict'
import test from 'node:test'
import { attachStarterEscapeListener } from '../src/utils/flowchartStarterEscape.ts'

class KeyboardTarget {
  private listener: ((event: KeyboardEvent) => void) | undefined

  addEventListener(_type: 'keydown', listener: (event: KeyboardEvent) => void) { this.listener = listener }
  removeEventListener(_type: 'keydown', listener: (event: KeyboardEvent) => void) { if (this.listener === listener) this.listener = undefined }
  dispatch(key: string) {
    let prevented = false
    this.listener?.({ key, preventDefault: () => { prevented = true } } as KeyboardEvent)
    return prevented
  }
}

test('starter Escape closes from outside focus and stops intercepting after the starter closes', () => {
  const target = new KeyboardTarget()
  let starterOpen = true, closed = 0
  const remove = attachStarterEscapeListener(target, () => starterOpen, () => { closed++ })

  assert.equal(target.dispatch('Escape'), true)
  assert.equal(closed, 1)

  starterOpen = false
  assert.equal(target.dispatch('Escape'), false)
  assert.equal(closed, 1)

  starterOpen = true
  remove()
  assert.equal(target.dispatch('Escape'), false)
  assert.equal(closed, 1)
})
