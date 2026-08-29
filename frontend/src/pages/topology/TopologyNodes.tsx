import { Box, Chip, Stack, Typography } from '@mui/material';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import { TopologySecurityState } from 'api/aiopsApi';
import IconifyIcon from 'components/base/IconifyIcon';
import StatusChip from 'components/aiops/StatusChip';
import { formatDeviceType } from 'helpers/aiops';
import { DeviceStatus } from 'types/aiops';
import { ComponentFlowNode, ExternalFlowNode } from './layout';
import { deviceTypeIcon } from './topologyIcons';

const securityLabel = (state: TopologySecurityState) => {
  switch (state) {
    case 'UNDER_ATTACK':
      return 'UNDER ATTACK';
    case 'TARGETED':
      return 'TARGETED';
    case 'SUSPICIOUS_ACTIVITY':
      return 'SECURITY EVENT';
    default:
      return null;
  }
};

const securityColor = (state: TopologySecurityState) => {
  if (state === 'UNDER_ATTACK' || state === 'TARGETED') {
    return 'error' as const;
  }
  if (state === 'SUSPICIOUS_ACTIVITY') {
    return 'warning' as const;
  }
  return 'default' as const;
};

const securityBorder = (state: TopologySecurityState, selected: boolean) => {
  if (selected) {
    return 'primary.main';
  }
  if (state === 'UNDER_ATTACK') {
    return 'error.main';
  }
  if (state === 'TARGETED') {
    return 'error.light';
  }
  if (state === 'SUSPICIOUS_ACTIVITY') {
    return 'warning.main';
  }
  return 'divider';
};

const nodeBorder = (
  securityState: TopologySecurityState,
  operationalStatus: DeviceStatus,
  selected: boolean,
) => {
  if (selected || securityState !== 'NORMAL') {
    return securityBorder(securityState, selected);
  }
  if (operationalStatus === 'DOWN') {
    return 'error.main';
  }
  if (operationalStatus === 'DEGRADED') {
    return 'warning.main';
  }
  return securityBorder(securityState, selected);
};

export const ComponentTopologyNode = ({ data, selected }: NodeProps<ComponentFlowNode>) => {
  const node = data.node;
  const badge = securityLabel(node.securityState);
  const monitoringStopped = node.monitoringEnabled === false;
  return (
    <>
      <Handle type="target" position={Position.Left} />
      <Box
        sx={{
          width: 236,
          px: 1.5,
          py: 1.25,
          borderRadius: 2,
          bgcolor: 'background.paper',
          border: '2px solid',
          borderColor: nodeBorder(node.securityState, node.operationalStatus, Boolean(selected)),
          borderStyle: monitoringStopped ? 'dashed' : 'solid',
          boxShadow: selected ? 4 : 1,
          opacity: monitoringStopped ? 0.72 : 1,
        }}
      >
        <Stack direction="row" spacing={1} alignItems="center">
          <Box
            sx={{
              width: 34,
              height: 34,
              borderRadius: 1,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              bgcolor: 'action.hover',
              color: 'text.secondary',
              flexShrink: 0,
            }}
          >
            <IconifyIcon icon={deviceTypeIcon(node.type)} fontSize="1.35rem" />
          </Box>
          <Box minWidth={0}>
            <Typography variant="subtitle2" fontWeight={700} noWrap>
              {node.name}
            </Typography>
            <Typography variant="caption" color="text.secondary" noWrap display="block">
              {formatDeviceType(node.type)}
              {node.ipAddress ? ` · ${node.ipAddress}` : ''}
            </Typography>
          </Box>
        </Stack>
        <Stack direction="row" spacing={0.5} mt={1} flexWrap="wrap" useFlexGap>
          <StatusChip status={node.operationalStatus} />
          {monitoringStopped ? (
            <Chip size="small" variant="outlined" label="Monitoring stopped" />
          ) : null}
          {badge ? (
            <Chip size="small" color={securityColor(node.securityState)} label={badge} />
          ) : null}
        </Stack>
      </Box>
      <Handle type="source" position={Position.Right} />
    </>
  );
};

export const ExternalTopologyNode = ({ data, selected }: NodeProps<ExternalFlowNode>) => (
  <>
    <Handle type="target" position={Position.Left} />
    <Box
      sx={{
        width: 220,
        px: 1.5,
        py: 1.25,
        borderRadius: 2,
        bgcolor: '#FFF7ED',
        border: '2px dashed',
        borderColor: selected ? 'warning.main' : '#F59E0B',
      }}
    >
      <Stack direction="row" spacing={1} alignItems="center">
        <IconifyIcon icon="mdi:account-alert" fontSize="1.4rem" color="#B45309" />
        <Box>
          <Typography variant="caption" color="warning.dark" fontWeight={700}>
            {data.entity.label}
          </Typography>
          <Typography variant="subtitle2" fontWeight={700}>
            {data.entity.ip}
          </Typography>
        </Box>
      </Stack>
    </Box>
    <Handle type="source" position={Position.Right} />
  </>
);
