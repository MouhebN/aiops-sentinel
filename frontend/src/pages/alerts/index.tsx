import { Box, Grid, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material';
import { DataGrid, GridColDef } from '@mui/x-data-grid';
import { aiopsApi } from 'api/aiopsApi';
import PageHeader from 'components/common/PageHeader';
import ApiState from 'components/aiops/ApiState';
import FilterCard from 'components/aiops/FilterCard';
import IconActionButton from 'components/aiops/IconActionButton';
import SeverityChip from 'components/aiops/SeverityChip';
import { formatEventType } from 'helpers/aiops';
import { EventLog, Severity } from 'types/aiops';
import dayjs from 'dayjs';
import { useCallback, useEffect, useState } from 'react';

const columns: GridColDef<EventLog>[] = [
  {
    field: 'occurredAt',
    headerName: 'Time',
    minWidth: 170,
    flex: 1,
    valueFormatter: (value) => dayjs(value as string).format('DD/MM/YYYY HH:mm:ss'),
  },
  { field: 'deviceName', headerName: 'Device', minWidth: 180, flex: 1.1 },
  {
    field: 'eventType',
    headerName: 'Alert',
    minWidth: 190,
    flex: 1,
    valueGetter: (value) => formatEventType(value as string),
  },
  {
    field: 'severity',
    headerName: 'Severity',
    minWidth: 130,
    flex: 0.7,
    renderCell: (params) => <SeverityChip severity={params.row.severity} />,
  },
  { field: 'message', headerName: 'Message', minWidth: 280, flex: 1.6 },
  { field: 'details', headerName: 'Details', minWidth: 220, flex: 1.2 },
];

const AlertsPage = () => {
  const [alerts, setAlerts] = useState<EventLog[]>([]);
  const [minSeverity, setMinSeverity] = useState<Severity>('WARNING');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await aiopsApi.getAlerts({ minSeverity });
      setAlerts(response);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Unknown API error');
    } finally {
      setLoading(false);
    }
  }, [minSeverity]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  return (
    <Box sx={{ pb: 2 }}>
      <PageHeader
        actions={<IconActionButton icon="mdi:refresh" label="Refresh alerts" onClick={refresh} />}
      >
        Alerts
      </PageHeader>
      <Stack spacing={3} mt={3}>
        <ApiState loading={loading} error={error} onRetry={refresh} />
        <FilterCard title="Alert Filters">
          <Grid container spacing={2} alignItems="flex-start">
            <Grid item xs={12} md={4}>
              <TextField
                select
                fullWidth
                label="Minimum severity"
                value={minSeverity}
                onChange={(event) => setMinSeverity(event.target.value as Severity)}
              >
                <MenuItem value="INFO">INFO and above</MenuItem>
                <MenuItem value="WARNING">WARNING and above</MenuItem>
                <MenuItem value="CRITICAL">CRITICAL only</MenuItem>
              </TextField>
            </Grid>
            <Grid item xs={12} md={8}>
              <Stack direction="row" justifyContent="flex-end">
                <IconActionButton
                  icon="mdi:filter-check-outline"
                  label="Apply filters"
                  onClick={refresh}
                  color="primary"
                />
              </Stack>
            </Grid>
          </Grid>
        </FilterCard>
        <Paper
          sx={{ p: 2, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
          elevation={0}
        >
          <Typography variant="h5" mb={2}>
            Warning & Critical Alerts
          </Typography>
          <DataGrid
            rows={alerts}
            columns={columns}
            loading={loading}
            autoHeight
            disableRowSelectionOnClick
            pageSizeOptions={[10, 25, 50]}
            initialState={{ pagination: { paginationModel: { pageSize: 10 } } }}
          />
        </Paper>
      </Stack>
    </Box>
  );
};

export default AlertsPage;
