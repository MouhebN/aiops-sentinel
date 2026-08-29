import { Alert, Box, Grid, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material';
import { DataGrid, GridColDef, GridPaginationModel } from '@mui/x-data-grid';
import { aiopsApi, AuditAction, AuditLog } from 'api/aiopsApi';
import ApiState from 'components/aiops/ApiState';
import FilterCard from 'components/aiops/FilterCard';
import IconActionButton from 'components/aiops/IconActionButton';
import PageHeader from 'components/common/PageHeader';
import dayjs from 'dayjs';
import { useCallback, useEffect, useMemo, useState } from 'react';

const AUDIT_ACTIONS: AuditAction[] = [
  'LOGIN_SUCCESS',
  'LOGIN_FAILED',
  'LOGOUT',
  'COMPONENT_CREATED',
  'COMPONENT_UPDATED',
  'COMPONENT_DELETED',
  'COMPONENT_RELATION_CREATED',
  'COMPONENT_RELATION_DELETED',
  'MONITORING_STARTED',
  'MONITORING_STOPPED',
  'MANUAL_CHECK_TRIGGERED',
  'THRESHOLD_CREATED',
  'THRESHOLD_UPDATED',
  'THRESHOLD_DELETED',
  'INCIDENT_ACKNOWLEDGED',
  'INCIDENT_UNACKNOWLEDGED',
  'INCIDENT_RESOLVED',
  'INCIDENT_DELETED',
  'INCIDENT_REBUILT',
  'EVENT_DELETED',
  'OLD_EVENTS_PURGED',
  'DEMO_DATA_RESET',
  'AI_ANALYSIS_REQUESTED',
  'REPORT_CREATED',
  'REPORT_EXPORTED',
  'PACKET_CAPTURE_UPLOADED',
  'PACKET_CAPTURE_ANALYZED',
  'PCAP_ANALYSIS_DELETE',
  'NETFLOW_SOURCE_CREATED',
  'NETFLOW_SOURCE_UPDATED',
  'NETFLOW_IMPORT_STARTED',
  'NETFLOW_IMPORT_COMPLETED',
  'NETFLOW_IMPORT_FAILED',
  'NETFLOW_ANOMALY_DETECTED',
  'SYSLOG_SOURCE_CREATED',
  'SYSLOG_SOURCE_UPDATED',
  'SYSLOG_SOURCE_DELETED',
  'USER_CREATED',
  'USER_UPDATED',
  'USER_ROLE_CHANGED',
  'USER_DISABLED',
  'USER_ENABLED',
  'USER_PASSWORD_RESET',
];

const columns: GridColDef<AuditLog>[] = [
  {
    field: 'createdAt',
    headerName: 'Date',
    minWidth: 180,
    flex: 0.9,
    valueFormatter: (value) => dayjs(value as string).format('DD/MM/YYYY HH:mm:ss'),
  },
  { field: 'username', headerName: 'User', minWidth: 180, flex: 0.8 },
  {
    field: 'userRole',
    headerName: 'Role',
    minWidth: 110,
    flex: 0.45,
    valueGetter: (value) => value || 'SYSTEM',
  },
  { field: 'action', headerName: 'Action', minWidth: 220, flex: 0.95 },
  {
    field: 'target',
    headerName: 'Target',
    minWidth: 200,
    flex: 0.9,
    valueGetter: (_, row) => [row.targetType, row.targetId].filter(Boolean).join(' #') || '-',
  },
  {
    field: 'details',
    headerName: 'Details',
    minWidth: 360,
    flex: 1.6,
    valueGetter: (value) => value || '-',
  },
  {
    field: 'ipAddress',
    headerName: 'IP',
    minWidth: 140,
    flex: 0.55,
    valueGetter: (value) => value || '-',
  },
];

const AuditLogsPage = () => {
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [usernameFilter, setUsernameFilter] = useState('');
  const [actionFilter, setActionFilter] = useState<AuditAction | ''>('');
  const [fromFilter, setFromFilter] = useState('');
  const [toFilter, setToFilter] = useState('');
  const [paginationModel, setPaginationModel] = useState<GridPaginationModel>({
    page: 0,
    pageSize: 20,
  });
  const [rowCount, setRowCount] = useState(0);

  const queryParams = useMemo(
    () => ({
      page: paginationModel.page,
      size: paginationModel.pageSize,
      username: usernameFilter.trim() || undefined,
      action: actionFilter || undefined,
      from: fromFilter ? dayjs(fromFilter).toISOString() : undefined,
      to: toFilter ? dayjs(toFilter).toISOString() : undefined,
    }),
    [
      actionFilter,
      fromFilter,
      paginationModel.page,
      paginationModel.pageSize,
      toFilter,
      usernameFilter,
    ],
  );

  const refresh = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await aiopsApi.getAuditLogs(queryParams);
      setLogs(response.content);
      setRowCount(response.totalElements);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Unknown audit log error');
    } finally {
      setLoading(false);
    }
  }, [queryParams]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const resetFilters = () => {
    setUsernameFilter('');
    setActionFilter('');
    setFromFilter('');
    setToFilter('');
    setPaginationModel((current) => ({ ...current, page: 0 }));
  };

  return (
    <Box sx={{ pb: 2 }}>
      <PageHeader
        actions={
          <Stack direction="row" spacing={1}>
            <IconActionButton icon="mdi:refresh" label="Refresh audit logs" onClick={refresh} />
            <IconActionButton
              icon="mdi:filter-off-outline"
              label="Reset filters"
              onClick={resetFilters}
            />
          </Stack>
        }
      >
        Audit Logs
      </PageHeader>
      <Stack spacing={3} mt={3}>
        <ApiState loading={loading} error={error} onRetry={refresh} />
        {error && <Alert severity="error">{error}</Alert>}

        <FilterCard title="Traceability Filters">
          <Grid container spacing={2}>
            <Grid item xs={12} md={3}>
              <TextField
                fullWidth
                size="small"
                label="Username"
                value={usernameFilter}
                onChange={(event) => {
                  setUsernameFilter(event.target.value);
                  setPaginationModel((current) => ({ ...current, page: 0 }));
                }}
              />
            </Grid>
            <Grid item xs={12} md={3}>
              <TextField
                select
                fullWidth
                size="small"
                label="Action"
                value={actionFilter}
                onChange={(event) => {
                  setActionFilter(event.target.value as AuditAction | '');
                  setPaginationModel((current) => ({ ...current, page: 0 }));
                }}
              >
                <MenuItem value="">All actions</MenuItem>
                {AUDIT_ACTIONS.map((action) => (
                  <MenuItem key={action} value={action}>
                    {action}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid item xs={12} md={3}>
              <TextField
                fullWidth
                size="small"
                type="datetime-local"
                label="From"
                InputLabelProps={{ shrink: true }}
                value={fromFilter}
                onChange={(event) => {
                  setFromFilter(event.target.value);
                  setPaginationModel((current) => ({ ...current, page: 0 }));
                }}
              />
            </Grid>
            <Grid item xs={12} md={3}>
              <TextField
                fullWidth
                size="small"
                type="datetime-local"
                label="To"
                InputLabelProps={{ shrink: true }}
                value={toFilter}
                onChange={(event) => {
                  setToFilter(event.target.value);
                  setPaginationModel((current) => ({ ...current, page: 0 }));
                }}
              />
            </Grid>
          </Grid>
        </FilterCard>

        <Paper
          sx={{ p: 2, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
          elevation={0}
        >
          <Typography variant="h5" mb={2}>
            Sensitive User Actions
          </Typography>
          <DataGrid
            rows={logs}
            columns={columns}
            loading={loading}
            autoHeight
            disableRowSelectionOnClick
            paginationMode="server"
            rowCount={rowCount}
            pageSizeOptions={[10, 20, 50]}
            paginationModel={paginationModel}
            onPaginationModelChange={setPaginationModel}
          />
        </Paper>
      </Stack>
    </Box>
  );
};

export default AuditLogsPage;
