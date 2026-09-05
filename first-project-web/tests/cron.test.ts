import assert from 'node:assert/strict'
import { test } from 'node:test'
import { nextCronRuns, parseCronExpression } from '../src/utils/cron.ts'

test('cron parser accepts xxl-job six-field expressions', () => {
  const parsed = parseCronExpression('0 0/15 9-18 ? * MON-FRI')
  assert.equal(parsed.expression, '0 0/15 9-18 ? * MON-FRI')
  assert.deepEqual(parsed.fields.minute.values, [0, 15, 30, 45])
  assert.deepEqual(parsed.fields.weekday.values, [2, 3, 4, 5, 6])
})

test('five-field cron expressions receive a zero-second field', () => {
  assert.equal(parseCronExpression('30 8 * * *').expression, '0 30 8 * * *')
})

test('next runs are calculated in chronological order', () => {
  const runs = nextCronRuns('0 */30 9-10 * * ?', new Date(2026, 8, 4, 9, 10, 0), 4)
  assert.deepEqual(runs.map((item) => [item.getHours(), item.getMinutes()]), [[9, 30], [10, 0], [10, 30], [9, 0]])
  assert.equal(runs[3]?.getDate(), 5)
})

test('invalid cron ranges report a useful error', () => {
  assert.throws(() => parseCronExpression('0 99 * * * ?'), /分钟必须在/)
})
