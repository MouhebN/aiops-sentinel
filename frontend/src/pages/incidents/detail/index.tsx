import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Divider,
  Grid,
  Paper,
  Stack,
  Typography,
} from '@mui/material';
import { DataGrid, GridColDef } from '@mui/x-data-grid';
import {
  aiopsApi,
  Incident,
  IncidentComponentMatch,
  MetricSample,
  MonitoredComponent,
  NetworkFlow,
  PacketCaptureAnalysis,
} from 'api/aiopsApi';
import { useAuth } from 'auth/AuthContext';
import {
  canAcknowledgeIncidents,
  canManageMaintenance,
  canResolveIncidents,
  canUploadPacketCaptures,
  canUseAiAnalysis,
} from 'auth/permissions';
import ApiState from 'components/aiops/ApiState';
import EventDetailsDialog from 'components/aiops/EventDetailsDialog';
import IconActionButton from 'components/aiops/IconActionButton';
import PcapSizeMetric, {
  CAPTURED_PACKET_BYTES_HINT,
  capturedPacketBytesOf,
  formatByteCount,
  PCAP_FILE_SIZE_HINT,
} from 'components/aiops/PcapSizeMetric';
import SeverityChip from 'components/aiops/SeverityChip';
import StatusChip from 'components/aiops/StatusChip';
import PageHeader from 'components/common/PageHeader';
import {
  formatDeviceType,
  formatEventType,
  incidentStatusColor,
  isIncidentResolved,
} from 'helpers/aiops';
import { EventLog } from 'types/aiops';
import dayjs from 'dayjs';
import { ChangeEvent, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import PacketCapturePanel from 'pages/incidents/detail/PacketCapturePanel';

const PACKET_CAPTURE_EVENT_TYPES = [
  'FIREWALL_DENY',
  'PORT_SCAN',
  'POSSIBLE_PORT_SCAN',
  'AUTH_FAILURE',
  'SUSPICIOUS',
];

const PACKET_CAPTURE_DETAIL_KEYS = [
  'sourceAddress',
  'destinationAddress',
  'destinationPort',
] as const;

const relationLabel = (relation: string) => {
  if (relation === 'TARGET') {
    return 'Target';
  }
  if (relation === 'SOURCE') {
    return 'Source';
  }
  if (relation === 'ASSOCIATED') {
    return 'Associated';
  }
  return relation;
};

const extractDetailValue = (details: string | null | undefined, key: string) => {
  if (!details) {
    return null;
  }
  const matcher = new RegExp(`${key}=([^;]+)`, 'i').exec(details);
  return matcher?.[1]?.trim() || null;
};

const extractProtocolValue = (details: string | null | undefined) => {
  const protocol = extractDetailValue(details, 'protocol')?.toLowerCase();
  if (protocol === 'tcp' || protocol === 'udp' || protocol === 'icmp') {
    return protocol;
  }
  return null;
};

const hasPacketCaptureDetailHints = (details: string | null | undefined) =>
  PACKET_CAPTURE_DETAIL_KEYS.some((key) => Boolean(extractDetailValue(details, key)));

const buildSuggestedPacketCaptureFilter = (
  sourceAddress: string | null,
  destinationAddress: string | null,
) => {
  if (sourceAddress && destinationAddress) {
    if (sourceAddress.toLowerCase() === destinationAddress.toLowerCase()) {
      return `host ${sourceAddress} and tcp`;
    }
    return `host ${sourceAddress} and host ${destinationAddress} and tcp`;
  }
  if (sourceAddress) {
    return `host ${sourceAddress} and tcp`;
  }
  if (destinationAddress) {
    return `host ${destinationAddress} and tcp`;
  }
  return 'tcp';
};

const formatDuration = (minutes: number) => {
  if (minutes < 1) {
    return '< 1 min';
  }
  if (minutes < 60) {
    return `${minutes} min`;
  }
  const hours = Math.floor(minutes / 60);
  const remainingMinutes = minutes % 60;
  return remainingMinutes ? `${hours}h ${remainingMinutes}m` : `${hours}h`;
};

const DetailItem = ({ label, value }: { label: string; value: string }) => (
  <Stack spacing={0.25}>
    <Typography variant="caption" color="text.secondary">
      {label}
    </Typography>
    <Typography sx={{ overflowWrap: 'anywhere' }}>{value}</Typography>
  </Stack>
);

const IncidentDetailPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const incidentId = Number(id);
  const canAcknowledge = canAcknowledgeIncidents(user?.role);
  const canResolve = canResolveIncidents(user?.role);
  const canAnalyze = canUseAiAnalysis(user?.role);
  const canUploadPcap = canUploadPacketCaptures(user?.role);
  const canManageAdminMaintenance = canManageMaintenance(user?.role);
  const [incident, setIncident] = useState<Incident | null>(null);
  const [selectedEvent, setSelectedEvent] = useState<EventLog | null>(null);
  const [relatedComponents, setRelatedComponents] = useState<IncidentComponentMatch[]>([]);
  const [componentsById, setComponentsById] = useState<Record<number, MonitoredComponent>>({});
  const [metrics, setMetrics] = useState<MetricSample[]>([]);
  const [packetCaptures, setPacketCaptures] = useState<PacketCaptureAnalysis[]>([]);
  const [networkFlows, setNetworkFlows] = useState<NetworkFlow[]>([]);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);
  const [uploadingPcap, setUploadingPcap] = useState(false);
  const [deletingPcapId, setDeletingPcapId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [copiedCommandKey, setCopiedCommandKey] = useState<string | null>(null);
  const [resolveDialogOpen, setResolveDialogOpen] = useState(false);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const refresh = useCallback(async () => {
    if (!incidentId) {
      setError('Invalid incident id.');
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      setError(null);
      const [incidentResponse, packetCaptureResponse, networkFlowResponse] = await Promise.all([
        aiopsApi.getIncident(incidentId),
        aiopsApi.getIncidentPacketCaptures(incidentId),
        aiopsApi.getIncidentNetworkFlows(incidentId),
      ]);
      setIncident(incidentResponse);
      setPacketCaptures(packetCaptureResponse);
      setNetworkFlows(networkFlowResponse.filter((flow) => flow.incidentId === incidentId));

      const related = incidentResponse.relatedComponents ?? [];
      setRelatedComponents(related);
      if (related.length) {
        const loaded = await Promise.all(
          related.map(async (match) => {
            const [componentResponse, metricResponse] = await Promise.all([
              aiopsApi.getComponent(match.id),
              aiopsApi.getComponentMetrics(match.id, 24),
            ]);
            return { componentResponse, metricResponse };
          }),
        );
        setComponentsById(
          Object.fromEntries(
            loaded.map((item) => [item.componentResponse.id, item.componentResponse]),
          ),
        );
        setMetrics(loaded.flatMap((item) => item.metricResponse));
      } else {
        setComponentsById({});
        setMetrics([]);
      }
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Unknown incident error');
    } finally {
      setLoading(false);
    }
  }, [incidentId]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const latestMetrics = useMemo(() => {
    const latest = new Map<string, MetricSample>();
    metrics.forEach((sample) => {
      const current = latest.get(sample.metricName);
      if (!current || dayjs(sample.sampledAt).isAfter(current.sampledAt)) {
        latest.set(sample.metricName, sample);
      }
    });
    return Array.from(latest.values()).slice(0, 10);
  }, [metrics]);

  const packetCaptureRecommendation = useMemo(() => {
    if (!incident) {
      return null;
    }

    const relevantEvents = incident.relatedEvents.filter((event) => {
      if (incident.category === 'SECURITY') {
        return true;
      }
      if (PACKET_CAPTURE_EVENT_TYPES.includes(event.eventType)) {
        return true;
      }
      return hasPacketCaptureDetailHints(event.details);
    });

    if (!relevantEvents.length) {
      return null;
    }

    const firstSourceAddress =
      relevantEvents
        .map((event) => extractDetailValue(event.details, 'sourceAddress'))
        .find(Boolean) || null;
    const firstDestinationAddress =
      relevantEvents
        .map((event) => extractDetailValue(event.details, 'destinationAddress'))
        .find(Boolean) || null;
    const firstProtocol =
      relevantEvents.map((event) => extractProtocolValue(event.details)).find(Boolean) || null;
    const destinationPorts = Array.from(
      new Set(
        relevantEvents
          .map((event) => extractDetailValue(event.details, 'destinationPort'))
          .filter((value): value is string => Boolean(value)),
      ),
    );

    const protocol = (firstProtocol || 'tcp').toLowerCase();
    const suggestedFilter = buildSuggestedPacketCaptureFilter(
      firstSourceAddress,
      firstDestinationAddress,
    );
    const linuxCommand = `sudo tcpdump -i any -w incident-${incident.id}.pcap "${suggestedFilter}"`;
    const windowsListInterfacesCommand = 'dumpcap -D';
    const windowsDumpcapCommand = `dumpcap -i <interface_number> -f "${suggestedFilter}" -w incident-${incident.id}.pcap`;
    const windowsTsharkCommand = `tshark -i <interface_number> -f "${suggestedFilter}" -w incident-${incident.id}.pcap`;

    return {
      reason:
        'Security-related events were detected. A packet capture can help confirm network behavior.',
      durationSeconds: 60,
      sourceAddress: firstSourceAddress,
      destinationAddress: firstDestinationAddress,
      destinationPorts,
      protocol: protocol.toUpperCase(),
      suggestedFilter,
      linuxCommand,
      windowsListInterfacesCommand,
      windowsDumpcapCommand,
      windowsTsharkCommand,
    };
  }, [incident]);

  const handleToggleAcknowledged = async () => {
    if (!incident) {
      return;
    }

    try {
      setActionLoading(true);
      const updated = incident.acknowledged
        ? await aiopsApi.unacknowledgeIncident(incident.id)
        : await aiopsApi.acknowledgeIncident(incident.id);
      setIncident(updated);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Unknown acknowledge error');
    } finally {
      setActionLoading(false);
    }
  };

  const handleAnalyze = () => {
    if (!incident) {
      return;
    }
    navigate(`/ai-analysis?eventId=${incident.latestEvent.id}&incidentId=${incident.id}`);
  };

  const handleResolveIncident = async () => {
    if (!incident) {
      return;
    }
    try {
      setActionLoading(true);
      setError(null);
      setIncident(await aiopsApi.resolveIncident(incident.id));
      setResolveDialogOpen(false);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Resolve incident failed');
    } finally {
      setActionLoading(false);
    }
  };

  const handleDeleteIncident = async () => {
    if (!incident) {
      return;
    }
    if (!window.confirm('Delete this incident? This action cannot be undone.')) {
      return;
    }
    try {
      setActionLoading(true);
      setError(null);
      await aiopsApi.deleteIncident(incident.id);
      navigate('/incidents');
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Delete incident failed');
    } finally {
      setActionLoading(false);
    }
  };

  const handleUploadButtonClick = () => {
    fileInputRef.current?.click();
  };

  const handleCopyCommand = async (command: string, commandKey: string) => {
    if (!command) {
      return;
    }
    try {
      await navigator.clipboard.writeText(command);
      setCopiedCommandKey(commandKey);
      window.setTimeout(() => {
        setCopiedCommandKey((current) => (current === commandKey ? null : current));
      }, 2000);
    } catch (copyError) {
      setError(
        copyError instanceof Error ? copyError.message : 'Could not copy packet capture command',
      );
    }
  };

  const handlePcapSelected = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file || !incident) {
      return;
    }

    try {
      setUploadingPcap(true);
      setError(null);
      const analysis = await aiopsApi.uploadIncidentPcap(incident.id, file);
      setPacketCaptures((current) => [analysis, ...current]);
    } catch (requestError) {
      setError(
        requestError instanceof Error ? requestError.message : 'Packet capture upload failed',
      );
    } finally {
      setUploadingPcap(false);
    }
  };

  const handleDeletePcap = async (capture: PacketCaptureAnalysis) => {
    if (!incident) {
      return;
    }
    if (
      !window.confirm('Delete this packet capture analysis? This does not delete the incident.')
    ) {
      return;
    }

    try {
      setDeletingPcapId(capture.id);
      setError(null);
      await aiopsApi.deleteIncidentPcap(incident.id, capture.id);
      const packetCaptureResponse = await aiopsApi.getIncidentPacketCaptures(incident.id);
      setPacketCaptures(packetCaptureResponse);
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : 'Packet capture analysis could not be deleted',
      );
    } finally {
      setDeletingPcapId(null);
    }
  };

  const eventColumns: GridColDef<EventLog>[] = [
    {
      field: 'occurredAt',
      headerName: 'Time',
      minWidth: 170,
      flex: 0.8,
      valueFormatter: (value) => dayjs(value as string).format('DD/MM/YYYY HH:mm:ss'),
    },
    {
      field: 'eventType',
      headerName: 'Event',
      minWidth: 220,
      flex: 1,
      renderCell: (params) => (
        <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
          <Typography variant="body2">{formatEventType(params.row.eventType)}</Typography>
          {params.row.eventSource === 'SYSLOG' && <Chip size="small" color="info" label="SYSLOG" />}
        </Stack>
      ),
    },
    {
      field: 'severity',
      headerName: 'Severity',
      minWidth: 120,
      flex: 0.5,
      renderCell: (params) => <SeverityChip severity={params.row.severity} />,
    },
    {
      field: 'source',
      headerName: 'Source',
      minWidth: 220,
      flex: 1,
      sortable: false,
      renderCell: (params) => (
        <Stack spacing={0.25} sx={{ py: 0.75 }}>
          <Typography variant="body2">{params.row.sourceIp || '-'}</Typography>
          <Typography variant="caption" color="text.secondary">
            {[params.row.syslogSourceName, params.row.parsingProfile].filter(Boolean).join(' • ') ||
              'No syslog metadata'}
          </Typography>
        </Stack>
      ),
    },
    {
      field: 'resultingStatus',
      headerName: 'Status',
      minWidth: 120,
      flex: 0.5,
      renderCell: (params) => <StatusChip status={params.row.resultingStatus} />,
    },
    { field: 'message', headerName: 'Message', minWidth: 280, flex: 1.5 },
    {
      field: 'actions',
      headerName: 'Inspect',
      minWidth: 90,
      flex: 0.4,
      sortable: false,
      renderCell: (params) => (
        <IconActionButton
          icon="mdi:eye-outline"
          label="View event details"
          onClick={() => setSelectedEvent(params.row)}
        />
      ),
    },
  ];

  const hasSyslogEvidence = Boolean(
    incident?.relatedEvents.some((event) => event.eventSource === 'SYSLOG'),
  );
  const hasPacketCaptureEvidence = packetCaptures.length > 0;
  const hasNetFlowEvidence = networkFlows.length > 0;

  return (
    <Box sx={{ pb: 2 }}>
      <PageHeader
        actions={
          <Stack direction="row" spacing={1}>
            <IconActionButton
              icon="mdi:arrow-left"
              label="Back to incidents"
              onClick={() => navigate('/incidents')}
            />
            <IconActionButton icon="mdi:refresh" label="Refresh incident" onClick={refresh} />
            {canAcknowledge && (
              <IconActionButton
                icon={incident?.acknowledged ? 'mdi:bell-ring-outline' : 'mdi:check'}
                label={incident?.acknowledged ? 'Remove acknowledgement' : 'Acknowledge incident'}
                onClick={handleToggleAcknowledged}
                disabled={!incident || actionLoading || isIncidentResolved(incident?.status)}
                color={incident?.acknowledged ? 'warning' : 'success'}
              />
            )}
            {canAnalyze && (
              <IconActionButton
                icon="mdi:brain"
                label="Analyze with AI"
                onClick={handleAnalyze}
                disabled={!incident}
                color="primary"
              />
            )}
            {canResolve && (
              <IconActionButton
                icon="mdi:check-decagram-outline"
                label="Resolve incident"
                onClick={() => setResolveDialogOpen(true)}
                disabled={!incident || actionLoading || isIncidentResolved(incident?.status)}
                color="success"
              />
            )}
            {canManageAdminMaintenance && (
              <IconActionButton
                icon="mdi:trash-can-outline"
                label="Delete incident"
                onClick={handleDeleteIncident}
                disabled={!incident || actionLoading}
                color="error"
              />
            )}
            {canUploadPcap && (
              <IconActionButton
                icon="mdi:upload-network-outline"
                label="Upload packet capture"
                onClick={handleUploadButtonClick}
                disabled={!incident || uploadingPcap}
                color="secondary"
              />
            )}
          </Stack>
        }
      >
        Incident Detail
      </PageHeader>
      <input
        ref={fileInputRef}
        type="file"
        accept=".pcap,.pcapng"
        hidden
        onChange={handlePcapSelected}
      />

      <Stack spacing={3} mt={3}>
        <ApiState loading={loading} error={error} onRetry={refresh} />

        {incident ? (
          <>
            <Paper
              sx={{ p: 3, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
              elevation={0}
            >
              <Stack spacing={2.5}>
                <Stack
                  direction={{ xs: 'column', lg: 'row' }}
                  justifyContent="space-between"
                  spacing={2}
                >
                  <Stack spacing={1}>
                    <Typography variant="h4">{incident.title}</Typography>
                    <Stack direction="row" spacing={1} flexWrap="wrap">
                      <Chip
                        size="small"
                        label={incident.status}
                        color={incidentStatusColor(incident.status)}
                      />
                      <SeverityChip severity={incident.severity} />
                      <StatusChip status={incident.resultingStatus} />
                      <Chip
                        size="small"
                        label={incident.acknowledged ? 'Acknowledged' : 'Unacknowledged'}
                        color={incident.acknowledged ? 'success' : 'warning'}
                      />
                    </Stack>
                  </Stack>
                  <Typography color="text.secondary">Incident #{incident.id}</Typography>
                </Stack>

                <Divider />

                <Grid container spacing={2.5}>
                  <Grid item xs={12} md={3}>
                    <DetailItem label="Device" value={incident.deviceName} />
                  </Grid>
                  <Grid item xs={12} md={3}>
                    <DetailItem label="Type" value={formatDeviceType(incident.deviceType)} />
                  </Grid>
                  <Grid item xs={12} md={3}>
                    <DetailItem label="Category" value={formatEventType(incident.category)} />
                  </Grid>
                  <Grid item xs={12} md={3}>
                    <DetailItem label="Location" value={incident.location} />
                  </Grid>
                  <Grid item xs={12} md={3}>
                    <DetailItem
                      label="Created"
                      value={dayjs(incident.createdAt ?? incident.firstSeenAt).format(
                        'DD/MM/YYYY HH:mm',
                      )}
                    />
                  </Grid>
                  {!isIncidentResolved(incident.status) && (
                    <Grid item xs={12} md={3}>
                      <DetailItem
                        label="Last activity"
                        value={dayjs(incident.lastActivityAt ?? incident.lastSeenAt).format(
                          'DD/MM/YYYY HH:mm',
                        )}
                      />
                    </Grid>
                  )}
                  {isIncidentResolved(incident.status) && (
                    <Grid item xs={12} md={3}>
                      <DetailItem
                        label="Resolved"
                        value={
                          incident.resolvedAt || incident.recoveredAt
                            ? dayjs(incident.resolvedAt ?? incident.recoveredAt).format(
                                'DD/MM/YYYY HH:mm',
                              )
                            : '-'
                        }
                      />
                    </Grid>
                  )}
                  <Grid item xs={12} md={3}>
                    <DetailItem label="Duration" value={formatDuration(incident.durationMinutes)} />
                  </Grid>
                  {incident.previousSimilarIncidentId ? (
                    <Grid item xs={12} md={3}>
                      <DetailItem
                        label="Previous similar"
                        value={`Incident #${incident.previousSimilarIncidentId}`}
                      />
                    </Grid>
                  ) : null}
                </Grid>

                <Alert
                  severity={
                    incident.status === 'ACTIVE'
                      ? 'warning'
                      : isIncidentResolved(incident.status)
                        ? 'success'
                        : 'info'
                  }
                >
                  {incident.latestEvent.message}
                </Alert>
              </Stack>
            </Paper>

            <Grid container spacing={3}>
              <Grid item xs={12} lg={6}>
                <Paper
                  sx={{ p: 2.5, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
                  elevation={0}
                >
                  <Typography variant="h5" mb={2}>
                    Component Context
                  </Typography>
                  {relatedComponents.length ? (
                    <Stack spacing={2.5}>
                      {relatedComponents.map((match) => {
                        const full = componentsById[match.id];
                        return (
                          <Stack
                            key={`${match.relation}-${match.id}`}
                            spacing={1.5}
                            sx={{
                              p: 1.5,
                              borderRadius: 1.5,
                              border: '1px solid',
                              borderColor: 'divider',
                            }}
                          >
                            <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                              <Typography variant="h6">{match.name}</Typography>
                              <Chip size="small" label={relationLabel(match.relation)} />
                              <IconActionButton
                                icon="mdi:open-in-new"
                                label="Open component"
                                onClick={() => navigate(`/components/${match.id}`)}
                              />
                            </Stack>
                            <Grid container spacing={2}>
                              <Grid item xs={12} md={6}>
                                <DetailItem
                                  label="Operational status"
                                  value={match.lastStatus || '-'}
                                />
                              </Grid>
                              <Grid item xs={12} md={6}>
                                <DetailItem
                                  label="Primary IP"
                                  value={match.primaryIp || full?.ipAddress || '-'}
                                />
                              </Grid>
                              <Grid item xs={12} md={6}>
                                <DetailItem
                                  label="Matched interface"
                                  value={
                                    match.matchedIp
                                      ? `${match.matchedIp}${
                                          match.matchedInterfaceName
                                            ? ` (${match.matchedInterfaceName})`
                                            : ''
                                        }`
                                      : '-'
                                  }
                                />
                              </Grid>
                              <Grid item xs={12} md={6}>
                                <DetailItem
                                  label="Component type"
                                  value={formatDeviceType(match.type)}
                                />
                              </Grid>
                              {full?.criticality ? (
                                <Grid item xs={12} md={6}>
                                  <DetailItem label="Criticality" value={full.criticality} />
                                </Grid>
                              ) : null}
                              <Grid item xs={12} md={6}>
                                <DetailItem
                                  label="Monitoring"
                                  value={match.monitoringEnabled ? 'STARTED' : 'STOPPED'}
                                />
                              </Grid>
                              {match.location ? (
                                <Grid item xs={12} md={6}>
                                  <DetailItem label="Location" value={match.location} />
                                </Grid>
                              ) : null}
                              <Grid item xs={12} md={6}>
                                <DetailItem
                                  label="Last check"
                                  value={
                                    match.lastCheckedAt
                                      ? dayjs(match.lastCheckedAt).format('DD/MM/YYYY HH:mm:ss')
                                      : '-'
                                  }
                                />
                              </Grid>
                              {full?.monitoringMethods?.length ? (
                                <Grid item xs={12}>
                                  <DetailItem
                                    label="Monitoring methods"
                                    value={full.monitoringMethods.join(', ')}
                                  />
                                </Grid>
                              ) : null}
                              {full?.httpUrl ? (
                                <Grid item xs={12} md={6}>
                                  <DetailItem label="HTTP / RTSP URL" value={full.httpUrl} />
                                </Grid>
                              ) : null}
                              {match.lastError ? (
                                <Grid item xs={12}>
                                  <DetailItem label="Last error" value={match.lastError} />
                                </Grid>
                              ) : null}
                            </Grid>
                          </Stack>
                        );
                      })}
                    </Stack>
                  ) : (
                    <Typography color="text.secondary">
                      No monitored component is linked to this incident.
                    </Typography>
                  )}
                </Paper>
              </Grid>

              <Grid item xs={12} lg={6}>
                <Paper
                  sx={{ p: 2.5, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
                  elevation={0}
                >
                  <Typography variant="h5" mb={2}>
                    Latest Metrics
                  </Typography>
                  {latestMetrics.length ? (
                    <Grid container spacing={1.5}>
                      {latestMetrics.map((metric) => (
                        <Grid item xs={12} md={6} key={`${metric.metricName}-${metric.sampledAt}`}>
                          <Box
                            sx={{
                              p: 1.5,
                              borderRadius: 1.5,
                              border: '1px solid',
                              borderColor: 'divider',
                            }}
                          >
                            <Typography variant="caption" color="text.secondary">
                              {metric.metricName}
                            </Typography>
                            <Typography variant="h5">
                              {metric.metricValue} {metric.unit}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              {dayjs(metric.sampledAt).format('HH:mm:ss')} - {metric.source}
                            </Typography>
                          </Box>
                        </Grid>
                      ))}
                    </Grid>
                  ) : (
                    <Typography color="text.secondary">
                      {relatedComponents.length
                        ? 'No recent metrics linked to this incident.'
                        : 'No monitored component is linked to this incident.'}
                    </Typography>
                  )}
                </Paper>
              </Grid>
            </Grid>

            <Paper
              sx={{ p: 2, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
              elevation={0}
            >
              <Typography variant="h5" mb={2}>
                Related Events
              </Typography>
              <DataGrid
                rows={incident.relatedEvents}
                columns={eventColumns}
                autoHeight
                disableRowSelectionOnClick
                onRowClick={(params) => setSelectedEvent(params.row)}
                pageSizeOptions={[10, 25, 50]}
                initialState={{ pagination: { paginationModel: { pageSize: 10 } } }}
              />
            </Paper>
            <Paper
              sx={{ p: 2.5, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
              elevation={0}
            >
              <Stack spacing={2}>
                <PacketCapturePanel
                  incidentId={incident.id}
                  incidentStatus={incident.status}
                  canCapture={canUploadPcap}
                  packetCaptures={packetCaptures}
                  uploadingPcap={uploadingPcap}
                  onUploadClick={handleUploadButtonClick}
                  onPacketCapturesChange={setPacketCaptures}
                  onError={setError}
                />
                {packetCaptureRecommendation && (
                  <Box
                    sx={{
                      p: 2,
                      borderRadius: 1.5,
                      border: '1px solid',
                      borderColor: 'divider',
                      bgcolor: 'background.default',
                    }}
                  >
                    <Stack spacing={1.5}>
                      <Stack
                        direction={{ xs: 'column', md: 'row' }}
                        justifyContent="space-between"
                        spacing={1}
                      >
                        <Stack spacing={0.5}>
                          <Typography variant="subtitle1">Manual capture (debug)</Typography>
                          <Typography variant="body2" color="text.secondary">
                            {packetCaptureRecommendation.reason}
                          </Typography>
                        </Stack>
                      </Stack>
                      <Grid container spacing={1.5}>
                        <Grid item xs={12} md={3}>
                          <DetailItem
                            label="Suggested Duration"
                            value={`${packetCaptureRecommendation.durationSeconds} seconds`}
                          />
                        </Grid>
                        <Grid item xs={12} md={3}>
                          <DetailItem
                            label="Protocol"
                            value={packetCaptureRecommendation.protocol}
                          />
                        </Grid>
                        <Grid item xs={12} md={3}>
                          <DetailItem
                            label="Source Address"
                            value={packetCaptureRecommendation.sourceAddress || '-'}
                          />
                        </Grid>
                        <Grid item xs={12} md={3}>
                          <DetailItem
                            label="Destination Address"
                            value={packetCaptureRecommendation.destinationAddress || '-'}
                          />
                        </Grid>
                        <Grid item xs={12}>
                          <DetailItem
                            label="Observed Destination Ports"
                            value={packetCaptureRecommendation.destinationPorts.join(', ') || '-'}
                          />
                        </Grid>
                        <Grid item xs={12}>
                          <DetailItem
                            label="Suggested Filter"
                            value={packetCaptureRecommendation.suggestedFilter}
                          />
                        </Grid>
                        <Grid item xs={12}>
                          <Box
                            sx={{
                              p: 1.5,
                              borderRadius: 1.5,
                              border: '1px solid',
                              borderColor: 'divider',
                            }}
                          >
                            <Stack spacing={1}>
                              <Stack
                                direction={{ xs: 'column', md: 'row' }}
                                justifyContent="space-between"
                                spacing={1}
                              >
                                <Box>
                                  <Typography variant="subtitle2">Linux/macOS</Typography>
                                  <Typography variant="body2" color="text.secondary">
                                    Requires tcpdump and sudo/root permissions.
                                  </Typography>
                                </Box>
                                <Stack direction="row" spacing={1} alignItems="center">
                                  {copiedCommandKey === 'linux' && (
                                    <Chip size="small" color="success" label="Copied" />
                                  )}
                                  <IconActionButton
                                    icon="mdi:content-copy"
                                    label="Copy Linux/macOS command"
                                    onClick={() =>
                                      handleCopyCommand(
                                        packetCaptureRecommendation.linuxCommand,
                                        'linux',
                                      )
                                    }
                                  />
                                </Stack>
                              </Stack>
                              <DetailItem
                                label="Capture Command"
                                value={packetCaptureRecommendation.linuxCommand}
                              />
                            </Stack>
                          </Box>
                        </Grid>
                        <Grid item xs={12}>
                          <Box
                            sx={{
                              p: 1.5,
                              borderRadius: 1.5,
                              border: '1px solid',
                              borderColor: 'divider',
                            }}
                          >
                            <Stack spacing={1}>
                              <Box>
                                <Typography variant="subtitle2">Windows PowerShell</Typography>
                                <Typography variant="body2" color="text.secondary">
                                  Install Wireshark, run PowerShell as Administrator, use `dumpcap
                                  -D` to find the interface number.
                                </Typography>
                              </Box>
                              <Stack
                                direction={{ xs: 'column', md: 'row' }}
                                justifyContent="space-between"
                                spacing={1}
                              >
                                <DetailItem
                                  label="List Interfaces"
                                  value={packetCaptureRecommendation.windowsListInterfacesCommand}
                                />
                                <Stack direction="row" spacing={1} alignItems="center">
                                  {copiedCommandKey === 'windows-list' && (
                                    <Chip size="small" color="success" label="Copied" />
                                  )}
                                  <IconActionButton
                                    icon="mdi:content-copy"
                                    label="Copy interface listing command"
                                    onClick={() =>
                                      handleCopyCommand(
                                        packetCaptureRecommendation.windowsListInterfacesCommand,
                                        'windows-list',
                                      )
                                    }
                                  />
                                </Stack>
                              </Stack>
                              <Stack
                                direction={{ xs: 'column', md: 'row' }}
                                justifyContent="space-between"
                                spacing={1}
                              >
                                <DetailItem
                                  label="dumpcap Capture Command"
                                  value={packetCaptureRecommendation.windowsDumpcapCommand}
                                />
                                <Stack direction="row" spacing={1} alignItems="center">
                                  {copiedCommandKey === 'windows-dumpcap' && (
                                    <Chip size="small" color="success" label="Copied" />
                                  )}
                                  <IconActionButton
                                    icon="mdi:content-copy"
                                    label="Copy dumpcap command"
                                    onClick={() =>
                                      handleCopyCommand(
                                        packetCaptureRecommendation.windowsDumpcapCommand,
                                        'windows-dumpcap',
                                      )
                                    }
                                  />
                                </Stack>
                              </Stack>
                              <Stack
                                direction={{ xs: 'column', md: 'row' }}
                                justifyContent="space-between"
                                spacing={1}
                              >
                                <DetailItem
                                  label="Alternative tshark Command"
                                  value={packetCaptureRecommendation.windowsTsharkCommand}
                                />
                                <Stack direction="row" spacing={1} alignItems="center">
                                  {copiedCommandKey === 'windows-tshark' && (
                                    <Chip size="small" color="success" label="Copied" />
                                  )}
                                  <IconActionButton
                                    icon="mdi:content-copy"
                                    label="Copy tshark command"
                                    onClick={() =>
                                      handleCopyCommand(
                                        packetCaptureRecommendation.windowsTsharkCommand,
                                        'windows-tshark',
                                      )
                                    }
                                  />
                                </Stack>
                              </Stack>
                            </Stack>
                          </Box>
                        </Grid>
                        <Grid item xs={12}>
                          <Typography variant="body2" color="text.secondary">
                            After capture, upload the generated `.pcap` file below.
                          </Typography>
                        </Grid>
                        <Grid item xs={12}>
                          <Box
                            sx={{
                              p: 1.5,
                              borderRadius: 1.5,
                              border: '1px solid',
                              borderColor: 'divider',
                            }}
                          >
                            <Stack spacing={1}>
                              <Typography variant="subtitle2">Address check</Typography>
                              <Typography variant="body2" color="text.secondary">
                                Make sure the IP addresses are reachable and current before
                                capturing traffic.
                              </Typography>
                              <DetailItem
                                label="Linux"
                                value={`ip -4 route get 1.1.1.1 | awk '{print $7; exit}'`}
                              />
                              <DetailItem
                                label="Windows PowerShell"
                                value={`Get-NetIPAddress -AddressFamily IPv4 | Where-Object {$_.IPAddress -ne '127.0.0.1'} | Select-Object -First 1 -ExpandProperty IPAddress`}
                              />
                            </Stack>
                          </Box>
                        </Grid>
                      </Grid>
                    </Stack>
                  </Box>
                )}
                {(hasPacketCaptureEvidence || hasNetFlowEvidence) && (
                  <Alert severity="info">
                    {hasSyslogEvidence && hasPacketCaptureEvidence && hasNetFlowEvidence
                      ? 'This incident contains multi-source evidence: Syslog, NetFlow, and Packet Capture.'
                      : hasSyslogEvidence && hasPacketCaptureEvidence
                        ? 'Syslog evidence and packet capture evidence are both available for this incident.'
                        : hasSyslogEvidence && hasNetFlowEvidence
                          ? 'Syslog evidence and NetFlow evidence are both available for this incident.'
                          : hasPacketCaptureEvidence
                            ? 'Packet capture evidence is available for this incident.'
                            : 'NetFlow evidence is available for this incident.'}
                  </Alert>
                )}
                {packetCaptures.length ? (
                  <Stack spacing={2}>
                    <Typography variant="subtitle1">Analysis</Typography>
                    {packetCaptures.map((capture) => (
                      <Box
                        key={capture.id}
                        sx={{
                          p: 2,
                          borderRadius: 1.5,
                          border: '1px solid',
                          borderColor: 'divider',
                        }}
                      >
                        <Stack
                          direction={{ xs: 'column', md: 'row' }}
                          justifyContent="space-between"
                          spacing={1}
                          mb={1}
                          alignItems={{ md: 'center' }}
                        >
                          <Typography variant="subtitle1">{capture.fileName}</Typography>
                          <Stack direction="row" spacing={1} alignItems="center">
                            <Typography variant="caption" color="text.secondary">
                              {dayjs(capture.createdAt).format('DD/MM/YYYY HH:mm:ss')}
                            </Typography>
                            {canUploadPcap && (
                              <IconActionButton
                                icon="mdi:trash-can-outline"
                                label={
                                  deletingPcapId === capture.id
                                    ? 'Deleting packet capture analysis...'
                                    : 'Delete packet capture analysis'
                                }
                                onClick={() => void handleDeletePcap(capture)}
                                disabled={deletingPcapId !== null || uploadingPcap}
                                color="error"
                              />
                            )}
                          </Stack>
                        </Stack>
                        <Typography variant="subtitle2" mb={1}>
                          Packet capture evidence
                        </Typography>
                        <Grid container spacing={1.5}>
                          <Grid item xs={12} md={3}>
                            <DetailItem label="Packets" value={String(capture.totalPackets)} />
                          </Grid>
                          <Grid item xs={12} md={3}>
                            <PcapSizeMetric
                              label="Captured packet bytes"
                              value={formatByteCount(capturedPacketBytesOf(capture))}
                              hint={CAPTURED_PACKET_BYTES_HINT}
                            />
                          </Grid>
                          <Grid item xs={12} md={3}>
                            <PcapSizeMetric
                              label="PCAP file size"
                              value={formatByteCount(capture.fileSize)}
                              hint={PCAP_FILE_SIZE_HINT}
                            />
                          </Grid>
                          <Grid item xs={12} md={6}>
                            <DetailItem
                              label="Top Protocols"
                              value={capture.topProtocols.join(', ') || '-'}
                            />
                          </Grid>
                          <Grid item xs={12} md={6}>
                            <DetailItem
                              label="Top Source IPs"
                              value={capture.topSourceIps.join(', ') || '-'}
                            />
                          </Grid>
                          <Grid item xs={12} md={6}>
                            <DetailItem
                              label="Top Destination IPs"
                              value={capture.topDestinationIps.join(', ') || '-'}
                            />
                          </Grid>
                          <Grid item xs={12}>
                            <DetailItem
                              label="Top Destination Ports"
                              value={capture.topDestinationPorts.join(', ') || '-'}
                            />
                          </Grid>
                          <Grid item xs={12}>
                            <DetailItem
                              label="Suspicious Findings"
                              value={capture.suspiciousFindings.join(' | ') || '-'}
                            />
                          </Grid>
                          <Grid item xs={12}>
                            <DetailItem label="Summary" value={capture.summary} />
                          </Grid>
                        </Grid>
                      </Box>
                    ))}
                  </Stack>
                ) : (
                  <Typography color="text.secondary">
                    No packet capture uploaded yet. Use Capture traffic above, or run a manual
                    capture command and upload the generated .pcap file.
                  </Typography>
                )}
              </Stack>
            </Paper>
            <Paper
              sx={{ p: 2.5, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
              elevation={0}
            >
              <Stack spacing={2}>
                <Stack spacing={0.5}>
                  <Typography variant="h5">Related NetFlow Evidence</Typography>
                  <Typography color="text.secondary">
                    NetFlow provides traffic behavior evidence. It summarizes who communicated with
                    whom, on which ports, and how much data was exchanged.
                  </Typography>
                </Stack>
                {networkFlows.length ? (
                  <>
                    <Grid container spacing={1.5}>
                      <Grid item xs={12} md={3}>
                        <DetailItem
                          label="Linked Flow Records"
                          value={String(networkFlows.length)}
                        />
                      </Grid>
                      <Grid item xs={12} md={3}>
                        <DetailItem
                          label="Observed Anomalies"
                          value={
                            Array.from(
                              new Set(
                                networkFlows
                                  .map((flow) => flow.anomalyType)
                                  .filter(
                                    (value): value is NonNullable<NetworkFlow['anomalyType']> =>
                                      Boolean(value),
                                  ),
                              ),
                            ).join(', ') || '-'
                          }
                        />
                      </Grid>
                      <Grid item xs={12} md={3}>
                        <DetailItem
                          label="Total Packets"
                          value={String(networkFlows.reduce((sum, flow) => sum + flow.packets, 0))}
                        />
                      </Grid>
                      <Grid item xs={12} md={3}>
                        <DetailItem
                          label="Total Bytes"
                          value={String(networkFlows.reduce((sum, flow) => sum + flow.bytes, 0))}
                        />
                      </Grid>
                    </Grid>
                    <Stack spacing={1.5}>
                      {networkFlows.slice(0, 10).map((flow) => (
                        <Box
                          key={flow.id}
                          sx={{
                            p: 1.5,
                            borderRadius: 1.5,
                            border: '1px solid',
                            borderColor: 'divider',
                          }}
                        >
                          <Stack spacing={0.5}>
                            <Typography variant="subtitle2">
                              {flow.anomalyType || 'FLOW'} · {flow.sourceIp} to {flow.destinationIp}
                            </Typography>
                            <Typography variant="body2" color="text.secondary">
                              {dayjs(flow.startTime).format('DD/MM/YYYY HH:mm:ss')} to{' '}
                              {dayjs(flow.endTime).format('DD/MM/YYYY HH:mm:ss')} · protocol{' '}
                              {flow.protocol}
                            </Typography>
                            <Typography variant="body2">
                              Ports: {flow.sourcePort || '-'} to {flow.destinationPort || '-'} ·
                              Packets: {flow.packets} · Bytes: {flow.bytes}
                            </Typography>
                            <Typography variant="body2" color="text.secondary">
                              {flow.anomalyReason || 'No anomaly reason recorded.'}
                            </Typography>
                          </Stack>
                        </Box>
                      ))}
                    </Stack>
                  </>
                ) : (
                  <Typography color="text.secondary">
                    No NetFlow evidence linked to this incident yet.
                  </Typography>
                )}
              </Stack>
            </Paper>
            <EventDetailsDialog
              event={selectedEvent}
              open={Boolean(selectedEvent)}
              onClose={() => setSelectedEvent(null)}
            />
            <Dialog
              open={resolveDialogOpen}
              onClose={() => !actionLoading && setResolveDialogOpen(false)}
            >
              <DialogTitle>Resolve this incident?</DialogTitle>
              <DialogContent>
                <DialogContentText>
                  This closes the current incident episode. New matching activity will create a new
                  incident.
                </DialogContentText>
              </DialogContent>
              <DialogActions>
                <Button onClick={() => setResolveDialogOpen(false)} disabled={actionLoading}>
                  Cancel
                </Button>
                <Button
                  onClick={handleResolveIncident}
                  color="success"
                  variant="contained"
                  disabled={actionLoading}
                >
                  Resolve incident
                </Button>
              </DialogActions>
            </Dialog>
          </>
        ) : (
          !loading && <Typography color="text.secondary">Incident not found.</Typography>
        )}
      </Stack>
    </Box>
  );
};

export default IncidentDetailPage;
