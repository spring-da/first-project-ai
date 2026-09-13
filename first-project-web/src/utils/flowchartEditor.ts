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

export const FLOWCHART_SHAPES: FlowchartShapeItem[] = [
  { kind: 'terminal', label: '开始 / 结束', category: 'flow', hint: '流程起止节点' },
  { kind: 'rectangle', label: '处理步骤', category: 'flow', hint: '执行一个动作' },
  { kind: 'diamond', label: '条件判断', category: 'flow', hint: '分支与决策' },
  { kind: 'rounded', label: '圆角矩形', category: 'basic', hint: '通用容器' },
  { kind: 'io', label: '输入 / 输出', category: 'basic', hint: '数据输入或输出' },
  { kind: 'database', label: '数据库', category: 'data', hint: '数据存储' },
  { kind: 'text', label: '文本', category: 'annotation', hint: '说明文字' },
]

export function filterFlowchartShapes(query = '', category: FlowchartShapeCategory = 'all'): FlowchartShapeItem[] {
  const normalized = query.trim().toLocaleLowerCase()
  return FLOWCHART_SHAPES.filter(shape => (category === 'all' || shape.category === category) && (!normalized || `${shape.label} ${shape.hint}`.toLocaleLowerCase().includes(normalized)))
}
