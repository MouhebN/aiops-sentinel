import { Alert, Box, Paper, Stack, Typography } from '@mui/material';
import {
  Background,
  Controls,
  MiniMap,
  ReactFlow,
  ReactFlowProvider,
  useEdgesState,
  useNodesState,
  useReactFlow,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import './topology.css';
import {
  aiopsApi,
  TopologyActiveAttack,
  TopologyExternalEntity,
  TopologyGraph,
  TopologyNode,
} from 'api/aiopsApi';
import { useAuth } from 'auth/AuthContext';
import { canManageTopology } from 'auth/permissions';
import ApiState from 'components/aiops/ApiState';
import IconActionButton from 'components/aiops/IconActionButton';
import PageHeader from 'components/common/PageHeader';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { buildTopologyElements, TopologyFlowNode } from './layout';
import ManageConnectionsDialog from './ManageConnectionsDialog';
import TopologyDetailsDrawer from './TopologyDetailsDrawer';
import TopologyLegend from './TopologyLegend';
import { ComponentTopologyNode, ExternalTopologyNode } from './TopologyNodes';

const nodeTypes = {
  component: ComponentTopologyNode,
  external: ExternalTopologyNode,
};

const prefersReducedMotion = () =>
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

const TopologyCanvas = ({
  graph,
  onSelectComponent,
  onSelectExternal,
}: {
  graph: TopologyGraph;
  onSelectComponent: (node: TopologyNode) => void;
  onSelectExternal: (entity: TopologyExternalEntity) => void;
}) => {
  const { fitView } = useReactFlow();
  const elements = useMemo(() => buildTopologyElements(graph, !prefersReducedMotion()), [graph]);
  const [nodes, setNodes, onNodesChange] = useNodesState(elements.nodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(elements.edges);

  useEffect(() => {
    setNodes(elements.nodes);
    setEdges(elements.edges);
    const timer = window.setTimeout(() => fitView({ padding: 0.2 }), 50);
    return () => window.clearTimeout(timer);
  }, [elements, fitView, setEdges, setNodes]);

  return (
    <ReactFlow
      nodes={nodes}
      edges={edges}
      onNodesChange={onNodesChange}
      onEdgesChange={onEdgesChange}
      nodeTypes={nodeTypes}
      fitView
      panOnScroll
      nodesConnectable={false}
      elementsSelectable
      minZoom={0.35}
      maxZoom={1.8}
      proOptions={{ hideAttribution: false }}
      onNodeClick={(_, node) => {
        const typed = node as TopologyFlowNode;
        if (typed.type === 'component') {
          onSelectComponent(typed.data.node);
        } else {
          onSelectExternal(typed.data.entity);
        }
      }}
    >
      <Background />
      <Controls showInteractive={false} />
      <MiniMap pannable zoomable />
    </ReactFlow>
  );
};

const TopologyPage = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [graph, setGraph] = useState<TopologyGraph | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [manageOpen, setManageOpen] = useState(false);
  const [selectedComponent, setSelectedComponent] = useState<TopologyNode | null>(null);
  const [selectedExternal, setSelectedExternal] = useState<TopologyExternalEntity | null>(null);

  const refresh = useCallback(async () => {
    try {
      setError(null);
      const next = await aiopsApi.getTopology();
      setGraph(next);
      setSelectedComponent((current) =>
        current ? next.nodes.find((node) => node.id === current.id) ?? current : null,
      );
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Could not load topology');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
    const interval = window.setInterval(() => void refresh(), 15000);
    return () => window.clearInterval(interval);
  }, [refresh]);

  const attacks: TopologyActiveAttack[] = graph?.activeAttacks ?? [];

  return (
    <Stack spacing={2}>
      <PageHeader
        actions={
          <Stack direction="row" spacing={1}>
            <IconActionButton icon="mdi:refresh" label="Refresh" onClick={() => void refresh()} />
            {canManageTopology(user?.role) ? (
              <IconActionButton
                icon="mdi:vector-link"
                label="Manage connections"
                color="primary"
                onClick={() => setManageOpen(true)}
              />
            ) : null}
          </Stack>
        }
      >
        Infrastructure map
      </PageHeader>
      <Typography variant="body2" color="text.secondary">
        Operational status and security state are shown separately. A service can stay UP while it
        is under attack.
      </Typography>
      <ApiState loading={loading && !graph} error={error} onRetry={() => void refresh()} />
      {graph && graph.nodes.length === 0 ? (
        <Alert
          severity="info"
          action={
            <IconActionButton
              icon="mdi:plus"
              label="Add components"
              onClick={() => navigate('/components')}
            />
          }
        >
          No monitored components yet. Add components first, then connect them on this map.
        </Alert>
      ) : null}
      {graph && graph.nodes.length > 0 && graph.links.length === 0 ? (
        <Alert
          severity="info"
          action={
            canManageTopology(user?.role) ? (
              <IconActionButton
                icon="mdi:vector-link"
                label="Manage connections"
                onClick={() => setManageOpen(true)}
              />
            ) : null
          }
        >
          Components are monitored, but no topology connections have been defined yet.
        </Alert>
      ) : null}
      {graph && graph.nodes.length > 0 ? (
        <Paper
          elevation={0}
          sx={{
            height: { xs: 520, md: 640 },
            overflow: 'hidden',
            borderRadius: 2,
            border: '1px solid',
            borderColor: 'divider',
            position: 'relative',
          }}
        >
          <Box sx={{ height: 1, width: 1 }}>
            <ReactFlowProvider>
              <TopologyCanvas
                graph={graph}
                onSelectComponent={(node) => {
                  setSelectedExternal(null);
                  setSelectedComponent(node);
                }}
                onSelectExternal={(entity) => {
                  setSelectedComponent(null);
                  setSelectedExternal(entity);
                }}
              />
            </ReactFlowProvider>
          </Box>
          <TopologyLegend />
        </Paper>
      ) : null}
      <TopologyDetailsDrawer
        open={Boolean(selectedComponent || selectedExternal)}
        onClose={() => {
          setSelectedComponent(null);
          setSelectedExternal(null);
        }}
        component={selectedComponent}
        external={selectedExternal}
        attacks={attacks}
      />
      <ManageConnectionsDialog
        open={manageOpen}
        onClose={() => setManageOpen(false)}
        onChanged={() => void refresh()}
      />
    </Stack>
  );
};

export default TopologyPage;
