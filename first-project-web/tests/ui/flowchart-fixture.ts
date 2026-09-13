import type { FlowchartDiagram, FlowchartDocument, FlowchartDraft, FlowchartRevisionDetail, FlowchartSummary, FlowNodeKind } from '../../src/types/flowcharts'
import { EDGE_STYLE, NODE_STYLE, validateDiagram } from '../../src/utils/flowcharts.ts'

/** Deterministic graph data only; rendering and editing always use production X6 components. */
export function fixtureDiagram(large = false): FlowchartDiagram {
  const kinds: FlowNodeKind[] = ['terminal', 'rectangle', 'rounded', 'diamond', 'io', 'database', 'text']
  const count = large ? 300 : 7
  return {
    schemaVersion: 1,
    nodes: Array.from({ length: count }, (_, index) => ({
      id: `node-${index + 1}`, kind: kinds[index % kinds.length]!, label: index === 0 ? '开始 · 合成流程' : index === 3 ? '审批通过？' : index === 6 ? '<script>这是纯文本</script>' : `步骤 ${index + 1}`,
      x: (index % (large ? 20 : 4)) * 210 + 40, y: Math.floor(index / (large ? 20 : 4)) * 150 + 40,
      width: 160, height: 80, zIndex: 1, style: { ...NODE_STYLE, ...(index % 7 === 6 ? { fill: 'transparent', strokeWidth: 0 } : {}) },
    })),
    edges: Array.from({ length: large ? 500 : 7 }, (_, index) => ({
      id: `edge-${index + 1}`, kind: index % 3 === 0 ? 'straight' : 'orthogonal',
      source: { nodeId: `node-${index % count + 1}`, port: 'right' },
      target: { nodeId: `node-${(index + (index >= count ? 21 : 1)) % count + 1}`, port: 'left' },
      label: index === 3 ? '批准' : index % 5 === 0 ? `连线 ${index + 1}` : '', vertices: [], zIndex: 0,
      sourceArrow: false, targetArrow: true, style: { ...EDGE_STYLE },
    })),
  }
}

export interface FlowchartFixtureSource { flowcharts: FlowchartSummary[]; flowchartBodies: Map<string, FlowchartDiagram> }
export function seedFlowcharts(count: number, prefix = 'fixture-flowchart', large = false): FlowchartFixtureSource {
  const now = new Date().toISOString(), flowchartBodies = new Map<string, FlowchartDiagram>()
  const flowcharts = Array.from({ length: count }, (_, index): FlowchartSummary => {
    const id = index ? `${prefix}-${index + 1}` : prefix, diagram = fixtureDiagram(large && index === 0)
    flowchartBodies.set(id, diagram)
    return { id, title: large && index === 0 ? '大型流程图 · 300 图形 / 500 连线' : `流程图回归示例 ${index + 1}`, domainId: 'fixture-domain', favorite: index % 5 === 0, createdAt: now, updatedAt: now, version: 0, deletedAt: null, nodeCount: diagram.nodes.length, edgeCount: diagram.edges.length, excerpt: searchText(diagram).slice(0, 240) }
  })
  return { flowcharts, flowchartBodies }
}
const searchText = (diagram: FlowchartDiagram) => [...diagram.nodes, ...diagram.edges].map(cell => cell.label).join('\n')
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })
const empty = () => new Response(null, { status: 204 })
type Failure = 'offline' | 'rate' | 'conflict'

