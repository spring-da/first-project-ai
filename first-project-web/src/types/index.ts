import type { FlowchartSummary } from './flowcharts'
export type ProjectStatus = 'PLANNING' | 'BUILDING' | 'PAUSED' | 'COMPLETED'
export type UserRole = 'USER' | 'ADMIN'
export type ProfileGender = 'MALE' | 'FEMALE' | 'OTHER'
export type TaskPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT'
export type TaskView = 'TODAY' | 'UPCOMING' | 'INBOX' | 'COMPLETED' | 'ARCHIVED'

export interface AuthUser {
  id: string
  email: string
  displayName: string
  role: UserRole
  mustChangePassword: boolean
}

export interface AuthSession {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
  expiresAt: number
  user: AuthUser
}

export interface AuthResponse extends Omit<AuthSession, 'expiresAt'> {}

export interface AdminAccount {
  userId: string | null
  invitationId: string | null
  email: string
  displayName: string | null
  role: UserRole | null
  registered: boolean
  enabled: boolean
  mustChangePassword: boolean
  invitedAt: string | null
  invitationExpiresAt: string | null
  invitationExpired: boolean
  registeredAt: string | null
}

export interface InvitationSecret {
  account: AdminAccount
  invitationToken: string
  expiresAt: string
}

export interface TemporaryPasswordSecret {
  temporaryPassword: string
  expiresAt: string
}

export type AdminAuditAction = 'INVITATION_CREATED' | 'INVITATION_ROTATED' | 'INVITATION_REVOKED'
  | 'ACCOUNT_ENABLED' | 'ACCOUNT_DISABLED' | 'ACCOUNT_PASSWORD_RESET' | 'ACCOUNT_DELETED'
  | 'MEMBER_WORKSPACE_WRITE' | 'ANNOUNCEMENT_PUBLISHED' | 'ANNOUNCEMENT_ARCHIVED'
  | 'COMMUNITY_MESSAGE_MODERATED'

export interface AdminAuditEvent {
  id: string
  actorEmail: string
  targetId: string | null
  targetLabel: string | null
  action: AdminAuditAction
  resourceType: string
  httpMethod: string
  requestPath: string
  responseStatus: number
  success: boolean
  createdAt: string
}

export interface SystemAnnouncement {
  id: string
  title: string
  content: string
  publisherName: string
  active: boolean
  read: boolean
  readCount: number
  publishedAt: string
}

export interface CommunityMessage {
  id: string
  parentId: string | null
  authorId: string
  authorName: string
  authorAvatarUrl: string | null
  authorRole: UserRole
  content: string
  imageUrl: string | null
  replyCount: number
  viewerCanDelete: boolean
  createdAt: string
}

export interface CommunityMessagePage {
  items: CommunityMessage[]
  nextCursor: string | null
}

export interface CommunityReplyPage {
  items: CommunityMessage[]
  nextPage: number | null
}

export interface AdminWorkspaceAccount {
  id: string
  email: string
  displayName: string
  role: UserRole
  enabled: boolean
  registeredAt: string
}

export interface AdminWorkspaceSnapshot {
  account: AdminWorkspaceAccount
  profile: DeveloperProfile
  domains: KnowledgeDomain[]
  tasks: DevTask[]
  projects: DevProject[]
  markdownDocuments: MarkdownDocument[]
  snippets: CodeSnippet[]
  flowcharts: FlowchartSummary[]
}

export interface DevTask {
  id: string
  title: string
  done: boolean
  sortOrder: number
  scheduledDate: string | null
  dueAt: string | null
  priority: TaskPriority
  completedAt: string | null
  archived: boolean
  createdAt: string
  updatedAt: string
}

export interface TaskDraft {
  title: string
  scheduledDate: string | null
  dueAt: string | null
  priority: TaskPriority
}

export interface DevProject {
  id: string
  name: string
  description: string
  techStack: string[]
  status: ProjectStatus
  progress: number
  nextAction: string
  createdAt: string
  updatedAt: string
}

export interface CodeSnippet {
  id: string
  title: string
  language: string
  code: string
  favorite: boolean
  domainId: string | null
  createdAt: string
  updatedAt: string
}

export interface TrashedSnippet {
  id: string
  title: string
  language: string
  favorite: boolean
  domainId: string | null
  excerpt: string
  deletedAt: string
}



export interface MarkdownDocument {
  id: string
  title: string
  fileName: string
  content?: string
  excerpt?: string
  contentLength?: number
  version?: number
  deletedAt?: string | null
  domainId: string | null
  favorite: boolean
  createdAt: string
  updatedAt: string
}

export interface MarkdownDocumentDraft {
  title: string
  fileName: string
  content: string
  domainId: string | null
  favorite: boolean
}

export interface MarkdownDocumentUpdateDraft extends MarkdownDocumentDraft {
  expectedVersion: number
}

export interface MarkdownRevision {
  id: string
  documentVersion: number
  action: 'CREATED' | 'BASELINE' | 'UPDATED' | 'IMPORTED' | 'RESTORED' | 'RECOVERED'
  title: string
  fileName: string
  createdAt: string
}

export interface MarkdownRevisionDetail extends MarkdownRevision {
  content: string
  domainId: string | null
  favorite: boolean
}

export interface MarkdownShareLink {
  id: string
  expiresAt: string
  createdAt: string
  revokedAt: string | null
  active: boolean
}

export interface MarkdownShareSecret {
  id: string
  token: string
  expiresAt: string
  createdAt: string
}

export interface PublicMarkdownShare {
  title: string
  fileName: string
  content: string
  createdAt: string
  updatedAt: string
  expiresAt: string
}

export type KnowledgeShareLink = MarkdownShareLink
export type KnowledgeShareSecret = MarkdownShareSecret

export interface PublicKnowledgeShare {
  resourceType: 'SNIPPET'
  title: string
  content: string
  language: string | null
  category: string | null
  tags: string[]
  createdAt: string
  updatedAt: string
  expiresAt: string
}

export interface MarkdownImportDocument {
  fileName: string
  title?: string
  content: string
  domainId?: string | null
}

export interface MarkdownImportFailure {
  fileName: string
  message: string
}

export interface MarkdownImportResult {
  documents: MarkdownDocument[]
  errors?: MarkdownImportFailure[]
}

export interface KnowledgeDomain {
  id: string
  name: string
  description: string
  sortOrder: number
  createdAt: string
  updatedAt: string
}

export interface KnowledgeBulkMoveItem {
  type: 'DOCUMENT' | 'SNIPPET' | 'FLOWCHART'
  id: string
  expectedVersion?: number
}

export interface DeveloperProfile {
  id: string
  name: string
  role: string
  bio: string
  avatarUrl: string | null
  gender: ProfileGender | null
  updatedAt: string
}

export interface ProjectDraft {
  name: string
  description: string
  techStack: string[]
  status: ProjectStatus
  progress: number
  nextAction: string
}

export interface SnippetDraft {
  title: string
  language: string
  code: string
  favorite: boolean
  domainId: string | null
}


export interface DomainDraft {
  name: string
  description: string
  sortOrder: number
}

export interface ProfileDraft {
  name: string
  role: string
  bio: string
  avatarUrl: string | null
  gender: ProfileGender | null
}
