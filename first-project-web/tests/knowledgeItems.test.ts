import assert from 'node:assert/strict'
import { test } from 'node:test'
import { buildUnifiedKnowledgeItems, countKnowledgeInDomain, filterUnifiedKnowledgeItems } from '../src/utils/knowledgeItems.ts'
import type { CodeSnippet, MarkdownDocument } from '../src/types/index.ts'
import type { FlowchartSummary } from '../src/types/flowcharts.ts'
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
const flowcharts: FlowchartSummary[] = [{ ...common, id: 'flowchart', title: '登录流程', excerpt: '登录 → 认证', favorite: true, domainId: null, version: 0, deletedAt: null, nodeCount: 2, edgeCount: 1, updatedAt: '2026-08-02T00:00:00Z' }]

test('featured knowledge stays on top and each group remains newest-first', () => {
  const items = buildUnifiedKnowledgeItems(documents, snippets, flowcharts)
  assert.deepEqual(items.map((item) => [item.type, item.id]), [
    ['documents', 'doc'], ['flowcharts', 'flowchart'], ['snippets', 'snippet'],
  ])
  assert.equal(items[2]?.summary, 'const token = sign()')
  assert.equal(items[1]?.title, '登录流程')
})

test('one filter model combines type, directory, search, featured, and server flowchart matches', () => {
  const items = buildUnifiedKnowledgeItems(documents, snippets, flowcharts)
  const base = { domain: 'ALL', allDomains: 'ALL', unassigned: 'UNASSIGNED', query: '', featuredOnly: false }
  assert.deepEqual(filterUnifiedKnowledgeItems(items, { ...base, type: 'snippets', query: 'typescript' }).map((item) => item.id), ['snippet'])
  assert.deepEqual(filterUnifiedKnowledgeItems(items, { ...base, type: 'all', domain: 'UNASSIGNED' }).map((item) => item.id), ['flowchart'])
  assert.deepEqual(filterUnifiedKnowledgeItems(items, { ...base, type: 'all', featuredOnly: true }).map((item) => item.id), ['doc', 'flowchart'])
  assert.deepEqual(filterUnifiedKnowledgeItems(items, { ...base, type: 'flowcharts', query: 'not in summary', flowchartMatches: new Set<string>() }).map((item) => item.id), [])
})

test('directory badges always describe the whole knowledge base', () => {
  const items = buildUnifiedKnowledgeItems(documents, snippets, flowcharts)
  assert.equal(countKnowledgeInDomain(items, 'ALL', 'ALL', 'UNASSIGNED'), 3)
  assert.equal(countKnowledgeInDomain(items, 'backend', 'ALL', 'UNASSIGNED'), 2)
  assert.equal(countKnowledgeInDomain(items, 'UNASSIGNED', 'ALL', 'UNASSIGNED'), 1)
})
