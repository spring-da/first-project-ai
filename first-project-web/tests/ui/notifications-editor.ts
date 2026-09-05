import { installAuthGuards } from '../../src/router/guards'
import { isWorkspacePath } from '../../src/services/workspaceContext'
// Manual browser regression fixture. It mounts production components, but uses only
// in-memory storage and synthetic responses. No real account, note or API is accessed.
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import App from '../../src/App.vue'
import { router as productionRouter } from '../../src/router'
import { useThemeStore } from '../../src/stores/theme'
import { useNotificationStore } from '../../src/stores/notifications'
import { useAuthStore } from '../../src/stores/auth'
import { useWorkspaceStore } from '../../src/stores/workspace'
import { localDateKey } from '../../src/utils/tasks'
import { installWheelScrollChaining } from '../../src/utils/scrollChaining'
import type { CodeSnippet, DevLogEntry, DevProject, DevTask, KnowledgeDomain } from '../../src/types'
import '../../src/style.css'

installWheelScrollChaining()

// Reproduce ordinary HTTP's missing secure-context API even on localhost.
const scenario = new URLSearchParams(window.location.search)
if (scenario.has('no-random-uuid')) {
  Object.defineProperty(window.crypto, 'randomUUID', { configurable: true, value: undefined })
}
let unauthorizedEvents = 0
let loginAttempts = 0
let saveRequests = 0
let imageRequests = 0
let imageReads = 0
let savePrevented = false
let multipartValid = true
const diagnostics = document.createElement('p')
function updateDiagnostics() {
  diagnostics.textContent = `${import.meta.env.PROD ? '生产构建' : '开发模式'} · randomUUID：${typeof window.crypto.randomUUID === 'function' ? '可用' : '不可用'} · 登录请求：${loginAttempts} · 会话失效事件：${unauthorizedEvents} · 保存请求：${saveRequests} · 保存快捷键已拦截：${savePrevented} · 图片上传：${imageRequests} · 图片读取：${imageReads} · multipart 正确：${multipartValid}`
}
updateDiagnostics()

const data = new Map<string, string>()
let failDrafts = false
let failSave = false
let failHistory = false
let failImages = false
let holdImages = false
const imageReleases: Array<() => void> = []
const imageBlobs = new Map<string, Blob>()
const sampleCanvas = document.createElement('canvas')
sampleCanvas.width = 320; sampleCanvas.height = 160
const sampleContext = sampleCanvas.getContext('2d')!
sampleContext.fillStyle = '#4267eb'; sampleContext.fillRect(0, 0, 320, 160)
sampleContext.fillStyle = '#ffffff'; sampleContext.font = '20px sans-serif'; sampleContext.fillText('DevNest image test', 48, 85)
const sampleImage = await new Promise<Blob>((resolve) => sampleCanvas.toBlob((blob) => resolve(blob!), 'image/png'))
const fixtureUser = {
  id: 'ui-fixture',
  email: 'fixture@example.invalid',
  displayName: '界面回归测试',
  role: scenario.has('admin') || scenario.has('admin-workspace') ? 'ADMIN' : 'USER',
  mustChangePassword: scenario.has('force-password'),
}
if (!scenario.has('public-share')) {
  data.set('devnest_web_session_v1', JSON.stringify({ accessToken: 'fixture-only', tokenType: 'Bearer', expiresInSeconds: 86400, expiresAt: Date.now() + 86400000, user: fixtureUser }))
}
data.set('devnest_theme_v1', 'light')
Object.defineProperty(window, 'localStorage', { configurable: true, value: {
  get length() { return data.size },
  key: (index: number) => [...data.keys()][index] ?? null,
  getItem: (key: string) => data.get(key) ?? null,
  setItem: (key: string, value: string) => {
    if (failDrafts && key.startsWith('devnest_markdown_drafts_v1:')) throw new Error('QuotaExceededError')
    data.set(key, value)
  },
  removeItem: (key: string) => { data.delete(key) },
} })

