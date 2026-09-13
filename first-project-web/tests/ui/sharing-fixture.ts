import type { CodeSnippet } from '../../src/types'
import type { FlowchartDiagram, FlowchartSummary } from '../../src/types/flowcharts'
import type { ShareBundleItem, ShareBundleSummary, SharedContent, SharedPoolEntry, ShareReference } from '../../src/types/sharing'

export const fixtureBundleToken = 'b'.repeat(43)
export const fixtureSharingImageId = '11111111-2222-4333-8444-555555555555'
type FixtureSources = {
  documents: Array<{ id: string; title: string; content: string; updatedAt: string }>
  snippets: CodeSnippet[]
  flowcharts: FlowchartSummary[]
  flowchartBodies: Map<string, FlowchartDiagram>
}
type PoolRecord = { id: string; ownerId: string; reference: ShareReference; sharedAt: string; withdrawn: boolean }
type LinkRecord = {
  id: string; ownerId: string; token: string; title: string; expiresAt: string; createdAt: string; revokedAt: string | null
  items: Array<{ id: string; reference: ShareReference; removedAt: string | null }>
}
type FailureKind = 'list' | 'content' | 'images'
type FixtureOptions = {
  scenario: URLSearchParams
  sources: (ownerId: string) => FixtureSources | undefined
  owner: (ownerId: string) => { name: string; enabled: boolean } | undefined
  image: (path: string, signal?: AbortSignal | null) => Promise<Response>
  changed?: () => void
}
const response = (payload: unknown, status = 200) => new Response(JSON.stringify(payload), {
  status, headers: { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' },
})
const noContent = () => new Response(null, { status: 204, headers: { 'Cache-Control': 'no-store' } })
const failure = (detail = '测试：分享内容不存在、已过期或已停止分享。', status = 404) => response({ detail }, status)

/** One in-memory server for pool, link management and public readers. Never uses fetch. */
export function createSharingFixture(options: FixtureOptions) {
  let sequence = 30
  let currentPublicToken = fixtureBundleToken
  let lastCreatedToken = fixtureBundleToken
  const now = new Date().toISOString()
  const pool: PoolRecord[] = []
  const links: LinkRecord[] = []
  const synthetic = new Map<string, SharedContent>()
  const failures: Record<FailureKind, boolean> = { list: options.scenario.has('sharing-error'), content: false, images: false }
  const stats = { poolReads: 0, directoryReads: 0, contentReads: 0, imageReads: 0, writes: 0, publicAuthorization: 0 }
  const resourceKey = (ownerId: string, reference: ShareReference) => `${ownerId}:${reference.type}:${reference.id}`
  const ownerEnabled = (ownerId: string) => ownerId.startsWith('sharing-member-') || !!options.owner(ownerId)?.enabled
  function content(ownerId: string, reference: ShareReference): SharedContent | null {
    if (!ownerEnabled(ownerId)) return null
    const source = options.sources(ownerId)
    const extra = synthetic.get(resourceKey(ownerId, reference))
    if (extra) return { ...extra, tags: [...extra.tags] }
    if (reference.type === 'MARKDOWN') {
      const item = source?.documents.find((entry) => entry.id === reference.id)
      return item ? { id: item.id, resourceType: 'MARKDOWN', title: item.title, content: item.content, diagram: null, language: null, category: null, tags: [], updatedAt: item.updatedAt } : null
    }
    if (reference.type === 'SNIPPET') {
      const item = source?.snippets.find((entry) => entry.id === reference.id)
      return item ? { id: item.id, resourceType: 'SNIPPET', title: item.title, content: item.code, diagram: null, language: item.language, category: null, tags: [], updatedAt: item.updatedAt } : null
    }
    const item = source?.flowcharts.find((entry) => entry.id === reference.id && !entry.deletedAt)
    const diagram = item ? source?.flowchartBodies.get(item.id) : undefined
    return item && diagram ? { id: item.id, resourceType: 'FLOWCHART', title: item.title, content: [...diagram.nodes, ...diagram.edges].map(cell => cell.label).join('\n'), diagram: structuredClone(diagram), language: null, category: null, tags: [], updatedAt: item.updatedAt } : null
  }
  function references(ownerId: string): ShareReference[] {
    const source = options.sources(ownerId)
    if (!source) return []
    return [
      ...source.documents.slice(0, 3).map((entry): ShareReference => ({ type: 'MARKDOWN', id: entry.id })),
      ...source.snippets.slice(0, 3).map((entry): ShareReference => ({ type: 'SNIPPET', id: entry.id })),
      ...source.flowcharts.filter(entry => !entry.deletedAt).slice(0, 3).map((entry): ShareReference => ({ type: 'FLOWCHART', id: entry.id })),
    ]
  }
  function addPool(ownerId: string, reference: ShareReference, index = pool.length) {
    pool.push({ id: `fixture-pool-${++sequence}`, ownerId, reference, sharedAt: new Date(Date.now() - index * 60000).toISOString(), withdrawn: false })
  }
  if (!options.scenario.has('empty-pool')) {
    for (const ownerId of ['ui-fixture', 'fixture-friend']) for (const reference of references(ownerId)) addPool(ownerId, reference)
    // Enough distinct synthetic entries to exercise pagination, long titles and mixed types.
    const seed = references('ui-fixture')
    for (let index = 0; index < 21 && seed.length; index++) {
      const original = content('ui-fixture', seed[index % seed.length]!)!
      const ownerId = `sharing-member-${index % 5 + 1}`
      const item = { ...original, id: `sharing-source-${index + 1}`, title: `${['Vue 异步请求与状态管理', '团队知识整理实践', '日常问题排查记录'][index % 3]} ${index + 1}${index === 5 ? ' · 用于验证超长中英文标题和 responsive-layout-with-unbroken-identifiers' : ''}` }
      const reference = { type: item.resourceType, id: item.id }
      synthetic.set(resourceKey(ownerId, reference), item)
      addPool(ownerId, reference)
    }
  }
  function addLink(ownerId: string, title: string, selected: ShareReference[], expiresAt: string, token: string) {
    const record: LinkRecord = {
      id: `fixture-bundle-${++sequence}`, ownerId, token, title, expiresAt, createdAt: new Date().toISOString(), revokedAt: null,
      items: selected.map((reference) => ({ id: `fixture-bundle-item-${++sequence}`, reference, removedAt: null })),
    }
    links.unshift(record)
    return record
  }
  const expirySeconds = Math.max(1, Math.min(3600, Number(options.scenario.get('bundle-expiry-seconds')) || 0))
  const expiry = options.scenario.has('expired-bundle') ? Date.now() - 1000
    : options.scenario.has('bundle-expiry-seconds') ? Date.now() + expirySeconds * 1000 : Date.now() + 7 * 86400000
  addLink('ui-fixture', '开发知识精选 · 文章、代码与记录', references('ui-fixture'), new Date(expiry).toISOString(), fixtureBundleToken)
  if (!options.scenario.has('empty-links')) {
    const revoked = addLink('ui-fixture', '已撤销的项目交流资料', references('ui-fixture').slice(0, 2), new Date(Date.now() + 86400000).toISOString(), 'r'.repeat(43))
    revoked.revokedAt = now
    addLink('ui-fixture', '已到期的上周记录', references('ui-fixture').slice(0, 1), new Date(Date.now() - 86400000).toISOString(), 'e'.repeat(43))
  }
  if (options.scenario.has('empty-links')) links.splice(0)

  function availableItems(link: LinkRecord) { return link.items.filter((item) => !item.removedAt && content(link.ownerId, item.reference)) }
  function accessible(link: LinkRecord | undefined): link is LinkRecord {
    return !!link && !link.revokedAt && new Date(link.expiresAt).getTime() > Date.now() && ownerEnabled(link.ownerId) && availableItems(link).length > 0
  }
  function summary(link: LinkRecord): ShareBundleSummary {
    return { id: link.id, title: link.title, expiresAt: link.expiresAt, createdAt: link.createdAt, revokedAt: link.revokedAt, active: accessible(link), itemCount: availableItems(link).length }
  }
  function detail(link: LinkRecord): ShareBundleItem[] {
    return link.items.map((item) => ({ id: item.id, resourceId: item.reference.id, resourceType: item.reference.type,
      title: content(link.ownerId, item.reference)?.title ?? '原内容已删除', removedAt: item.removedAt, available: !item.removedAt && !!content(link.ownerId, item.reference) }))
  }
  function page<T>(items: T[], query: URLSearchParams) {
    const page = Math.max(0, Number(query.get('page')) || 0), size = Math.max(1, Math.min(100, Number(query.get('size')) || 20))
    return { items: items.slice(page * size, (page + 1) * size), total: items.length, page, size }
  }
  function validatedReferences(ownerId: string, value: unknown): ShareReference[] | null {
    if (!Array.isArray(value) || !value.length || value.length > 100) return null
    const selected = value as ShareReference[]
    if (selected.some((item) => !item || !['MARKDOWN', 'SNIPPET', 'FLOWCHART'].includes(item.type) || !content(ownerId, item))) return null
    return [...new Map(selected.map((item) => [`${item.type}:${item.id}`, { type: item.type, id: item.id }])).values()]
  }
  async function wait(signal?: AbortSignal | null) {
    signal?.throwIfAborted()
    const milliseconds = Math.max(0, Math.min(5000, Number(options.scenario.get('sharing-delay')) || 0))
    if (!milliseconds) return
    await new Promise<void>((resolve, reject) => {
      const onAbort = () => { clearTimeout(timer); reject(signal?.reason ?? new DOMException('Aborted', 'AbortError')) }
      const timer = setTimeout(() => { signal?.removeEventListener('abort', onAbort); resolve() }, milliseconds)
      signal?.addEventListener('abort', onAbort, { once: true })
    })
  }
  async function handle(path: string, request: RequestInit, ownerId: string): Promise<Response | null> {
    const [pathname = '', queryText = ''] = path.split('?')
    // New sharing dialogs retain read/revoke access to existing single-item links.
    if (/^\/(snippets)\/[^/]+\/shares(?:\/[^/]+)?$/.test(pathname)) return request.method === 'DELETE' ? noContent() : response([])
    if (!pathname.startsWith('/sharing/') && !pathname.startsWith('/public/share-bundles/')) return null
    await wait(request.signal)
    request.signal?.throwIfAborted()
    const parts = pathname.split('/').filter(Boolean).map(decodeURIComponent)
    const query = new URLSearchParams(queryText), method = request.method ?? 'GET'
    const publicRead = parts[0] === 'public'
    if (publicRead && (new Headers(request.headers).has('Authorization') || new Headers(request.headers).has('X-Workspace-Owner'))) stats.publicAuthorization++
    if (method !== 'GET') stats.writes++
    options.changed?.()
    if (publicRead) {
      const link = links.find((entry) => entry.token === parts[2])
      currentPublicToken = parts[2] ?? fixtureBundleToken
      if (!accessible(link)) return failure()
      if (parts.length === 3) {
        stats.directoryReads++; options.changed?.()
        if (failures.list) return failure('测试：分享目录暂时无法加载，请重试。', 503)
        return response({ title: link.title, expiresAt: link.expiresAt, items: detail(link).filter((item) => item.available).map(({ id, resourceType, title }) => ({ id, resourceType, title })) })
      }
      const item = link.items.find((entry) => entry.id === parts[4] && !entry.removedAt)
      const value = item ? content(link.ownerId, item.reference) : null
      if (!item || !value) return failure()
      if (parts[5] === 'images') {
        stats.imageReads++; options.changed?.()
        if (failures.images) return failure('测试：图片暂时无法加载。', 503)
        if (value.resourceType !== 'MARKDOWN' || !value.content.includes(`/api/v1/markdown-images/${parts[6]}`)) return failure()
        const result = await options.image(path, request.signal)
        request.signal?.throwIfAborted()
        result.headers.set('Cache-Control', 'no-store')
        return accessible(link) && !item.removedAt ? result : failure()
      }
      stats.contentReads++; options.changed?.()
      return failures.content ? failure('测试：正文暂时无法加载，请重试。', 503) : response(value)
    }
    if (!new Headers(request.headers).has('Authorization')) return failure('测试：请先登录。', 401)
    if (!ownerEnabled(ownerId)) return failure('测试：账号不可用。', 403)
    const body = request.body ? JSON.parse(String(request.body)) : {}
    if (parts[1] === 'pool') {
      if (parts.length === 2 && method === 'GET') {
        stats.poolReads++; options.changed?.()
        if (failures.list) return failure('测试：知识广场暂时无法加载，请重试。', 503)
        const queryText = (query.get('q') ?? '').trim().toLocaleLowerCase()
        const entries = pool.flatMap((entry): SharedPoolEntry[] => {
          const value = content(entry.ownerId, entry.reference)
          if (entry.withdrawn || !value) return []
          const authorName = options.owner(entry.ownerId)?.name ?? `知识伙伴 ${entry.ownerId.split('-').at(-1)}`
          if (query.get('mine') === 'true' && entry.ownerId !== ownerId) return []
          if (query.get('type') && value.resourceType !== query.get('type')) return []
          if (queryText && !`${value.title}\n${value.content}\n${authorName}`.toLocaleLowerCase().includes(queryText)) return []
          return [{ id: entry.id, resourceType: value.resourceType, title: value.title, excerpt: value.content.replace(/[#>*`]/g, '').slice(0, 160), authorName, sharedAt: entry.sharedAt, updatedAt: value.updatedAt, mine: entry.ownerId === ownerId }]
        })
        return response(page(entries.sort((left, right) => right.sharedAt.localeCompare(left.sharedAt)), query))
      }
      if (parts.length === 2 && method === 'POST') {
        const selected = validatedReferences(ownerId, body.items)
        if (!selected) return failure('测试：请选择 1–100 项属于当前成员的内容。', 400)
        let publishedCount = 0, existingCount = 0
        for (const reference of selected) {
          const existing = pool.find((entry) => entry.ownerId === ownerId && !entry.withdrawn && resourceKey(ownerId, entry.reference) === resourceKey(ownerId, reference))
          if (existing) existingCount++
          else { addPool(ownerId, reference, 0); publishedCount++ }
        }
        return response({ publishedCount, existingCount })
      }
      if (parts[2] === 'revoke' && method === 'POST') {
        const ids = body.ids as string[]
        if (!Array.isArray(ids) || ids.some((id) => pool.some((entry) => entry.id === id && entry.ownerId !== ownerId))) return failure('测试：只能撤下自己的分享。', 403)
        for (const entry of pool) if (ids.includes(entry.id)) entry.withdrawn = true
        return noContent()
      }
      const entry = pool.find((item) => item.id === parts[2] && !item.withdrawn)
      const value = entry ? content(entry.ownerId, entry.reference) : null
      if (!value) return failure()
      if (parts[3] === 'images') {
        stats.imageReads++; options.changed?.()
        if (failures.images) return failure('测试：图片暂时无法加载。', 503)
        if (value.resourceType !== 'MARKDOWN' || !value.content.includes(`/api/v1/markdown-images/${parts[4]}`)) return failure()
        const result = await options.image(path, request.signal)
        request.signal?.throwIfAborted()
        result.headers.set('Cache-Control', 'no-store')
        return entry && !entry.withdrawn && content(entry.ownerId, entry.reference) ? result : failure()
      }
      stats.contentReads++; options.changed?.()
      return failures.content ? failure('测试：正文暂时无法加载，请重试。', 503) : response(value)
    }
    if (parts[1] === 'links') {
      if (parts.length === 2 && method === 'GET') {
        if (failures.list) return failure('测试：外链列表暂时无法加载，请重试。', 503)
        const queryText = (query.get('q') ?? '').trim().toLocaleLowerCase()
        return response(page(links.filter((entry) => entry.ownerId === ownerId && (!queryText || entry.title.toLocaleLowerCase().includes(queryText))).map(summary), query))
      }
      if (parts.length === 2 && method === 'POST') {
        const selected = validatedReferences(ownerId, body.items)
        const expiresAt = new Date(body.expiresAt).getTime()
        if (!selected || !String(body.title ?? '').trim() || !Number.isFinite(expiresAt) || expiresAt <= Date.now()) return failure('测试：分享信息无效。', 400)
        const token = `f${String(++sequence).padStart(42, '0')}`
        const link = addLink(ownerId, body.title, selected, body.expiresAt, token)
        lastCreatedToken = token
        return response({ id: link.id, token, title: link.title, expiresAt: link.expiresAt, createdAt: link.createdAt }, 201)
      }
      if (parts[2] === 'revoke' && method === 'POST') {
        const ids = body.ids as string[]
        if (!Array.isArray(ids) || ids.some((id) => links.some((entry) => entry.id === id && entry.ownerId !== ownerId))) return failure('测试：只能撤销自己的外链。', 403)
        for (const link of links) if (ids.includes(link.id)) link.revokedAt = new Date().toISOString()
        return noContent()
      }
      const link = links.find((entry) => entry.id === parts[2] && entry.ownerId === ownerId)
      if (!link) return failure()
      if (parts[3] === 'items' && method === 'DELETE') {
        const item = link.items.find((entry) => entry.id === parts[4])
        if (item) item.removedAt = new Date().toISOString()
        return noContent()
      }
      if (method === 'DELETE') { link.revokedAt = new Date().toISOString(); return noContent() }
      return response({ link: summary(link), items: detail(link) })
    }
    return failure('测试：未实现的分享接口。')
  }
  return {
    handle, stats,
    invalidateResource(ownerId: string, reference: ShareReference) {
      for (const entry of pool) if (resourceKey(entry.ownerId, entry.reference) === resourceKey(ownerId, reference)) entry.withdrawn = true
      for (const link of links) if (link.ownerId === ownerId) for (const item of link.items) if (resourceKey(ownerId, item.reference) === resourceKey(ownerId, reference)) item.removedAt = new Date().toISOString()
    },
    get latestToken() { return lastCreatedToken },
    toggleFailure(kind: FailureKind) { failures[kind] = !failures[kind]; return failures[kind] },
    revokeCurrent() { const link = links.find((entry) => entry.token === currentPublicToken); if (link) link.revokedAt = new Date().toISOString() },
    removeCurrentItem() { const link = links.find((entry) => entry.token === currentPublicToken); const item = link?.items.find((entry) => !entry.removedAt); if (item) item.removedAt = new Date().toISOString() },
    expireCurrentIn(seconds: number) { const link = links.find((entry) => entry.token === currentPublicToken); if (link) link.expiresAt = new Date(Date.now() + seconds * 1000).toISOString() },
  }
}
