import type { CodeSnippet, DevLogEntry, LogCategory, MarkdownDocument } from '../types/index.ts'
import { matchesDocumentSearch, matchesSearch } from './search.ts'

export type KnowledgeItemType = 'documents' | 'snippets' | 'logs'
export type KnowledgeTypeFilter = 'all' | KnowledgeItemType

export type UnifiedKnowledgeItem =
  | { type: 'documents'; id: string; domainId: string | null; title: string; summary: string; detail: string; updatedAt: string; featured: boolean; source: MarkdownDocument }
  | { type: 'snippets'; id: string; domainId: string | null; title: string; summary: string; detail: string; updatedAt: string; featured: boolean; source: CodeSnippet }
  | { type: 'logs'; id: string; domainId: string | null; title: string; summary: string; detail: string; updatedAt: string; featured: boolean; source: DevLogEntry }

export interface KnowledgeFilters {
  type: KnowledgeTypeFilter
  domain: string
  allDomains: string
  unassigned: string
  query: string
  featuredOnly: boolean
  logCategory: 'ALL' | LogCategory
}

function firstCodeLine(value: string) {
  return value.split(/\r?\n/).find((line) => line.trim())?.trim() ?? ''
}

export function buildUnifiedKnowledgeItems(
  documents: MarkdownDocument[],
  snippets: CodeSnippet[],
  logs: DevLogEntry[],
): UnifiedKnowledgeItem[] {
  return [
    ...documents.map((source): UnifiedKnowledgeItem => ({
      type: 'documents', id: source.id, domainId: source.domainId, title: source.title,
      summary: source.excerpt?.trim() || source.content?.replace(/^#\s+.*$/m, '').replace(/[#>*_`\[\]()~-]+/g, ' ').replace(/\s+/g, ' ').trim() || '这篇文章还没有摘要，点击查看完整内容。',
      detail: source.fileName, updatedAt: source.updatedAt, featured: source.favorite, source,
    })),
    ...snippets.map((source): UnifiedKnowledgeItem => ({
      type: 'snippets', id: source.id, domainId: source.domainId, title: source.title,
      summary: firstCodeLine(source.code) || '这段代码还没有内容。',
      detail: source.language, updatedAt: source.updatedAt, featured: source.favorite, source,
    })),
    ...logs.map((source): UnifiedKnowledgeItem => ({
      type: 'logs', id: source.id, domainId: source.domainId,
      title: source.title || source.content.slice(0, 34) || '未命名日志',
      summary: source.content || '这条日志还没有正文。', detail: source.category,
      updatedAt: source.updatedAt, featured: source.pinned, source,
    })),
  ].sort((a, b) => Number(b.featured) - Number(a.featured) || b.updatedAt.localeCompare(a.updatedAt))
}

export function filterUnifiedKnowledgeItems(items: UnifiedKnowledgeItem[], filters: KnowledgeFilters) {
  return items.filter((item) => {
    if (filters.type !== 'all' && item.type !== filters.type) return false
    if (filters.domain === filters.unassigned ? item.domainId : filters.domain !== filters.allDomains && item.domainId !== filters.domain) return false
    if (filters.featuredOnly && !item.featured) return false
    if (item.type === 'logs' && filters.logCategory !== 'ALL' && item.source.category !== filters.logCategory) return false
    return item.type === 'documents'
      ? matchesDocumentSearch(filters.query, item.source)
      : item.type === 'snippets'
        ? matchesSearch(filters.query, item.source.title, item.source.language, item.source.code)
        : matchesSearch(filters.query, item.source.title, item.source.content, item.source.tags.join(' '))
  })
}

export function countKnowledgeInDomain(items: UnifiedKnowledgeItem[], domain: string, allDomains: string, unassigned: string) {
  if (domain === allDomains) return items.length
  if (domain === unassigned) return items.filter((item) => !item.domainId).length
  return items.filter((item) => item.domainId === domain).length
}
