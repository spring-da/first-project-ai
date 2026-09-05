import assert from 'node:assert/strict'
import test from 'node:test'
import { beginRequest, requestPendingCount } from '../src/services/requestActivity.ts'

test('request activity tracks concurrent requests and releases once', () => {
  const finishFirst = beginRequest()
  const finishSecond = beginRequest()

  assert.equal(requestPendingCount.value, 2)
  finishFirst()
  finishFirst()
  assert.equal(requestPendingCount.value, 1)
  finishSecond()
  assert.equal(requestPendingCount.value, 0)
})
