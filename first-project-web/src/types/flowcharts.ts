export type FlowNodeKind = 'terminal' | 'rectangle' | 'rounded' | 'diamond' | 'io' | 'database' | 'text'
export type FlowPort = 'top' | 'right' | 'bottom' | 'left'
export type FlowSaveMode = 'AUTO' | 'MANUAL'
export interface FlowPoint { x: number; y: number }
export interface FlowTextStyle { textColor: string; fontSize: number }
export interface FlowLineStyle extends FlowTextStyle { stroke: string; strokeWidth: number; dash: boolean }
export interface FlowNodeStyle extends FlowLineStyle { fill: string; textAlign: 'left' | 'center' | 'right' }
export interface FlowNode extends FlowPoint { id: string; kind: FlowNodeKind; label: string; width: number; height: number; zIndex: number; style: FlowNodeStyle }
export interface FlowEndpoint { nodeId: string; port: FlowPort }
export interface FlowEdge { id: string; kind: 'straight' | 'orthogonal'; source: FlowEndpoint; target: FlowEndpoint; label: string; vertices: FlowPoint[]; zIndex: number; sourceArrow: boolean; targetArrow: boolean; style: FlowLineStyle }
export interface FlowchartDiagram { schemaVersion: 1; nodes: FlowNode[]; edges: FlowEdge[] }
export interface FlowchartDraft { title: string; domainId: string | null; favorite: boolean; diagram: FlowchartDiagram }
export interface FlowchartSummary { id: string; title: string; domainId: string | null; favorite: boolean; createdAt: string; updatedAt: string; version: number; deletedAt: string | null; nodeCount: number; edgeCount: number; excerpt: string }
export interface FlowchartDocument extends FlowchartSummary { diagram: FlowchartDiagram }
export interface FlowchartCreate extends FlowchartDraft { creationKey: string }
export interface FlowchartUpdate extends FlowchartDraft { expectedVersion: number; saveMode: FlowSaveMode }
export interface FlowchartRevision { id: string; documentVersion: number; title: string; action: string; createdAt: string }
export interface FlowchartRevisionDetail extends FlowchartRevision, FlowchartDraft {}
