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
import { DataGrid, GridColDef } from '@mui/x-data-grid';
import { aiopsApi, SaveSyslogSourcePayload, SyslogParserProfile, SyslogSource } from 'api/aiopsApi';
import ApiState from 'components/aiops/ApiState';
import IconActionButton from 'components/aiops/IconActionButton';
import PageHeader from 'components/common/PageHeader';
import { formatDeviceType } from 'helpers/aiops';
import { DEVICE_TYPES, DeviceType } from 'types/aiops';
import dayjs from 'dayjs';
import { useCallback, useEffect, useMemo, useState } from 'react';

const PARSER_PROFILES: SyslogParserProfile[] = [
  'GENERIC',
  'FIREWALL',
  'SWITCH',
  'LINUX_AUTH',
  'UPS',
  'CAMERA',
  'APPLICATION',
];

interface SyslogSourceForm {
  name: string;
  expectedHost: string;
  deviceId: string;
  deviceName: string;
  deviceType: DeviceType;
  location: string;
  parserProfile: SyslogParserProfile;
  enabled: boolean;
}

const emptyForm: SyslogSourceForm = {
  name: '',
  expectedHost: '',
  deviceId: '',
  deviceName: '',
  deviceType: 'FIREWALL',
  location: '',
  parserProfile: 'GENERIC',
  enabled: true,
};

const toForm = (source: SyslogSource): SyslogSourceForm => ({
  name: source.name,
  expectedHost: source.expectedHost,
  deviceId: source.deviceId,
  deviceName: source.deviceName,
  deviceType: source.deviceType,
  location: source.location,
  parserProfile: source.parserProfile,
  enabled: source.enabled,
});

const toPayload = (form: SyslogSourceForm): SaveSyslogSourcePayload => ({
  name: form.name.trim(),
  expectedHost: form.expectedHost.trim(),
  deviceId: form.deviceId.trim(),
  deviceName: form.deviceName.trim(),
  deviceType: form.deviceType,
  location: form.location.trim(),
  parserProfile: form.parserProfile,
  enabled: form.enabled,
});

const slugify = (value: string) =>
  value
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '');

