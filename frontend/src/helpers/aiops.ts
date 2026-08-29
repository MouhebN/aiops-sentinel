import { IncidentStatus } from 'api/aiopsApi';
import { DashboardSummary, Device, DeviceStatus, EventLog, Severity } from 'types/aiops';

export type IncidentStatusFilter = 'OPEN' | IncidentStatus | 'ALL';

export const isIncidentResolved = (status?: IncidentStatus | null) =>
  status === 'RESOLVED' || status === 'RECOVERED';

export const isIncidentOpen = (status?: IncidentStatus | null) =>
  status === 'ACTIVE' || status === 'ACKNOWLEDGED';

export const incidentStatusColor = (
  status?: IncidentStatus | null,
): 'default' | 'error' | 'warning' | 'success' => {
  if (status === 'ACTIVE') {
    return 'error';
  }
  if (status === 'ACKNOWLEDGED') {
    return 'warning';
  }
  if (isIncidentResolved(status)) {
    return 'success';
  }
  return 'default';
};

export const matchesIncidentStatusFilter = (
  status: IncidentStatus | undefined,
  filter: IncidentStatusFilter,
) => {
  if (filter === 'ALL') {
    return true;
  }
  if (filter === 'OPEN') {
    return isIncidentOpen(status);
  }
  if (filter === 'RESOLVED' || filter === 'RECOVERED') {
    return isIncidentResolved(status);
  }
  return status === filter;
};

export const statusColor = (status: DeviceStatus) => {
  switch (status) {
    case 'UNKNOWN':
      return 'default';
    case 'UP':
      return 'success';
    case 'WARNING':
      return 'warning';
    case 'DEGRADED':
      return 'info';
    case 'DOWN':
      return 'error';
    default:
      return 'default';
  }
};

export const severityColor = (severity: Severity) => {
  switch (severity) {
    case 'INFO':
      return 'success';
    case 'WARNING':
      return 'warning';
    case 'CRITICAL':
      return 'error';
    default:
      return 'default';
  }
};

export const formatDeviceType = (value: string) => value.replace(/_/g, ' ');

export const formatEventType = (value: string) =>
  value
    .replace(/_/g, ' ')
    .toLowerCase()
    .replace(/\b\w/g, (character: string) => character.toUpperCase());

export const buildSummary = (devices: Device[], alerts: EventLog[]): DashboardSummary => ({
  totalDevices: devices.length,
  up: devices.filter((device) => device.status === 'UP').length,
  warning: devices.filter((device) => device.status === 'WARNING').length,
  degraded: devices.filter((device) => device.status === 'DEGRADED').length,
  down: devices.filter((device) => device.status === 'DOWN').length,
  criticalAlerts: alerts.filter((alert) => alert.severity === 'CRITICAL').length,
});
