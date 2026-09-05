import assert from 'node:assert/strict'
import { test } from 'node:test'
import { buildUnifiedKnowledgeItems, countKnowledgeInDomain, filterUnifiedKnowledgeItems } from '../src/utils/knowledgeItems.ts'
import type { CodeSnippet, DevLogEntry, MarkdownDocument } from '../src/types/index.ts'
import { fitDirectoryTabs } from '../src/utils/directoryTabs.ts'

test('overflow directories keep the selected tab visible and never exceed available space', () => {
  const items = ['all', 'one', 'two', 'three'].map(id => ({ id, width: 100 }))
  assert.deepEqual(fitDirectoryTabs(items, 500, 'all'), ['all', 'one', 'two', 'three'])
  assert.deepEqual(fitDirectoryTabs(items, 220, 'three'), ['all', 'three'])
  assert.deepEqual(fitDirectoryTabs(items, 50, 'three'), ['three'])
  assert.deepEqual(fitDirectoryTabs([], 200, 'all'), [])
})

const common = { createdAt: '2026-08-01T00:00:00Z' }
const documents: MarkdownDocument[] = [{ ...common, id: 'doc', title: '架构说明', fileName: 'architecture.md', excerpt: '系统边界', domainId: 'backend', favorite: true, updatedAt: '2026-08-03T00:00:00Z' }]
const snippets: CodeSnippet[] = [{ ...common, id: 'snippet', title: 'JWT helper', language: 'TypeScript', code: '\nconst token = sign()\n', domainId: 'backend', favorite: false, updatedAt: '2026-08-04T00:00:00Z' }]
const logs: DevLogEntry[] = [{ ...common, id: 'log', title: '', content: '修复登录锁定问题', category: 'PROBLEM', tags: ['认证'], pinned: true, domainId: null, updatedAt: '2026-08-02T00:00:00Z' }]

test('featured knowledge stays on top and each group remains newest-first', () => {
  const items = buildUnifiedKnowledgeItems(documents, snippets, logs)
  assert.deepEqual(items.map((item) => [item.type, item.id]), [
    ['documents', 'doc'], ['logs', 'log'], ['snippets', 'snippet'],
  ])
  assert.equal(items[2]?.summary, 'const token = sign()')
  assert.equal(items[1]?.title, '修复登录锁定问题')
})

test('one filter model combines type, directory, search, featured, and log category', () => {
  const items = buildUnifiedKnowledgeItems(documents, snippets, logs)
  const base = { domain: 'ALL', allDomains: 'ALL', unassigned: 'UNASSIGNED', query: '', featuredOnly: false, logCategory: 'ALL' as const }
  assert.deepEqual(filterUnifiedKnowledgeItems(items, { ...base, type: 'snippets', query: 'typescript' }).map((item) => item.id), ['snippet'])
  assert.deepEqual(filterUnifiedKnowledgeItems(items, { ...base, type: 'all', domain: 'UNASSIGNED' }).map((item) => item.id), ['log'])
  assert.deepEqual(filterUnifiedKnowledgeItems(items, { ...base, type: 'all', featuredOnly: true }).map((item) => item.id), ['doc', 'log'])
  assert.deepEqual(filterUnifiedKnowledgeItems(items, { ...base, type: 'logs', logCategory: 'DECISION' }).map((item) => item.id), [])
})

test('directory badges always describe the whole knowledge base', () => {
  const items = buildUnifiedKnowledgeItems(documents, snippets, logs)
  assert.equal(countKnowledgeInDomain(items, 'ALL', 'ALL', 'UNASSIGNED'), 3)
  assert.equal(countKnowledgeInDomain(items, 'backend', 'ALL', 'UNASSIGNED'), 2)
  assert.equal(countKnowledgeInDomain(items, 'UNASSIGNED', 'ALL', 'UNASSIGNED'), 1)
})
