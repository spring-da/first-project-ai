import type { MarkdownDocumentDraft } from '../types'

export interface LocalMarkdownDraft {
  schemaVersion: 1
  id: string
  documentId: string | null
  baseVersion: number | null
  savedAt: string
  draft: MarkdownDocumentDraft
}

type DraftStorage = Pick<Storage, 'length' | 'key' | 'getItem' | 'setItem' | 'removeItem'>
const PREFIX = 'devnest_markdown_drafts_v1:'

export function copyMarkdownDraft(draft: MarkdownDocumentDraft): MarkdownDocumentDraft {
  return { title: draft.title, fileName: draft.fileName, content: draft.content, domainId: draft.domainId ?? null, favorite: draft.favorite }
}

export function serializeMarkdownDraft(draft: MarkdownDocumentDraft) {
  return JSON.stringify(copyMarkdownDraft(draft))
}

// Preserve edits made while a save request was in flight, including metadata edits.
export function mergeSavedMarkdownDraft(submitted: MarkdownDocumentDraft, current: MarkdownDocumentDraft, saved: MarkdownDocumentDraft): MarkdownDocumentDraft {
  return {
    title: current.title === submitted.title ? saved.title : current.title,
    fileName: current.fileName === submitted.fileName ? saved.fileName : current.fileName,
    content: current.content === submitted.content ? saved.content : current.content,
    domainId: current.domainId === submitted.domainId ? saved.domainId : current.domainId,
    favorite: current.favorite === submitted.favorite ? saved.favorite : current.favorite,
  }
}

function validRecord(value: unknown): value is LocalMarkdownDraft {
  if (!value || typeof value !== 'object') return false
  const item = value as LocalMarkdownDraft
  const draft = item.draft
  return item.schemaVersion === 1 && typeof item.id === 'string' && !!item.id
    && (item.documentId === null || typeof item.documentId === 'string')
    && (item.baseVersion === null || (Number.isSafeInteger(item.baseVersion) && item.baseVersion >= 0))
    && typeof item.savedAt === 'string' && Number.isFinite(Date.parse(item.savedAt))
    && !!draft && typeof draft.title === 'string' && typeof draft.fileName === 'string'
    && typeof draft.content === 'string' && typeof draft.favorite === 'boolean'
    && (draft.domainId === null || typeof draft.domainId === 'string')
}

export function createMarkdownDraftStorage(storage: DraftStorage) {
  function prefix(ownerId: string) {
    if (!ownerId) throw new Error('登录后才能保存本地草稿。')
    return `${PREFIX}${encodeURIComponent(ownerId)}:`
  }
  return {
    list(ownerId: string): LocalMarkdownDraft[] {
      const accountPrefix = prefix(ownerId)
      const records: LocalMarkdownDraft[] = []
      for (let index = 0; index < storage.length; index++) {
        const key = storage.key(index)
        if (!key?.startsWith(accountPrefix)) continue
        const raw = storage.getItem(key)
        try {
          const item: unknown = JSON.parse(raw ?? 'null')
          if (validRecord(item) && key === accountPrefix + encodeURIComponent(item.id)) records.push(item)
        } catch { /* Leave unrecognized/corrupted entries untouched; other drafts remain recoverable. */ }
      }
      return records.sort((a, b) => b.savedAt.localeCompare(a.savedAt))
    },
    write(ownerId: string, record: LocalMarkdownDraft) {
      if (!validRecord(record)) throw new Error('草稿格式无效。')
      // Separate keys avoid one document/tab overwriting another. Never evict drafts on quota failure.
      storage.setItem(prefix(ownerId) + encodeURIComponent(record.id), JSON.stringify(record))
    },
    remove(ownerId: string, id: string) {
      storage.removeItem(prefix(ownerId) + encodeURIComponent(id))
    },
  }
}