export function createFlowchartFixture(options: {
  scenario: URLSearchParams; sources: (owner: string) => FlowchartFixtureSource | undefined;
  saveFailed: () => boolean; historyFailed: () => boolean; changed: () => void;
  invalidateShares: (owner: string, id: string) => void;
}) {
  const revisions = new Map<string, FlowchartRevisionDetail[]>(), creations = new Map<string, string>()
  const failures = { offline: options.scenario.has('flowchart-offline'), rate: options.scenario.has('flowchart-rate-limit'), conflict: options.scenario.has('flowchart-conflict') }
  const stats = { listReads: 0, detailReads: 0, saves: 0, conflicts: 0, historyReads: 0 }
  let sequence = 100, hold = options.scenario.has('flowchart-hold-save'), loseCreateResponse = options.scenario.has('flowchart-create-retry')
  const releases: Array<() => void> = []
  const key = (owner: string, id: string) => `${owner}:${id}`
  function detail(source: FlowchartFixtureSource, item: FlowchartSummary): FlowchartDocument { return { ...item, diagram: structuredClone(source.flowchartBodies.get(item.id)!) } }
  function checkpoint(owner: string, value: FlowchartDocument, action: string) {
    const id = key(owner, value.id), items = revisions.get(id) ?? []
    const draft = { title: value.title, domainId: value.domainId, favorite: value.favorite, diagram: value.diagram }
    const same = items[0] && JSON.stringify({ title: items[0].title, domainId: items[0].domainId, favorite: items[0].favorite, diagram: items[0].diagram }) === JSON.stringify(draft)
    if (same || action === 'AUTO' && items[0] && Date.now() - Date.parse(items[0].createdAt) < 300000) return
    items.unshift({ ...structuredClone(draft), id: `revision-${++sequence}`, documentVersion: value.version, action, createdAt: new Date().toISOString() })
    revisions.set(id, items.slice(0, 100))
  }
  function update(source: FlowchartFixtureSource, item: FlowchartSummary, draft: FlowchartDraft) {
    const diagram = validateDiagram(draft.diagram)
    Object.assign(item, { title: draft.title.trim(), domainId: draft.domainId, favorite: draft.favorite, version: item.version + 1, updatedAt: new Date().toISOString(), nodeCount: diagram.nodes.length, edgeCount: diagram.edges.length, excerpt: searchText(diagram).slice(0, 240) })
    source.flowchartBodies.set(item.id, structuredClone(diagram))
  }
  function remoteEdit(owner: string, id = 'fixture-flowchart') {
    const source = options.sources(owner), item = source?.flowcharts.find(value => value.id === id)
    if (!source || !item) return
    const value = detail(source, item); value.title = `${value.title} · 其他会话`; value.diagram.nodes[0]!.label = '其他会话已保存'
    update(source, item, value); checkpoint(owner, detail(source, item), 'MANUAL'); options.changed()
  }
  async function handle(path: string, request: RequestInit, owner: string): Promise<Response | null> {
    const url = new URL(path, 'http://fixture.invalid')
    if (!/^\/flowcharts(?:\/|$)/.test(url.pathname)) return null
    const source = options.sources(owner)
    if (!source) return json({ detail: '测试工作区不存在' }, 404)
    const parts = url.pathname.split('/').filter(Boolean), method = request.method ?? 'GET'
    const body = request.body ? JSON.parse(String(request.body)) : {}
    const bad = (text: string, status = 400) => json({ detail: text }, status)
    if (method === 'POST' && parts.length === 1 || method === 'PUT') {
      stats.saves++; options.changed()
      if (hold) await new Promise<void>((resolve, reject) => {
        const release = () => { request.signal?.removeEventListener('abort', abort); resolve() }
        const abort = () => { const i = releases.indexOf(release); if (i >= 0) releases.splice(i, 1); reject(request.signal?.reason ?? new DOMException('Aborted', 'AbortError')) }
        releases.push(release); request.signal?.addEventListener('abort', abort, { once: true }); if (request.signal?.aborted) abort()
      })
      if (failures.offline) throw new TypeError('测试：网络连接已断开')
      if (failures.rate) return bad('测试：请求过于频繁，请稍后重试。', 429)
      if (options.saveFailed()) return bad('测试：云端暂时不可用。', 503)
      try { validateDiagram(body.diagram); if (!String(body.title ?? '').trim() || body.title.trim().length > 200) throw new Error('流程图标题无效') } catch (error) { return bad((error as Error).message) }
    }
    if (parts.length === 1 && method === 'GET') {
      stats.listReads++; options.changed()
      const q = (url.searchParams.get('q') ?? '').trim().toLowerCase()
      return json(source.flowcharts.filter(item => !item.deletedAt && (!q || `${item.title}\n${searchText(source.flowchartBodies.get(item.id)!)}`.toLowerCase().includes(q))))
    }
    if (parts.length === 1 && method === 'POST') {
      const creationId = key(owner, body.creationKey), existing = source.flowcharts.find(item => item.id === creations.get(creationId))
      if (existing) return existing.deletedAt ? bad('测试：创建记录已移入回收站', 409) : json(detail(source, existing), 201)
      if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(body.creationKey ?? '')) return bad('测试：creationKey 必须是 UUID')
      const seeded = seedFlowcharts(1, `fixture-flowchart-${++sequence}`), item = seeded.flowcharts[0]!
      item.version = -1; source.flowcharts.unshift(item); update(source, item, body); creations.set(creationId, item.id)
      checkpoint(owner, detail(source, item), 'CREATE')
      if (loseCreateResponse) { loseCreateResponse = false; throw new TypeError('测试：已创建，但首次响应丢失') }
      return json(detail(source, item), 201)
    }
    if (parts[1] === 'trash') return json(source.flowcharts.filter(item => item.deletedAt))
    const item = source.flowcharts.find(item => item.id === parts[1])
    if (!item) return bad('测试流程图不存在', 404)
    if (parts[2] === 'restore' && method === 'POST') { item.deletedAt = null; item.version++; item.updatedAt = new Date().toISOString(); return json(detail(source, item)) }
    if (parts[2] === 'permanent' && method === 'DELETE') { source.flowcharts.splice(source.flowcharts.indexOf(item), 1); source.flowchartBodies.delete(item.id); options.invalidateShares(owner, item.id); return empty() }
    if (item.deletedAt) return bad('测试流程图已移入回收站', 404)
    if (!revisions.has(key(owner, item.id))) checkpoint(owner, detail(source, item), 'CREATE')
    if (parts[2] === 'revisions') {
      stats.historyReads++; options.changed()
      if (options.historyFailed()) return bad('测试：历史版本加载失败，可重试。', 503)
      const items = revisions.get(key(owner, item.id))!
      if (!parts[3]) { const page = Math.max(0, Number(url.searchParams.get('page')) || 0); return json(items.slice(page * 20, (page + 1) * 20).map(({ diagram: _diagram, domainId: _domainId, favorite: _favorite, ...summary }) => summary)) }
      const revision = items.find(value => value.id === parts[3])
      if (!revision) return bad('测试历史版本不存在', 404)
      if (parts[4] === 'restore' && method === 'POST') {
        if (body.expectedVersion !== item.version) return bad('测试：流程图版本冲突', 409)
        update(source, item, revision); checkpoint(owner, detail(source, item), 'RESTORE'); return json(detail(source, item))
      }
      return json(revision)
    }
    if (method === 'DELETE') { item.deletedAt = new Date().toISOString(); options.invalidateShares(owner, item.id); return empty() }
    if (method === 'PUT') {
      if (failures.conflict) { failures.conflict = false; remoteEdit(owner, item.id) }
      if (body.expectedVersion !== item.version) { stats.conflicts++; options.changed(); return bad('测试：流程图已被其他会话修改，请读取云端或另存副本。', 409) }
      update(source, item, body); checkpoint(owner, detail(source, item), body.saveMode ?? 'MANUAL')
    } else { stats.detailReads++; options.changed() }
    return json(detail(source, item))
  }
  return { handle, stats, remoteEdit, toggle(kind: Failure) { failures[kind] = !failures[kind]; return failures[kind] }, toggleHold() { hold = !hold; if (!hold) for (const release of releases.splice(0)) release(); return hold } }
}
