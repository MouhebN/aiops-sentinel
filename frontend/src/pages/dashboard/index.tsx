import { Box, Grid, Paper, Stack, Typography } from '@mui/material';
import PageHeader from 'components/common/PageHeader';
import ApiState from 'components/aiops/ApiState';
import DashboardCharts from 'components/aiops/DashboardCharts';
import IconActionButton from 'components/aiops/IconActionButton';
import MetricCard from 'components/aiops/MetricCard';
import SeverityChip from 'components/aiops/SeverityChip';
import StatusChip from 'components/aiops/StatusChip';
import { buildSummary, formatDeviceType, formatEventType } from 'helpers/aiops';
import { useAiopsData } from 'hooks/useAiopsData';
import dayjs from 'dayjs';

const Dashboard = () => {
  const { devices, components, events, alerts, metrics, loading, error, refresh } = useAiopsData();
  const summary = buildSummary(devices, alerts);
  const recentEvents = events.slice(0, 6);
  const criticalAlerts = alerts.filter((alert) => alert.severity === 'CRITICAL').slice(0, 5);
  const activeComponents = components.filter((component) => component.enabled).length;

  return (
    <Box sx={{ pb: 2 }}>
      <PageHeader
        actions={
          <IconActionButton icon="mdi:refresh" label="Refresh dashboard" onClick={refresh} />
        }
      >
        Overview
      </PageHeader>

      <Stack spacing={3} mt={3}>
        <ApiState loading={loading} error={error} onRetry={refresh} />

        <Grid container spacing={2.5}>
          <Grid item xs={12} sm={6} lg={2.4}>
            <MetricCard
              title="Components"
              value={components.length || summary.totalDevices}
              icon="mdi:server-network"
              color="#2563eb"
            />
          </Grid>
          <Grid item xs={12} sm={6} lg={2.4}>
            <MetricCard
              title="UP"
              value={summary.up}
              icon="mdi:check-circle-outline"
              color="#16a34a"
            />
          </Grid>
          <Grid item xs={12} sm={6} lg={2.4}>
            <MetricCard
              title="WARNING"
              value={summary.warning}
              icon="mdi:alert-outline"
              color="#d97706"
            />
          </Grid>
          <Grid item xs={12} sm={6} lg={2.4}>
            <MetricCard
              title="Monitoring"
              value={activeComponents}
              icon="mdi:radar"
              color="#0f766e"
            />
          </Grid>
          <Grid item xs={12} sm={6} lg={2.4}>
            <MetricCard
              title="DOWN"
              value={
                components.length
                  ? components.filter((component) => component.lastStatus === 'DOWN').length
                  : summary.down
              }
              icon="mdi:close-octagon-outline"
              color="#dc2626"
            />
          </Grid>
          <Grid item xs={12} sm={6} lg={2.4}>
            <MetricCard
              title="Critical Alerts"
              value={summary.criticalAlerts}
              icon="mdi:bell-alert-outline"
              color="#be123c"
            />
          </Grid>
        </Grid>

        <DashboardCharts
          devices={devices}
          components={components}
          events={events}
          metrics={metrics}
        />

        <Grid container spacing={3}>
          <Grid item xs={12} lg={7}>
            <Paper
              sx={{ p: 2.5, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
              elevation={0}
            >
              <Typography variant="h5" mb={2}>
                Recent Events
              </Typography>
              <Stack spacing={1.5}>
                {recentEvents.map((event) => (
                  <Stack
                    key={event.id}
                    direction={{ xs: 'column', sm: 'row' }}
                    alignItems={{ xs: 'flex-start', sm: 'center' }}
                    justifyContent="space-between"
                    spacing={1}
                    sx={{ py: 1, borderBottom: '1px solid', borderColor: 'divider' }}
                  >
                    <Stack spacing={0.25}>
                      <Typography fontWeight={600}>{formatEventType(event.eventType)}</Typography>
                      <Typography variant="body2" color="text.secondary">
                        {event.deviceName} · {event.message}
                      </Typography>
                    </Stack>
                    <Stack direction="row" spacing={1} alignItems="center">
                      <SeverityChip severity={event.severity} />
                      <Typography variant="caption" color="text.secondary">
                        {dayjs(event.occurredAt).format('HH:mm:ss')}
                      </Typography>
                    </Stack>
                  </Stack>
                ))}
                {!recentEvents.length && !loading && (
                  <Typography color="text.secondary">No events received yet.</Typography>
                )}
              </Stack>
            </Paper>
          </Grid>

          <Grid item xs={12} lg={5}>
            <Paper
              sx={{ p: 2.5, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
              elevation={0}
            >
              <Typography variant="h5" mb={2}>
                Device Status
              </Typography>
              <Stack spacing={1.5}>
                {devices.slice(0, 6).map((device) => (
                  <Stack
                    key={device.id}
                    direction="row"
                    alignItems="center"
                    justifyContent="space-between"
                    spacing={1}
                    sx={{ py: 1, borderBottom: '1px solid', borderColor: 'divider' }}
                  >
                    <Stack>
                      <Typography fontWeight={600}>{device.name}</Typography>
                      <Typography variant="body2" color="text.secondary">
                        {formatDeviceType(device.type)} · {device.location}
                      </Typography>
                    </Stack>
                    <StatusChip status={device.status} />
                  </Stack>
                ))}
                {!devices.length && !loading && (
                  <Typography color="text.secondary">
                    Run simulators to populate devices.
                  </Typography>
                )}
              </Stack>
            </Paper>
          </Grid>
        </Grid>

        <Paper
          sx={{ p: 2.5, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
          elevation={0}
        >
          <Typography variant="h5" mb={2}>
            Critical Incidents
          </Typography>
          <Stack spacing={1.5}>
            {criticalAlerts.map((alert) => (
              <Stack
                key={alert.id}
                direction={{ xs: 'column', md: 'row' }}
                justifyContent="space-between"
                spacing={1}
              >
                <Stack>
                  <Typography fontWeight={600}>{alert.deviceName}</Typography>
                  <Typography variant="body2" color="text.secondary">
                    {formatEventType(alert.eventType)} · {alert.message}
                  </Typography>
                </Stack>
                <Typography variant="body2" color="text.secondary">
                  {dayjs(alert.occurredAt).format('DD/MM/YYYY HH:mm')}
                </Typography>
              </Stack>
            ))}
            {!criticalAlerts.length && !loading && (
              <Typography color="text.secondary">
                No critical incidents currently listed.
              </Typography>
            )}
          </Stack>
        </Paper>
      </Stack>
    </Box>
  );
};

export default Dashboard;
