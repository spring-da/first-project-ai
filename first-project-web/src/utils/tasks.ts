import type { DevTask, TaskPriority, TaskView } from '../types'

const priorityRank: Record<TaskPriority, number> = { URGENT: 0, HIGH: 1, NORMAL: 2, LOW: 3 }

export function localDateKey(date = new Date()): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function dueDateKey(task: DevTask): string | null {
  return task.dueAt ? localDateKey(new Date(task.dueAt)) : null
}

export function isTodayTask(task: DevTask, today = localDateKey()): boolean {
  if (task.archived) return false
  if (task.scheduledDate === today) return true
  const dueDate = dueDateKey(task)
  return !task.done && dueDate !== null && dueDate <= today
}

export function matchesTaskView(task: DevTask, view: TaskView, today = localDateKey()): boolean {
  if (view === 'ARCHIVED') return task.archived
  if (task.archived) return false
  if (view === 'COMPLETED') return task.done
  if (task.done) return false
  if (view === 'TODAY') return isTodayTask(task, today)
  if (view === 'INBOX') return task.scheduledDate === null
  const dueDate = dueDateKey(task)
  return (task.scheduledDate !== null && task.scheduledDate > today)
    || (dueDate !== null && dueDate > today)
}

export function sortTasks(tasks: DevTask[]): DevTask[] {
  return [...tasks].sort((left, right) => {
    const leftDate = left.dueAt ?? left.scheduledDate ?? '9999-12-31'
    const rightDate = right.dueAt ?? right.scheduledDate ?? '9999-12-31'
    return Number(left.done) - Number(right.done)
      || priorityRank[left.priority] - priorityRank[right.priority]
      || leftDate.localeCompare(rightDate)
      || left.sortOrder - right.sortOrder
      || left.createdAt.localeCompare(right.createdAt)
  })
}