const SyslogSourcesPage = () => {
  const [sources, setSources] = useState<SyslogSource[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingSource, setEditingSource] = useState<SyslogSource | null>(null);
  const [form, setForm] = useState<SyslogSourceForm>(emptyForm);

  const refresh = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      setSources(await aiopsApi.getSyslogSources());
    } catch (requestError) {
      setError(
        requestError instanceof Error ? requestError.message : 'Unknown syslog source error',
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const openCreateDialog = () => {
    setEditingSource(null);
    setForm(emptyForm);
    setFormError(null);
    setDialogOpen(true);
  };

  const openEditDialog = (source: SyslogSource) => {
    setEditingSource(source);
    setForm(toForm(source));
    setFormError(null);
    setDialogOpen(true);
  };

  const validate = () => {
    if (!form.name.trim()) {
      return 'Name is required.';
    }
    if (!form.expectedHost.trim()) {
      return 'Expected host or sender IP is required.';
    }
    if (!form.deviceId.trim()) {
      return 'Device ID is required.';
    }
    if (!form.deviceName.trim()) {
      return 'Device name is required.';
    }
    if (!form.location.trim()) {
      return 'Location is required.';
    }
    return null;
  };

  const handleSave = async () => {
    const validationError = validate();
    if (validationError) {
      setFormError(validationError);
      return;
    }

    try {
      setSaving(true);
      setFormError(null);
      if (editingSource) {
        await aiopsApi.updateSyslogSource(editingSource.id, toPayload(form));
      } else {
        await aiopsApi.createSyslogSource(toPayload(form));
      }
      setDialogOpen(false);
      await refresh();
    } catch (requestError) {
      setFormError(
        requestError instanceof Error ? requestError.message : 'Unknown syslog source save error',
      );
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (source: SyslogSource) => {
    try {
      setError(null);
      await aiopsApi.deleteSyslogSource(source.id);
      await refresh();
    } catch (requestError) {
      setError(
        requestError instanceof Error ? requestError.message : 'Unknown syslog source delete error',
      );
    }
  };

  const columns = useMemo<GridColDef<SyslogSource>[]>(
    () => [
      { field: 'name', headerName: 'Source', minWidth: 200, flex: 1 },
      { field: 'expectedHost', headerName: 'Expected Host/IP', minWidth: 170, flex: 0.9 },
      { field: 'deviceName', headerName: 'Device', minWidth: 220, flex: 1.1 },
      {
        field: 'deviceType',
        headerName: 'Type',
        minWidth: 130,
        flex: 0.7,
        valueGetter: (value) => formatDeviceType(value as string),
      },
      { field: 'location', headerName: 'Location', minWidth: 190, flex: 1 },
      { field: 'parserProfile', headerName: 'Parser', minWidth: 140, flex: 0.7 },
      {
        field: 'enabled',
        headerName: 'Enabled',
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
        field: 'updatedAt',
        headerName: 'Updated',
        minWidth: 170,
        flex: 0.8,
        valueFormatter: (value) => dayjs(value as string).format('DD/MM/YYYY HH:mm:ss'),
      },
      {
        field: 'actions',
        headerName: 'Actions',
        minWidth: 160,
        flex: 0.7,
        sortable: false,
        renderCell: (params) => (
          <Stack direction="row" spacing={1}>
            <IconActionButton
              icon="mdi:pencil-outline"
              label="Edit source"
              onClick={() => openEditDialog(params.row)}
            />
            <IconActionButton
              icon="mdi:trash-can-outline"
              label="Delete source"
              color="error"
              onClick={() => handleDelete(params.row)}
            />
          </Stack>
        ),
      },
    ],
    [],
  );

  return (
    <Box sx={{ pb: 2 }}>
      <PageHeader
        actions={
          <Stack direction="row" spacing={1}>
            <IconActionButton icon="mdi:refresh" label="Refresh syslog sources" onClick={refresh} />
            <IconActionButton
              icon="mdi:plus"
              label="Add syslog source"
              color="primary"
              onClick={openCreateDialog}
            />
          </Stack>
        }
      >
        Syslog Sources
      </PageHeader>

      <Stack spacing={3} mt={3}>
        <ApiState loading={loading} error={error} onRetry={refresh} />

        <Paper
          sx={{ p: 2, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
          elevation={0}
        >
          <Typography variant="h5" mb={2}>
            Configured Syslog Sources
          </Typography>
          <DataGrid
            rows={sources}
            columns={columns}
            loading={loading}
            autoHeight
            disableRowSelectionOnClick
            pageSizeOptions={[10, 25, 50]}
            initialState={{ pagination: { paginationModel: { pageSize: 10 } } }}
          />
        </Paper>

        <Paper
          sx={{ p: 2, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
          elevation={0}
        >
          <Typography variant="h5" mb={1.5}>
            Test UDP Syslog Ingestion
          </Typography>
          <Typography color="text.secondary" mb={2}>
            The backend UDP syslog listener uses port 5514 by default. These examples send test
            messages to the local collector.
          </Typography>
          <Stack spacing={1.25}>
            {[
              'echo "<134>firewall-01 DENY TCP 192.168.1.50:53000 -> 10.0.0.5:22" | nc -u -w1 127.0.0.1 5514',
              'echo "<134>switch-01 interface Gi0/1 link down" | nc -u -w1 127.0.0.1 5514',
              'echo "<134>linux-server sshd[123]: Failed password for invalid user admin from 192.168.1.77 port 54231 ssh2" | nc -u -w1 127.0.0.1 5514',
              'echo "<134>ups-01 battery low 12 percent" | nc -u -w1 127.0.0.1 5514',
              'echo "<134>camera-01 RTSP stream lost" | nc -u -w1 127.0.0.1 5514',
              'echo "<134>firewall-01 port scan detected source_ip=198.51.100.22 ports=22|80|443|8443" | nc -u -w1 127.0.0.1 5514',
            ].map((command) => (
              <Box
                key={command}
                component="pre"
                sx={{
                  m: 0,
                  p: 1.25,
                  borderRadius: 1.5,
                  bgcolor: 'grey.100',
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-word',
                  fontSize: 13,
                  fontFamily: 'monospace',
                }}
              >
                {command}
              </Box>
            ))}
          </Stack>
        </Paper>
      </Stack>

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="md" fullWidth>
        <DialogTitle>{editingSource ? 'Edit Syslog Source' : 'Add Syslog Source'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} mt={1}>
            {formError && <Alert severity="error">{formError}</Alert>}
            <Grid container spacing={2}>
              <Grid item xs={12} md={6}>
                <TextField
                  label="Source name"
                  value={form.name}
                  onChange={(event) => {
                    const name = event.target.value;
                    setForm({
                      ...form,
                      name,
                      deviceName: form.deviceName || name,
                      deviceId: form.deviceId || `syslog-${slugify(name)}`,
                    });
                  }}
                  fullWidth
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  label="Expected host or sender IP"
                  value={form.expectedHost}
                  onChange={(event) => setForm({ ...form, expectedHost: event.target.value })}
                  placeholder="bank-fw-01 or 192.168.1.10"
                  fullWidth
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  label="Device ID"
                  value={form.deviceId}
                  onChange={(event) => setForm({ ...form, deviceId: event.target.value })}
                  fullWidth
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  label="Device name"
                  value={form.deviceName}
                  onChange={(event) => setForm({ ...form, deviceName: event.target.value })}
                  fullWidth
                />
              </Grid>
              <Grid item xs={12} md={4}>
                <TextField
                  select
                  label="Device type"
                  value={form.deviceType}
                  onChange={(event) =>
                    setForm({ ...form, deviceType: event.target.value as DeviceType })
                  }
                  fullWidth
                >
                  {DEVICE_TYPES.map((type) => (
                    <MenuItem key={type} value={type}>
                      {formatDeviceType(type)}
                    </MenuItem>
                  ))}
                </TextField>
              </Grid>
              <Grid item xs={12} md={4}>
                <TextField
                  select
                  label="Parser profile"
                  value={form.parserProfile}
                  onChange={(event) =>
                    setForm({ ...form, parserProfile: event.target.value as SyslogParserProfile })
                  }
                  fullWidth
                >
                  {PARSER_PROFILES.map((profile) => (
                    <MenuItem key={profile} value={profile}>
                      {profile}
                    </MenuItem>
                  ))}
                </TextField>
              </Grid>
              <Grid item xs={12} md={4}>
                <TextField
                  label="Location"
                  value={form.location}
                  onChange={(event) => setForm({ ...form, location: event.target.value })}
                  fullWidth
                />
              </Grid>
              <Grid item xs={12}>
                <FormControlLabel
                  control={
                    <Switch
                      checked={form.enabled}
                      onChange={(event) => setForm({ ...form, enabled: event.target.checked })}
                    />
                  }
                  label="Enabled"
                />
              </Grid>
            </Grid>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleSave} disabled={saving}>
            {saving ? 'Saving...' : 'Save'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default SyslogSourcesPage;
