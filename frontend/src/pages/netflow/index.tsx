import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  Grid,
  MenuItem,
  Paper,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@mui/material';
import { DataGrid, GridColDef, GridPaginationModel } from '@mui/x-data-grid';
import {
  aiopsApi,
  NetFlowAnomalyType,
  NetFlowImportRun,
  NetFlowSource,
  NetworkFlow,
  SaveNetFlowSourcePayload,
} from 'api/aiopsApi';
import { useAuth } from 'auth/AuthContext';
import { canManageNetFlow } from 'auth/permissions';
import ApiState from 'components/aiops/ApiState';
import IconActionButton from 'components/aiops/IconActionButton';
import PageHeader from 'components/common/PageHeader';
import dayjs from 'dayjs';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';

const ANOMALY_TYPES: NetFlowAnomalyType[] = [
  'PORT_SCAN',
  'MANY_DESTINATIONS',
  'SENSITIVE_PORT_ACCESS',
  'HIGH_VOLUME_TRANSFER',
];

interface NetFlowSourceForm {
  name: string;
  providerType: 'NFDUMP';
  dataDirectory: string;
  collectorPort: number;
  enabled: boolean;
}

const emptyForm: NetFlowSourceForm = {
  name: '',
  providerType: 'NFDUMP',
  dataDirectory: './runtime/netflow',
  collectorPort: 2055,
  enabled: true,
};

const toPayload = (form: NetFlowSourceForm): SaveNetFlowSourcePayload => ({
  name: form.name.trim(),
  providerType: form.providerType,
  dataDirectory: form.dataDirectory.trim(),
  collectorPort: form.collectorPort,
  enabled: form.enabled,
});

const toForm = (source: NetFlowSource): NetFlowSourceForm => ({
  name: source.name,
  providerType: source.providerType,
  dataDirectory: source.dataDirectory,
  collectorPort: source.collectorPort,
  enabled: source.enabled,
});

