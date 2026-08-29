import {
  TopologyActiveAttack,
  TopologyExternalEntity,
  TopologyGraph,
  TopologyLink,
  TopologyNode,
} from 'api/aiopsApi';
import { MarkerType, type Edge, type Node } from '@xyflow/react';

export type ComponentFlowNode = Node<{ node: TopologyNode }, 'component'>;
export type ExternalFlowNode = Node<{ entity: TopologyExternalEntity }, 'external'>;
export type TopologyFlowNode = ComponentFlowNode | ExternalFlowNode;

const LAYER_X = 300;
const ROW_Y = 160;

export const buildTopologyElements = (graph: TopologyGraph, animateAttacks = true) => {
  const incoming = new Map<string, number>();
  graph.nodes.forEach((node) => incoming.set(node.id, 0));
  graph.links.forEach((link) => {
    incoming.set(link.target, (incoming.get(link.target) ?? 0) + 1);
  });

  const layers = new Map<string, number>();
  const queue: string[] = [];
  graph.nodes.forEach((node) => {
    if ((incoming.get(node.id) ?? 0) === 0) {
      layers.set(node.id, graph.externalEntities.length > 0 ? 1 : 0);
      queue.push(node.id);
    }
  });
  const adjacency = new Map<string, string[]>();
  graph.links.forEach((link) => {
    const next = adjacency.get(link.source) ?? [];
    next.push(link.target);
    adjacency.set(link.source, next);
  });
  while (queue.length > 0) {
    const current = queue.shift() as string;
    const depth = layers.get(current) ?? 0;
    (adjacency.get(current) ?? []).forEach((target) => {
      const nextDepth = Math.max(layers.get(target) ?? 0, depth + 1);
      if (!layers.has(target) || nextDepth > (layers.get(target) ?? 0)) {
        layers.set(target, nextDepth);
        queue.push(target);
      }
    });
  }
  graph.nodes.forEach((node) => {
    if (!layers.has(node.id)) {
      layers.set(node.id, 1);
    }
  });

  const byLayer = new Map<number, string[]>();
  layers.forEach((layer, id) => {
    const list = byLayer.get(layer) ?? [];
    list.push(id);
    byLayer.set(layer, list);
  });

  const componentNodes: ComponentFlowNode[] = graph.nodes.map((node) => {
    const layer = layers.get(node.id) ?? 1;
    const siblings = byLayer.get(layer) ?? [];
    const index = siblings.indexOf(node.id);
    return {
      id: node.id,
      type: 'component',
      position: { x: layer * LAYER_X, y: index * ROW_Y },
      data: { node },
      draggable: true,
    };
  });

  const targetLayerIndex = new Map<string, number>();
  graph.activeAttacks.forEach((attack) => {
    const target = graph.nodes.find((node) => node.id === attack.target);
    if (target) {
      const layer = layers.get(target.id) ?? 1;
      const siblings = byLayer.get(layer) ?? [];
      targetLayerIndex.set(attack.source, siblings.indexOf(target.id));
    }
  });

  const externalNodes: ExternalFlowNode[] = graph.externalEntities.map((entity, index) => ({
    id: entity.id,
    type: 'external',
    position: {
      x: 0,
      y: (targetLayerIndex.get(entity.id) ?? index) * ROW_Y,
    },
    data: { entity },
    draggable: true,
  }));

  const infrastructureEdges: Edge[] = graph.links.map((link: TopologyLink) => ({
    id: link.id,
    source: link.source,
    target: link.target,
    type: 'smoothstep',
    style: { stroke: '#A1A7C4', strokeWidth: 1.5 },
    label: link.label || undefined,
    labelStyle: { fontSize: 11, fill: '#5A607F' },
    markerEnd: { type: MarkerType.ArrowClosed, color: '#A1A7C4', width: 16, height: 16 },
  }));

  const attackEdges: Edge[] = graph.activeAttacks.map((attack: TopologyActiveAttack, index) => ({
    id: `attack:${attack.incidentId}:${index}`,
    source: attack.source,
    target: attack.target,
    type: 'smoothstep',
    animated: animateAttacks,
    className: 'topology-attack-edge',
    style: { stroke: '#DC2626', strokeWidth: 2, strokeDasharray: '6 4' },
    label: `${attack.type} · ${attack.severity}`,
    labelStyle: { fontSize: 11, fill: '#DC2626', fontWeight: 600 },
    markerEnd: { type: MarkerType.ArrowClosed, color: '#DC2626', width: 18, height: 18 },
    data: { attack },
  }));

  return {
    nodes: [...externalNodes, ...componentNodes] as TopologyFlowNode[],
    edges: [...infrastructureEdges, ...attackEdges],
  };
};