const now = new Date().toISOString()
const invitationExpiry = new Date(Date.now() + 48 * 60 * 60 * 1000).toISOString()
let taskSequence = Math.min(200, Math.max(0, Number(scenario.get('tasks')) || 0))
const tasks: DevTask[] = Array.from({ length: taskSequence }, (_, index) => ({
  id: `fixture-task-${index + 1}`, title: scenario.has('long-tasks') && index % 3 === 0
    ? `待办 ${index + 1} · ${'整理项目资料，记录今天的思考与下一步行动。'.repeat(9)}`
    : `待办 ${index + 1} · 整理知识与项目进度`,
  done: index % 5 === 4, sortOrder: index, scheduledDate: localDateKey(),
  dueAt: index % 3 === 0 ? new Date(Date.now() + (index + 1) * 60 * 60 * 1000).toISOString() : null,
  priority: index % 7 === 0 ? 'URGENT' : index % 3 === 0 ? 'HIGH' : 'NORMAL',
  completedAt: index % 5 === 4 ? now : null, archived: false, createdAt: now, updatedAt: now,
}))
const projects: DevProject[] = scenario.get('project') === 'none' ? [] : [{
  id: 'fixture-project', name: '项目回归示例', description: scenario.get('project') === 'long'
    ? '用于验证超长项目说明不会撑高卡片，内部仍然可以滚动查看完整内容。'.repeat(50) : '用于验证删除确认和全局搜索',
  techStack: ['Vue'], status: 'BUILDING', progress: 30, nextAction: '继续测试', createdAt: now, updatedAt: now,
}]
const documents = [{ id: 'fixture-note', title: '把想法写下来', fileName: 'writing-notes.md', content: '# 把想法写下来\n\n留一点空间，给正在发生的思考。\n\n## 今天的记录\n\n- 整理知识，让每一次记录都有迹可循\n- 专注写作，减少界面干扰\n- 在右上角查看操作提醒\n\n> 写作是思考的另一种方式。\n\n' + Array.from({ length: 40 }, (_, index) => `## 记录 ${index + 1}\n\n这是一段用来验证长文滚动的测试内容。编辑区域和实时预览应分别滚动，整个页面不应该出现多余的滚动条。\n\n`).join(''), domainId: 'fixture-domain' as string | null, favorite: false, version: 0, createdAt: now, updatedAt: now }]
const knowledgeItemCount = Math.min(100, Math.max(1, Number(scenario.get('knowledge-items')) || 1))
let knowledgeSequence = knowledgeItemCount
const snippets: CodeSnippet[] = Array.from({ length: knowledgeItemCount }, (_, index) => ({ id: index ? `fixture-snippet-${index + 1}` : 'fixture-snippet', title: `代码回归示例 ${index + 1}`, code: `const example${index + 1} = true`, language: 'TypeScript', domainId: 'fixture-domain', favorite: index % 4 === 0, createdAt: now, updatedAt: now }))
if (scenario.has('json-editor')) Object.assign(snippets[0]!, { title: 'JSON 结构编辑示例', language: 'JSON', code: '{\n  "requestId": 9223372036854775807,\n  "name": "采购示例",\n  "enabled": true,\n  "data": {\n    "items": [\n      { "id": 1, "name": "测试商品", "price": 12.50 },\n      { "id": 2, "name": "合成数据", "price": 6 }\n    ],\n    "note": null\n  }\n}' })
let domainSequence = Math.min(80, Math.max(1, Number(scenario.get('directories')) || 1))
const domains: KnowledgeDomain[] = Array.from({ length: domainSequence }, (_, index) => ({ id: index ? `fixture-domain-${index + 1}` : 'fixture-domain', name: index ? (index === 3 ? '用于验证超长名称与响应式收纳的知识目录' : `知识专题 ${index + 1}`) : '写作与思考', description: index ? `隔离测试目录 ${index + 1}` : '记录想法与开发知识', sortOrder: index, createdAt: now, updatedAt: now }))
const logs: DevLogEntry[] = Array.from({ length: knowledgeItemCount }, (_, index) => ({ id: index ? `fixture-log-${index + 1}` : 'fixture-log', title: `日志回归示例 ${index + 1}`, content: '只用于测试，不含真实数据。', category: 'LEARNING', tags: ['测试'], domainId: 'fixture-domain', pinned: index % 5 === 0, createdAt: now, updatedAt: now }))
const adminDataCount = Math.min(80, Math.max(1, Number(scenario.get('admin-data')) || 1))
const adminWorkspaceDocuments = Array.from({ length: adminDataCount }, (_, index) => ({
  id: `admin-document-${index + 1}`, title: `成员文章 ${index + 1} · ${index % 2 ? '采购流程记录' : '技术方案说明'}`, fileName: `member-note-${index + 1}.md`,
  content: `# 成员文章 ${index + 1}\n\n这是隔离环境中的管理员只读文章。\n\n## 阅读区验证\n\n${'长内容应当只在右侧阅读区滚动，不应拉长整个弹窗。\n\n'.repeat(18)}`,
  excerpt: '用于验证搜索、分页与独立 Markdown 阅读区域。', contentLength: 1200, domainId: 'fixture-domain', favorite: index % 4 === 0, version: 0, createdAt: now, updatedAt: new Date(Date.now() - index * 60000).toISOString(),
}))
const adminWorkspaceSnippets = Array.from({ length: adminDataCount }, (_, index): CodeSnippet => ({ id: `admin-snippet-${index + 1}`, title: `成员代码 ${index + 1}`, code: `const memberValue${index + 1} = { enabled: true }\nconsole.log(memberValue${index + 1})`, language: index % 3 === 0 ? 'JSON' : 'TypeScript', domainId: 'fixture-domain', favorite: false, createdAt: now, updatedAt: new Date(Date.now() - index * 70000).toISOString() }))
const adminWorkspaceLogs = Array.from({ length: adminDataCount }, (_, index): DevLogEntry => ({ id: `admin-log-${index + 1}`, title: `成员日志 ${index + 1}`, content: `排查记录 ${index + 1}，只用于隔离界面测试。`, category: 'LEARNING', tags: ['成员', '测试'], domainId: 'fixture-domain', pinned: false, createdAt: now, updatedAt: new Date(Date.now() - index * 80000).toISOString() }))
const fixtureShareToken = 's'.repeat(43)
let shareSequence = 1
let markdownShares = [{ id: 'fixture-share-1', token: fixtureShareToken, expiresAt: new Date(Date.now() + 7 * 86400000).toISOString(), createdAt: now, revokedAt: null as string | null, active: true }]
let accountSequence = 3
let adminAccounts = [
  { userId: 'ui-fixture', invitationId: null, email: 'fixture@example.invalid', displayName: '界面回归测试', role: 'ADMIN', registered: true, enabled: true, mustChangePassword: false, invitedAt: null, invitationExpiresAt: null, invitationExpired: false, registeredAt: now },
  { userId: 'fixture-friend', invitationId: 'invite-friend', email: 'friend@example.com', displayName: '朋友账户', role: 'USER', registered: true, enabled: true, mustChangePassword: false, invitedAt: now, invitationExpiresAt: null, invitationExpired: false, registeredAt: now },
  { userId: 'fixture-disabled', invitationId: 'invite-disabled', email: 'disabled@example.com', displayName: '已禁用用户', role: 'USER', registered: true, enabled: false, mustChangePassword: true, invitedAt: now, invitationExpiresAt: null, invitationExpired: false, registeredAt: now },
  { userId: null, invitationId: 'invite-pending', email: 'pending@example.com', displayName: null, role: null, registered: false, enabled: false, mustChangePassword: false, invitedAt: now, invitationExpiresAt: invitationExpiry, invitationExpired: false, registeredAt: null },
]
const json = (payload: unknown, status = 200) => new Response(JSON.stringify(payload), { status, headers: { 'Content-Type': 'application/json' } })
const memberData = new Map<string, { tasks: typeof tasks; projects: typeof projects; documents: typeof documents; snippets: typeof snippets; logs: typeof logs; domains: typeof domains; profile: { name: string; role: string; bio: string; avatarUrl: string | null } }>()
for (const account of adminAccounts.filter((item) => item.userId)) {
  memberData.set(account.userId!, {
    tasks: structuredClone(tasks), projects: structuredClone(projects), documents: structuredClone(documents),
    snippets: structuredClone(snippets), logs: structuredClone(logs), domains: structuredClone(domains),
    profile: { name: account.displayName!, role: 'Independent Developer', bio: '用代码记录成长，把想法构建成作品。', avatarUrl: null },
  })
}
window.fetch = async (input, options = {}) => {
  const path = String(input).replace(/^.*\/api\/v1/, '')
  const target = new Headers(options.headers).get('X-Workspace-Owner')
  if (target && isWorkspacePath(path) && fixtureUser.role !== 'ADMIN') return json({ detail: '需要管理员权限' }, 403)
  const scoped = memberData.get(target ?? fixtureUser.id)
  if (target && isWorkspacePath(path) && !scoped) return json({ detail: '测试成员不存在' }, 404)
  const { tasks, projects, documents, snippets, logs, domains, profile } = scoped ?? memberData.get('ui-fixture')!

  if (path.startsWith('/public/markdown-shares/')) {
    const parts = path.split('/')
    const token = decodeURIComponent(parts[3] ?? '')
    const share = markdownShares.find((item) => item.token === token && item.active)
    if (!share) return json({ detail: '分享链接不存在、已过期或已被撤销' }, 404)
    if (parts[4] === 'images') {
      const blob = imageBlobs.get(parts[5] ?? '')
      return blob ? new Response(blob) : json({ detail: '测试：分享图片不存在' }, 404)
    }
    const note = documents[0]!
    return json({ title: note.title, fileName: note.fileName, content: note.content, createdAt: note.createdAt, updatedAt: note.updatedAt, expiresAt: share.expiresAt })
  }
  if (path === '/markdown-images' && options.method === 'POST') {
    imageRequests++
    const headers = new Headers(options.headers)
    multipartValid = options.body instanceof FormData && !headers.has('Content-Type') && headers.get('Authorization') === 'Bearer fixture-only'
    updateDiagnostics()
    if (failImages) return json({ detail: '测试：OSS 暂时不可用，请稍后重试。' }, 503)
    if (holdImages) await new Promise<void>((resolve, reject) => {
      const abort = () => reject(new DOMException('Cancelled', 'AbortError'))
      options.signal?.addEventListener('abort', abort, { once: true })
      imageReleases.push(() => { options.signal?.removeEventListener('abort', abort); resolve() })
    })
    const file = (options.body as FormData).get('file') as Blob
    const id = `00000000-0000-4000-8000-${String(imageBlobs.size + 1).padStart(12, '0')}`
    imageBlobs.set(id, file)
    return json({ id, url: `/api/v1/markdown-images/${id}`, contentType: file.type, size: file.size }, 201)
  }
  if (path.startsWith('/markdown-images/')) {
    imageReads++
    updateDiagnostics()
    if (new Headers(options.headers).get('Authorization') !== 'Bearer fixture-only') return json({ detail: '测试：图片需要登录' }, 401)
    const blob = imageBlobs.get(path.split('/')[2]!)
    return blob ? new Response(blob) : json({ detail: '测试：图片不存在' }, 404)
  }
  if (path === '/auth/login') {
    loginAttempts++
    updateDiagnostics()
    return json({ detail: '测试：邮箱或密码不正确，请重试。' }, 401)
  }
  if (path === '/auth/me') return json(fixtureUser)
  if (path === '/auth/password' && options.method === 'PUT') {
    fixtureUser.mustChangePassword = false
    return json({ accessToken: 'fixture-only', tokenType: 'Bearer', expiresInSeconds: 86400, user: fixtureUser })
  }
  if (path === '/admin/accounts' && !options.method) return json(adminAccounts)
  if (path === '/admin/invitations' && options.method === 'POST') {
    const email = JSON.parse(String(options.body)).email
    const account = { userId: null, invitationId: `invite-${++accountSequence}`, email, displayName: null, role: null, registered: false, enabled: false, mustChangePassword: false, invitedAt: now, invitationExpiresAt: invitationExpiry, invitationExpired: false, registeredAt: null }
    adminAccounts = [...adminAccounts, account]
    return json({ account, invitationToken: 'fixture-invitation-token-12345678901234567890', expiresAt: invitationExpiry }, 201)
  }
  if (path.endsWith('/rotate-token') && options.method === 'POST') {
    const invitationId = path.split('/')[3]
    const account = adminAccounts.find((item) => item.invitationId === invitationId)!
    account.invitationExpiresAt = invitationExpiry
    account.invitationExpired = false
    return json({ account, invitationToken: 'fixture-rotated-token-12345678901234567890', expiresAt: invitationExpiry })
  }
  if (path.startsWith('/admin/invitations/') && options.method === 'DELETE') {
    adminAccounts = adminAccounts.filter((account) => account.invitationId !== path.split('/')[3])
    return new Response(null, { status: 204 })
  }
  if (path.startsWith('/admin/accounts/')) {
    const userId = path.split('/')[3]
    if (path.endsWith('/workspace/account') && !options.method) {
      const account = adminAccounts.find((item) => item.userId === userId)
      return account ? json({ id: userId, email: account.email, displayName: account.displayName, role: account.role, enabled: account.enabled, registeredAt: account.registeredAt }) : json({ detail: '测试成员不存在' }, 404)
    }
    if (path.endsWith('/workspace') && !options.method) {
      const account = adminAccounts.find((item) => item.userId === userId)!
      return json({
        account: { id: userId, email: account.email, displayName: account.displayName, role: account.role, enabled: account.enabled, registeredAt: account.registeredAt },
        profile: { id: `profile-${userId}`, name: account.displayName, role: '内部协作者', bio: '仅用于验证管理员只读工作区布局。', avatarUrl: null, updatedAt: now },
        domains: [{ id: 'fixture-domain', name: '写作与思考', description: '', sortOrder: 0, createdAt: now, updatedAt: now }],
        tasks: Array.from({ length: adminDataCount }, (_, index) => ({ id: `workspace-task-${index + 1}`, title: `整理用户工作区 ${index + 1}`, done: index % 3 === 0, sortOrder: index, scheduledDate: null, dueAt: null, priority: index % 4 === 0 ? 'HIGH' : 'NORMAL', completedAt: null, archived: false, createdAt: now, updatedAt: now })),
        projects: Array.from({ length: adminDataCount }, (_, index) => ({ id: `workspace-project-${index + 1}`, name: `成员项目 ${index + 1}`, description: '用于验证成员项目列表搜索与分页。', techStack: ['Vue', 'TypeScript'], status: 'BUILDING', progress: (index * 7) % 100, nextAction: '继续整理资料', createdAt: now, updatedAt: now })),
        markdownDocuments: adminWorkspaceDocuments.map(({ content: _content, ...document }) => document),
        snippets: adminWorkspaceSnippets,
        logs: adminWorkspaceLogs,
      })
    }
    if (path.includes('/workspace/markdown-documents/') && !options.method) {
      const note = adminWorkspaceDocuments.find((item) => item.id === path.split('/').at(-1))
      return note ? json(note) : json({ detail: '测试文章不存在' }, 404)
    }
    if (path.endsWith('/status') && options.method === 'PATCH') {
      const enabled = JSON.parse(String(options.body)).enabled
      const account = adminAccounts.find((item) => item.userId === userId)!
      account.enabled = enabled
      return json(account)
    }
    if (path.endsWith('/reset-password') && options.method === 'POST') {
      const account = adminAccounts.find((item) => item.userId === userId)
      if (account) account.mustChangePassword = true
      return json({ temporaryPassword: 'R4ndom!Fixture-Password', expiresAt: new Date(Date.now() + 30 * 60 * 1000).toISOString() })
    }
    if (options.method === 'DELETE') {
      adminAccounts = adminAccounts.filter((account) => account.userId !== userId)
      return new Response(null, { status: 204 })
    }
  }
  if (path === '/markdown-documents' && options.method === 'POST') {
    saveRequests++; updateDiagnostics()
    if (failSave) return json({ detail: '测试：云端暂时不可用，请稍后重试。' }, 503)
    const saved = { ...documents[0], ...JSON.parse(String(options.body)), id: `fixture-${documents.length}`, updatedAt: new Date().toISOString() }
    documents.unshift(saved)
    return json(saved)
  }
  if (path === '/markdown-documents/import' && options.method === 'POST' && options.body instanceof FormData) {
    const imported = [...options.body.getAll('files')].map((entry, index) => {
      const file = entry as File
      return { id: `fixture-import-${index}`, title: file.name.replace(/\.(?:md|markdown|mdown|zip)$/i, ''), fileName: file.name, content: '# 隔离导入\n', domainId: String(options.body instanceof FormData ? options.body.get('domainId') ?? '' : '') || null, favorite: false, version: 0, createdAt: now, updatedAt: now }
    })
    documents.unshift(...imported)
    return json({ importedCount: imported.length, documents: imported, errors: [] })
  }
  if (path === '/markdown-documents/bulk-domain' && options.method === 'PATCH') {
    const request = JSON.parse(String(options.body))
    const moved = request.documents.map(({ id }: { id: string }) => documents.find((item) => item.id === id)).filter(Boolean)
    moved.forEach((document: typeof documents[number]) => { document.domainId = request.domainId; document.version += 1 })
    return json(moved)
  }
  if (path === '/knowledge-items/bulk-domain' && options.method === 'PATCH') {
    const request = JSON.parse(String(options.body)) as { items: Array<{ type: 'DOCUMENT' | 'SNIPPET' | 'LOG', id: string, expectedVersion?: number }>, domainId: string | null }
    const resolved = request.items.map((item) => {
      if (item.type === 'DOCUMENT') return { item, value: documents.find((document) => document.id === item.id) }
      if (item.type === 'SNIPPET') return { item, value: snippets.find((snippet) => snippet.id === item.id) }
      return { item, value: logs.find((entry) => entry.id === item.id) }
    })
    if (resolved.some(({ value }) => !value)) return json({ detail: '测试知识不存在' }, 404)
    const staleDocument = resolved.find(({ item, value }) => item.type === 'DOCUMENT' && item.expectedVersion !== (value as typeof documents[number]).version)
    if (staleDocument) return json({ detail: '测试文章版本冲突' }, 409)
    resolved.forEach(({ item, value }) => {
      value!.domainId = request.domainId
      if (item.type === 'DOCUMENT') (value as typeof documents[number]).version += 1
      value!.updatedAt = new Date().toISOString()
    })
    return new Response(null, { status: 204 })
  }
  if (path === '/markdown-documents/fixture-note/shares') {
    if (options.method === 'POST') {
      const token = `f${String(++shareSequence).padStart(42, 's')}`
      const share = { id: `fixture-share-${shareSequence}`, token, expiresAt: JSON.parse(String(options.body)).expiresAt, createdAt: new Date().toISOString(), revokedAt: null as string | null, active: true }
      markdownShares = [share, ...markdownShares]
      return json({ id: share.id, token: share.token, expiresAt: share.expiresAt, createdAt: share.createdAt }, 201)
    }
    return json(markdownShares.map(({ token: _token, ...share }) => share))
  }
  if (path.startsWith('/markdown-documents/fixture-note/shares/') && options.method === 'DELETE') {
    const share = markdownShares.find((item) => item.id === path.split('/')[4])
    if (!share) return json({ detail: '测试分享链接不存在' }, 404)
    share.active = false
    share.revokedAt = new Date().toISOString()
    return new Response(null, { status: 204 })
  }
  if (path === '/markdown-documents') return json(documents)
  if (path.includes('/revisions')) return failHistory ? json({ detail: '测试：历史版本加载失败，可刷新列表重试。' }, 503) : json([])
  if (path === '/markdown-documents/trash') return json([])
  if (path.startsWith('/markdown-documents/')) {
    const note = documents.find((item) => item.id === path.split('/')[2])
    if (options.method === 'PUT' && note) {
      saveRequests++; updateDiagnostics()
      if (failSave) return json({ detail: '测试：云端暂时不可用，请稍后重试。' }, 503)
      Object.assign(note, JSON.parse(String(options.body)), { version: note.version + 1, updatedAt: new Date().toISOString() })
    }
    return note ? json(note) : json({ detail: '测试文章不存在' }, 404)
  }
  if (path === '/domains' && options.method === 'POST') {
    const domain: KnowledgeDomain = { ...JSON.parse(String(options.body)), id: `fixture-domain-${++domainSequence}`, createdAt: now, updatedAt: now }
    domains.push(domain)
    return json(domain, 201)
  }
  if (path === '/domains') return json(domains)
  if (path.startsWith('/domains/')) {
    const index = domains.findIndex(domain => domain.id === path.split('/')[2])
    if (index < 0) return json({ detail: '测试目录不存在' }, 404)
    if (options.method === 'DELETE') {
      const id = domains[index]!.id
      domains.splice(index, 1)
      for (const item of [...documents, ...snippets, ...logs]) if (item.domainId === id) Object.assign(item, { domainId: null })
      return new Response(null, { status: 204 })
    }
    if (options.method === 'PUT') Object.assign(domains[index]!, JSON.parse(String(options.body)), { updatedAt: now })
    return json(domains[index])
  }
  if (path === '/profile') {
    if (options.method === 'PUT') Object.assign(profile, JSON.parse(String(options.body)))
    return json(profile)
  }
  if (path === '/projects') return scenario.get('fail-module') === 'projects'
    ? json({ detail: '测试：项目模块暂时不可用。' }, 503) : json(projects)
  if (path === '/snippets' && options.method === 'POST') {
    const snippet: CodeSnippet = { ...JSON.parse(String(options.body)), id: `fixture-snippet-${++knowledgeSequence}`, createdAt: now, updatedAt: now }
    snippets.unshift(snippet)
    return json(snippet, 201)
  }
  if (path === '/snippets') return json(snippets)
  if (path.startsWith('/snippets/')) {
    const index = snippets.findIndex((snippet) => snippet.id === path.split('/')[2])
    if (index < 0) return json({ detail: '测试代码片段不存在' }, 404)
    if (options.method === 'DELETE') { snippets.splice(index, 1); return new Response(null, { status: 204 }) }
    if (options.method === 'PUT') Object.assign(snippets[index]!, JSON.parse(String(options.body)), { updatedAt: new Date().toISOString() })
    return json(snippets[index])
  }
  if (path === '/logs' && options.method === 'POST') {
    const entry: DevLogEntry = { ...JSON.parse(String(options.body)), id: `fixture-log-${++knowledgeSequence}`, createdAt: now, updatedAt: now }
    logs.unshift(entry)
    return json(entry, 201)
  }
  if (path === '/logs') return json(logs)
  if (path.startsWith('/logs/')) {
    const index = logs.findIndex((entry) => entry.id === path.split('/')[2])
    if (index < 0) return json({ detail: '测试开发日志不存在' }, 404)
    if (options.method === 'DELETE') { logs.splice(index, 1); return new Response(null, { status: 204 }) }
    if (options.method === 'PUT') Object.assign(logs[index]!, JSON.parse(String(options.body)), { updatedAt: new Date().toISOString() })
    return json(logs[index])
  }
  if (path === '/tasks' && options.method === 'POST') {
    const task: DevTask = { ...JSON.parse(String(options.body)), id: `fixture-task-${++taskSequence}`, done: false, completedAt: null, archived: false, createdAt: now, updatedAt: now }
    tasks.push(task)
    return json(task, 201)
  }
  if (path === '/tasks') return json(tasks)
  if (path.startsWith('/tasks/')) {
    const index = tasks.findIndex((task) => task.id === path.split('/')[2])
    if (index < 0) return json({ detail: '测试任务不存在' }, 404)
    if (options.method === 'DELETE') { tasks.splice(index, 1); return new Response(null, { status: 204 }) }
    if (options.method === 'PUT') Object.assign(tasks[index]!, JSON.parse(String(options.body)))
    return json(tasks[index])
  }
  return json({ detail: '此接口未包含在隔离测试中' }, 404)
}

