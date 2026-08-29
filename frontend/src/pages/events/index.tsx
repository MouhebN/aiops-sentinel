import { Box, Chip, Grid, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material';
import { DataGrid, GridColDef } from '@mui/x-data-grid';
import { aiopsApi } from 'api/aiopsApi';
import { useAuth } from 'auth/AuthContext';
import { canManageMaintenance } from 'auth/permissions';
import PageHeader from 'components/common/PageHeader';
import ApiState from 'components/aiops/ApiState';
import EventDetailsDialog from 'components/aiops/EventDetailsDialog';
import FilterCard from 'components/aiops/FilterCard';
import IconActionButton from 'components/aiops/IconActionButton';
import SeverityChip from 'components/aiops/SeverityChip';
import StatusChip from 'components/aiops/StatusChip';
import { formatDeviceType, formatEventType } from 'helpers/aiops';
import { useAiopsData } from 'hooks/useAiopsData';
import { DEVICE_TYPES, DeviceType, EventLog, SEVERITIES, Severity } from 'types/aiops';
import dayjs from 'dayjs';
import { useCallback, useEffect, useMemo, useState } from 'react';

const EventsPage = () => {
  const { user } = useAuth();
  const canDeleteEvents = canManageMaintenance(user?.role);
  const { devices } = useAiopsData();
  const [events, setEvents] = useState<EventLog[]>([]);
  const [selectedEvent, setSelectedEvent] = useState<EventLog | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [deviceId, setDeviceId] = useState('');
  const [deviceType, setDeviceType] = useState<DeviceType | ''>('');
  const [severity, setSeverity] = useState<Severity | ''>('');
  const [eventType, setEventType] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');

  const refresh = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await aiopsApi.getEvents({
        deviceId,
        deviceType,
        severity,
        eventType: eventType.trim(),
        from: from ? dayjs(from).toISOString() : undefined,
        to: to ? dayjs(to).toISOString() : undefined,
      });
      setEvents(response);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Unknown API error');
    } finally {
      setLoading(false);
    }
  }, [deviceId, deviceType, eventType, from, severity, to]);

  const handleDeleteEvent = async (id: number) => {
    if (!window.confirm('Delete this event? This action cannot be undone.')) {
      return;
    }
    try {
      setError(null);
      await aiopsApi.deleteEvent(id);
      await refresh();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Delete event failed');
    }
  };

  useEffect(() => {
    refresh();
  }, [refresh]);

  const columns = useMemo<GridColDef<EventLog>[]>(
    () => [
      {
        field: 'occurredAt',
        headerName: 'Time',
        minWidth: 170,
        flex: 0.95,
        valueFormatter: (value) => dayjs(value as string).format('DD/MM/YYYY HH:mm:ss'),
      },
      { field: 'deviceName', headerName: 'Device', minWidth: 180, flex: 1 },
      {
        field: 'deviceType',
        headerName: 'Type',
        minWidth: 130,
        flex: 0.7,
        valueGetter: (value) => formatDeviceType(value as string),
      },
      {
        field: 'eventType',
        headerName: 'Event',
        minWidth: 220,
        flex: 1.1,
        renderCell: (params) => (
          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
            <Typography variant="body2">{formatEventType(params.row.eventType)}</Typography>
            {params.row.eventSource === 'SYSLOG' && (
              <Chip size="small" label="SYSLOG" color="info" />
            )}
          </Stack>
        ),
      },
      {
        field: 'severity',
        headerName: 'Severity',
        minWidth: 130,
        flex: 0.6,
        renderCell: (params) => <SeverityChip severity={params.row.severity} />,
      },
      {
        field: 'resultingStatus',
        headerName: 'Status',
        minWidth: 130,
        flex: 0.6,
        renderCell: (params) => <StatusChip status={params.row.resultingStatus} />,
      },
      { field: 'message', headerName: 'Message', minWidth: 280, flex: 1.5 },
      {
        field: 'syslogMeta',
        headerName: 'Source',
        minWidth: 220,
        flex: 1.1,
        sortable: false,
        renderCell: (params) => (
          <Stack spacing={0.25} sx={{ py: 0.75 }}>
            <Typography variant="body2">{params.row.sourceIp || '-'}</Typography>
            <Typography variant="caption" color="text.secondary">
              {[params.row.syslogSourceName, params.row.parsingProfile]
                .filter(Boolean)
                .join(' • ') || 'No syslog metadata'}
            </Typography>
          </Stack>
        ),
      },
      {
        field: 'actions',
        headerName: 'Actions',
        minWidth: 120,
        flex: 0.55,
        sortable: false,
        renderCell: (params) => (
          <Stack direction="row" spacing={0.5}>
            <IconActionButton
              icon="mdi:eye-outline"
              label="View event details"
              onClick={() => setSelectedEvent(params.row)}
            />
            {canDeleteEvents && (
              <IconActionButton
                icon="mdi:trash-can-outline"
                label="Delete event"
                onClick={() => handleDeleteEvent(params.row.id)}
                color="error"
              />
            )}
          </Stack>
        ),
      },
    ],
    [canDeleteEvents, refresh],
  );

  return (
    <Box sx={{ pb: 2 }}>
      <PageHeader
        actions={<IconActionButton icon="mdi:refresh" label="Refresh events" onClick={refresh} />}
      >
        Events & Logs
      </PageHeader>
      <Stack spacing={3} mt={3}>
        <ApiState loading={loading} error={error} onRetry={refresh} />
        <FilterCard title="Event Filters">
          <Grid container spacing={2} alignItems="flex-start">
            <Grid item xs={12} md={3}>
              <TextField
                select
                fullWidth
                label="Device"
                value={deviceId}
                onChange={(event) => setDeviceId(event.target.value)}
              >
                <MenuItem value="">All devices</MenuItem>
                {devices.map((device) => (
                  <MenuItem key={device.id} value={device.id}>
                    {device.name}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid item xs={12} md={2}>
              <TextField
                select
                fullWidth
                label="Type"
                value={deviceType}
                onChange={(event) => setDeviceType(event.target.value as DeviceType | '')}
              >
                <MenuItem value="">All types</MenuItem>
                {DEVICE_TYPES.map((type) => (
                  <MenuItem key={type} value={type}>
                    {formatDeviceType(type)}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid item xs={12} md={2}>
              <TextField
                select
                fullWidth
                label="Severity"
                value={severity}
                onChange={(event) => setSeverity(event.target.value as Severity | '')}
              >
                <MenuItem value="">All severities</MenuItem>
                {SEVERITIES.map((item) => (
                  <MenuItem key={item} value={item}>
                    {item}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid item xs={12} md={2}>
              <TextField
                fullWidth
                label="Event type"
                value={eventType}
                onChange={(event) => setEventType(event.target.value)}
                placeholder="CPU_HIGH"
              />
            </Grid>
            <Grid item xs={12} md={1.5}>
              <TextField
                fullWidth
                type="datetime-local"
                label="From"
                value={from}
                onChange={(event) => setFrom(event.target.value)}
                InputLabelProps={{ shrink: true }}
              />
            </Grid>
            <Grid item xs={12} md={1.5}>
              <TextField
                fullWidth
                type="datetime-local"
                label="To"
                value={to}
                onChange={(event) => setTo(event.target.value)}
                InputLabelProps={{ shrink: true }}
              />
            </Grid>
            <Grid item xs={12}>
              <Stack direction="row" spacing={1} justifyContent="flex-end">
                <IconActionButton
                  icon="mdi:filter-remove-outline"
                  label="Clear filters"
                  onClick={() => {
                    setDeviceId('');
                    setDeviceType('');
                    setSeverity('');
                    setEventType('');
                    setFrom('');
                    setTo('');
                  }}
                />
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
            Centralized Event Stream
          </Typography>
          <DataGrid
            rows={events}
            columns={columns}
            loading={loading}
            autoHeight
            disableRowSelectionOnClick
            onRowClick={(params) => setSelectedEvent(params.row)}
            pageSizeOptions={[10, 25, 50]}
            initialState={{ pagination: { paginationModel: { pageSize: 10 } } }}
          />
        </Paper>
      </Stack>
      <EventDetailsDialog
        event={selectedEvent}
        open={Boolean(selectedEvent)}
        onClose={() => setSelectedEvent(null)}
      />
    </Box>
  );
};

export default EventsPage;
