import type { MarkdownDocument } from '../types'

export function matchesSearch(query: string, ...fields: (string | undefined | null)[]) {
  const keyword = query.trim().toLocaleLowerCase()
  return !keyword || fields.filter(Boolean).join(' ').toLocaleLowerCase().includes(keyword)
}

// Both the article list and global search use the same visible-name matching.
// Loading an article's full content must not change whether it appears in search.
export function matchesDocumentSearch(query: string, document: Pick<MarkdownDocument, 'title' | 'fileName'>) {
  return matchesSearch(query, document.title, document.fileName)
}