const pinia = createPinia()
const router = createRouter({ history: createMemoryHistory(), routes: productionRouter.options.routes })
const app = createApp(App).use(pinia).use(router)
const auth = useAuthStore(pinia)
useThemeStore(pinia).initialize()
installAuthGuards(router, pinia)
window.addEventListener('devnest:unauthorized', () => {
  unauthorizedEvents++
  updateDiagnostics()
  auth.clear()
  useWorkspaceStore(pinia).clear()
  void router.push({ name: 'login' })
})
await router.replace(scenario.has('public-share') ? `/share/markdown/${scenario.has('invalid-share') ? 'x'.repeat(43) : fixtureShareToken}` : scenario.has('force-password') ? '/change-password' : scenario.has('admin-workspace') ? '/admin/accounts/fixture-friend/workspace' : scenario.has('admin') ? '/admin/accounts' : scenario.has('tools') ? '/tools' : scenario.has('dashboard') ? '/' : '/knowledge')
app.mount('#app')

const controls = document.createElement('details')
controls.style.cssText = 'position:fixed;bottom:80px;right:8px;z-index:90;max-width:260px;max-height:65vh;overflow:auto;padding:6px 10px;background:var(--panel);color:var(--muted);border:1px solid var(--border);border-radius:8px;font-size:11px;'
const summary = document.createElement('summary')
summary.textContent = '隔离测试控制台'
controls.append(summary)
controls.append(diagnostics)
function button(label: string, action: () => void) {
  const node = document.createElement('button')
  node.textContent = label
  node.style.cssText = 'display:block;margin-top:8px;cursor:pointer;'
  node.onclick = action
  controls.append(node)
}
button('切换草稿存储故障', () => { failDrafts = !failDrafts; summary.textContent = `草稿故障：${failDrafts ? '开启' : '关闭'}` })
button('切换云端保存故障', () => { failSave = !failSave; summary.textContent = `保存故障：${failSave ? '开启' : '关闭'}` })
button('切换历史加载故障', () => { failHistory = !failHistory; summary.textContent = `历史故障：${failHistory ? '开启' : '关闭'}` })
button('切换图片上传故障', () => { failImages = !failImages; summary.textContent = `图片故障：${failImages ? '开启' : '关闭'}` })
button('暂停图片响应', () => { holdImages = true })
button('完成图片响应', () => { holdImages = false; imageReleases.splice(0).forEach((release) => release()) })
button('模拟粘贴测试图片', () => {
  const clipboard = new DataTransfer()
  clipboard.items.add(new File([sampleImage], 'clipboard-test.png', { type: 'image/png' }))
  document.querySelector('textarea[aria-label="Markdown 正文"]')?.dispatchEvent(new ClipboardEvent('paste', { bubbles: true, cancelable: true, clipboardData: clipboard }))
})
button('发送带详情的提示', () => useNotificationStore(pinia).notify('有 2 个文件未完成导入。', { type: 'warning', details: ['empty.md：空文件不会导入', 'large.md：单个文件不能超过 1 MB'] }))
button('正常登录朋友账户（模拟）', () => {
  auth.setWorkspaceMember(null)
  Object.assign(fixtureUser, { id: 'fixture-friend', email: 'friend@example.com', displayName: '朋友账户', role: 'USER' })
  auth.updateUser({ ...fixtureUser, role: 'USER' })
  useWorkspaceStore(pinia).clear()
  void router.push({ path: '/', query: {} })
})
button('恢复管理员登录（模拟）', () => {
  auth.setWorkspaceMember(null)
  Object.assign(fixtureUser, { id: 'ui-fixture', email: 'fixture@example.invalid', displayName: '界面回归测试', role: 'ADMIN' })
  auth.updateUser({ ...fixtureUser, role: 'ADMIN' })
  useWorkspaceStore(pinia).clear()
  void router.push('/admin/accounts')
})
button('打开登录页', () => { auth.clear(); useWorkspaceStore(pinia).clear(); void router.push('/login') })
document.body.append(controls)
window.addEventListener('keydown', (event) => {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') queueMicrotask(() => { savePrevented = event.defaultPrevented; updateDiagnostics() })
}, true)
