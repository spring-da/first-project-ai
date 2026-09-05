import assert from 'node:assert/strict'
import test from 'node:test'
import { settleWorkspaceModules } from '../src/utils/workspaceLoading.ts'

test('independent workspace loading keeps successful module results when one request fails', async () => {
  const committed: string[] = []
  const results = await settleWorkspaceModules([
    async () => { committed.push('tasks'); return 'tasks' },
    async () => { throw new Error('projects unavailable') },
    async () => { committed.push('markdownDocuments'); return 'markdownDocuments' },
  ])

  assert.deepEqual(committed, ['tasks', 'markdownDocuments'])
  assert.deepEqual(results.map((result) => result.status), ['fulfilled', 'rejected', 'fulfilled'])
  assert.match(String((results[1] as PromiseRejectedResult).reason), /projects unavailable/)
})
