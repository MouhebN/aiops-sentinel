import { Box, Button, Chip, Drawer, Stack, Typography } from '@mui/material';
import { TopologyActiveAttack, TopologyExternalEntity, TopologyNode } from 'api/aiopsApi';
import { useAuth } from 'auth/AuthContext';
import { canUseAiAnalysis } from 'auth/permissions';
import IconActionButton from 'components/aiops/IconActionButton';
import SeverityChip from 'components/aiops/SeverityChip';
import StatusChip from 'components/aiops/StatusChip';
import IconifyIcon from 'components/base/IconifyIcon';
import { formatDeviceType } from 'helpers/aiops';
import dayjs from 'dayjs';
import type { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { deviceTypeIcon } from './topologyIcons';

interface TopologyDetailsDrawerProps {
  open: boolean;
  onClose: () => void;
  component: TopologyNode | null;
  external: TopologyExternalEntity | null;
  attacks: TopologyActiveAttack[];
}

const securityCopy = (state: TopologyNode['securityState']) => {
  switch (state) {
    case 'UNDER_ATTACK':
      return 'UNDER ATTACK';
    case 'TARGETED':
      return 'TARGETED';
    case 'SUSPICIOUS_ACTIVITY':
      return 'SECURITY EVENT';
    default:
      return 'NORMAL';
  }
};

const securityChipColor = (state: TopologyNode['securityState']) => {
  if (state === 'UNDER_ATTACK' || state === 'TARGETED') {
    return 'error' as const;
  }
  if (state === 'SUSPICIOUS_ACTIVITY') {
    return 'warning' as const;
  }
  return 'success' as const;
};

const DetailRow = ({ label, value }: { label: string; value: string }) => (
  <Stack spacing={0.25}>
    <Typography variant="caption" color="text.secondary">
      {label}
    </Typography>
    <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
      {value}
    </Typography>
  </Stack>
);

const Section = ({ title, children }: { title: string; children: ReactNode }) => (
  <Box
    sx={{
      p: 1.75,
      borderRadius: 1.5,
      border: '1px solid',
      borderColor: 'divider',
      bgcolor: 'grey.100',
    }}
  >
    <Typography variant="subtitle2" mb={1.25}>
      {title}
    </Typography>
    {children}
  </Box>
);

const TopologyDetailsDrawer = ({
  open,
  onClose,
  component,
  external,
  attacks,
}: TopologyDetailsDrawerProps) => {
  const navigate = useNavigate();
  const { user } = useAuth();
  const relatedAttacks = attacks.filter(
    (attack) =>
      Boolean(component && component.activeIncidentIds.includes(attack.incidentId)) ||
      Boolean(component && (attack.target === component.id || attack.source === component.id)) ||
      Boolean(external && attack.source === external.id),
  );
  const primaryAttack = relatedAttacks[0] ?? null;
  const isReporterOnly = Boolean(
    component && primaryAttack && primaryAttack.target !== component.id,
  );
  const evidence = primaryAttack?.evidence.length
    ? primaryAttack.evidence
    : component?.evidenceTypes ?? [];

  return (
    <Drawer
      anchor="right"
      variant="temporary"
      open={open}
      onClose={onClose}
      ModalProps={{
        keepMounted: false,
        BackdropProps: {
          sx: { backgroundColor: 'rgba(17, 24, 39, 0.4)' },
        },
      }}
      PaperProps={{
        sx: {
          width: { xs: '100%', sm: 420 },
          maxWidth: '100%',
          height: 1,
          whiteSpace: 'normal',
          bgcolor: 'common.white',
          backgroundColor: '#ffffff',
          backgroundImage: 'none',
          borderLeft: '1px solid',
          borderColor: 'divider',
          boxShadow: 8,
          overflow: 'hidden',
        },
      }}
    >
      <Stack sx={{ height: 1, bgcolor: 'common.white' }}>
        <Stack
          direction="row"
          alignItems="flex-start"
          spacing={1.5}
          sx={{
            px: 2.5,
            py: 2,
            borderBottom: '1px solid',
            borderColor: 'divider',
            bgcolor: 'common.white',
          }}
        >
          <Box
            sx={{
              width: 42,
              height: 42,
              borderRadius: 1.5,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0,
              bgcolor: external ? 'warning.lighter' : 'primary.lighter',
              color: external ? 'warning.dark' : 'primary.main',
            }}
          >
            <IconifyIcon
              icon={external ? 'mdi:account-alert' : deviceTypeIcon(component?.type ?? 'OTHER')}
              fontSize="1.45rem"
            />
          </Box>
          <Box minWidth={0} flex={1}>
            <Typography variant="h6" sx={{ lineHeight: 1.3, overflowWrap: 'anywhere' }}>
              {component?.name ?? external?.label}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {component
                ? `${formatDeviceType(component.type)}${component.location ? ` · ${component.location}` : ''}`
                : 'Not a monitored component'}
            </Typography>
          </Box>
          <IconActionButton icon="mdi:close" label="Close" onClick={onClose} />
        </Stack>

        <Box sx={{ px: 2.5, py: 2, overflow: 'auto', flex: 1, bgcolor: 'common.white' }}>
          {component ? (
            <Stack spacing={1.5}>
              <Section title="Component">
                <Stack spacing={1.25}>
                  <DetailRow label="Type" value={formatDeviceType(component.type)} />
                  <DetailRow label="IP" value={component.ipAddress || 'Not set'} />
                  <DetailRow label="Location" value={component.location || 'Not set'} />
                </Stack>
              </Section>

              <Section title="Operational">
                <Stack spacing={1} direction="row" alignItems="center" flexWrap="wrap" useFlexGap>
                  <StatusChip status={component.operationalStatus} />
                  <Chip
                    size="small"
                    variant={component.monitoringEnabled === false ? 'outlined' : 'filled'}
                    color={component.monitoringEnabled === false ? 'default' : 'success'}
                    label={
                      component.monitoringEnabled === false
                        ? 'Monitoring stopped'
                        : 'Monitoring started'
                    }
                  />
                  <Typography variant="body2" color="text.secondary">
                    {component.lastCheckedAt
                      ? `Last check ${dayjs(component.lastCheckedAt).format('DD/MM/YYYY HH:mm:ss')}`
                      : 'No check recorded yet'}
                  </Typography>
                </Stack>
              </Section>

              <Section title="Security">
                <Stack spacing={1.25}>
                  <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
                    <Chip
                      size="small"
                      color={securityChipColor(component.securityState)}
                      label={securityCopy(component.securityState)}
                    />
                    {component.highestIncidentSeverity ? (
                      <SeverityChip severity={component.highestIncidentSeverity} />
                    ) : null}
                  </Stack>
                  <Typography variant="body2" color="text.secondary">
                    {component.activeIncidentCount} active security incident
                    {component.activeIncidentCount === 1 ? '' : 's'}
                  </Typography>
                  {isReporterOnly ? (
                    <Typography variant="body2" color="text.secondary">
                      This component reported the incident. The destination of the traffic is
                      another monitored component.
                    </Typography>
                  ) : null}
                  {primaryAttack ? (
                    <Stack spacing={0.75}>
                      <Typography variant="body2" fontWeight={600}>
                        {primaryAttack.title}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Type {primaryAttack.type}
                        {primaryAttack.sourceIp ? ` · Source ${primaryAttack.sourceIp}` : ''}
                        {primaryAttack.blocked ? ' · Firewall blocking evidenced' : ''}
                      </Typography>
                      {evidence.length > 0 ? (
                        <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
                          {evidence.map((item) => (
                            <Chip key={item} size="small" variant="outlined" label={item} />
                          ))}
                        </Stack>
                      ) : null}
                    </Stack>
                  ) : (
                    <Typography variant="body2" color="text.secondary">
                      No active security incident on this component.
                    </Typography>
                  )}
                </Stack>
              </Section>
            </Stack>
          ) : external ? (
            <Stack spacing={1.5}>
              <Section title="External source">
                <Stack spacing={1.25}>
                  <DetailRow label="IP" value={external.ip} />
                  <Typography variant="body2" color="text.secondary">
                    This address is not a monitored component. Sentinel does not create a component
                    record for attacker IPs.
                  </Typography>
                </Stack>
              </Section>
              {primaryAttack ? (
                <Section title="Related incident">
                  <Stack spacing={0.75}>
                    <Typography variant="body2" fontWeight={600}>
                      {primaryAttack.title}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      Type {primaryAttack.type}
                      {primaryAttack.blocked ? ' · Firewall blocking evidenced' : ''}
                    </Typography>
                    {primaryAttack.evidence.length > 0 ? (
                      <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
                        {primaryAttack.evidence.map((item) => (
                          <Chip key={item} size="small" variant="outlined" label={item} />
                        ))}
                      </Stack>
                    ) : null}
                  </Stack>
                </Section>
              ) : null}
            </Stack>
          ) : null}
        </Box>

        <Stack
          spacing={1}
          sx={{
            px: 2.5,
            py: 2,
            borderTop: '1px solid',
            borderColor: 'divider',
            bgcolor: 'common.white',
          }}
        >
          {component ? (
            <Button
              fullWidth
              variant="contained"
              startIcon={<IconifyIcon icon="mdi:server" />}
              onClick={() => navigate(`/components/${component.componentId}`)}
            >
              View component
            </Button>
          ) : null}
          {primaryAttack ? (
            <Button
              fullWidth
              variant="outlined"
              startIcon={<IconifyIcon icon="mdi:alert-circle-outline" />}
              onClick={() => navigate(`/incidents/${primaryAttack.incidentId}`)}
            >
              View incident
            </Button>
          ) : null}
          {primaryAttack && canUseAiAnalysis(user?.role) ? (
            <Button
              fullWidth
              variant="outlined"
              startIcon={<IconifyIcon icon="mdi:robot" />}
              onClick={() => navigate(`/ai-analysis?incidentId=${primaryAttack.incidentId}`)}
            >
              Analyze with AI
            </Button>
          ) : null}
        </Stack>
      </Stack>
    </Drawer>
  );
};

export default TopologyDetailsDrawer;
