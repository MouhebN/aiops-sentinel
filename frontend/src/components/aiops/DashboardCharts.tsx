import { Box, Grid, Paper, Stack, Typography } from '@mui/material';
import ReactECharts from 'echarts-for-react';
import type { EChartsOption } from 'echarts';
import dayjs from 'dayjs';
import { formatDeviceType } from 'helpers/aiops';
import { MetricSample, MonitoredComponent } from 'api/aiopsApi';
import { Device, DEVICE_STATUSES, EventLog, SEVERITIES } from 'types/aiops';
import { useMemo } from 'react';

interface DashboardChartsProps {
  devices: Device[];
  components: MonitoredComponent[];
  events: EventLog[];
  metrics: MetricSample[];
}

const chartCardSx = {
  p: 2.5,
  borderRadius: 2,
  border: '1px solid',
  borderColor: 'divider',
  height: 1,
};

const statusColors: Record<string, string> = {
  UP: '#16a34a',
  WARNING: '#d97706',
  DEGRADED: '#2563eb',
  DOWN: '#dc2626',
};

const severityColors: Record<string, string> = {
  INFO: '#16a34a',
  WARNING: '#d97706',
  CRITICAL: '#dc2626',
};

const emptyPie = [{ name: 'No data', value: 1, itemStyle: { color: '#cbd5e1' } }];

const ChartFrame = ({ children, height }: { children: React.ReactNode; height: number }) => (
  <Box sx={{ height, minHeight: height, width: 1 }}>{children}</Box>
);

const availabilityMetric = (sample: MetricSample) =>
  sample.unit === 'state' || sample.metricName.endsWith('Available');

const latestSamplesByMetric = (metrics: MetricSample[]) => {
  const latest = new Map<string, MetricSample>();
  metrics.forEach((sample) => {
    const key = `${sample.componentId}-${sample.metricName}`;
    const current = latest.get(key);
    if (!current || dayjs(sample.sampledAt).isAfter(current.sampledAt)) {
      latest.set(key, sample);
    }
  });

  return Array.from(latest.values());
};

