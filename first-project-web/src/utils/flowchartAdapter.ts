import type { NodeProperties, EdgeProperties, Graph } from '@antv/x6'
import type { FlowchartDiagram, FlowNode, FlowEdge, FlowPort } from '../types/flowcharts.ts'
import { makeEdge } from './flowcharts.ts'

const ports = ['top', 'right', 'bottom', 'left'] as const
export function nodeToCell(node: FlowNode, readonly = false): NodeProperties {
  const style = node.style
  const shape = node.kind === 'diamond' || node.kind === 'io' ? 'polygon' : node.kind === 'database' ? 'path' : 'rect'
  return {
    id: node.id, shape, x: node.x, y: node.y, width: node.width, height: node.height, zIndex: node.zIndex,
    data: structuredClone(node),
    attrs: {
      body: { fill: style.fill, stroke: style.stroke, strokeWidth: style.strokeWidth, strokeDasharray: style.dash ? '6 4' : '',
        ...(node.kind === 'terminal' ? { rx: 32, ry: 32 } : node.kind === 'rounded' ? { rx: 12, ry: 12 } : {}),
        ...(node.kind === 'diamond' ? { refPoints: '0,50 50,0 100,50 50,100' } : node.kind === 'io' ? { refPoints: '15,0 100,0 85,60 0,60' } : {}),
        ...(node.kind === 'database' ? { refD: 'M0 10 C0 -3 160 -3 160 10 L160 54 C160 68 0 68 0 54 Z M0 10 C0 24 160 24 160 10' } : {}),
      },
      label: { text: node.label, fill: style.textColor, fontSize: style.fontSize, fontFamily: 'system-ui, "Microsoft YaHei", sans-serif',
        refY: '50%', textVerticalAnchor: 'middle', textAnchor: style.textAlign === 'left' ? 'start' : style.textAlign === 'right' ? 'end' : 'middle',
        refX: style.textAlign === 'left' ? 12 : style.textAlign === 'right' ? '100%' : '50%', refX2: style.textAlign === 'right' ? -12 : 0,
        textWrap: { width: -24, height: -8, ellipsis: true, breakWord: true },
      },
    },
    ports: { groups: Object.fromEntries(ports.map(port => [port, { position: port, attrs: { circle: { r: 4, magnet: !readonly, fill: '#ffffff', stroke: '#6366f1', strokeWidth: 1.5, opacity: readonly ? 0 : 1 } } }])), items: ports.map(port => ({ id: port, group: port })) },
  }
}
export function orthogonalRouter(edge: FlowEdge) {
  return {
    name: 'manhattan' as const,
    args: {
      padding: 12,
      step: 8,
      startDirections: [edge.source.port],
      endDirections: [edge.target.port],
      excludeTerminals: ['source', 'target'] as const,
    },
  }
}
export function edgeToCell(edge: FlowEdge): EdgeProperties {
  const s = edge.style
  return { id: edge.id, shape: 'edge', zIndex: edge.zIndex, data: structuredClone(edge),
    source: { cell: edge.source.nodeId, port: edge.source.port }, target: { cell: edge.target.nodeId, port: edge.target.port }, vertices: edge.vertices,
    router: edge.kind === 'orthogonal' ? orthogonalRouter(edge) : undefined,
    connector: edge.kind === 'orthogonal' ? { name: 'rounded', args: { radius: 8 } } : { name: 'normal' },
    attrs: { line: { stroke: s.stroke, strokeWidth: s.strokeWidth, strokeDasharray: s.dash ? '6 4' : '', sourceMarker: edge.sourceArrow ? { name: 'block', width: 8, height: 6 } : null, targetMarker: edge.targetArrow ? { name: 'block', width: 8, height: 6 } : null } },
    labels: edge.label ? [{ attrs: { label: { text: edge.label, fill: s.textColor, fontSize: s.fontSize, fontFamily: 'system-ui, "Microsoft YaHei", sans-serif' }, body: { fill: '#ffffff', stroke: 'none', rx: 3, ry: 3 } } }] : [],
  }
}
export function graphDiagram(graph: Graph): FlowchartDiagram {
  // X6 sorts cells by zIndex on reload. Serialize that same stable order after
  // undo re-inserts a cell, so re-opening does not create a spurious edit.
  const nodes = graph.getNodes().map(node => ({ ...structuredClone(node.getData<FlowNode>()), id: node.id, ...node.getPosition(), ...node.getSize(), zIndex: node.getZIndex() ?? 1 })).sort((a, b) => a.zIndex - b.zIndex)
  const ids = new Set(nodes.map(n => n.id))
  const edges = graph.getEdges().filter(edge => ids.has(edge.getSourceCellId()) && ids.has(edge.getTargetCellId())).map(edge => ({
    ...(structuredClone(edge.getData<FlowEdge>()) ?? makeEdge(edge.getSourceCellId(), edge.getTargetCellId())),
    id: edge.id, source: { nodeId: edge.getSourceCellId(), port: (edge.getSourcePortId() ?? 'right') as FlowPort }, target: { nodeId: edge.getTargetCellId(), port: (edge.getTargetPortId() ?? 'left') as FlowPort },
    vertices: edge.getVertices().map(p => ({ x: p.x, y: p.y })), zIndex: edge.getZIndex() ?? 0,
  })).sort((a, b) => a.zIndex - b.zIndex)
  return { schemaVersion: 1, nodes, edges }
}
