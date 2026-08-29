import {
  Alert,
  Box,
  Chip,
  Grid,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { DataGrid, GridColDef } from '@mui/x-data-grid';
import { aiopsApi, Incident } from 'api/aiopsApi';
import { useAuth } from 'auth/AuthContext';
import { canAcknowledgeIncidents, canRebuildIncidents, canUseAiAnalysis } from 'auth/permissions';
import ApiState from 'components/aiops/ApiState';
import FilterCard from 'components/aiops/FilterCard';
import IconActionButton from 'components/aiops/IconActionButton';
import SeverityChip from 'components/aiops/SeverityChip';
import StatusChip from 'components/aiops/StatusChip';
import PageHeader from 'components/common/PageHeader';
import {
  formatDeviceType,
  formatEventType,
  incidentStatusColor,
  isIncidentResolved,
  matchesIncidentStatusFilter,
  type IncidentStatusFilter,
} from 'helpers/aiops';
import { Severity } from 'types/aiops';
import dayjs from 'dayjs';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';

const relatedComponentId = (incident: Incident) => {
  const related = incident.relatedComponents ?? [];
  const target = related.find((item) => item.relation === 'TARGET');
  return (target ?? related[0])?.id;
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

const IncidentsPage = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  const canAcknowledge = canAcknowledgeIncidents(user?.role);
  const canRebuild = canRebuildIncidents(user?.role);
  const canAnalyze = canUseAiAnalysis(user?.role);
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [statusFilter, setStatusFilter] = useState<IncidentStatusFilter>('OPEN');
  const [severityFilter, setSeverityFilter] = useState<Severity | 'ALL'>('ALL');
  const [selectedIncidentId, setSelectedIncidentId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [rebuilding, setRebuilding] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const incidentResponse = await aiopsApi.getIncidents();
      setIncidents(incidentResponse);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Unknown incident error');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const filteredIncidents = useMemo(
    () =>
      incidents.filter(
        (incident) =>
          matchesIncidentStatusFilter(incident.status, statusFilter) &&
          (severityFilter === 'ALL' || incident.severity === severityFilter),
      ),
    [incidents, severityFilter, statusFilter],
  );

  const selectedIncident =
    incidents.find((incident) => incident.id === selectedIncidentId) || filteredIncidents[0];

  const toggleAcknowledged = async (incident: Incident) => {
    try {
      setError(null);
      const updated = incident.acknowledged
        ? await aiopsApi.unacknowledgeIncident(incident.id)
        : await aiopsApi.acknowledgeIncident(incident.id);
      setIncidents((current) => current.map((item) => (item.id === updated.id ? updated : item)));
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Unknown acknowledge error');
    }
  };

  const handleRebuild = async () => {
    try {
      setRebuilding(true);
      setError(null);
      const rebuilt = await aiopsApi.rebuildIncidents();
      setIncidents(rebuilt);
      setSelectedIncidentId(null);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Unknown rebuild error');
    } finally {
      setRebuilding(false);
    }
  };

  const handleAnalyzeIncident = (incident: Incident) => {
    navigate(`/ai-analysis?eventId=${incident.latestEvent.id}&incidentId=${incident.id}`);
  };

  const columns: GridColDef<Incident>[] = [
    {
      field: 'status',
      headerName: 'State',
      minWidth: 120,
      flex: 0.7,
      renderCell: (params) => (
        <Chip
          size="small"
          label={params.row.status}
          color={incidentStatusColor(params.row.status)}
        />
      ),
    },
    { field: 'title', headerName: 'Incident', minWidth: 280, flex: 1.5 },
    {
      field: 'severity',
      headerName: 'Severity',
      minWidth: 120,
      flex: 0.7,
      renderCell: (params) => <SeverityChip severity={params.row.severity} />,
    },
    {
      field: 'resultingStatus',
      headerName: 'Component',
      minWidth: 130,
      flex: 0.7,
      renderCell: (params) => <StatusChip status={params.row.resultingStatus} />,
    },
    { field: 'eventCount', headerName: 'Events', minWidth: 90, flex: 0.5 },
    {
      field: 'durationMinutes',
      headerName: 'Duration',
      minWidth: 110,
      flex: 0.6,
      valueFormatter: (value) => formatDuration(Number(value)),
    },
    {
      field: 'createdAt',
      headerName: 'Created',
      minWidth: 150,
      flex: 0.8,
      valueGetter: (_, row) => row.createdAt ?? row.firstSeenAt,
      valueFormatter: (value) => (value ? dayjs(value as string).format('DD/MM/YYYY HH:mm') : '-'),
    },
    {
      field: 'lastActivityAt',
      headerName: 'Last activity',
      minWidth: 150,
      flex: 0.8,
      valueGetter: (_, row) => row.lastActivityAt ?? row.lastSeenAt,
      valueFormatter: (value) => (value ? dayjs(value as string).format('DD/MM/YYYY HH:mm') : '-'),
    },
    {
      field: 'acknowledged',
      headerName: 'Ack',
      minWidth: 90,
      flex: 0.5,
      valueGetter: (value) => (value ? 'Yes' : 'No'),
    },
    {
      field: 'actions',
      headerName: 'Actions',
      minWidth: 190,
      flex: 1,
      sortable: false,
      renderCell: (params) => (
        <Stack direction="row" spacing={1}>
          <IconActionButton
            icon="mdi:eye-outline"
            label="View incident"
            onClick={() => navigate(`/incidents/${params.row.id}`)}
          />
          {canAcknowledge && !isIncidentResolved(params.row.status) && (
            <IconActionButton
              icon={params.row.acknowledged ? 'mdi:bell-ring-outline' : 'mdi:check'}
              label={params.row.acknowledged ? 'Remove acknowledgement' : 'Acknowledge'}
              color={params.row.acknowledged ? 'warning' : 'success'}
              onClick={() => toggleAcknowledged(params.row)}
            />
          )}
        </Stack>
      ),
    },
  ];

  return (
    <Box sx={{ pb: 2 }}>
      <PageHeader
        actions={
          <Stack direction="row" spacing={1}>
            <IconActionButton icon="mdi:refresh" label="Refresh incidents" onClick={refresh} />
            {canRebuild && (
              <IconActionButton
                icon="mdi:database-sync-outline"
                label={rebuilding ? 'Rebuilding incidents' : 'Rebuild incidents'}
                onClick={handleRebuild}
                disabled={rebuilding}
                color="primary"
              />
            )}
          </Stack>
        }
      >
        Incidents
      </PageHeader>
      <Stack spacing={3} mt={3}>
        <ApiState loading={loading} error={error} onRetry={refresh} />

        <Grid container spacing={2.5}>
          <Grid item xs={12} md={3}>
            <Paper
              sx={{ p: 2.5, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
              elevation={0}
            >
              <Typography variant="body2" color="text.secondary">
                Open Incidents
              </Typography>
              <Typography variant="h3">
                {
                  incidents.filter(
                    (incident) =>
                      incident.status === 'ACTIVE' || incident.status === 'ACKNOWLEDGED',
                  ).length
                }
              </Typography>
            </Paper>
          </Grid>
          <Grid item xs={12} md={3}>
            <Paper
              sx={{ p: 2.5, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
              elevation={0}
            >
              <Typography variant="body2" color="text.secondary">
                Critical
              </Typography>
              <Typography variant="h3">
                {incidents.filter((incident) => incident.severity === 'CRITICAL').length}
              </Typography>
            </Paper>
          </Grid>
          <Grid item xs={12} md={3}>
            <Paper
              sx={{ p: 2.5, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
              elevation={0}
            >
              <Typography variant="body2" color="text.secondary">
                Acknowledged
              </Typography>
              <Typography variant="h3">
                {incidents.filter((incident) => incident.acknowledged).length}
              </Typography>
            </Paper>
          </Grid>
          <Grid item xs={12} md={3}>
            <Paper
              sx={{ p: 2.5, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
              elevation={0}
            >
              <Typography variant="body2" color="text.secondary">
                Resolved
              </Typography>
              <Typography variant="h3">
                {incidents.filter((incident) => isIncidentResolved(incident.status)).length}
              </Typography>
            </Paper>
          </Grid>
        </Grid>

        <FilterCard title="Incident Filters">
          <Grid container spacing={2}>
            <Grid item xs={12} md={3}>
              <TextField
                select
                fullWidth
                label="Incident state"
                value={statusFilter}
                onChange={(event) => setStatusFilter(event.target.value as IncidentStatusFilter)}
              >
                <MenuItem value="OPEN">Open (ACTIVE + ACKNOWLEDGED)</MenuItem>
                <MenuItem value="ACTIVE">ACTIVE</MenuItem>
                <MenuItem value="ACKNOWLEDGED">ACKNOWLEDGED</MenuItem>
                <MenuItem value="RESOLVED">RESOLVED</MenuItem>
                <MenuItem value="ALL">All incidents</MenuItem>
              </TextField>
            </Grid>
            <Grid item xs={12} md={3}>
              <TextField
                select
                fullWidth
                label="Severity"
                value={severityFilter}
                onChange={(event) => setSeverityFilter(event.target.value as Severity | 'ALL')}
              >
                <MenuItem value="ALL">All severities</MenuItem>
                <MenuItem value="WARNING">WARNING</MenuItem>
                <MenuItem value="CRITICAL">CRITICAL</MenuItem>
              </TextField>
            </Grid>
          </Grid>
        </FilterCard>

        <Grid container spacing={3}>
          <Grid item xs={12} xl={8}>
            <Paper
              sx={{ p: 2, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
              elevation={0}
            >
              <Typography variant="h5" mb={2}>
                Persisted Incidents
              </Typography>
              <DataGrid
                rows={filteredIncidents}
                columns={columns}
                loading={loading}
                autoHeight
                disableRowSelectionOnClick
                pageSizeOptions={[10, 25, 50]}
                initialState={{ pagination: { paginationModel: { pageSize: 10 } } }}
                onRowClick={(params) => setSelectedIncidentId(params.row.id)}
              />
            </Paper>
          </Grid>

          <Grid item xs={12} xl={4}>
            <Paper
              sx={{ p: 2.5, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
              elevation={0}
            >
              {selectedIncident ? (
                <Stack spacing={2}>
                  <Stack spacing={1}>
                    <Typography variant="h5">{selectedIncident.title}</Typography>
                    <Stack direction="row" spacing={1} flexWrap="wrap">
                      <Chip
                        size="small"
                        label={selectedIncident.status}
                        color={incidentStatusColor(selectedIncident.status)}
                      />
                      <SeverityChip severity={selectedIncident.severity} />
                      <StatusChip status={selectedIncident.resultingStatus} />
                    </Stack>
                  </Stack>

                  <Grid container spacing={1.5}>
                    <Grid item xs={6}>
                      <Typography variant="caption" color="text.secondary">
                        Type
                      </Typography>
                      <Typography>
                        {formatDeviceType(selectedIncident.latestEvent.deviceType)}
                      </Typography>
                    </Grid>
                    <Grid item xs={6}>
                      <Typography variant="caption" color="text.secondary">
                        Category
                      </Typography>
                      <Typography>{formatEventType(selectedIncident.category)}</Typography>
                    </Grid>
                    <Grid item xs={6}>
                      <Typography variant="caption" color="text.secondary">
                        Created
                      </Typography>
                      <Typography>
                        {dayjs(selectedIncident.createdAt ?? selectedIncident.firstSeenAt).format(
                          'DD/MM/YYYY HH:mm',
                        )}
                      </Typography>
                    </Grid>
                    <Grid item xs={6}>
                      {isIncidentResolved(selectedIncident.status) ? (
                        <>
                          <Typography variant="caption" color="text.secondary">
                            Resolved
                          </Typography>
                          <Typography>
                            {dayjs(
                              selectedIncident.resolvedAt ?? selectedIncident.recoveredAt,
                            ).format('DD/MM/YYYY HH:mm')}
                          </Typography>
                        </>
                      ) : (
                        <>
                          <Typography variant="caption" color="text.secondary">
                            Last activity
                          </Typography>
                          <Typography>
                            {dayjs(
                              selectedIncident.lastActivityAt ?? selectedIncident.lastSeenAt,
                            ).format('DD/MM/YYYY HH:mm')}
                          </Typography>
                        </>
                      )}
                    </Grid>
                    <Grid item xs={6}>
                      <Typography variant="caption" color="text.secondary">
                        Duration
                      </Typography>
                      <Typography>{formatDuration(selectedIncident.durationMinutes)}</Typography>
                    </Grid>
                  </Grid>

                  <Alert
                    severity={
                      selectedIncident.status === 'ACTIVE'
                        ? 'warning'
                        : isIncidentResolved(selectedIncident.status)
                          ? 'success'
                          : 'info'
                    }
                  >
                    {selectedIncident.latestEvent.message}
                  </Alert>

                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    {canAnalyze && (
                      <IconActionButton
                        icon="mdi:brain"
                        label="Analyze with AI"
                        onClick={() => handleAnalyzeIncident(selectedIncident)}
                        color="primary"
                      />
                    )}
                    <IconActionButton
                      icon="mdi:open-in-new"
                      label="Open incident detail"
                      onClick={() => navigate(`/incidents/${selectedIncident.id}`)}
                    />
                    {relatedComponentId(selectedIncident) && (
                      <IconActionButton
                        icon="mdi:server-network"
                        label="Open component"
                        onClick={() =>
                          navigate(`/components/${relatedComponentId(selectedIncident)}`)
                        }
                      />
                    )}
                    {canAcknowledge && !isIncidentResolved(selectedIncident.status) && (
                      <IconActionButton
                        icon={selectedIncident.acknowledged ? 'mdi:bell-ring-outline' : 'mdi:check'}
                        label={
                          selectedIncident.acknowledged
                            ? 'Remove acknowledgement'
                            : 'Acknowledge incident'
                        }
                        color={selectedIncident.acknowledged ? 'warning' : 'success'}
                        onClick={() => toggleAcknowledged(selectedIncident)}
                      />
                    )}
                  </Stack>

                  <Stack spacing={1}>
                    <Typography variant="h6">Related Events</Typography>
                    {selectedIncident.relatedEvents.slice(0, 6).map((event) => (
                      <Stack
                        key={event.id}
                        spacing={0.25}
                        sx={{ pb: 1, borderBottom: '1px solid', borderColor: 'divider' }}
                      >
                        <Typography fontWeight={600}>{formatEventType(event.eventType)}</Typography>
                        <Typography variant="body2" color="text.secondary">
                          {dayjs(event.occurredAt).format('DD/MM/YYYY HH:mm:ss')} - {event.message}
                        </Typography>
                      </Stack>
                    ))}
                  </Stack>
                </Stack>
              ) : (
                <Typography color="text.secondary">No incidents detected.</Typography>
              )}
            </Paper>
          </Grid>
        </Grid>
      </Stack>
    </Box>
  );
};

export default IncidentsPage;
