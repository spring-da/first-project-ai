import type { FlowNodeKind } from '../types/flowcharts.ts'

export type FlowchartShapeCategory = 'all' | 'flow' | 'basic' | 'data' | 'annotation'

export interface FlowchartShapeItem {
  kind: FlowNodeKind
  label: string
  category: Exclude<FlowchartShapeCategory, 'all'>
  hint: string
}

export type CanvasWheelIntent = 'pan' | 'zoom'
export type PropertyEditTrigger = 'single' | 'double' | 'context'
export interface CanvasWheelInput {
  ctrlKey?: boolean
  metaKey?: boolean
  deltaX?: number
  deltaY?: number
  deltaMode?: number
}

/** Keep gesture decisions DOM-free so the canvas can share them with tests. */
export function classifyCanvasWheel(input: Pick<CanvasWheelInput, 'ctrlKey' | 'metaKey'>): CanvasWheelIntent {
  return input.ctrlKey || input.metaKey ? 'zoom' : 'pan'
}

/** Convert WheelEvent deltas to CSS-pixel-like values without reading window. */
export function normalizeCanvasWheelDelta(input: CanvasWheelInput): { x: number; y: number } {
  const multiplier = input.deltaMode === 1 ? 16 : input.deltaMode === 2 ? 800 : 1
  return { x: (input.deltaX ?? 0) * multiplier, y: (input.deltaY ?? 0) * multiplier }
}

export function shouldOpenProperties(trigger: PropertyEditTrigger): boolean {
  return trigger === 'double' || trigger === 'context'
}

/**
 * Keep export-only UI transitions deterministic: editor overlays disappear
 * before serialization and are restored even when an encoder rejects.
 */
export async function runCanvasExportCycle<T>(setOverlayHidden: (hidden: boolean) => void, exportGraph: () => Promise<T>): Promise<T> {
  setOverlayHidden(true)
  try { return await exportGraph() }
  finally { setOverlayHidden(false) }
}

/**
 * Defend exported SVGs against an exporter that serializes surrounding editor
 * chrome. X6 normally returns graph-only markup, but the final artifact must
 * never include canvas controls or the workbench inspector.
 */
const FLOWCHART_EXPORT_OVERLAY_CLASSES = new Set([
  'flow-canvas__tools',
  'flow-canvas__context',
  'flow-canvas__edge-editor',
  'flow-canvas__mini',
  'flow-canvas__hint',
  'inspector-panel',
])

function isExportOverlayElement(token: string): boolean {
  const classValue = token.match(/\bclass\s*=\s*(["'])(.*?)\1/i)?.[2] ?? ''
  return classValue.split(/\s+/).some(name => FLOWCHART_EXPORT_OVERLAY_CLASSES.has(name))
}

/** Remove complete overlay subtrees from serialized SVG text before download. */
export function sanitizeFlowchartSvgMarkup(markup: string): string {
  let skippedDepth = 0
  const output: string[] = []
  for (const token of markup.match(/<!--[\s\S]*?-->|<[^>]*>|[^<]+/g) ?? []) {
    const isClosing = /^<\//.test(token)
    const isOpening = /^<[A-Za-z][^\s/>]*/.test(token)
    const isSelfClosing = /\/\s*>$/.test(token)
    if (skippedDepth) {
      if (isOpening && !isSelfClosing) skippedDepth++
      else if (isClosing) skippedDepth--
      continue
    }
    if (isOpening && isExportOverlayElement(token)) {
      if (!isSelfClosing) skippedDepth = 1
      continue
    }
    output.push(token)
  }
  return output.join('')
}

export const FLOWCHART_SHAPES: FlowchartShapeItem[] = [
  { kind: 'terminal', label: '开始 / 结束', category: 'flow', hint: '流程起止节点' },
  { kind: 'rectangle', label: '处理步骤', category: 'flow', hint: '执行一个动作' },
  { kind: 'diamond', label: '条件判断', category: 'flow', hint: '分支与决策' },
  { kind: 'rounded', label: '圆角矩形', category: 'basic', hint: '通用容器' },
  { kind: 'io', label: '输入 / 输出', category: 'basic', hint: '数据输入或输出' },
  { kind: 'database', label: '数据库', category: 'data', hint: '数据存储' },
  { kind: 'text', label: '文本', category: 'annotation', hint: '说明文字' },
]

export function isFlowchartShapeKind(value: unknown): value is FlowNodeKind {
  return FLOWCHART_SHAPES.some(shape => shape.kind === value)
}

export function filterFlowchartShapes(query = '', category: FlowchartShapeCategory = 'all'): FlowchartShapeItem[] {
  const normalized = query.trim().toLocaleLowerCase()
  return FLOWCHART_SHAPES.filter(shape => (category === 'all' || shape.category === category) && (!normalized || `${shape.label} ${shape.hint}`.toLocaleLowerCase().includes(normalized)))
}
