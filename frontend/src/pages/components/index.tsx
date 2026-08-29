import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormControlLabel,
  InputLabel,
  ListItemText,
  MenuItem,
  OutlinedInput,
  Paper,
  Select,
  Snackbar,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@mui/material';
import { DataGrid, GridColDef } from '@mui/x-data-grid';
import {
  aiopsApi,
  ComponentConnectionCheck,
  MonitoredComponent,
  SaveMonitoredComponentPayload,
} from 'api/aiopsApi';
import { useAuth } from 'auth/AuthContext';
import { canManageComponents, canRunManualChecks } from 'auth/permissions';
import IconActionButton from 'components/aiops/IconActionButton';
import StatusChip from 'components/aiops/StatusChip';
import PageHeader from 'components/common/PageHeader';
import { formatDeviceType } from 'helpers/aiops';
import dayjs from 'dayjs';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  CRITICALITIES,
  Criticality,
  DEVICE_TYPES,
  DeviceType,
  MONITORING_METHODS,
  MonitoringMethod,
} from 'types/aiops';
import NetworkInterfacesSection from './NetworkInterfacesSection';

interface ComponentFormState {
  name: string;
  type: DeviceType;
  ipAddress: string;
  httpUrl: string;
  tcpPort: string;
  snmpPort: string;
  snmpCommunity: string;
  snmpOid: string;
  location: string;
  criticality: Criticality;
  monitoringMethods: MonitoringMethod[];
  checkIntervalSeconds: number;
  enabled: boolean;
}

const emptyForm: ComponentFormState = {
  name: '',
  type: 'SERVER',
  ipAddress: '',
  httpUrl: '',
  tcpPort: '',
  snmpPort: '161',
  snmpCommunity: '',
  snmpOid: '1.3.6.1.2.1.1.1.0',
  location: '',
  criticality: 'MEDIUM',
  monitoringMethods: ['PING'],
  checkIntervalSeconds: 30,
  enabled: true,
};

const toFormState = (component: MonitoredComponent): ComponentFormState => ({
  name: component.name,
  type: component.type,
  ipAddress: component.ipAddress || '',
  httpUrl: component.httpUrl || '',
  tcpPort: component.tcpPort ? String(component.tcpPort) : '',
  snmpPort: component.snmpPort ? String(component.snmpPort) : '161',
  snmpCommunity: component.snmpCommunity || '',
  snmpOid: component.snmpOid || '1.3.6.1.2.1.1.1.0',
  location: component.location,
  criticality: component.criticality,
  monitoringMethods: component.monitoringMethods,
  checkIntervalSeconds: component.checkIntervalSeconds,
  enabled: component.enabled,
});

const toPayload = (form: ComponentFormState): SaveMonitoredComponentPayload => ({
  name: form.name.trim(),
  type: form.type,
  ipAddress: form.ipAddress.trim() || undefined,
  httpUrl: form.httpUrl.trim() || undefined,
  tcpPort: form.tcpPort ? Number(form.tcpPort) : undefined,
  snmpPort: form.snmpPort ? Number(form.snmpPort) : undefined,
  snmpCommunity: form.snmpCommunity.trim(),
  snmpOid: form.snmpOid.trim() || undefined,
  location: form.location.trim(),
  criticality: form.criticality,
  monitoringMethods: form.monitoringMethods,
  checkIntervalSeconds: form.checkIntervalSeconds,
  enabled: form.enabled,
});

const compactFieldSx = {
  '& .MuiFormHelperText-root': {
    mx: 0,
  },
};

const ComponentsPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { user } = useAuth();
  const canManage = canManageComponents(user?.role);
  const canCheck = canRunManualChecks(user?.role);
  const [components, setComponents] = useState<MonitoredComponent[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingComponent, setEditingComponent] = useState<MonitoredComponent | null>(null);
  const [form, setForm] = useState<ComponentFormState>(emptyForm);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [checkingId, setCheckingId] = useState<number | null>(null);
  const [checkResult, setCheckResult] = useState<ComponentConnectionCheck | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<MonitoredComponent | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      setComponents(await aiopsApi.getComponents());
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Unknown components error');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  useEffect(() => {
    const deletedName = (location.state as { deletedComponentName?: string } | null)
      ?.deletedComponentName;
    if (!deletedName) {
      return;
    }
    setSuccessMessage(`${deletedName} was removed from monitoring and topology.`);
    navigate(location.pathname, { replace: true, state: {} });
  }, [location, navigate]);

  useEffect(() => {
    const interval = window.setInterval(() => {
      refresh();
    }, 5000);

    return () => window.clearInterval(interval);
  }, [refresh]);

  const columns = useMemo<GridColDef<MonitoredComponent>[]>(
    () => [
      {
        field: 'lastStatus',
        headerName: 'Status',
        width: 118,
        renderCell: (params) => <StatusChip status={params.row.lastStatus} />,
      },
      {
        field: 'name',
        headerName: 'Component',
        minWidth: 260,
        flex: 1.5,
        renderCell: (params) => (
          <Stack spacing={0.25} sx={{ py: 0.75, minWidth: 0 }}>
            <Typography fontWeight={700} noWrap>
              {params.row.name}
            </Typography>
            <Typography variant="caption" color="text.secondary" noWrap>
              {formatDeviceType(params.row.type)} - {params.row.location}
            </Typography>
          </Stack>
        ),
      },
      {
        field: 'criticality',
        headerName: 'Criticality',
        width: 118,
        renderCell: (params) => (
          <Chip label={params.row.criticality} size="small" variant="outlined" />
        ),
      },
      {
        field: 'monitoringState',
        headerName: 'Monitoring',
        width: 120,
        renderCell: (params) => (
          <Chip
            label={params.row.enabled ? 'STARTED' : 'STOPPED'}
            size="small"
            color={params.row.enabled ? 'success' : 'default'}
            variant={params.row.enabled ? 'filled' : 'outlined'}
          />
        ),
      },
      {
        field: 'lastCheckedAt',
        headerName: 'Last Checked',
        width: 150,
        valueFormatter: (value) => (value ? dayjs(value as string).format('DD/MM HH:mm') : 'Never'),
      },
      {
        field: 'actions',
        headerName: 'Actions',
        width: 220,
        sortable: false,
        align: 'right',
        headerAlign: 'right',
        renderCell: (params) => (
          <Stack direction="row" spacing={0.75} justifyContent="flex-end" sx={{ width: '100%' }}>
            <IconActionButton
              icon="mdi:eye-outline"
              label="View component"
              onClick={() => navigate(`/components/${params.row.id}`)}
            />
            <IconActionButton
              icon="mdi:connection"
              label={
                checkingId === params.row.id
                  ? 'Checking connection'
                  : params.row.enabled
                    ? 'Check connection'
                    : 'One-off check (does not resume monitoring)'
              }
              onClick={() => handleCheckNow(params.row)}
              disabled={checkingId === params.row.id || !canCheck}
              color="primary"
            />
            {canManage && (
              <IconActionButton
                icon="mdi:pencil-outline"
                label="Edit component"
                onClick={() => openEditDialog(params.row)}
              />
            )}
            {canManage && (
              <IconActionButton
                icon={params.row.enabled ? 'mdi:pause' : 'mdi:play'}
                label={params.row.enabled ? 'Stop monitoring' : 'Start monitoring'}
                onClick={() => handleToggleEnabled(params.row)}
                color={params.row.enabled ? 'warning' : 'success'}
              />
            )}
            {canManage && (
              <IconActionButton
                icon="mdi:delete-outline"
                label="Delete component"
                color="error"
                onClick={() => setDeleteTarget(params.row)}
              />
            )}
          </Stack>
        ),
      },
    ],
    [canCheck, canManage, checkingId, navigate],
  );

  const openCreateDialog = () => {
    setEditingComponent(null);
    setForm(emptyForm);
    setFormError(null);
    setDialogOpen(true);
  };

  const openEditDialog = (component: MonitoredComponent) => {
    setEditingComponent(component);
    setForm(toFormState(component));
    setFormError(null);
    setDialogOpen(true);
  };

  const validateForm = () => {
    if (!form.name.trim()) {
      return 'Component name is required.';
    }
    if (!form.location.trim()) {
      return 'Location or branch is required.';
    }
    if (!form.monitoringMethods.length) {
      return 'Select at least one monitoring method.';
    }
    if (form.monitoringMethods.includes('PING') && !form.ipAddress.trim()) {
      return 'IP address is required for PING monitoring.';
    }
    if (
      (form.monitoringMethods.includes('HTTP_HEALTH') ||
        form.monitoringMethods.includes('UPS_HTTP_METRICS')) &&
      !form.httpUrl.trim()
    ) {
      return 'HTTP URL is required for HTTP_HEALTH or UPS_HTTP_METRICS monitoring.';
    }
    if (
      (form.monitoringMethods.includes('TCP_PORT') ||
        form.monitoringMethods.includes('RTSP_HEALTH')) &&
      !form.ipAddress.trim()
    ) {
      return 'IP address is required for TCP_PORT or RTSP_HEALTH monitoring.';
    }
    if (
      (form.monitoringMethods.includes('TCP_PORT') ||
        form.monitoringMethods.includes('RTSP_HEALTH')) &&
      !form.tcpPort
    ) {
      return 'TCP/RTSP port is required for TCP_PORT or RTSP_HEALTH monitoring.';
    }
    if (
      (form.monitoringMethods.includes('SNMP_BASIC') ||
        form.monitoringMethods.includes('SNMP_ROUTER_METRICS') ||
        form.monitoringMethods.includes('SNMP_SERVER_METRICS') ||
        form.monitoringMethods.includes('UPS_SNMP_METRICS')) &&
      !form.ipAddress.trim()
    ) {
      return 'IP address is required for SNMP monitoring.';
    }
    if (
      (form.monitoringMethods.includes('SNMP_BASIC') ||
        form.monitoringMethods.includes('SNMP_ROUTER_METRICS') ||
        form.monitoringMethods.includes('SNMP_SERVER_METRICS') ||
        form.monitoringMethods.includes('UPS_SNMP_METRICS')) &&
      !form.snmpCommunity.trim()
    ) {
      return 'SNMP community is required for SNMP monitoring.';
    }
    if (form.checkIntervalSeconds < 5) {
      return 'Check interval must be at least 5 seconds.';
    }
    return null;
  };

  const handleSave = async () => {
    const validationError = validateForm();
    if (validationError) {
      setFormError(validationError);
      return;
    }

    try {
      setSaving(true);
      setFormError(null);
      const payload = toPayload(form);
      if (editingComponent) {
        await aiopsApi.updateComponent(editingComponent.id, payload);
      } else {
        await aiopsApi.createComponent(payload);
      }
      setDialogOpen(false);
      await refresh();
    } catch (requestError) {
      setFormError(requestError instanceof Error ? requestError.message : 'Unknown save error');
    } finally {
      setSaving(false);
    }
  };

  const handleToggleEnabled = async (component: MonitoredComponent) => {
    try {
      setError(null);
      if (component.enabled) {
        await aiopsApi.disableComponent(component.id);
      } else {
        await aiopsApi.enableComponent(component.id);
      }
      await refresh();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Unknown update error');
    }
  };

  const handleCheckNow = async (component: MonitoredComponent) => {
    try {
      setCheckingId(component.id);
      setError(null);
      setCheckResult(await aiopsApi.checkComponentNow(component.id));
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Unknown check error');
    } finally {
      setCheckingId(null);
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) {
      return;
    }
    try {
      setDeleting(true);
      setError(null);
      await aiopsApi.deleteComponent(deleteTarget.id);
      setSuccessMessage(`${deleteTarget.name} was removed from monitoring and topology.`);
      setDeleteTarget(null);
      await refresh();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Could not delete component');
    } finally {
      setDeleting(false);
    }
  };

  return (
    <Box sx={{ pb: 2 }}>
      <PageHeader
        actions={
          <Stack direction="row" spacing={1}>
            <IconActionButton icon="mdi:refresh" label="Refresh components" onClick={refresh} />
            {canManage && (
              <IconActionButton
                icon="mdi:plus"
                label="Add component"
                color="primary"
                onClick={openCreateDialog}
              />
            )}
          </Stack>
        }
      >
        Monitored Components
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
          sx={{ p: 2, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
          elevation={0}
        >
          <Typography variant="h5" mb={0.5}>
            Component Registry
          </Typography>
          <Typography color="text.secondary" mb={2}>
            Configure real or simulated components and start monitoring from the dashboard.
          </Typography>

          <DataGrid
            rows={components}
            columns={columns}
            loading={loading}
            autoHeight
            disableRowSelectionOnClick
            pageSizeOptions={[5, 10, 25]}
            initialState={{ pagination: { paginationModel: { pageSize: 10 } } }}
            getRowHeight={() => 'auto'}
            sx={{
              '& .MuiDataGrid-cell': {
                py: 1,
                alignItems: 'center',
              },
            }}
          />
        </Paper>
      </Stack>

      <Dialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        fullWidth
        maxWidth={editingComponent ? 'md' : 'sm'}
        PaperProps={{
          sx: { borderRadius: 2 },
        }}
      >
        <DialogTitle>{editingComponent ? 'Edit Component' : 'Add Component'}</DialogTitle>
        <DialogContent dividers sx={{ px: 3, py: 2 }}>
          <Stack spacing={1.75}>
            {formError && <Alert severity="error">{formError}</Alert>}

            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
              <TextField
                label="Name"
                value={form.name}
                onChange={(event) => setForm({ ...form, name: event.target.value })}
                fullWidth
                required
                size="small"
                sx={compactFieldSx}
              />
              <TextField
                label="Location / Branch"
                value={form.location}
                onChange={(event) => setForm({ ...form, location: event.target.value })}
                fullWidth
                required
                size="small"
                sx={compactFieldSx}
              />
            </Stack>

            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
              <TextField
                select
                label="Type"
                value={form.type}
                onChange={(event) => setForm({ ...form, type: event.target.value as DeviceType })}
                fullWidth
                required
                size="small"
                sx={compactFieldSx}
              >
                {DEVICE_TYPES.map((type) => (
                  <MenuItem key={type} value={type}>
                    {formatDeviceType(type)}
                  </MenuItem>
                ))}
              </TextField>
              <TextField
                select
                label="Criticality"
                value={form.criticality}
                onChange={(event) =>
                  setForm({ ...form, criticality: event.target.value as Criticality })
                }
                fullWidth
                size="small"
                sx={compactFieldSx}
              >
                {CRITICALITIES.map((criticality) => (
                  <MenuItem key={criticality} value={criticality}>
                    {criticality}
                  </MenuItem>
                ))}
              </TextField>
              <TextField
                label="Interval seconds"
                type="number"
                value={form.checkIntervalSeconds}
                onChange={(event) =>
                  setForm({
                    ...form,
                    checkIntervalSeconds: Number(event.target.value),
                  })
                }
                fullWidth
                size="small"
                inputProps={{ min: 5 }}
                sx={{ ...compactFieldSx, maxWidth: { sm: 150 } }}
              />
            </Stack>

            <FormControl fullWidth size="small" sx={compactFieldSx}>
              <InputLabel id="monitoring-methods-label">Monitoring Methods</InputLabel>
              <Select
                labelId="monitoring-methods-label"
                multiple
                value={form.monitoringMethods}
                onChange={(event) =>
                  setForm({
                    ...form,
                    monitoringMethods: event.target.value as MonitoringMethod[],
                  })
                }
                input={<OutlinedInput label="Monitoring Methods" />}
                renderValue={(selected) => (
                  <Box sx={{ display: 'flex', gap: 0.5, overflow: 'hidden' }}>
                    {(selected as MonitoringMethod[]).map((method) => (
                      <Chip
                        key={method}
                        label={method}
                        size="small"
                        variant="outlined"
                        sx={{ maxWidth: 150 }}
                      />
                    ))}
                  </Box>
                )}
                MenuProps={{
                  PaperProps: {
                    sx: { maxHeight: 320 },
                  },
                }}
              >
                {MONITORING_METHODS.map((method) => (
                  <MenuItem key={method} value={method} dense>
                    <Checkbox checked={form.monitoringMethods.includes(method)} size="small" />
                    <ListItemText primary={method} />
                  </MenuItem>
                ))}
              </Select>
            </FormControl>

            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
              <TextField
                label="IP Address"
                value={form.ipAddress}
                onChange={(event) => setForm({ ...form, ipAddress: event.target.value })}
                fullWidth
                helperText="Primary/management IP used by monitoring checks"
                size="small"
                sx={compactFieldSx}
              />
              <TextField
                label="HTTP Health URL"
                value={form.httpUrl}
                onChange={(event) => setForm({ ...form, httpUrl: event.target.value })}
                fullWidth
                helperText="Required for HTTP_HEALTH"
                size="small"
                sx={compactFieldSx}
              />
            </Stack>

            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
              <TextField
                label="TCP Port"
                type="number"
                value={form.tcpPort}
                onChange={(event) => setForm({ ...form, tcpPort: event.target.value })}
                fullWidth
                helperText="Required for TCP_PORT and RTSP_HEALTH"
                inputProps={{ min: 1, max: 65535 }}
                size="small"
                sx={compactFieldSx}
              />
              <TextField
                label="SNMP Port"
                type="number"
                value={form.snmpPort}
                onChange={(event) => setForm({ ...form, snmpPort: event.target.value })}
                fullWidth
                helperText="Default 161"
                inputProps={{ min: 1, max: 65535 }}
                size="small"
                sx={compactFieldSx}
              />
            </Stack>

            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
              <TextField
                label="SNMP Community"
                value={form.snmpCommunity}
                onChange={(event) => setForm({ ...form, snmpCommunity: event.target.value })}
                fullWidth
                helperText="Required for SNMP methods (e.g. banklab)"
                size="small"
                sx={compactFieldSx}
              />
              <TextField
                label="SNMP OID"
                value={form.snmpOid}
                onChange={(event) => setForm({ ...form, snmpOid: event.target.value })}
                fullWidth
                helperText="Default sysDescr.0"
                size="small"
                sx={compactFieldSx}
              />
            </Stack>

            <FormControlLabel
              control={
                <Switch
                  checked={form.enabled}
                  onChange={(event) => setForm({ ...form, enabled: event.target.checked })}
                />
              }
              label={editingComponent ? 'Monitoring enabled' : 'Start monitoring after save'}
              labelPlacement="end"
            />

            {editingComponent ? (
              <NetworkInterfacesSection
                componentId={editingComponent.id}
                interfaces={
                  components.find((item) => item.id === editingComponent.id)?.networkInterfaces ||
                  editingComponent.networkInterfaces ||
                  []
                }
                canManage={canManage}
                onChanged={refresh}
              />
            ) : (
              <Typography variant="body2" color="text.secondary">
                After saving, add LAN/WAN/service IPs from Edit or the component page. Monitoring
                continues to use the primary IP above.
              </Typography>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleSave} disabled={saving}>
            {saving ? 'Saving...' : 'Save Component'}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={Boolean(deleteTarget)} onClose={() => !deleting && setDeleteTarget(null)}>
        <DialogTitle>Delete {deleteTarget?.name}?</DialogTitle>
        <DialogContent>
          <Typography>
            This removes the component from active monitoring and topology. Historical incidents and
            events will be preserved.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteTarget(null)} disabled={deleting}>
            Cancel
          </Button>
          <Button color="error" variant="contained" onClick={handleDelete} disabled={deleting}>
            {deleting ? 'Deleting...' : 'Delete component'}
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={Boolean(successMessage)}
        autoHideDuration={4000}
        onClose={() => setSuccessMessage(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert severity="success" onClose={() => setSuccessMessage(null)}>
          {successMessage}
        </Alert>
      </Snackbar>
    </Box>
  );
};

export default ComponentsPage;
