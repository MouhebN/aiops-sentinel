import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControl,
  FormControlLabel,
  Grid,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@mui/material';
import { DataGrid, GridColDef } from '@mui/x-data-grid';
import ReactECharts from 'echarts-for-react';
import type { EChartsOption } from 'echarts';
import {
  aiopsApi,
  ComponentConnectionCheck,
  MetricSample,
  MetricThreshold,
  MonitoredComponent,
  SaveMetricThresholdPayload,
  ThresholdOperator,
} from 'api/aiopsApi';
import { useAuth } from 'auth/AuthContext';
import {
  canManageComponents,
  canManageThresholds,
  canRunManualChecks,
  canUseAiAnalysis,
} from 'auth/permissions';
import IconActionButton from 'components/aiops/IconActionButton';
import SeverityChip from 'components/aiops/SeverityChip';
import StatusChip from 'components/aiops/StatusChip';
import PageHeader from 'components/common/PageHeader';
import { formatDeviceType, formatEventType } from 'helpers/aiops';
import dayjs from 'dayjs';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { EventLog } from 'types/aiops';
import NetworkInterfacesSection from '../NetworkInterfacesSection';

const DetailItem = ({ label, value }: { label: string; value: string }) => (
  <Stack spacing={0.25}>
    <Typography variant="caption" color="text.secondary">
      {label}
    </Typography>
    <Typography sx={{ overflowWrap: 'anywhere' }}>{value}</Typography>
  </Stack>
);

const chartCardSx = {
  p: 3,
  borderRadius: 2,
  border: '1px solid',
  borderColor: 'divider',
};

const thresholdLinesFor = (metricName: string, thresholds: MetricThreshold[]) => {
  const threshold = thresholds.find((item) => item.enabled && item.metricName === metricName);
  if (!threshold) {
    return undefined;
  }

  return {
    symbol: 'none',
    label: {
      formatter: '{b}',
    },
    data: [
      {
        name: 'Warning',
        yAxis: threshold.warningValue,
        lineStyle: { color: '#d97706', type: 'dashed' as const },
      },
      {
        name: 'Critical',
        yAxis: threshold.criticalValue,
        lineStyle: { color: '#dc2626', type: 'dashed' as const },
      },
    ],
  };
};

const formatMetricName = (metricName: string) =>
  metricName
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (letter) => letter.toUpperCase());

const buildAvailabilityOption = (samples: MetricSample[]): EChartsOption => {
  const metricNames = Array.from(new Set(samples.map((sample) => sample.metricName)));
  const times = Array.from(new Set(samples.map((sample) => sample.sampledAt))).sort();
  const timeLabels = times.map((time) => dayjs(time).format('HH:mm'));
  const data = metricNames.flatMap((metricName, metricIndex) =>
    times.map((time, timeIndex) => {
      const sample = samples.find(
        (item) => item.metricName === metricName && item.sampledAt === time,
      );

      return [timeIndex, metricIndex, sample ? sample.metricValue : null];
    }),
  );

  return {
    tooltip: {
      position: 'top',
      formatter: (params) => {
        const point = params as { value?: [number, number, number | null] };
        const timeIndex = point.value?.[0] ?? 0;
        const metricIndex = point.value?.[1] ?? 0;
        const value = point.value?.[2] ?? 0;
        const status = Number(value) >= 1 ? 'UP' : 'DOWN';

        return `<strong>${timeLabels[timeIndex] || ''}</strong><br />${formatMetricName(
          metricNames[metricIndex] || '',
        )}: <strong>${status}</strong>`;
      },
    },
    grid: {
      left: 118,
      right: 28,
      top: 18,
      bottom: 72,
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      data: timeLabels,
      axisLabel: {
        hideOverlap: true,
      },
      splitArea: {
        show: true,
      },
    },
    yAxis: {
      type: 'category',
      data: metricNames.map(formatMetricName),
      splitArea: {
        show: true,
      },
    },
    visualMap: {
      show: false,
      min: 0,
      max: 1,
      inRange: {
        color: ['#ef4444', '#22c55e'],
      },
    },
    dataZoom:
      times.length > 40
        ? [
            {
              type: 'slider',
              height: 18,
              bottom: 22,
              start: Math.max(0, 100 - (40 / times.length) * 100),
              end: 100,
            },
          ]
        : undefined,
    series: [
      {
        name: 'Availability',
        type: 'heatmap',
        data,
        label: {
          show: false,
        },
        emphasis: {
          itemStyle: {
            borderColor: '#111827',
            borderWidth: 1,
          },
        },
        itemStyle: {
          borderColor: '#ffffff',
          borderWidth: 2,
        },
      },
    ],
  };
};

