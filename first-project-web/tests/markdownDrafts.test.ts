import assert from 'node:assert/strict'
import { test } from 'node:test'
import { copyMarkdownDraft, createMarkdownDraftStorage, mergeSavedMarkdownDraft, serializeMarkdownDraft } from '../src/utils/markdownDrafts.ts'
import type { LocalMarkdownDraft } from '../src/utils/markdownDrafts.ts'

function memoryStorage() {
  const values = new Map<string, string>()
  return {
    get length() { return values.size },
    key: (index: number) => [...values.keys()][index] ?? null,
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => { values.set(key, value) },
    removeItem: (key: string) => { values.delete(key) },
  }
}

const record = (id: string, documentId: string | null = null): LocalMarkdownDraft => ({
  schemaVersion: 1, id, documentId, baseVersion: documentId ? 2 : null, savedAt: '2026-08-26T10:00:00Z',
  draft: { title: '中文笔记', fileName: 'notes.md', content: '# 内容\n\n未保存的修改', domainId: null, favorite: true },
})

test('drafts survive recreating the repository and remain isolated by account', () => {
  const storage = memoryStorage()
  const first = createMarkdownDraftStorage(storage)
  first.write('user-1', record('tab-1', 'document-1'))
  first.write('user-2', { ...record('tab-1'), draft: { ...record('tab-1').draft, content: '另一个账号' } })
  const reopened = createMarkdownDraftStorage(storage)
  assert.deepEqual(reopened.list('user-1'), [record('tab-1', 'document-1')])
  assert.equal(reopened.list('user-2')[0]?.draft.content, '另一个账号')
  assert.throws(() => reopened.list(''), /登录/)
})

test('multiple new documents and tabs cannot overwrite each other', () => {
  const drafts = createMarkdownDraftStorage(memoryStorage())
  drafts.write('user', record('first'))
  drafts.write('user', record('second'))
  drafts.write('user', record('third', 'same-document'))
  drafts.write('user', record('fourth', 'same-document'))
  drafts.remove('user', 'second')
  assert.deepEqual(drafts.list('user').map((item) => item.id), ['first', 'third', 'fourth'])
})

test('corrupt, future-schema and malformed entries do not prevent other drafts from recovering', () => {
  const storage = memoryStorage()
  const drafts = createMarkdownDraftStorage(storage)
  drafts.write('user', record('valid'))
  storage.setItem('devnest_markdown_drafts_v1:user:corrupt', '{')
  storage.setItem('devnest_markdown_drafts_v1:user:future', JSON.stringify({ ...record('future'), schemaVersion: 2 }))
  storage.setItem('devnest_markdown_drafts_v1:user:bad', JSON.stringify({ ...record('bad'), draft: null }))
  assert.deepEqual(drafts.list('user'), [record('valid')])
  assert.equal(storage.length, 4)
})

test('storage quota failures surface without evicting previously saved drafts', () => {
  const storage = memoryStorage()
  createMarkdownDraftStorage(storage).write('user', record('first'))
  const failing = createMarkdownDraftStorage({ ...storage, setItem: () => { throw new Error('QuotaExceededError') } })
  assert.throws(() => failing.write('user', record('second')), /QuotaExceededError/)
  assert.deepEqual(createMarkdownDraftStorage(storage).list('user'), [record('first')])
})

test('save responses keep typing and metadata changes made during the request', () => {
  const submitted = record('one').draft
  const current = { ...submitted, content: '保存时继续输入的内容', title: '新标题', domainId: 'new-domain', favorite: false }
  const saved = { ...submitted, fileName: 'normalized.md' }
  assert.deepEqual(mergeSavedMarkdownDraft(submitted, current, saved), { ...current, fileName: 'normalized.md' })
  assert.notEqual(serializeMarkdownDraft(current), serializeMarkdownDraft(saved))
  assert.deepEqual(mergeSavedMarkdownDraft(submitted, submitted, saved), saved)
})

test('dirty comparison ignores transport versions and preserves empty drafts', () => {
  const draft = { ...record('empty').draft, title: '', content: '', favorite: false }
  assert.equal(serializeMarkdownDraft({ ...draft, expectedVersion: 5 }), serializeMarkdownDraft(draft))
  const drafts = createMarkdownDraftStorage(memoryStorage())
  drafts.write('user', { ...record('empty'), draft })
  assert.equal(drafts.list('user')[0]?.draft.content, '')
})

test('nullable folders omitted by the API are normalized before persisting a draft', () => {
  const { domainId: _unused, ...apiFields } = record('api-note').draft
  const copy = copyMarkdownDraft(apiFields as LocalMarkdownDraft['draft'])
  assert.equal(copy.domainId, null)
  const storage = createMarkdownDraftStorage(memoryStorage())
  storage.write('user', { ...record('api-note'), draft: copy })
  assert.equal(storage.list('user').length, 1)
})
