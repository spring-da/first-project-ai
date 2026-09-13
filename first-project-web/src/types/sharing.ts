import type { FlowchartDiagram } from './flowcharts'
export type ShareResourceType = 'MARKDOWN' | 'SNIPPET' | 'FLOWCHART'

export interface ShareReference { type: ShareResourceType; id: string }
export interface ShareSelection extends ShareReference { title: string }

export interface SharedContent {
  id: string
  resourceType: ShareResourceType
  title: string
  content: string
  diagram?: FlowchartDiagram | null
  language: string | null
  category: string | null
  tags: string[]
  updatedAt: string
}

export interface SharedPoolEntry {
  id: string
  resourceType: ShareResourceType
  title: string
  excerpt: string
  authorName: string
  sharedAt: string
  updatedAt: string
  mine: boolean
}

export interface SharePage<T> { items: T[]; total: number; page: number; size: number }
export interface ShareBundleSummary {
  id: string
  title: string
  expiresAt: string
  createdAt: string
  revokedAt: string | null
  active: boolean
  itemCount: number
}
export interface ShareBundleSecret { id: string; token: string; title: string; expiresAt: string; createdAt: string }
export interface ShareBundleItem {
  id: string
  resourceType: ShareResourceType
  resourceId: string
  title: string
  removedAt: string | null
  available: boolean
}
export interface ShareBundleDetail { link: ShareBundleSummary; items: ShareBundleItem[] }
export interface PublicShareBundle {
  title: string
  expiresAt: string
  items: Array<{ id: string; resourceType: ShareResourceType; title: string }>
}
export interface SharedPoolQuery { page?: number; size?: number; q?: string; type?: ShareResourceType; mine?: boolean }
export interface ShareBundleQuery { page?: number; size?: number; q?: string }
