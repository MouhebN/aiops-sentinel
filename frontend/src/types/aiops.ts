export type DeviceType =
  | 'SERVER'
  | 'ROUTER'
  | 'SWITCH'
  | 'IP_CAMERA'
  | 'UPS'
  | 'FIREWALL'
  | 'APPLICATION'
  | 'DATABASE'
  | 'ATM'
  | 'OTHER';

export type DeviceStatus = 'UNKNOWN' | 'UP' | 'WARNING' | 'DEGRADED' | 'DOWN';

export type MonitoringMethod =
  | 'PING'
  | 'HTTP_HEALTH'
  | 'TCP_PORT'
  | 'SNMP_BASIC'
  | 'SNMP_ROUTER_METRICS'
  | 'SNMP_SERVER_METRICS'
  | 'RTSP_HEALTH'
  | 'UPS_HTTP_METRICS'
  | 'UPS_SNMP_METRICS';

export type Criticality = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export type Severity = 'INFO' | 'WARNING' | 'CRITICAL';

export type UserRole = 'ADMIN' | 'OPERATOR' | 'VIEWER';

export interface Device {
  id: string;
  name: string;
  type: DeviceType;
  location: string;
  status: DeviceStatus;
  lastSeenAt: string;
}

export interface EventLog {
  id: number;
  deviceId: string;
  deviceName: string;
  deviceType: DeviceType;
  location: string;
  eventType: string;
  severity: Severity;
  resultingStatus: DeviceStatus;
  message: string;
  details?: string | null;
  rawLog?: string | null;
  sourceIp?: string | null;
  syslogSourceName?: string | null;
  parsingProfile?: string | null;
  eventSource?: string | null;
  occurredAt: string;
}

export interface DashboardSummary {
  totalDevices: number;
  up: number;
  warning: number;
  degraded: number;
  down: number;
  criticalAlerts: number;
}

export const DEVICE_TYPES: DeviceType[] = [
  'SERVER',
  'ROUTER',
  'SWITCH',
  'IP_CAMERA',
  'UPS',
  'FIREWALL',
  'APPLICATION',
  'DATABASE',
  'ATM',
  'OTHER',
];

export const DEVICE_STATUSES: DeviceStatus[] = ['UNKNOWN', 'UP', 'WARNING', 'DEGRADED', 'DOWN'];

export const MONITORING_METHODS: MonitoringMethod[] = [
  'PING',
  'HTTP_HEALTH',
  'TCP_PORT',
  'SNMP_BASIC',
  'SNMP_ROUTER_METRICS',
  'SNMP_SERVER_METRICS',
  'RTSP_HEALTH',
  'UPS_HTTP_METRICS',
  'UPS_SNMP_METRICS',
];

export const CRITICALITIES: Criticality[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

export const SEVERITIES: Severity[] = ['INFO', 'WARNING', 'CRITICAL'];
