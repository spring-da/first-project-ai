import assert from 'node:assert/strict'
import test from 'node:test'
import type { DevTask } from '../src/types/index.ts'
import { isTodayTask, matchesTaskView, sortTasks } from '../src/utils/tasks.ts'

const base: DevTask = {
  id: 'task-1', title: 'Task', done: false, sortOrder: 0,
  scheduledDate: null, dueAt: null, priority: 'NORMAL', completedAt: null, archived: false,
  createdAt: '2026-08-30T08:00:00Z', updatedAt: '2026-08-30T08:00:00Z',
}

test('task views separate inbox, today, upcoming, completed and archived work', () => {
  const today = '2026-08-30'
  assert.equal(matchesTaskView(base, 'INBOX', today), true)
  assert.equal(matchesTaskView({ ...base, scheduledDate: today }, 'TODAY', today), true)
  assert.equal(matchesTaskView({ ...base, scheduledDate: '2026-09-01' }, 'UPCOMING', today), true)
  assert.equal(matchesTaskView({ ...base, done: true, completedAt: '2026-08-30T09:00:00Z' }, 'COMPLETED', today), true)
  assert.equal(matchesTaskView({ ...base, archived: true }, 'ARCHIVED', today), true)
})

test('overdue open tasks stay visible in today while completed progress only includes scheduled work', () => {
  assert.equal(isTodayTask({ ...base, dueAt: '2026-08-29T09:00:00+08:00' }, '2026-08-30'), true)
  assert.equal(isTodayTask({ ...base, done: true, dueAt: '2026-08-29T09:00:00+08:00' }, '2026-08-30'), false)
})

test('task sorting puts unfinished urgent work before normal work', () => {
  const sorted = sortTasks([
    { ...base, id: 'normal' },
    { ...base, id: 'urgent', priority: 'URGENT' },
    { ...base, id: 'done', done: true },
  ])
  assert.deepEqual(sorted.map((item) => item.id), ['urgent', 'normal', 'done'])
})