const NetFlowPage = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  const canManage = canManageNetFlow(user?.role);
  const [sources, setSources] = useState<NetFlowSource[]>([]);
  const [flows, setFlows] = useState<NetworkFlow[]>([]);
  const [runs, setRuns] = useState<NetFlowImportRun[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [importing, setImporting] = useState(false);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingSource, setEditingSource] = useState<NetFlowSource | null>(null);
  const [form, setForm] = useState<NetFlowSourceForm>(emptyForm);
  const [sourceFilter, setSourceFilter] = useState('');
  const [destinationFilter, setDestinationFilter] = useState('');
  const [protocolFilter, setProtocolFilter] = useState('');
  const [suspiciousOnly, setSuspiciousOnly] = useState(false);
  const [anomalyTypeFilter, setAnomalyTypeFilter] = useState<NetFlowAnomalyType | ''>('');
  const [paginationModel, setPaginationModel] = useState<GridPaginationModel>({
    page: 0,
    pageSize: 20,
  });
  const [rowCount, setRowCount] = useState(0);

  const refresh = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const [sourcesResponse, runsResponse, flowsResponse] = await Promise.all([
        aiopsApi.getNetFlowSources(),
        aiopsApi.getNetFlowImportRuns(),
        aiopsApi.getNetFlows({
          sourceIp: sourceFilter || undefined,
          destinationIp: destinationFilter || undefined,
          protocol: protocolFilter || undefined,
          suspicious: suspiciousOnly || undefined,
          anomalyType: anomalyTypeFilter || undefined,
          page: paginationModel.page,
          size: paginationModel.pageSize,
        }),
      ]);
      setSources(sourcesResponse);
      setRuns(runsResponse);
      setFlows(flowsResponse.content);
      setRowCount(flowsResponse.totalElements);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Unknown NetFlow error');
    } finally {
      setLoading(false);
    }
  }, [
    anomalyTypeFilter,
    destinationFilter,
    paginationModel.page,
    paginationModel.pageSize,
    protocolFilter,
    sourceFilter,
    suspiciousOnly,
  ]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const latestRun = runs[0] || null;
  const totalFlowsToday = flows.filter((flow) =>
    dayjs(flow.startTime).isSame(dayjs(), 'day'),
  ).length;
  const suspiciousFlowCount = flows.filter((flow) => flow.suspicious).length;
  const topSourceIp =
    Object.entries(
      flows.reduce<Record<string, number>>((accumulator, flow) => {
        accumulator[flow.sourceIp] = (accumulator[flow.sourceIp] || 0) + 1;
        return accumulator;
      }, {}),
    ).sort((left, right) => right[1] - left[1])[0]?.[0] || '-';
  const topDestinationPorts =
    Object.entries(
      flows.reduce<Record<string, number>>((accumulator, flow) => {
        const key = String(flow.destinationPort ?? '-');
        accumulator[key] = (accumulator[key] || 0) + 1;
        return accumulator;
      }, {}),
    )
      .sort((left, right) => right[1] - left[1])
      .slice(0, 3)
      .map(([port]) => port)
      .join(', ') || '-';

  const openCreateDialog = () => {
    setEditingSource(null);
    setForm(emptyForm);
    setActionError(null);
    setDialogOpen(true);
  };

  const openEditDialog = (source: NetFlowSource) => {
    setEditingSource(source);
    setForm(toForm(source));
    setActionError(null);
    setDialogOpen(true);
  };

  const handleSave = async () => {
    try {
      setSaving(true);
      setActionError(null);
      if (editingSource) {
        await aiopsApi.updateNetFlowSource(editingSource.id, toPayload(form));
      } else {
        await aiopsApi.createNetFlowSource(toPayload(form));
      }
      setDialogOpen(false);
      await refresh();
    } catch (requestError) {
      setActionError(
        requestError instanceof Error ? requestError.message : 'Could not save NetFlow source',
      );
    } finally {
      setSaving(false);
    }
  };

  const handleImportLatest = async (sourceId: number) => {
    try {
      setImporting(true);
      setActionError(null);
      await aiopsApi.importLatestNetFlows(sourceId);
      await refresh();
    } catch (requestError) {
      setActionError(
        requestError instanceof Error ? requestError.message : 'Could not import latest flows',
      );
    } finally {
      setImporting(false);
    }
  };

  const handleImportSample = async () => {
    try {
      setImporting(true);
      setActionError(null);
      await aiopsApi.importSampleNetFlows();
      await refresh();
    } catch (requestError) {
      setActionError(
        requestError instanceof Error ? requestError.message : 'Could not import demo flows',
      );
    } finally {
      setImporting(false);
    }
  };

  const sourceColumns = useMemo<GridColDef<NetFlowSource>[]>(
    () => [
      { field: 'name', headerName: 'Source', minWidth: 220, flex: 1 },
      { field: 'providerType', headerName: 'Provider', minWidth: 120, flex: 0.5 },
      { field: 'dataDirectory', headerName: 'Data Directory', minWidth: 200, flex: 1 },
      { field: 'collectorPort', headerName: 'Port', minWidth: 90, flex: 0.4 },
      {
        field: 'enabled',
        headerName: 'Status',
        minWidth: 110,
        flex: 0.5,
        renderCell: (params) => (
          <Chip
            size="small"
            label={params.row.enabled ? 'Enabled' : 'Disabled'}
            color={params.row.enabled ? 'success' : 'default'}
          />
        ),
      },
      {
        field: 'lastImportStatus',
        headerName: 'Last Import',
        minWidth: 120,
        flex: 0.5,
        valueGetter: (value) => value || '-',
      },
      {
        field: 'actions',
        headerName: 'Actions',
        minWidth: 220,
        flex: 1,
        sortable: false,
        renderCell: (params) => (
          <Stack direction="row" spacing={1}>
            <IconActionButton
              icon="mdi:download-network-outline"
              label="Import latest flows"
              onClick={() => handleImportLatest(params.row.id)}
              disabled={importing || !params.row.enabled || !canManage}
            />
            <IconActionButton
              icon="mdi:pencil-outline"
              label="Edit NetFlow source"
              onClick={() => openEditDialog(params.row)}
              disabled={!canManage}
            />
          </Stack>
        ),
      },
    ],
    [canManage, importing],
  );

  const flowColumns = useMemo<GridColDef<NetworkFlow>[]>(
    () => [
      {
        field: 'startTime',
        headerName: 'Time',
        minWidth: 170,
        flex: 0.8,
        valueFormatter: (value) => dayjs(value as string).format('DD/MM HH:mm:ss'),
      },
      { field: 'sourceIp', headerName: 'Source IP', minWidth: 150, flex: 0.8 },
      { field: 'destinationIp', headerName: 'Destination IP', minWidth: 150, flex: 0.8 },
      { field: 'sourcePort', headerName: 'Src Port', minWidth: 90, flex: 0.45 },
      { field: 'destinationPort', headerName: 'Dst Port', minWidth: 90, flex: 0.45 },
      { field: 'protocol', headerName: 'Protocol', minWidth: 90, flex: 0.45 },
      { field: 'packets', headerName: 'Packets', minWidth: 100, flex: 0.45 },
      { field: 'bytes', headerName: 'Bytes', minWidth: 120, flex: 0.55 },
      {
        field: 'suspicious',
        headerName: 'Suspicious',
        minWidth: 110,
        flex: 0.5,
        renderCell: (params) => (
          <Chip
            size="small"
            label={params.row.suspicious ? 'Yes' : 'No'}
            color={params.row.suspicious ? 'error' : 'default'}
          />
        ),
      },
      {
        field: 'anomalyType',
        headerName: 'Anomaly',
        minWidth: 170,
        flex: 0.8,
        valueGetter: (value) => value || '-',
      },
      {
        field: 'incidentId',
        headerName: 'Incident',
        minWidth: 110,
        flex: 0.5,
        renderCell: (params) =>
          params.row.incidentId ? (
            <Button size="small" onClick={() => navigate(`/incidents/${params.row.incidentId}`)}>
              #{params.row.incidentId}
            </Button>
          ) : (
            <Typography variant="body2" color="text.secondary">
              -
            </Typography>
          ),
      },
    ],
    [navigate],
  );

  return (
    <Box sx={{ pb: 2 }}>
      <PageHeader
        actions={
          <Stack direction="row" spacing={1}>
            <IconActionButton icon="mdi:refresh" label="Refresh NetFlow" onClick={refresh} />
            <IconActionButton
              icon="mdi:plus"
              label="Add NetFlow source"
              color="primary"
              onClick={openCreateDialog}
              disabled={!canManage}
            />
          </Stack>
        }
      >
        NetFlow Traffic Analysis
      </PageHeader>

      <Stack spacing={3} mt={3}>
        <ApiState loading={loading} error={error} onRetry={refresh} />
        {actionError && <Alert severity="error">{actionError}</Alert>}

        <Grid container spacing={2.5}>
          <Grid item xs={12} sm={6} lg={2.4}>
            <Paper
              sx={{ p: 2, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
              elevation={0}
            >
              <Typography variant="caption" color="text.secondary">
                Total flows today
              </Typography>
              <Typography variant="h5">{totalFlowsToday}</Typography>
            </Paper>
          </Grid>
          <Grid item xs={12} sm={6} lg={2.4}>
            <Paper
              sx={{ p: 2, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
              elevation={0}
            >
              <Typography variant="caption" color="text.secondary">
                Suspicious flows
              </Typography>
              <Typography variant="h5">{suspiciousFlowCount}</Typography>
            </Paper>
          </Grid>
          <Grid item xs={12} sm={6} lg={2.4}>
            <Paper
              sx={{ p: 2, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
              elevation={0}
            >
              <Typography variant="caption" color="text.secondary">
                Top source IP
              </Typography>
              <Typography variant="h5">{topSourceIp}</Typography>
            </Paper>
          </Grid>
          <Grid item xs={12} sm={6} lg={2.4}>
            <Paper
              sx={{ p: 2, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
              elevation={0}
            >
              <Typography variant="caption" color="text.secondary">
                Top destination ports
              </Typography>
              <Typography variant="h5">{topDestinationPorts}</Typography>
            </Paper>
          </Grid>
          <Grid item xs={12} sm={6} lg={2.4}>
            <Paper
              sx={{ p: 2, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
              elevation={0}
            >
              <Typography variant="caption" color="text.secondary">
                Latest import status
              </Typography>
              <Typography variant="h5">{latestRun?.status || '-'}</Typography>
            </Paper>
          </Grid>
        </Grid>

        <Paper
          sx={{ p: 2.5, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
          elevation={0}
        >
          <Stack spacing={2}>
            <Stack
              direction={{ xs: 'column', md: 'row' }}
              justifyContent="space-between"
              spacing={1}
            >
              <Typography variant="h5">NetFlow Sources</Typography>
              <Stack direction="row" spacing={1}>
                <IconActionButton
                  icon="mdi:download-network-outline"
                  label={importing ? 'Importing latest flows...' : 'Import latest flows'}
                  onClick={() => sources[0] && handleImportLatest(sources[0].id)}
                  disabled={importing || !sources.length || !canManage}
                />
                <IconActionButton
                  icon="mdi:flask-outline"
                  label={importing ? 'Importing demo flows...' : 'Demo sample import'}
                  onClick={handleImportSample}
                  disabled={importing || !canManage}
                  color="warning"
                />
              </Stack>
            </Stack>
            <Typography color="text.secondary">
              The demo import creates realistic sample flows when the collector or nfdump data is
              not available.
            </Typography>
            <DataGrid
              rows={sources}
              columns={sourceColumns}
              autoHeight
              disableRowSelectionOnClick
              pageSizeOptions={[5, 10]}
              initialState={{ pagination: { paginationModel: { pageSize: 5 } } }}
            />
          </Stack>
        </Paper>

        <Paper
          sx={{ p: 2.5, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
          elevation={0}
        >
          <Typography variant="h5" mb={2}>
            Latest Import Run
          </Typography>
          {latestRun ? (
            <Grid container spacing={2}>
              <Grid item xs={12} md={2}>
                <Typography variant="body2">
                  Status: <strong>{latestRun.status}</strong>
                </Typography>
              </Grid>
              <Grid item xs={12} md={2}>
                <Typography variant="body2">
                  Read: <strong>{latestRun.recordsRead}</strong>
                </Typography>
              </Grid>
              <Grid item xs={12} md={2}>
                <Typography variant="body2">
                  Imported: <strong>{latestRun.recordsImported}</strong>
                </Typography>
              </Grid>
              <Grid item xs={12} md={2}>
                <Typography variant="body2">
                  Suspicious: <strong>{latestRun.suspiciousFlows}</strong>
                </Typography>
              </Grid>
              <Grid item xs={12} md={2}>
                <Typography variant="body2">
                  Incidents: <strong>{latestRun.incidentsCreated}</strong>
                </Typography>
              </Grid>
              <Grid item xs={12} md={2}>
                <Typography variant="body2">
                  Started:{' '}
                  <strong>{dayjs(latestRun.startedAt).format('DD/MM/YYYY HH:mm:ss')}</strong>
                </Typography>
              </Grid>
              {latestRun.errorMessage && (
                <Grid item xs={12}>
                  <Alert severity="warning">{latestRun.errorMessage}</Alert>
                </Grid>
              )}
            </Grid>
          ) : (
            <Typography color="text.secondary">No NetFlow import run recorded yet.</Typography>
          )}
        </Paper>

        <Paper
          sx={{ p: 2.5, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
          elevation={0}
        >
          <Stack spacing={2}>
            <Typography variant="h5">Network Flows</Typography>
            <Grid container spacing={2}>
              <Grid item xs={12} md={3}>
                <TextField
                  fullWidth
                  label="Source IP"
                  value={sourceFilter}
                  onChange={(event) => setSourceFilter(event.target.value)}
                />
              </Grid>
              <Grid item xs={12} md={3}>
                <TextField
                  fullWidth
                  label="Destination IP"
                  value={destinationFilter}
                  onChange={(event) => setDestinationFilter(event.target.value)}
                />
              </Grid>
              <Grid item xs={12} md={2}>
                <TextField
                  fullWidth
                  label="Protocol"
                  value={protocolFilter}
                  onChange={(event) => setProtocolFilter(event.target.value)}
                />
              </Grid>
              <Grid item xs={12} md={2}>
                <TextField
                  select
                  fullWidth
                  label="Anomaly type"
                  value={anomalyTypeFilter}
                  onChange={(event) =>
                    setAnomalyTypeFilter(event.target.value as NetFlowAnomalyType | '')
                  }
                >
                  <MenuItem value="">All</MenuItem>
                  {ANOMALY_TYPES.map((type) => (
                    <MenuItem key={type} value={type}>
                      {type}
                    </MenuItem>
                  ))}
                </TextField>
              </Grid>
              <Grid item xs={12} md={2}>
                <FormControlLabel
                  control={
                    <Switch
                      checked={suspiciousOnly}
                      onChange={(event) => setSuspiciousOnly(event.target.checked)}
                    />
                  }
                  label="Suspicious only"
                />
              </Grid>
            </Grid>
            <DataGrid
              rows={flows}
              columns={flowColumns}
              autoHeight
              disableRowSelectionOnClick
              rowCount={rowCount}
              paginationMode="server"
              paginationModel={paginationModel}
              onPaginationModelChange={setPaginationModel}
              pageSizeOptions={[10, 20, 50]}
            />
          </Stack>
        </Paper>
      </Stack>

      <Dialog
        open={dialogOpen}
        onClose={() => !saving && setDialogOpen(false)}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>{editingSource ? 'Edit NetFlow Source' : 'Add NetFlow Source'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} mt={1}>
            <TextField
              fullWidth
              label="Name"
              value={form.name}
              onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
            />
            <TextField
              fullWidth
              label="Data Directory"
              value={form.dataDirectory}
              onChange={(event) =>
                setForm((current) => ({ ...current, dataDirectory: event.target.value }))
              }
            />
            <TextField
              fullWidth
              type="number"
              label="Collector Port"
              value={form.collectorPort}
              onChange={(event) =>
                setForm((current) => ({ ...current, collectorPort: Number(event.target.value) }))
              }
            />
            <FormControlLabel
              control={
                <Switch
                  checked={form.enabled}
                  onChange={(event) =>
                    setForm((current) => ({ ...current, enabled: event.target.checked }))
                  }
                />
              }
              label="Enabled"
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)} disabled={saving}>
            Cancel
          </Button>
          <Button onClick={handleSave} variant="contained" disabled={saving}>
            {saving ? 'Saving...' : 'Save'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default NetFlowPage;