const buildMetricOption = (
  samples: MetricSample[],
  title: string,
  thresholds: MetricThreshold[],
): EChartsOption => {
  const metricNames = Array.from(new Set(samples.map((sample) => sample.metricName)));
  const times = Array.from(new Set(samples.map((sample) => sample.sampledAt))).sort();

  return {
    tooltip: {
      trigger: 'axis',
      valueFormatter: (value) => String(value),
    },
    legend: {
      type: 'scroll',
      bottom: 0,
    },
    grid: {
      left: 56,
      right: 28,
      top: 36,
      bottom: 78,
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      data: times.map((time) => dayjs(time).format('HH:mm')),
      boundaryGap: false,
      axisLabel: {
        hideOverlap: true,
      },
    },
    yAxis: {
      type: 'value',
      name: title,
    },
    series: metricNames.map((name) => {
      const series = {
        name: formatMetricName(name),
        type: 'line' as const,
        smooth: false,
        symbolSize: 6,
        data: times.map((time) => {
          const sample = samples.find(
            (item) => item.metricName === name && item.sampledAt === time,
          );
          return sample ? sample.metricValue : null;
        }),
        markLine: thresholdLinesFor(name, thresholds),
      };

      return series;
    }),
  };
};

interface ThresholdFormState {
  metricName: string;
  operator: ThresholdOperator;
  warningValue: string;
  criticalValue: string;
  enabled: boolean;
}

const emptyThresholdForm: ThresholdFormState = {
  metricName: '',
  operator: 'GREATER_THAN',
  warningValue: '',
  criticalValue: '',
  enabled: true,
};

const thresholdToForm = (threshold: MetricThreshold): ThresholdFormState => ({
  metricName: threshold.metricName,
  operator: threshold.operator,
  warningValue: String(threshold.warningValue),
  criticalValue: String(threshold.criticalValue),
  enabled: threshold.enabled,
});

const thresholdPayload = (form: ThresholdFormState): SaveMetricThresholdPayload => ({
  metricName: form.metricName.trim(),
  operator: form.operator,
  warningValue: Number(form.warningValue),
  criticalValue: Number(form.criticalValue),
  enabled: form.enabled,
});

const ComponentDetailPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const componentId = Number(id);
  const canManage = canManageComponents(user?.role);
  const canCheck = canRunManualChecks(user?.role);
  const canManageMetricThresholds = canManageThresholds(user?.role);
  const canAnalyze = canUseAiAnalysis(user?.role);
  const [component, setComponent] = useState<MonitoredComponent | null>(null);
  const [events, setEvents] = useState<EventLog[]>([]);
  const [metrics, setMetrics] = useState<MetricSample[]>([]);
  const [thresholds, setThresholds] = useState<MetricThreshold[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [checking, setChecking] = useState(false);
  const [checkResult, setCheckResult] = useState<ComponentConnectionCheck | null>(null);
  const [thresholdDialogOpen, setThresholdDialogOpen] = useState(false);
  const [editingThreshold, setEditingThreshold] = useState<MetricThreshold | null>(null);
  const [thresholdForm, setThresholdForm] = useState<ThresholdFormState>(emptyThresholdForm);
  const [thresholdError, setThresholdError] = useState<string | null>(null);
  const [savingThreshold, setSavingThreshold] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const eventColumns: GridColDef<EventLog>[] = [
    {
      field: 'occurredAt',
      headerName: 'Time',
      minWidth: 170,
      flex: 0.9,
      valueFormatter: (value) => dayjs(value as string).format('DD/MM/YYYY HH:mm:ss'),
    },
    {
      field: 'eventType',
      headerName: 'Event',
      minWidth: 180,
      flex: 1,
      valueGetter: (value) => formatEventType(value as string),
    },
    {
      field: 'severity',
      headerName: 'Severity',
      minWidth: 120,
      flex: 0.6,
      renderCell: (params) => <SeverityChip severity={params.row.severity} />,
    },
    {
      field: 'resultingStatus',
      headerName: 'Status',
      minWidth: 120,
      flex: 0.6,
      renderCell: (params) => <StatusChip status={params.row.resultingStatus} />,
    },
    { field: 'message', headerName: 'Message', minWidth: 260, flex: 1.5 },
    { field: 'details', headerName: 'Details', minWidth: 260, flex: 1.5 },
    {
      field: 'actions',
      headerName: 'Actions',
      minWidth: 120,
      flex: 0.5,
      sortable: false,
      renderCell: (params) => (
        <IconActionButton
          icon="mdi:brain"
          label="Analyze event"
          color="primary"
          onClick={() => navigate(`/ai-analysis?eventId=${params.row.id}`)}
          disabled={!canAnalyze}
        />
      ),
    },
  ];

  const refresh = useCallback(async () => {
    if (!componentId) {
      setError('Invalid component id.');
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      setError(null);
      const [componentResponse, eventResponse, metricResponse, thresholdResponse] =
        await Promise.all([
          aiopsApi.getComponent(componentId),
          aiopsApi.getEvents({ deviceId: `component-${componentId}` }),
          aiopsApi.getComponentMetrics(componentId, 24),
          aiopsApi.getComponentThresholds(componentId),
        ]);
      setComponent(componentResponse);
      setEvents(eventResponse);
      setMetrics(metricResponse);
      setThresholds(thresholdResponse);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Unknown component error');
    } finally {
      setLoading(false);
    }
  }, [componentId]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  useEffect(() => {
    const interval = window.setInterval(() => {
      refresh();
    }, 5000);

    return () => window.clearInterval(interval);
  }, [refresh]);

  const handleToggleMonitoring = async () => {
    if (!component) {
      return;
    }

    try {
      setError(null);
      if (component.enabled) {
        await aiopsApi.disableComponent(component.id);
      } else {
        await aiopsApi.enableComponent(component.id);
      }
      await refresh();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Unknown monitoring error');
    }
  };

  const handleCheckConnection = async () => {
    if (!component) {
      return;
    }

    try {
      setChecking(true);
      setError(null);
      setCheckResult(await aiopsApi.checkComponentNow(component.id));
    } catch (requestError) {
      setError(
        requestError instanceof Error ? requestError.message : 'Unknown connection check error',
      );
    } finally {
      setChecking(false);
    }
  };

  const handleDelete = async () => {
    if (!component) {
      return;
    }
    try {
      setDeleting(true);
      setError(null);
      await aiopsApi.deleteComponent(component.id);
      navigate('/components', {
        replace: true,
        state: { deletedComponentName: component.name },
      });
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Could not delete component');
    } finally {
      setDeleting(false);
      setDeleteOpen(false);
    }
  };

  const metricNames = useMemo(
    () => Array.from(new Set(metrics.map((sample) => sample.metricName))).sort(),
    [metrics],
  );

  const openCreateThreshold = () => {
    setEditingThreshold(null);
    setThresholdForm({
      ...emptyThresholdForm,
      metricName: metricNames[0] || '',
    });
    setThresholdError(null);
    setThresholdDialogOpen(true);
  };

  const openEditThreshold = (threshold: MetricThreshold) => {
    setEditingThreshold(threshold);
    setThresholdForm(thresholdToForm(threshold));
    setThresholdError(null);
    setThresholdDialogOpen(true);
  };

  const validateThreshold = () => {
    if (!thresholdForm.metricName.trim()) {
      return 'Metric name is required.';
    }
    if (thresholdForm.warningValue === '' || thresholdForm.criticalValue === '') {
      return 'Warning and critical values are required.';
    }
    const warning = Number(thresholdForm.warningValue);
    const critical = Number(thresholdForm.criticalValue);
    if (Number.isNaN(warning) || Number.isNaN(critical)) {
      return 'Threshold values must be valid numbers.';
    }
    if (thresholdForm.operator === 'GREATER_THAN' && critical < warning) {
      return 'For GREATER_THAN, critical value must be greater than or equal to warning value.';
    }
    if (thresholdForm.operator === 'LESS_THAN' && critical > warning) {
      return 'For LESS_THAN, critical value must be less than or equal to warning value.';
    }
    return null;
  };

  const handleSaveThreshold = async () => {
    const validationError = validateThreshold();
    if (validationError) {
      setThresholdError(validationError);
      return;
    }

    try {
      setSavingThreshold(true);
      setThresholdError(null);
      if (editingThreshold) {
        await aiopsApi.updateComponentThreshold(
          componentId,
          editingThreshold.id,
          thresholdPayload(thresholdForm),
        );
      } else {
        await aiopsApi.createComponentThreshold(componentId, thresholdPayload(thresholdForm));
      }
      setThresholdDialogOpen(false);
      await refresh();
    } catch (requestError) {
      setThresholdError(
        requestError instanceof Error ? requestError.message : 'Unknown threshold error',
      );
    } finally {
      setSavingThreshold(false);
    }
  };

  const handleDeleteThreshold = async (threshold: MetricThreshold) => {
    try {
      setError(null);
      await aiopsApi.deleteComponentThreshold(componentId, threshold.id);
      await refresh();
    } catch (requestError) {
      setError(
        requestError instanceof Error ? requestError.message : 'Unknown threshold delete error',
      );
    }
  };

  const thresholdColumns: GridColDef<MetricThreshold>[] = [
    { field: 'metricName', headerName: 'Metric', minWidth: 190, flex: 1 },
    {
      field: 'operator',
      headerName: 'Operator',
      minWidth: 130,
      flex: 0.7,
      valueGetter: (value) => (value === 'GREATER_THAN' ? '>' : '<'),
    },
    { field: 'warningValue', headerName: 'Warning', minWidth: 110, flex: 0.6 },
    { field: 'criticalValue', headerName: 'Critical', minWidth: 110, flex: 0.6 },
    {
      field: 'enabled',
      headerName: 'Enabled',
      minWidth: 110,
      flex: 0.6,
      valueGetter: (value) => (value ? 'Yes' : 'No'),
    },
    {
      field: 'lastState',
      headerName: 'State',
      minWidth: 120,
      flex: 0.6,
      renderCell: (params) => (
        <Chip
          size="small"
          label={params.row.lastState}
          color={
            params.row.lastState === 'CRITICAL'
              ? 'error'
              : params.row.lastState === 'WARNING'
                ? 'warning'
                : 'success'
          }
        />
      ),
    },
    {
      field: 'lastTriggeredAt',
      headerName: 'Last Triggered',
      minWidth: 170,
      flex: 0.9,
      valueFormatter: (value) =>
        value ? dayjs(value as string).format('DD/MM/YYYY HH:mm:ss') : 'Never',
    },
    {
      field: 'actions',
      headerName: 'Actions',
      minWidth: 150,
      flex: 0.7,
      sortable: false,
      renderCell: (params) =>
        canManageMetricThresholds ? (
          <Stack direction="row" spacing={1}>
            <IconActionButton
              icon="mdi:pencil-outline"
              label="Edit threshold"
              onClick={() => openEditThreshold(params.row)}
            />
            <IconActionButton
              icon="mdi:trash-can-outline"
              label="Delete threshold"
              color="error"
              onClick={() => handleDeleteThreshold(params.row)}
            />
          </Stack>
        ) : null,
    },
  ];

  const availabilityMetrics = useMemo(
    () =>
      metrics.filter(
        (sample) =>
          sample.unit === 'state' ||
          sample.metricName.endsWith('Available') ||
          sample.metricName.endsWith('Reachable'),
      ),
    [metrics],
  );

  const numericMetrics = useMemo(
    () =>
      metrics.filter(
        (sample) =>
          sample.unit !== 'state' &&
          !sample.metricName.endsWith('Available') &&
          !sample.metricName.endsWith('Reachable'),
      ),
    [metrics],
  );

  return (
    <Box sx={{ pb: 2 }}>
      <PageHeader
        actions={
          <Stack direction="row" spacing={1}>
            <IconActionButton
              icon="mdi:arrow-left"
              label="Back to components"
              onClick={() => navigate('/components')}
            />
            <IconActionButton icon="mdi:refresh" label="Refresh component" onClick={refresh} />
            <IconActionButton
              icon="mdi:connection"
              label={
                checking
                  ? 'Checking connection'
                  : component && !component.enabled
                    ? 'One-off check (does not resume monitoring)'
                    : 'Check connection'
              }
              onClick={handleCheckConnection}
              disabled={!component || checking || !canCheck}
              color="primary"
            />
            {canManage && (
              <IconActionButton
                icon={component?.enabled ? 'mdi:pause' : 'mdi:play'}
                label={component?.enabled ? 'Stop monitoring' : 'Start monitoring'}
                color={component?.enabled ? 'warning' : 'success'}
                onClick={handleToggleMonitoring}
                disabled={!component}
              />
            )}
            {canManage && (
              <IconActionButton
                icon="mdi:delete-outline"
                label="Delete component"
                color="error"
                onClick={() => setDeleteOpen(true)}
                disabled={!component}
              />
            )}
          </Stack>
        }
      >
        Component Detail
      </PageHeader>

      <Stack spacing={3} mt={3}>
        {error && <Alert severity="error">{error}</Alert>}
        {checkResult && (
          <Alert
            severity={
              checkResult.resultStatus === 'DEGRADED'
                ? 'warning'
                : checkResult.successful
                  ? 'success'
                  : 'error'
            }
            onClose={() => setCheckResult(null)}
          >
            <Typography fontWeight={700}>{checkResult.message}</Typography>
            <Typography variant="body2">
              Checked at {dayjs(checkResult.checkedAt).format('DD/MM/YYYY HH:mm:ss')}
            </Typography>
            {checkResult.details.map((detail) => (
              <Typography key={detail} variant="body2" sx={{ overflowWrap: 'anywhere' }}>
                {detail}
              </Typography>
            ))}
          </Alert>
        )}

        <Paper
          sx={{ p: 3, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
          elevation={0}
        >
          {component ? (
            <Stack spacing={3}>
              <Stack
                direction={{ xs: 'column', md: 'row' }}
                justifyContent="space-between"
                spacing={2}
              >
                <Stack spacing={1}>
                  <Typography variant="h4">{component.name}</Typography>
                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    <StatusChip status={component.lastStatus} />
                    <Chip
                      size="small"
                      label={component.enabled ? 'Monitoring: STARTED' : 'Monitoring: STOPPED'}
                      color={component.enabled ? 'success' : 'default'}
                      variant={component.enabled ? 'filled' : 'outlined'}
                    />
                    <Chip size="small" label={component.criticality} />
                  </Stack>
                </Stack>
                <Typography color="text.secondary">Component ID #{component.id}</Typography>
              </Stack>

              <Divider />

              <Grid container spacing={2.5}>
                <Grid item xs={12} md={3}>
                  <DetailItem label="Type" value={formatDeviceType(component.type)} />
                </Grid>
                <Grid item xs={12} md={3}>
                  <DetailItem label="Location" value={component.location} />
                </Grid>
                <Grid item xs={12} md={3}>
                  <DetailItem label="Primary IP (monitoring)" value={component.ipAddress || '-'} />
                </Grid>
                <Grid item xs={12} md={3}>
                  <DetailItem label="HTTP URL" value={component.httpUrl || '-'} />
                </Grid>
                <Grid item xs={12} md={3}>
                  <DetailItem
                    label="TCP Port"
                    value={component.tcpPort ? String(component.tcpPort) : '-'}
                  />
                </Grid>
                <Grid item xs={12} md={3}>
                  <DetailItem
                    label="SNMP"
                    value={
                      component.snmpPort
                        ? `${component.snmpCommunity || '(not set)'} / ${component.snmpPort}`
                        : '-'
                    }
                  />
                </Grid>
                <Grid item xs={12} md={6}>
                  <DetailItem label="SNMP OID" value={component.snmpOid || '-'} />
                </Grid>
                <Grid item xs={12} md={3}>
                  <DetailItem label="Methods" value={component.monitoringMethods.join(', ')} />
                </Grid>
                <Grid item xs={12} md={3}>
                  <DetailItem
                    label="Interval"
                    value={`${component.checkIntervalSeconds} seconds`}
                  />
                </Grid>
                <Grid item xs={12} md={3}>
                  <DetailItem
                    label="Last Checked"
                    value={
                      component.lastCheckedAt
                        ? dayjs(component.lastCheckedAt).format('DD/MM/YYYY HH:mm:ss')
                        : 'Never'
                    }
                  />
                </Grid>
                <Grid item xs={12} md={3}>
                  <DetailItem
                    label="Last Seen"
                    value={
                      component.lastSeenAt
                        ? dayjs(component.lastSeenAt).format('DD/MM/YYYY HH:mm:ss')
                        : 'Never'
                    }
                  />
                </Grid>
                <Grid item xs={12} md={3}>
                  <DetailItem label="Success Count" value={String(component.successCount)} />
                </Grid>
                <Grid item xs={12} md={3}>
                  <DetailItem label="Failure Count" value={String(component.failureCount)} />
                </Grid>
                <Grid item xs={12}>
                  <DetailItem label="Last Error" value={component.lastError || '-'} />
                </Grid>
                <Grid item xs={12}>
                  <Stack spacing={0.75}>
                    <Typography variant="caption" color="text.secondary">
                      Last Check Details
                    </Typography>
                    <Box
                      component="pre"
                      sx={{
                        m: 0,
                        p: 1.5,
                        borderRadius: 1,
                        bgcolor: 'grey.50',
                        border: '1px solid',
                        borderColor: 'divider',
                        whiteSpace: 'pre-wrap',
                        overflowWrap: 'anywhere',
                        fontFamily: 'monospace',
                        fontSize: 13,
                        maxHeight: 260,
                        overflow: 'auto',
                      }}
                    >
                      {component.lastCheckDetails || 'No scheduled check has run yet.'}
                    </Box>
                  </Stack>
                </Grid>
              </Grid>
            </Stack>
          ) : (
            <Typography color="text.secondary">
              {loading ? 'Loading component...' : 'Component not found.'}
            </Typography>
          )}
        </Paper>

        {component && (
          <Paper
            sx={{ p: 3, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
            elevation={0}
          >
            <NetworkInterfacesSection
              componentId={componentId}
              interfaces={component.networkInterfaces || []}
              canManage={canManage}
              onChanged={refresh}
            />
          </Paper>
        )}

        <Paper sx={chartCardSx} elevation={0}>
          <Stack spacing={2}>
            <Stack
              direction={{ xs: 'column', md: 'row' }}
              justifyContent="space-between"
              spacing={1}
            >
              <Typography variant="h5">Metric History</Typography>
              <Typography variant="body2" color="text.secondary">
                Last 24 hours
              </Typography>
            </Stack>

            {metrics.length ? (
              <Stack spacing={3}>
                <Box>
                  <Typography variant="subtitle2" color="text.secondary" mb={1}>
                    Availability Status
                  </Typography>
                  {availabilityMetrics.length ? (
                    <Box sx={{ height: 360, width: 1 }}>
                      <ReactECharts
                        option={buildAvailabilityOption(availabilityMetrics)}
                        style={{ height: '100%', width: '100%' }}
                      />
                    </Box>
                  ) : (
                    <Typography color="text.secondary">No availability samples yet.</Typography>
                  )}
                </Box>
                <Divider />
                <Box>
                  <Typography variant="subtitle2" color="text.secondary" mb={1}>
                    Numeric Metrics
                  </Typography>
                  {numericMetrics.length ? (
                    <Box sx={{ height: 430, width: 1 }}>
                      <ReactECharts
                        option={buildMetricOption(numericMetrics, 'Value', thresholds)}
                        style={{ height: '100%', width: '100%' }}
                      />
                    </Box>
                  ) : (
                    <Typography color="text.secondary">No numeric metrics yet.</Typography>
                  )}
                </Box>
              </Stack>
            ) : (
              <Typography color="text.secondary">
                No metric samples yet. Start monitoring this component and wait for scheduled
                checks.
              </Typography>
            )}
          </Stack>
        </Paper>

        <Paper
          sx={{ p: 2, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
          elevation={0}
        >
          <Stack spacing={2}>
            <Stack
              direction={{ xs: 'column', md: 'row' }}
              justifyContent="space-between"
              spacing={1}
            >
              <Stack spacing={0.5}>
                <Typography variant="h5">Metric Thresholds</Typography>
                <Typography variant="body2" color="text.secondary">
                  Thresholds create events only when a metric state changes.
                </Typography>
              </Stack>
              {canManageMetricThresholds && (
                <IconActionButton
                  icon="mdi:plus"
                  label="Add threshold"
                  color="primary"
                  onClick={openCreateThreshold}
                  disabled={!component}
                />
              )}
            </Stack>

            <DataGrid
              rows={thresholds}
              columns={thresholdColumns}
              autoHeight
              disableRowSelectionOnClick
              pageSizeOptions={[5, 10]}
              initialState={{ pagination: { paginationModel: { pageSize: 5 } } }}
            />
          </Stack>
        </Paper>

        <Paper
          sx={{ p: 2, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
          elevation={0}
        >
          <Typography variant="h5" mb={2}>
            Recent Component Events
          </Typography>
          <DataGrid
            rows={events}
            columns={eventColumns}
            loading={loading}
            autoHeight
            disableRowSelectionOnClick
            pageSizeOptions={[5, 10, 25]}
            initialState={{ pagination: { paginationModel: { pageSize: 5 } } }}
          />
        </Paper>
      </Stack>

      <Dialog
        open={thresholdDialogOpen}
        onClose={() => setThresholdDialogOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>{editingThreshold ? 'Edit Threshold' : 'Add Threshold'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} mt={1}>
            {thresholdError && <Alert severity="error">{thresholdError}</Alert>}
            <TextField
              label="Metric name"
              value={thresholdForm.metricName}
              onChange={(event) =>
                setThresholdForm({ ...thresholdForm, metricName: event.target.value })
              }
              helperText={
                metricNames.length
                  ? `Known metrics: ${metricNames.slice(0, 8).join(', ')}`
                  : 'Start monitoring first to discover metric names.'
              }
              fullWidth
            />
            <FormControl fullWidth>
              <InputLabel>Operator</InputLabel>
              <Select
                label="Operator"
                value={thresholdForm.operator}
                onChange={(event) =>
                  setThresholdForm({
                    ...thresholdForm,
                    operator: event.target.value as ThresholdOperator,
                  })
                }
              >
                <MenuItem value="GREATER_THAN">Greater than or equal</MenuItem>
                <MenuItem value="LESS_THAN">Less than or equal</MenuItem>
              </Select>
            </FormControl>
            <Grid container spacing={2}>
              <Grid item xs={12} md={6}>
                <TextField
                  label="Warning value"
                  type="number"
                  value={thresholdForm.warningValue}
                  onChange={(event) =>
                    setThresholdForm({ ...thresholdForm, warningValue: event.target.value })
                  }
                  fullWidth
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  label="Critical value"
                  type="number"
                  value={thresholdForm.criticalValue}
                  onChange={(event) =>
                    setThresholdForm({ ...thresholdForm, criticalValue: event.target.value })
                  }
                  fullWidth
                />
              </Grid>
            </Grid>
            <FormControlLabel
              control={
                <Switch
                  checked={thresholdForm.enabled}
                  onChange={(event) =>
                    setThresholdForm({ ...thresholdForm, enabled: event.target.checked })
                  }
                />
              }
              label="Enabled"
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setThresholdDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleSaveThreshold} disabled={savingThreshold}>
            {savingThreshold ? 'Saving...' : 'Save'}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={deleteOpen} onClose={() => !deleting && setDeleteOpen(false)}>
        <DialogTitle>Delete {component?.name}?</DialogTitle>
        <DialogContent>
          <Typography>
            This removes the component from active monitoring and topology. Historical incidents and
            events will be preserved.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteOpen(false)} disabled={deleting}>
            Cancel
          </Button>
          <Button color="error" variant="contained" onClick={handleDelete} disabled={deleting}>
            {deleting ? 'Deleting...' : 'Delete component'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default ComponentDetailPage;
