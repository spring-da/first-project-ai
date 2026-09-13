import { createClientId } from './clientId.ts'
import type { FlowchartDiagram, FlowNode, FlowEdge, FlowNodeKind, FlowchartDraft } from '../types/flowcharts.ts'

export const MAX_DIAGRAM_BYTES = 5 * 1024 * 1024
export const NODE_LABELS: Record<FlowNodeKind, string> = { terminal: '开始 / 结束', rectangle: '处理步骤', rounded: '圆角矩形', diamond: '条件判断', io: '输入 / 输出', database: '数据库', text: '文本' }
export const NODE_STYLE = { fill: '#eef2ff', stroke: '#6366f1', strokeWidth: 1.5, dash: false, textColor: '#1e293b', fontSize: 14, textAlign: 'center' as const }
export const EDGE_STYLE = { stroke: '#64748b', strokeWidth: 1.5, dash: false, textColor: '#334155', fontSize: 13 }
export const emptyDiagram = (): FlowchartDiagram => ({ schemaVersion: 1, nodes: [], edges: [] })
export const copyDraft = (value: FlowchartDraft): FlowchartDraft => JSON.parse(JSON.stringify({ title: value.title, domainId: value.domainId, favorite: value.favorite, diagram: value.diagram }))
// Object key order is not document content. The API canonicalizes JSON keys.
export function flowchartFingerprint(value: unknown): string {
  return JSON.stringify(value, (_key, item) => item && typeof item === 'object' && !Array.isArray(item)
    ? Object.fromEntries(Object.keys(item).sort().map(key => [key, item[key]])) : item)
}
export function makeNode(kind: FlowNodeKind, x: number, y: number, label = NODE_LABELS[kind]): FlowNode {
  return { id: createClientId(), kind, label, x, y, width: kind === 'diamond' ? 140 : 160, height: kind === 'diamond' ? 100 : 64, zIndex: 1, style: { ...NODE_STYLE, ...(kind === 'text' ? { fill: 'transparent', strokeWidth: 0 } : {}) } }
}
export function makeEdge(source: string, target: string): FlowEdge {
  return { id: createClientId(), kind: 'orthogonal', source: { nodeId: source, port: 'right' }, target: { nodeId: target, port: 'left' }, label: '', vertices: [], zIndex: 0, sourceArrow: false, targetArrow: true, style: { ...EDGE_STYLE } }
}
function invalid(message = '流程图文件格式无效。'): never { throw new Error(message) }
function object(value: unknown, keys: string[]): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return invalid()
  const v = value as Record<string, unknown>
  if (Object.keys(v).length !== keys.length || keys.some(key => !Object.hasOwn(v, key)) || Object.keys(v).some(key => !keys.includes(key))) return invalid('流程图包含缺失或不支持的属性。')
  return v
}
function numeric(value: unknown, min: number, max: number, integer = false) {
  if (typeof value !== 'number' || !Number.isFinite(value) || value < min || value > max || (integer && !Number.isInteger(value))) invalid('流程图的坐标、尺寸或样式数值超出范围。')
}
function text(value: unknown, max: number) { if (typeof value !== 'string' || value.length > max) invalid('流程图文字超出限制。') }
function oneOf(value: unknown, choices: readonly unknown[]) { if (!choices.includes(value)) invalid('流程图包含不支持的类型。') }
function bool(value: unknown) { if (typeof value !== 'boolean') invalid() }
function point(value: unknown) { const p = object(value, ['x', 'y']); numeric(p.x, -1e6, 1e6); numeric(p.y, -1e6, 1e6) }
function color(value: unknown, transparent = false) { if (!(typeof value === 'string' && (/^#[a-f\d]{6}([a-f\d]{2})?$/i.test(value) || transparent && value === 'transparent'))) invalid('流程图颜色格式无效。') }
function style(value: unknown, node: boolean) {
  const s = object(value, ['stroke', 'strokeWidth', 'dash', 'textColor', 'fontSize', ...(node ? ['fill', 'textAlign'] : [])])
  color(s.stroke); color(s.textColor); numeric(s.strokeWidth, 0, 12); numeric(s.fontSize, 8, 96); bool(s.dash)
  if (node) { color(s.fill, true); oneOf(s.textAlign, ['left', 'center', 'right']) }
}
export function validateDiagram(value: unknown): FlowchartDiagram {
  const d = object(value, ['schemaVersion', 'nodes', 'edges'])
  if (d.schemaVersion !== 1) invalid('当前版本无法打开此流程图，请使用版本 1 的源文件。')
  if (!Array.isArray(d.nodes) || !Array.isArray(d.edges) || d.nodes.length > 1000 || d.edges.length > 2000) invalid('单图最多支持 1,000 个节点和 2,000 条连线。')
  const ids = new Set<string>(), nodeIds = new Set<string>()
  const id = (v: unknown) => { if (typeof v !== 'string' || !/^[A-Za-z0-9_-]{1,80}$/.test(v) || ids.has(v)) invalid('流程图包含无效或重复的标识。'); ids.add(v as string) }
  for (const raw of d.nodes as unknown[]) {
    const n = object(raw, ['id', 'kind', 'label', 'x', 'y', 'width', 'height', 'zIndex', 'style'])
    id(n.id); nodeIds.add(n.id as string); oneOf(n.kind, Object.keys(NODE_LABELS)); text(n.label, 10000)
    numeric(n.x, -1e6, 1e6); numeric(n.y, -1e6, 1e6); numeric(n.width, 10, 10000); numeric(n.height, 10, 10000); numeric(n.zIndex, -10000, 10000, true); style(n.style, true)
  }
  for (const raw of d.edges as unknown[]) {
    const e = object(raw, ['id', 'kind', 'source', 'target', 'label', 'vertices', 'zIndex', 'sourceArrow', 'targetArrow', 'style'])
    id(e.id); oneOf(e.kind, ['straight', 'orthogonal']); text(e.label, 10000); numeric(e.zIndex, -10000, 10000, true); bool(e.sourceArrow); bool(e.targetArrow); style(e.style, false)
    for (const endpoint of [e.source, e.target]) { const p = object(endpoint, ['nodeId', 'port']); if (!nodeIds.has(p.nodeId as string)) invalid('连线引用了不存在的图形。'); oneOf(p.port, ['top', 'right', 'bottom', 'left']) }
    if (!Array.isArray(e.vertices) || e.vertices.length > 100) invalid('单条连线的折点过多。')
    ;(e.vertices as unknown[]).forEach(point)
  }
  if (new TextEncoder().encode(JSON.stringify(value)).length > MAX_DIAGRAM_BYTES) invalid('单个流程图不能超过 5 MiB。')
  return JSON.parse(JSON.stringify(value)) as FlowchartDiagram
}
export function exportFlowchartSource(title: string, diagram: FlowchartDiagram) {
  return JSON.stringify({ format: 'devnest-flowchart', schemaVersion: 1, title: title.trim() || '未命名流程图', diagram: validateDiagram(diagram) })
}
export function importFlowchartSource(source: string): { title: string; diagram: FlowchartDiagram } {
  if (new TextEncoder().encode(source).length > MAX_DIAGRAM_BYTES + 1024) invalid('源文件不能超过 5 MiB。')
  let parsed: unknown
  try { parsed = JSON.parse(source) } catch { return invalid('源文件不是有效的 JSON。') }
  const v = object(parsed, ['format', 'schemaVersion', 'title', 'diagram'])
  if (v.format !== 'devnest-flowchart' || v.schemaVersion !== 1) invalid('请选择本项目导出的 .flowchart.json 文件。')
  text(v.title, 200)
  return { title: (v.title as string).trim() || '导入的流程图', diagram: validateDiagram(v.diagram) }
}
export function createTemplate(kind: 'sequence' | 'decision' | 'architecture'): FlowchartDiagram {
  const nodes = kind === 'architecture'
    ? [makeNode('rounded', 80, 160, '前端应用'), makeNode('rectangle', 360, 160, '后端服务'), makeNode('database', 640, 160, '数据库')]
    : kind === 'decision'
      ? [makeNode('terminal', 60, 160, '开始'), makeNode('diamond', 320, 142, '条件满足？'), makeNode('rectangle', 580, 60, '执行处理'), makeNode('terminal', 580, 270, '结束')]
      : [makeNode('terminal', 60, 160, '开始'), makeNode('rectangle', 330, 160, '处理步骤'), makeNode('terminal', 600, 160, '结束')]
  const edges = [makeEdge(nodes[0]!.id, nodes[1]!.id), makeEdge(nodes[1]!.id, nodes[2]!.id)]
  if (kind === 'decision') { edges[1]!.label = '是'; const no = makeEdge(nodes[1]!.id, nodes[3]!.id); no.label = '否'; no.source.port = 'bottom'; edges.push(no) }
  return { schemaVersion: 1, nodes, edges }
}
export function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob), a = document.createElement('a')
  a.href = url; a.download = filename.replace(/[<>:"/\\|?*\x00-\x1f]/g, '_'); a.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