const DashboardCharts = ({ devices, components, events, metrics }: DashboardChartsProps) => {
  const statusOption = useMemo<EChartsOption>(() => {
    const statusItems = components.length
      ? components.map((component) => component.lastStatus)
      : devices.map((device) => device.status);
    const data = DEVICE_STATUSES.map((status) => ({
      name: status,
      value: statusItems.filter((item) => item === status).length,
      itemStyle: { color: statusColors[status] },
    })).filter((item) => item.value > 0);

    return {
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, left: 'center' },
      series: [
        {
          name: 'Devices',
          type: 'pie',
          radius: ['48%', '72%'],
          center: ['50%', '44%'],
          avoidLabelOverlap: true,
          label: { formatter: '{b}: {c}' },
          data: data.length ? data : emptyPie,
        },
      ],
    };
  }, [components, devices]);

  const severityOption = useMemo<EChartsOption>(() => {
    const data = SEVERITIES.map((severity) => ({
      name: severity,
      value: events.filter((event) => event.severity === severity).length,
      itemStyle: { color: severityColors[severity] },
    })).filter((item) => item.value > 0);

    return {
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, left: 'center' },
      series: [
        {
          name: 'Events',
          type: 'pie',
          radius: ['48%', '72%'],
          center: ['50%', '44%'],
          label: { formatter: '{b}: {c}' },
          data: data.length ? data : emptyPie,
        },
      ],
    };
  }, [events]);

  const timelineOption = useMemo<EChartsOption>(() => {
    const buckets = Array.from({ length: 12 }, (_, index) => {
      const timestamp = dayjs().subtract(55 - index * 5, 'minute');
      return {
        key: timestamp.format('HH:mm'),
        start: timestamp.startOf('minute'),
        end: timestamp.add(4, 'minute').endOf('minute'),
      };
    });

    const countEvents = (bucket: (typeof buckets)[number], severity?: string) =>
      events.filter((event) => {
        const occurredAt = dayjs(event.occurredAt);
        const inBucket = occurredAt.isAfter(bucket.start) && occurredAt.isBefore(bucket.end);
        return inBucket && (!severity || event.severity === severity);
      }).length;

    return {
      tooltip: { trigger: 'axis' },
      legend: { bottom: 0 },
      grid: { left: 42, right: 18, top: 24, bottom: 58 },
      xAxis: {
        type: 'category',
        data: buckets.map((bucket) => bucket.key),
        boundaryGap: false,
      },
      yAxis: {
        type: 'value',
        minInterval: 1,
      },
      series: [
        {
          name: 'All events',
          type: 'line',
          smooth: true,
          symbolSize: 7,
          areaStyle: { color: 'rgba(37, 99, 235, 0.12)' },
          lineStyle: { color: '#2563eb', width: 3 },
          itemStyle: { color: '#2563eb' },
          data: buckets.map((bucket) => countEvents(bucket)),
        },
        {
          name: 'Critical',
          type: 'line',
          smooth: true,
          symbolSize: 7,
          lineStyle: { color: '#dc2626', width: 2 },
          itemStyle: { color: '#dc2626' },
          data: buckets.map((bucket) => countEvents(bucket, 'CRITICAL')),
        },
        {
          name: 'Threshold breaches',
          type: 'bar',
          barWidth: '38%',
          itemStyle: { color: '#d97706', borderRadius: [5, 5, 0, 0] },
          data: buckets.map(
            (bucket) =>
              events.filter((event) => {
                const occurredAt = dayjs(event.occurredAt);
                return (
                  occurredAt.isAfter(bucket.start) &&
                  occurredAt.isBefore(bucket.end) &&
                  event.eventType.startsWith('METRIC_THRESHOLD_') &&
                  event.eventType !== 'METRIC_THRESHOLD_RECOVERED'
                );
              }).length,
          ),
        },
      ],
    };
  }, [events]);

  const deviceTypeOption = useMemo<EChartsOption>(() => {
    const counts = events.reduce<Record<string, number>>((accumulator, event) => {
      accumulator[event.deviceType] = (accumulator[event.deviceType] || 0) + 1;
      return accumulator;
    }, {});
    const labels = Object.keys(counts);

    return {
      tooltip: { trigger: 'axis' },
      grid: { left: 44, right: 18, top: 24, bottom: 48 },
      xAxis: {
        type: 'category',
        data: labels.map(formatDeviceType),
        axisLabel: { rotate: 18 },
      },
      yAxis: {
        type: 'value',
        minInterval: 1,
      },
      series: [
        {
          name: 'Events',
          type: 'bar',
          barWidth: '48%',
          itemStyle: {
            color: '#0f766e',
            borderRadius: [6, 6, 0, 0],
          },
          data: labels.map((label) => counts[label]),
        },
      ],
    };
  }, [events]);

  const availabilityTrendOption = useMemo<EChartsOption>(() => {
    const buckets = Array.from({ length: 12 }, (_, index) => {
      const timestamp = dayjs()
        .subtract(11 - index, 'hour')
        .startOf('hour');
      return {
        key: timestamp.format('HH:mm'),
        start: timestamp,
        end: timestamp.endOf('hour'),
      };
    });
    const componentNames = Array.from(new Set(metrics.map((sample) => sample.componentName)))
      .sort()
      .slice(0, 8);

    return {
      tooltip: {
        trigger: 'axis',
        valueFormatter: (value) => `${value}%`,
      },
      legend: {
        type: 'scroll',
        bottom: 0,
      },
      grid: { left: 48, right: 24, top: 28, bottom: 72, containLabel: true },
      xAxis: {
        type: 'category',
        data: buckets.map((bucket) => bucket.key),
        boundaryGap: false,
      },
      yAxis: {
        type: 'value',
        min: 0,
        max: 100,
        name: 'Availability %',
      },
      series: componentNames.map((componentName) => ({
        name: componentName,
        type: 'line' as const,
        smooth: true,
        symbolSize: 6,
        data: buckets.map((bucket) => {
          const samples = metrics.filter((sample) => {
            const sampledAt = dayjs(sample.sampledAt);
            return (
              sample.componentName === componentName &&
              availabilityMetric(sample) &&
              sampledAt.isAfter(bucket.start) &&
              sampledAt.isBefore(bucket.end)
            );
          });
          if (!samples.length) {
            return null;
          }
          const average =
            samples.reduce((total, sample) => total + sample.metricValue, 0) / samples.length;
          return Math.round(average * 100);
        }),
      })),
    };
  }, [metrics]);

  const latestMetricsOption = useMemo<EChartsOption>(() => {
    const samples = latestSamplesByMetric(metrics)
      .filter(
        (sample) =>
          sample.unit === '%' ||
          sample.unit === 'state' ||
          sample.metricName === 'totalInterfaceErrors' ||
          sample.metricName === 'runtimeMinutes',
      )
      .sort((a, b) => {
        const aRisk = a.unit === 'state' ? 1 - a.metricValue : a.metricValue;
        const bRisk = b.unit === 'state' ? 1 - b.metricValue : b.metricValue;
        return bRisk - aRisk;
      })
      .slice(0, 10);

    return {
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        valueFormatter: (value) => String(value),
      },
      grid: { left: 128, right: 28, top: 24, bottom: 28 },
      xAxis: { type: 'value' },
      yAxis: {
        type: 'category',
        data: samples.map((sample) => `${sample.componentName} / ${sample.metricName}`),
        axisLabel: {
          width: 112,
          overflow: 'truncate',
        },
      },
      series: [
        {
          name: 'Latest value',
          type: 'bar',
          barWidth: '48%',
          itemStyle: {
            color: '#7c3aed',
            borderRadius: [0, 6, 6, 0],
          },
          data: samples.map((sample) => sample.metricValue),
        },
      ],
    };
  }, [metrics]);

  return (
    <Grid container spacing={3}>
      <Grid item xs={12} lg={6}>
        <Paper sx={chartCardSx} elevation={0}>
          <Stack spacing={1}>
            <Typography variant="h5">Device Health Distribution</Typography>
            <ChartFrame height={300}>
              <ReactECharts option={statusOption} style={{ height: '100%', width: '100%' }} />
            </ChartFrame>
          </Stack>
        </Paper>
      </Grid>
      <Grid item xs={12} lg={6}>
        <Paper sx={chartCardSx} elevation={0}>
          <Stack spacing={1}>
            <Typography variant="h5">Events by Priority</Typography>
            <ChartFrame height={300}>
              <ReactECharts option={severityOption} style={{ height: '100%', width: '100%' }} />
            </ChartFrame>
          </Stack>
        </Paper>
      </Grid>
      <Grid item xs={12} lg={7}>
        <Paper sx={chartCardSx} elevation={0}>
          <Stack spacing={1}>
            <Typography variant="h5">Event Volume, Last 60 Minutes</Typography>
            <ChartFrame height={320}>
              <ReactECharts option={timelineOption} style={{ height: '100%', width: '100%' }} />
            </ChartFrame>
          </Stack>
        </Paper>
      </Grid>
      <Grid item xs={12} lg={5}>
        <Paper sx={chartCardSx} elevation={0}>
          <Stack spacing={1}>
            <Typography variant="h5">Events by Component Type</Typography>
            <ChartFrame height={320}>
              <ReactECharts option={deviceTypeOption} style={{ height: '100%', width: '100%' }} />
            </ChartFrame>
          </Stack>
        </Paper>
      </Grid>
      <Grid item xs={12} lg={7}>
        <Paper sx={chartCardSx} elevation={0}>
          <Stack spacing={1}>
            <Typography variant="h5">Availability Trend, Last 12 Hours</Typography>
            <ChartFrame height={340}>
              <ReactECharts
                option={availabilityTrendOption}
                style={{ height: '100%', width: '100%' }}
              />
            </ChartFrame>
          </Stack>
        </Paper>
      </Grid>
      <Grid item xs={12} lg={5}>
        <Paper sx={chartCardSx} elevation={0}>
          <Stack spacing={1}>
            <Typography variant="h5">Latest Collected Metrics</Typography>
            <ChartFrame height={340}>
              <ReactECharts
                option={latestMetricsOption}
                style={{ height: '100%', width: '100%' }}
              />
            </ChartFrame>
          </Stack>
        </Paper>
      </Grid>
    </Grid>
  );
};

export default DashboardCharts;
