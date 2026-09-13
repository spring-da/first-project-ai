import type { FlowNodeKind } from '../types/flowcharts.ts'
import { isFlowchartShapeKind } from './flowchartEditor.ts'

export type FlowchartPanel = 'library' | 'inspector'

export interface FlowchartWorkbenchPreferences {
  libraryCollapsed: boolean
  inspectorCollapsed: boolean
  recentKinds: FlowNodeKind[]
}

export function defaultWorkbenchPreferences(): FlowchartWorkbenchPreferences {
  return { libraryCollapsed: false, inspectorCollapsed: false, recentKinds: [] }
}

export function toggleWorkbenchPanel(state: FlowchartWorkbenchPreferences, panel: FlowchartPanel): FlowchartWorkbenchPreferences {
  if (panel === 'library') return { ...state, libraryCollapsed: !state.libraryCollapsed }
  return { ...state, inspectorCollapsed: !state.inspectorCollapsed }
}

export function rememberRecentShape(state: FlowchartWorkbenchPreferences, kind: FlowNodeKind, limit = 5): FlowchartWorkbenchPreferences {
  const safeLimit = Number.isFinite(limit) && limit > 0 ? Math.floor(limit) : 0
  return { ...state, recentKinds: [kind, ...state.recentKinds.filter(item => item !== kind)].slice(0, safeLimit) }
}

export function visibleRecentKinds(kinds: readonly unknown[]): FlowNodeKind[] {
  const visible: FlowNodeKind[] = []
  for (const kind of kinds) {
    if (isFlowchartShapeKind(kind) && !visible.includes(kind)) visible.push(kind)
  }
  return visible
}

export function parseWorkbenchPreferences(value: unknown): FlowchartWorkbenchPreferences {
  let parsed: unknown = value
  if (typeof value === 'string') {
    try { parsed = JSON.parse(value) } catch { return defaultWorkbenchPreferences() }
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return defaultWorkbenchPreferences()
  const candidate = parsed as Record<string, unknown>
  return {
    libraryCollapsed: typeof candidate.libraryCollapsed === 'boolean' ? candidate.libraryCollapsed : false,
    inspectorCollapsed: typeof candidate.inspectorCollapsed === 'boolean' ? candidate.inspectorCollapsed : false,
    recentKinds: Array.isArray(candidate.recentKinds) ? visibleRecentKinds(candidate.recentKinds) : [],
  }
}
