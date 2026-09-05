import assert from 'node:assert/strict'
import { beforeEach, test } from 'node:test'
import { createPinia, setActivePinia } from 'pinia'
import { useSearchStore } from '../src/stores/search.ts'
import { matchesDocumentSearch, matchesSearch } from '../src/utils/search.ts'

beforeEach(() => setActivePinia(createPinia()))

test('the shared query filters a current list without a second input', () => {
  const search = useSearchStore()
  search.reset('page')
  search.query = 'Vue'
  assert.equal(search.pageQuery, 'Vue')
  assert.equal(useSearchStore().pageQuery, 'Vue')
})

test('global scope stops filtering the page and preserves the entered query', () => {
  const search = useSearchStore()
  search.reset('page')
  search.query = '采购'
  search.scope = 'global'
  assert.equal(search.pageQuery, '')
  assert.equal(search.query, '采购')
  search.scope = 'page'
  assert.equal(search.pageQuery, '采购')
})

test('navigation clears the previous page filter and selects the next scope', () => {
  const search = useSearchStore()
  search.query = 'previous project'
  search.reset('page')
  assert.equal(search.query, '')
  assert.equal(search.scope, 'page')
  search.query = 'article'
  search.reset('global')
  assert.equal(search.pageQuery, '')
  assert.equal(search.query, '')
})

test('clearing a query preserves the selected scope', () => {
  const search = useSearchStore()
  search.reset('page')
  search.query = 'hello'
  search.clear()
  assert.equal(search.pageQuery, '')
  assert.equal(search.scope, 'page')
})

test('search matches Chinese text, filenames, tags, and technology names', () => {
  assert.equal(matchesSearch('  vUe  ', '项目', 'Vue 3'), true)
  assert.equal(matchesSearch('采购', '供应链', '采购协同门户'), true)
  assert.equal(matchesSearch('.md', '笔记', 'hello.md'), true)
  assert.equal(matchesSearch('复盘', '日志', '成长 复盘'), true)
  assert.equal(matchesSearch('missing', 'hello', undefined, null), false)
  assert.equal(matchesSearch('   ', undefined), true)
})

test('article search does not match a keyword found only in content or excerpt', () => {
  const document = { title: 'hello', fileName: 'hello.md', content: 'project in the body', excerpt: 'project in the excerpt' }
  assert.equal(matchesDocumentSearch('project', document), false)
  assert.equal(matchesDocumentSearch('hello', document), true)
})

test('article search matches titles and filenames regardless of case or whitespace', () => {
  assert.equal(matchesDocumentSearch('  PROJECT  ', { title: 'Project 计划', fileName: 'plan.md' }), true)
  assert.equal(matchesDocumentSearch('project', { title: '项目说明', fileName: 'project-plan.md' }), true)
  assert.equal(matchesDocumentSearch('笔记', { title: '开发笔记', fileName: 'notes.md' }), true)
  assert.equal(matchesDocumentSearch('.md', { title: 'hello', fileName: 'hello.md' }), true)
  assert.equal(matchesDocumentSearch('   ', { title: 'hello', fileName: 'hello.md' }), true)
})

test('loading or changing article content never changes the name-based search result', () => {
  const summary = { title: 'hello', fileName: 'hello.md', excerpt: '摘要' }
  const loaded = { ...summary, content: 'new project content', excerpt: 'project' }
  for (const query of ['project', 'hello', '.md', '']) {
    assert.equal(matchesDocumentSearch(query, summary), matchesDocumentSearch(query, loaded))
  }
})
