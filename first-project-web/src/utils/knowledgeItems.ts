import type { CodeSnippet, MarkdownDocument } from '../types/index.ts'
import type { FlowchartSummary } from '../types/flowcharts.ts'
import { matchesDocumentSearch, matchesSearch } from './search.ts'

export type KnowledgeItemType = 'documents' | 'snippets' | 'flowcharts'
export type KnowledgeTypeFilter = 'all' | KnowledgeItemType

export type UnifiedKnowledgeItem =
  | { type: 'documents'; id: string; domainId: string | null; title: string; summary: string; detail: string; updatedAt: string; featured: boolean; source: MarkdownDocument }
  | { type: 'snippets'; id: string; domainId: string | null; title: string; summary: string; detail: string; updatedAt: string; featured: boolean; source: CodeSnippet }
  | { type: 'flowcharts'; id: string; domainId: string | null; title: string; summary: string; detail: string; updatedAt: string; featured: boolean; source: FlowchartSummary }

export interface KnowledgeFilters {
  type: KnowledgeTypeFilter
  domain: string
  allDomains: string
  unassigned: string
  query: string
  featuredOnly: boolean
  flowchartMatches?: ReadonlySet<string>
}

function firstCodeLine(value: string) {
  return value.split(/\r?\n/).find((line) => line.trim())?.trim() ?? ''
}

export function buildUnifiedKnowledgeItems(
  documents: MarkdownDocument[],
  snippets: CodeSnippet[],
  flowcharts: FlowchartSummary[],
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
    ...flowcharts.map((source): UnifiedKnowledgeItem => ({
      type: 'flowcharts', id: source.id, domainId: source.domainId,
      title: source.title,
      summary: source.excerpt || '点击查看流程图。', detail: `${source.nodeCount} 个图形 · ${source.edgeCount} 条连线`,
      updatedAt: source.updatedAt, featured: source.favorite, source,
    })),
  ].sort((a, b) => Number(b.featured) - Number(a.featured) || b.updatedAt.localeCompare(a.updatedAt))
}

export function filterUnifiedKnowledgeItems(items: UnifiedKnowledgeItem[], filters: KnowledgeFilters) {
  return items.filter((item) => {
    if (filters.type !== 'all' && item.type !== filters.type) return false
    if (filters.domain === filters.unassigned ? item.domainId : filters.domain !== filters.allDomains && item.domainId !== filters.domain) return false
    if (filters.featuredOnly && !item.featured) return false
    return item.type === 'documents'
      ? matchesDocumentSearch(filters.query, item.source)
      : item.type === 'snippets'
        ? matchesSearch(filters.query, item.source.title, item.source.language, item.source.code)
        : !filters.query.trim() || (filters.flowchartMatches ? filters.flowchartMatches.has(item.id) : matchesSearch(filters.query, item.source.title, item.source.excerpt))
  })
}

export function countKnowledgeInDomain(items: UnifiedKnowledgeItem[], domain: string, allDomains: string, unassigned: string) {
  if (domain === allDomains) return items.length
  if (domain === unassigned) return items.filter((item) => !item.domainId).length
  return items.filter((item) => item.domainId === domain).length
}
