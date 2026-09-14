import type { FlowNodeKind } from '../types/flowcharts.ts'
import { isFlowchartShapeKind } from './flowchartEditor.ts'

export type FlowchartPanel = 'library' | 'inspector'

export interface FlowchartWorkbenchPreferences {
  libraryCollapsed: boolean
  inspectorCollapsed: boolean
  recentKinds: FlowNodeKind[]
}

export const FLOW_NODE_DRAG_TYPE = 'application/x-devnest-flow-node'

export interface NodeLibraryCanvas {
  add: (kind: FlowNodeKind) => void
}

export interface NodeLibraryController {
  readonly key: string
  readonly preferences: FlowchartWorkbenchPreferences
  create: (kind: FlowNodeKind) => void
  dragStart: (kind: FlowNodeKind, event: DragEvent) => void
  toggleLibrary: () => void
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

export function readNativeFlowNodeKind(transfer: Pick<DataTransfer, 'getData'> | null): FlowNodeKind | null {
  const kind = transfer?.getData(FLOW_NODE_DRAG_TYPE)
  return isFlowchartShapeKind(kind) ? kind : null
}

/** Keep node-library state local to one user/workspace and degrade gracefully without browser storage. */
export function createNodeLibraryController(ownerKey: string, canvas: () => NodeLibraryCanvas | undefined, storage?: Pick<Storage, 'getItem' | 'setItem'>): NodeLibraryController {
  const key = `devnest-flowchart-workbench:${ownerKey}`
  let preferences: FlowchartWorkbenchPreferences
  try { preferences = parseWorkbenchPreferences(storage?.getItem(key)) } catch { preferences = defaultWorkbenchPreferences() }
  const persist = () => { try { storage?.setItem(key, JSON.stringify(preferences)) } catch { /* Preferences are optional. */ } }
  const update = (next: FlowchartWorkbenchPreferences) => { preferences = next; persist() }
  return {
    key,
    get preferences() { return preferences },
    create(kind) { update(rememberRecentShape(preferences, kind)); canvas()?.add(kind) },
    dragStart(kind, event) { void event; update(rememberRecentShape(preferences, kind)) },
    toggleLibrary() { update(toggleWorkbenchPanel(preferences, 'library')) },
  }
}
