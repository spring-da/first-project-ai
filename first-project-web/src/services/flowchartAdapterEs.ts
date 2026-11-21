import type { EdgeProperties, Graph, NodeProperties } from '@antv/x6/es'
import type { FlowchartDiagram, FlowEdge, FlowNode } from '../types/flowcharts'
import {
  edgeToCell as legacyEdgeToCell,
  graphDiagram as legacyGraphDiagram,
  nodeToCell as legacyNodeToCell,
} from '../utils/flowchartAdapter'

export const nodeToCell = legacyNodeToCell as (node: FlowNode, readonly?: boolean) => NodeProperties
export const edgeToCell = legacyEdgeToCell as (edge: FlowEdge) => EdgeProperties
export const graphDiagram = (graph: Graph): FlowchartDiagram => legacyGraphDiagram(graph as never)
