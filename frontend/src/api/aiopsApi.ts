import {
  Criticality,
  Device,
  DeviceStatus,
  DeviceType,
  EventLog,
  MonitoringMethod,
  Severity,
  UserRole,
} from 'types/aiops';
import { IncidentAnalysis } from './aiServiceApi';

const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL ?? (import.meta.env.DEV ? 'http://localhost:8080' : '');
export const AUTH_TOKEN_STORAGE_KEY = 'aiops-auth-token';

function getAuthToken() {
  return window.localStorage.getItem(AUTH_TOKEN_STORAGE_KEY);
}

function buildHeaders(headers?: HeadersInit) {
  const nextHeaders = new Headers(headers);
  const token = getAuthToken();
  if (token) {
    nextHeaders.set('Authorization', `Bearer ${token}`);
  }
  return nextHeaders;
}

async function apiRequest<T>(path: string, init?: RequestInit, expectJson = true): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: buildHeaders(init?.headers),
  });

  if (!response.ok) {
    let message = `API request failed: ${response.status} ${response.statusText}`;
    const contentType = response.headers.get('content-type') || '';
    if (contentType.includes('application/json')) {
      try {
        const body = (await response.json()) as { message?: string };
        if (body?.message && body.message.trim()) {
          message = body.message;
        }
      } catch {
        // keep the HTTP status fallback
      }
    }
    throw new Error(message);
  }

  if (!expectJson) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

async function apiGet<T>(path: string): Promise<T> {
  return apiRequest<T>(path);
}

interface EventFilters {
  deviceId?: string;
  deviceType?: DeviceType | '';
  eventType?: string;
  severity?: Severity | '';
  from?: string;
  to?: string;
}

interface AlertFilters {
  minSeverity?: Severity;
}

export interface DiagnosticReport {
  id: number;
  eventId?: number | null;
  deviceId: string;
  deviceName: string;
  deviceType: DeviceType;
  location: string;
  eventType: string;
  severity: Severity;
  message: string;
  details?: string | null;
  occurredAt: string;
  summary: string;
  priority: string;
  impact: string;
  risk: string;
  probableCauses: string[];
  suggestedActions: string[];
  diagnosticCommands: string[];
  packetCaptureSummaries: PacketCaptureAnalysis[];
  networkFlowSummaries: NetworkFlowSummary[];
  provider: string;
  model?: string | null;
  requestedProvider?: string | null;
  fallbackUsed?: boolean;
  fallbackReasonCode?: string | null;
  fallbackReason?: string | null;
  cacheHit?: boolean;
  contextFingerprint?: string | null;
  analysisDurationMs?: number | null;
  generatedAt: string;
}

export interface AuthUser {
  id: number;
  fullName: string;
  email: string;
  role: UserRole;
  enabled: boolean;
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export type AuditAction =
  | 'LOGIN_SUCCESS'
  | 'LOGIN_FAILED'
  | 'LOGOUT'
  | 'COMPONENT_CREATED'
  | 'COMPONENT_UPDATED'
  | 'COMPONENT_DELETED'
  | 'COMPONENT_RELATION_CREATED'
  | 'COMPONENT_RELATION_DELETED'
  | 'MONITORING_STARTED'
  | 'MONITORING_STOPPED'
  | 'MANUAL_CHECK_TRIGGERED'
  | 'THRESHOLD_CREATED'
  | 'THRESHOLD_UPDATED'
  | 'THRESHOLD_DELETED'
  | 'INCIDENT_ACKNOWLEDGED'
  | 'INCIDENT_UNACKNOWLEDGED'
  | 'INCIDENT_RESOLVED'
  | 'INCIDENT_DELETED'
  | 'INCIDENT_REBUILT'
  | 'EVENT_DELETED'
  | 'OLD_EVENTS_PURGED'
  | 'DEMO_DATA_RESET'
  | 'AI_ANALYSIS_REQUESTED'
  | 'REPORT_CREATED'
  | 'REPORT_EXPORTED'
  | 'PACKET_CAPTURE_UPLOADED'
  | 'PACKET_CAPTURE_STARTED'
  | 'PACKET_CAPTURE_CAPTURED'
  | 'PACKET_CAPTURE_FAILED'
  | 'PACKET_CAPTURE_ANALYZED'
  | 'PCAP_ANALYSIS_DELETE'
  | 'NETFLOW_SOURCE_CREATED'
  | 'NETFLOW_SOURCE_UPDATED'
  | 'NETFLOW_IMPORT_STARTED'
  | 'NETFLOW_IMPORT_COMPLETED'
  | 'NETFLOW_IMPORT_FAILED'
  | 'NETFLOW_ANOMALY_DETECTED'
  | 'SYSLOG_SOURCE_CREATED'
  | 'SYSLOG_SOURCE_UPDATED'
  | 'SYSLOG_SOURCE_DELETED'
  | 'USER_CREATED'
  | 'USER_UPDATED'
  | 'USER_ROLE_CHANGED'
  | 'USER_DISABLED'
  | 'USER_ENABLED'
  | 'USER_PASSWORD_RESET';

export interface AuditLog {
  id: number;
  username: string;
  userRole?: UserRole | null;
  action: AuditAction;
  targetType?: string | null;
  targetId?: string | null;
  details?: string | null;
  ipAddress?: string | null;
  createdAt: string;
}

export interface LoginResponse {
  token: string;
  user: AuthUser;
}

export interface ManagedUser {
  id: number;
  fullName: string;
  email: string;
  role: UserRole;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateUserPayload {
  fullName: string;
  email: string;
  password: string;
  role: UserRole;
  enabled: boolean;
}

export interface UpdateUserPayload {
  fullName: string;
  role: UserRole;
  enabled: boolean;
}

export interface MonitoredComponent {
  id: number;
  name: string;
  type: DeviceType;
  ipAddress?: string | null;
  httpUrl?: string | null;
  tcpPort?: number | null;
  snmpPort?: number | null;
  snmpCommunity?: string | null;
  snmpOid?: string | null;
  location: string;
  criticality: Criticality;
  monitoringMethods: MonitoringMethod[];
  checkIntervalSeconds: number;
  enabled: boolean;
  lastStatus: DeviceStatus;
  lastSeenAt?: string | null;
  lastCheckedAt?: string | null;
  lastError?: string | null;
  lastCheckDetails?: string | null;
  failureCount: number;
  successCount: number;
  createdAt: string;
  updatedAt: string;
  networkInterfaces?: ComponentNetworkInterface[];
}

export type NetworkInterfaceRole = 'MANAGEMENT' | 'LAN' | 'WAN' | 'SERVICE' | 'OTHER';

export interface ComponentNetworkInterface {
  id: number;
  monitoredComponentId: number;
  name: string;
  ipAddress: string;
  role: NetworkInterfaceRole;
  primary: boolean;
  createdAt: string;
}

export interface SaveComponentNetworkInterfacePayload {
  name: string;
  ipAddress: string;
  role: NetworkInterfaceRole;
  primary?: boolean;
}

export interface IncidentComponentMatch {
  id: number;
  name: string;
  type: DeviceType;
  lastStatus: DeviceStatus;
  monitoringEnabled: boolean;
  primaryIp?: string | null;
  matchedIp?: string | null;
  matchedInterfaceName?: string | null;
  matchedRole?: NetworkInterfaceRole | null;
  matchedPrimary: boolean;
  relation: string;
  location?: string | null;
  lastCheckedAt?: string | null;
  lastSeenAt?: string | null;
  lastError?: string | null;
}

export type TopologySecurityState = 'NORMAL' | 'SUSPICIOUS_ACTIVITY' | 'TARGETED' | 'UNDER_ATTACK';

export type ComponentRelationType = 'CONNECTED_TO' | 'ROUTES_TO' | 'DEPENDS_ON' | 'PROTECTS';

export interface TopologyNode {
  id: string;
  componentId: number;
  name: string;
  type: DeviceType;
  ipAddress?: string | null;
  location: string;
  operationalStatus: DeviceStatus;
  lastCheckedAt?: string | null;
  lastSeenAt?: string | null;
  monitoringEnabled: boolean;
  activeIncidentCount: number;
  highestIncidentSeverity?: Severity | null;
  securityState: TopologySecurityState;
  activeIncidentIds: number[];
  evidenceTypes: string[];
}

export interface TopologyLink {
  id: string;
  source: string;
  target: string;
  relationType: ComponentRelationType;
  label?: string | null;
}

export interface TopologyExternalEntity {
  id: string;
  ip: string;
  kind: string;
  label: string;
}

export interface TopologyActiveAttack {
  source: string;
  target: string;
  incidentId: number;
  title: string;
  severity: Severity;
  type: string;
  sourceIp?: string | null;
  blocked: boolean;
  evidence: string[];
}

export interface TopologyGraph {
  nodes: TopologyNode[];
  links: TopologyLink[];
  externalEntities: TopologyExternalEntity[];
  activeAttacks: TopologyActiveAttack[];
}

export interface ComponentRelation {
  id: number;
  sourceComponentId: number;
  sourceName: string;
  targetComponentId: number;
  targetName: string;
  relationType: ComponentRelationType;
  label?: string | null;
  createdAt: string;
}

export interface SaveComponentRelationPayload {
  sourceComponentId: number;
  targetComponentId: number;
  relationType: ComponentRelationType;
  label?: string;
}

export interface SaveMonitoredComponentPayload {
  name: string;
  type: DeviceType;
  ipAddress?: string;
  httpUrl?: string;
  tcpPort?: number;
  snmpPort?: number;
  snmpCommunity?: string;
  snmpOid?: string;
  location: string;
  criticality: Criticality;
  monitoringMethods: MonitoringMethod[];
  checkIntervalSeconds?: number;
  enabled?: boolean;
}

export interface ComponentConnectionCheck {
  componentId: number;
  componentName: string;
  successful: boolean;
  resultStatus: DeviceStatus;
  message: string;
  details: string[];
  checkedAt: string;
}

export interface MetricSample {
  id?: number | null;
  componentId: number;
  componentName: string;
  metricName: string;
  metricValue: number;
  unit: string;
  source: MonitoringMethod | string;
  sampledAt: string;
}

export interface AiRelatedEvent {
  id: number;
  eventType: string;
  severity: Severity;
  message: string;
  details?: string | null;
  rawLog?: string | null;
  sourceIp?: string | null;
  syslogSourceName?: string | null;
  parsingProfile?: string | null;
  eventSource?: string | null;
  deviceId: string;
  deviceName: string;
  deviceType: DeviceType;
  location: string;
  occurredAt: string;
}

export interface AiSimilarIncident {
  id: number;
  correlationKey: string;
  title: string;
  category: string;
  severity: Severity;
  status: IncidentStatus;
  deviceId: string;
  deviceName: string;
  firstSeenAt: string;
  lastSeenAt: string;
  eventCount: number;
}

export interface AiPreviousReport {
  id: number;
  eventId?: number | null;
  deviceId: string;
  deviceName: string;
  eventType: string;
  severity: Severity;
  summary: string;
  provider: string;
  model?: string | null;
  generatedAt: string;
}

export interface PacketCaptureAnalysis {
  id: number;
  incidentId: number;
  fileName: string;
  contentType: string;
  fileSize: number;
  totalPackets: number;
  totalBytes: number;
  capturedPacketBytes?: number;
  topSourceIps: string[];
  topDestinationIps: string[];
  topProtocols: string[];
  topDestinationPorts: string[];
  suspiciousFindings: string[];
  summary: string;
  createdAt: string;
}

export type PacketCaptureJobStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

export type PacketCaptureFailureCode =
  | 'PROVIDER_UNAVAILABLE'
  | 'TIMEOUT'
  | 'TCPDUMP_FAILURE'
  | 'EMPTY_CAPTURE'
  | 'TRANSFER_FAILURE'
  | 'ANALYSIS_FAILURE'
  | 'CANCELLED'
  | 'INVALID_REQUEST'
  | 'INCIDENT_RESOLVED'
  | 'BUSY';

export type CaptureTrigger = 'MANUAL' | 'AUTO_INCIDENT' | 'AUTO_ROLLING';

export interface PacketCaptureJob {
  id: number;
  incidentId: number;
  provider: string;
  status: PacketCaptureJobStatus;
  sourceIp?: string | null;
  destinationIp?: string | null;
  destinationComponentName?: string | null;
  matchedInterfaceIp?: string | null;
  capturePointId?: string | null;
  capturePoint?: string | null;
  interfaceName?: string | null;
  durationSeconds: number;
  startedAt?: string | null;
  completedAt?: string | null;
  packetCount?: number | null;
  fileSizeBytes?: number | null;
  failureCode?: PacketCaptureFailureCode | null;
  errorMessage?: string | null;
  analysisId?: number | null;
  createdAt: string;
  trigger?: CaptureTrigger | null;
  preTriggerSeconds?: number | null;
  postTriggerSeconds?: number | null;
  triggeredAt?: string | null;
  captureWindowStart?: string | null;
  captureWindowEnd?: string | null;
}

export interface PacketCapturePreview {
  sourceIp?: string | null;
  destinationIp?: string | null;
  destinationComponentName?: string | null;
  matchedInterfaceName?: string | null;
  matchedInterfaceIp?: string | null;
  capturePointId: string;
  capturePoint: string;
  interfaceName: string;
  durationSeconds: number;
  liveCaptureAllowed: boolean;
  message?: string | null;
}

export interface PacketCaptureProviderHealth {
  available: boolean;
  status: string;
  message: string;
  rollingEnabled?: boolean | null;
  rollingRunning?: boolean | null;
}

export type NetFlowProviderType = 'NFDUMP';
export type NetFlowImportStatus = 'RUNNING' | 'SUCCESS' | 'FAILED';
export type NetFlowAnomalyType =
  | 'PORT_SCAN'
  | 'MANY_DESTINATIONS'
  | 'SENSITIVE_PORT_ACCESS'
  | 'HIGH_VOLUME_TRANSFER';

export interface NetFlowSource {
  id: number;
  name: string;
  providerType: NetFlowProviderType;
  dataDirectory: string;
  collectorPort: number;
  enabled: boolean;
  lastImportAt?: string | null;
  lastImportStatus?: NetFlowImportStatus | null;
  lastImportMessage?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface SaveNetFlowSourcePayload {
  name: string;
  providerType: NetFlowProviderType;
  dataDirectory: string;
  collectorPort: number;
  enabled?: boolean;
}

export interface NetFlowImportRun {
  id: number;
  sourceId?: number | null;
  startedAt: string;
  finishedAt?: string | null;
  status: NetFlowImportStatus;
  recordsRead: number;
  recordsImported: number;
  suspiciousFlows: number;
  incidentsCreated: number;
  errorMessage?: string | null;
}

export interface NetworkFlow {
  id: number;
  sourceId?: number | null;
  startTime: string;
  endTime: string;
  durationMs: number;
  sourceIp: string;
  destinationIp: string;
  sourcePort?: number | null;
  destinationPort?: number | null;
  protocol: string;
  packets: number;
  bytes: number;
  exporterName?: string | null;
  inputInterface?: number | null;
  outputInterface?: number | null;
  suspicious: boolean;
  anomalyType?: NetFlowAnomalyType | null;
  anomalyReason?: string | null;
  incidentId?: number | null;
  rawRecord?: string | null;
  createdAt: string;
}

export interface NetworkFlowSummary {
  sourceName?: string | null;
  sourceIp: string;
  destinationIp: string;
  destinationPorts: number[];
  protocols: string[];
  flowCount: number;
  totalPackets: number;
  totalBytes: number;
  anomalyType?: NetFlowAnomalyType | null;
  anomalyReason?: string | null;
  firstSeenAt: string;
  lastSeenAt: string;
}

export interface AiIncidentContext {
  incidentId: number;
  correlationKey: string;
  title: string;
  category: string;
  severity: Severity;
  status: IncidentStatus;
  location: string;
  deviceId: string;
  deviceName: string;
  deviceType: DeviceType;
  firstSeenAt: string;
  lastSeenAt: string;
  durationMinutes: number;
  eventCount: number;
  acknowledged: boolean;
  component?: MonitoredComponent | null;
  relatedEvents: AiRelatedEvent[];
  recentMetrics: MetricSample[];
  packetCaptureSummaries: PacketCaptureAnalysis[];
  networkFlowSummaries: NetworkFlowSummary[];
  previousSimilarIncidents: AiSimilarIncident[];
  previousReports: AiPreviousReport[];
}

export type ThresholdOperator = 'GREATER_THAN' | 'LESS_THAN';
export type ThresholdState = 'NORMAL' | 'WARNING' | 'CRITICAL';

export interface MetricThreshold {
  id: number;
  componentId: number;
  metricName: string;
  operator: ThresholdOperator;
  warningValue: number;
  criticalValue: number;
  enabled: boolean;
  lastState: ThresholdState;
  lastTriggeredAt?: string | null;
  createdAt: string;
  updatedAt: string;
}

export type IncidentStatus = 'ACTIVE' | 'ACKNOWLEDGED' | 'RESOLVED' | 'RECOVERED';

export interface Incident {
  id: number;
  title: string;
  category: string;
  deviceId: string;
  deviceName: string;
  deviceType: DeviceType;
  location: string;
  severity: Severity;
  status: IncidentStatus;
  resultingStatus: DeviceStatus;
  firstSeenAt: string;
  lastSeenAt: string;
  createdAt?: string | null;
  lastActivityAt?: string | null;
  recoveredAt?: string | null;
  resolvedAt?: string | null;
  durationMinutes: number;
  eventCount: number;
  acknowledged: boolean;
  acknowledgedAt?: string | null;
  previousSimilarIncidentId?: number | null;
  recurring?: boolean;
  latestEvent: EventLog;
  relatedEvents: EventLog[];
  relatedComponents?: IncidentComponentMatch[];
}

export type SyslogParserProfile =
  | 'GENERIC'
  | 'FIREWALL'
  | 'SWITCH'
  | 'LINUX_AUTH'
  | 'UPS'
  | 'CAMERA'
  | 'APPLICATION';

export interface SyslogSource {
  id: number;
  name: string;
  expectedHost: string;
  deviceId: string;
  deviceName: string;
  deviceType: DeviceType;
  location: string;
  parserProfile: SyslogParserProfile;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface SaveSyslogSourcePayload {
  name: string;
  expectedHost: string;
  deviceId: string;
  deviceName: string;
  deviceType: DeviceType;
  location: string;
  parserProfile: SyslogParserProfile;
  enabled?: boolean;
}

export interface SaveMetricThresholdPayload {
  metricName: string;
  operator: ThresholdOperator;
  warningValue: number;
  criticalValue: number;
  enabled?: boolean;
}

interface SaveDiagnosticReportRequest {
  incident: EventLog;
  analysis: IncidentAnalysis;
  context?: AiIncidentContext | null;
}

function buildQuery(params: Record<string, string | number | boolean | undefined | null>) {
  const searchParams = new URLSearchParams();

  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      searchParams.set(key, String(value));
    }
  });

  const query = searchParams.toString();
  return query ? `?${query}` : '';
}

export const aiopsApi = {
  login: (email: string, password: string) =>
    apiRequest<LoginResponse>('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
    }),
  logout: () =>
    apiRequest<void>(
      '/api/auth/logout',
      {
        method: 'POST',
      },
      false,
    ),
  getCurrentUser: () => apiGet<AuthUser>('/api/auth/me'),
  getAuditLogs: (params: {
    page?: number;
    size?: number;
    username?: string;
    action?: AuditAction | '';
    from?: string;
    to?: string;
  }) =>
    apiGet<PageResponse<AuditLog>>(
      `/api/audit-logs${buildQuery({
        page: params.page ?? 0,
        size: params.size ?? 20,
        username: params.username,
        action: params.action,
        from: params.from,
        to: params.to,
      })}`,
    ),
  logAuditAction: (
    action: AuditAction,
    payload?: { targetType?: string; targetId?: string; details?: string },
  ) =>
    apiRequest<void>(
      '/api/audit-logs/client',
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          action,
          targetType: payload?.targetType,
          targetId: payload?.targetId,
          details: payload?.details,
        }),
      },
      false,
    ),
  getUsers: (params: {
    page?: number;
    size?: number;
    search?: string;
    role?: UserRole | '';
    enabled?: 'true' | 'false' | '';
  }) =>
    apiGet<PageResponse<ManagedUser>>(
      `/api/users${buildQuery({
        page: params.page ?? 0,
        size: params.size ?? 20,
        search: params.search,
        role: params.role,
        enabled: params.enabled,
      })}`,
    ),
  getUser: (id: number) => apiGet<ManagedUser>(`/api/users/${id}`),
  createUser: (payload: CreateUserPayload) =>
    apiRequest<ManagedUser>('/api/users', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    }),
  updateUser: (id: number, payload: UpdateUserPayload) =>
    apiRequest<ManagedUser>(`/api/users/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    }),
  enableUser: (id: number) =>
    apiRequest<ManagedUser>(`/api/users/${id}/enable`, {
      method: 'PATCH',
    }),
  disableUser: (id: number) =>
    apiRequest<ManagedUser>(`/api/users/${id}/disable`, {
      method: 'PATCH',
    }),
  resetUserPassword: (id: number, password: string) =>
    apiRequest<ManagedUser>(`/api/users/${id}/reset-password`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ password }),
    }),
  getDevices: () => apiGet<Device[]>('/api/devices'),
  getComponents: () => apiGet<MonitoredComponent[]>('/api/components'),
  getTopology: () => apiGet<TopologyGraph>('/api/topology'),
  getComponentRelations: () => apiGet<ComponentRelation[]>('/api/topology/relations'),
  createComponentRelation: (payload: SaveComponentRelationPayload) =>
    apiRequest<ComponentRelation>('/api/topology/relations', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    }),
  deleteComponentRelation: (id: number) =>
    apiRequest<void>(`/api/topology/relations/${id}`, { method: 'DELETE' }, false),
  getComponent: (id: number) => apiGet<MonitoredComponent>(`/api/components/${id}`),
  getComponentInterfaces: (id: number) =>
    apiGet<ComponentNetworkInterface[]>(`/api/components/${id}/interfaces`),
  createComponentInterface: (id: number, payload: SaveComponentNetworkInterfacePayload) =>
    apiRequest<ComponentNetworkInterface>(`/api/components/${id}/interfaces`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    }),
  updateComponentInterface: (
    id: number,
    interfaceId: number,
    payload: SaveComponentNetworkInterfacePayload,
  ) =>
    apiRequest<ComponentNetworkInterface>(`/api/components/${id}/interfaces/${interfaceId}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    }),
  deleteComponentInterface: async (id: number, interfaceId: number) => {
    await apiRequest<void>(
      `/api/components/${id}/interfaces/${interfaceId}`,
      {
        method: 'DELETE',
      },
      false,
    );
  },
  getComponentMetrics: (id: number, hours = 24) =>
    apiGet<MetricSample[]>(`/api/components/${id}/metrics${buildQuery({ hours: String(hours) })}`),
  getComponentThresholds: (id: number) =>
    apiGet<MetricThreshold[]>(`/api/components/${id}/thresholds`),
  createComponentThreshold: async (id: number, payload: SaveMetricThresholdPayload) => {
    return apiRequest<MetricThreshold>(`/api/components/${id}/thresholds`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
  },
  updateComponentThreshold: async (
    componentId: number,
    thresholdId: number,
    payload: SaveMetricThresholdPayload,
  ) => {
    return apiRequest<MetricThreshold>(`/api/components/${componentId}/thresholds/${thresholdId}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
  },
  deleteComponentThreshold: async (componentId: number, thresholdId: number) => {
    await apiRequest<void>(
      `/api/components/${componentId}/thresholds/${thresholdId}`,
      {
        method: 'DELETE',
      },
      false,
    );
  },
  getEnabledComponents: () => apiGet<MonitoredComponent[]>('/api/components/enabled'),
  getSyslogSources: () => apiGet<SyslogSource[]>('/api/syslog-sources'),
  getEnabledSyslogSources: () => apiGet<SyslogSource[]>('/api/syslog-sources/enabled'),
  createSyslogSource: async (payload: SaveSyslogSourcePayload) => {
    return apiRequest<SyslogSource>('/api/syslog-sources', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
  },
  updateSyslogSource: async (id: number, payload: SaveSyslogSourcePayload) => {
    return apiRequest<SyslogSource>(`/api/syslog-sources/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
  },
  deleteSyslogSource: async (id: number) => {
    await apiRequest<void>(
      `/api/syslog-sources/${id}`,
      {
        method: 'DELETE',
      },
      false,
    );
  },
  createComponent: async (payload: SaveMonitoredComponentPayload) => {
    return apiRequest<MonitoredComponent>('/api/components', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
  },
  updateComponent: async (id: number, payload: SaveMonitoredComponentPayload) => {
    return apiRequest<MonitoredComponent>(`/api/components/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
  },
  enableComponent: async (id: number) => {
    return apiRequest<MonitoredComponent>(`/api/components/${id}/enable`, {
      method: 'PATCH',
    });
  },
  disableComponent: async (id: number) => {
    return apiRequest<MonitoredComponent>(`/api/components/${id}/disable`, {
      method: 'PATCH',
    });
  },
  checkComponentNow: async (id: number) => {
    return apiRequest<ComponentConnectionCheck>(`/api/components/${id}/check`, {
      method: 'POST',
    });
  },
  deleteComponent: async (id: number) => {
    await apiRequest<void>(
      `/api/components/${id}`,
      {
        method: 'DELETE',
      },
      false,
    );
  },
  getEvents: (filters: EventFilters = {}) =>
    apiGet<EventLog[]>(
      `/api/events${buildQuery({
        deviceId: filters.deviceId,
        deviceType: filters.deviceType,
        eventType: filters.eventType,
        severity: filters.severity,
        from: filters.from,
        to: filters.to,
      })}`,
    ),
  getAlerts: (filters: AlertFilters = {}) =>
    apiGet<EventLog[]>(
      `/api/alerts${buildQuery({
        minSeverity: filters.minSeverity || 'WARNING',
      })}`,
    ),
  getIncidents: () => apiGet<Incident[]>('/api/incidents'),
  getIncident: (id: number) => apiGet<Incident>(`/api/incidents/${id}`),
  getIncidentAiContext: (id: number) =>
    apiGet<AiIncidentContext>(`/api/incidents/${id}/ai-context`),
  getIncidentNetworkFlows: (id: number) =>
    apiGet<NetworkFlow[]>(`/api/incidents/${id}/network-flows`),
  rebuildIncidents: async () => {
    return apiRequest<Incident[]>('/api/incidents/rebuild', {
      method: 'POST',
    });
  },
  acknowledgeIncident: async (id: number) => {
    return apiRequest<Incident>(`/api/incidents/${id}/acknowledge`, {
      method: 'PATCH',
    });
  },
  unacknowledgeIncident: async (id: number) => {
    return apiRequest<Incident>(`/api/incidents/${id}/unacknowledge`, {
      method: 'PATCH',
    });
  },
  resolveIncident: async (id: number) => {
    return apiRequest<Incident>(`/api/incidents/${id}/resolve`, {
      method: 'PATCH',
    });
  },
  deleteIncident: async (id: number) => {
    return apiRequest<void>(`/api/incidents/${id}`, { method: 'DELETE' }, false);
  },
  deleteEvent: async (id: number) => {
    return apiRequest<void>(`/api/events/${id}`, { method: 'DELETE' }, false);
  },
  purgeEventsOlderThan: async (days: number) => {
    return apiRequest<number>(`/api/events/older-than${buildQuery({ days })}`, {
      method: 'DELETE',
    });
  },
  resetDemoData: async () => {
    return apiRequest<void>('/api/admin/maintenance/reset-demo-data', { method: 'POST' }, false);
  },
  getIncidentPacketCaptures: (incidentId: number) =>
    apiGet<PacketCaptureAnalysis[]>(`/api/incidents/${incidentId}/pcap`),
  getPacketCapturePreview: (incidentId: number) =>
    apiGet<PacketCapturePreview>(`/api/incidents/${incidentId}/packet-captures/preview`),
  getPacketCaptureJobs: (incidentId: number) =>
    apiGet<PacketCaptureJob[]>(`/api/incidents/${incidentId}/packet-captures/jobs`),
  getPacketCaptureJob: (incidentId: number, jobId: number) =>
    apiGet<PacketCaptureJob>(`/api/incidents/${incidentId}/packet-captures/jobs/${jobId}`),
  startPacketCapture: (
    incidentId: number,
    payload?: { durationSeconds?: number; capturePointId?: string },
  ) =>
    apiRequest<PacketCaptureJob>(`/api/incidents/${incidentId}/packet-captures/start`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload ?? {}),
    }),
  cancelPacketCapture: (incidentId: number, jobId: number) =>
    apiRequest<PacketCaptureJob>(
      `/api/incidents/${incidentId}/packet-captures/jobs/${jobId}/cancel`,
      { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' },
    ),
  getPacketCaptureProviderHealth: () =>
    apiGet<PacketCaptureProviderHealth>('/api/packet-captures/provider-health'),
  deleteIncidentPcap: (incidentId: number, analysisId: number) =>
    apiRequest<void>(
      `/api/incidents/${incidentId}/pcap/${analysisId}`,
      { method: 'DELETE' },
      false,
    ),
  getNetFlowSources: () => apiGet<NetFlowSource[]>('/api/netflow/sources'),
  createNetFlowSource: (payload: SaveNetFlowSourcePayload) =>
    apiRequest<NetFlowSource>('/api/netflow/sources', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    }),
  updateNetFlowSource: (id: number, payload: SaveNetFlowSourcePayload) =>
    apiRequest<NetFlowSource>(`/api/netflow/sources/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    }),
  setNetFlowSourceEnabled: (id: number, enabled: boolean) =>
    apiRequest<NetFlowSource>(`/api/netflow/sources/${id}/enable${buildQuery({ enabled })}`, {
      method: 'PATCH',
    }),
  deleteNetFlowSource: (id: number) =>
    apiRequest<void>(`/api/netflow/sources/${id}`, { method: 'DELETE' }, false),
  getNetFlowImportRuns: () => apiGet<NetFlowImportRun[]>('/api/netflow/import-runs'),
  importLatestNetFlows: (sourceId: number) =>
    apiRequest<NetFlowImportRun>(`/api/netflow/import/latest${buildQuery({ sourceId })}`, {
      method: 'POST',
    }),
  importSampleNetFlows: () =>
    apiRequest<NetFlowImportRun>('/api/netflow/import/sample', {
      method: 'POST',
    }),
  getNetFlows: (params: {
    sourceIp?: string;
    destinationIp?: string;
    protocol?: string;
    suspicious?: boolean;
    anomalyType?: NetFlowAnomalyType | '';
    from?: string;
    to?: string;
    page?: number;
    size?: number;
  }) =>
    apiGet<PageResponse<NetworkFlow>>(
      `/api/netflow/flows${buildQuery({
        sourceIp: params.sourceIp,
        destinationIp: params.destinationIp,
        protocol: params.protocol,
        suspicious: params.suspicious,
        anomalyType: params.anomalyType,
        from: params.from,
        to: params.to,
        page: params.page ?? 0,
        size: params.size ?? 20,
      })}`,
    ),
  getPacketCaptureAnalysis: (id: number) =>
    apiGet<PacketCaptureAnalysis>(`/api/packet-captures/${id}`),
  uploadIncidentPcap: (incidentId: number, file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return apiRequest<PacketCaptureAnalysis>(`/api/incidents/${incidentId}/pcap`, {
      method: 'POST',
      body: formData,
    });
  },
  getReports: () => apiGet<DiagnosticReport[]>('/api/reports'),
  saveReport: async ({ incident, analysis, context }: SaveDiagnosticReportRequest) => {
    return apiRequest<DiagnosticReport>('/api/reports', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        eventId: incident.id,
        deviceId: incident.deviceId,
        deviceName: incident.deviceName,
        deviceType: incident.deviceType,
        location: incident.location,
        eventType: incident.eventType,
        severity: incident.severity,
        message: incident.message,
        details: incident.details,
        occurredAt: incident.occurredAt,
        summary: analysis.summary,
        priority: analysis.priority,
        impact: analysis.impact,
        risk: analysis.risk,
        probableCauses: analysis.probable_causes,
        suggestedActions: analysis.suggested_actions,
        diagnosticCommands: analysis.diagnostic_commands,
        packetCaptureSummaries: context?.packetCaptureSummaries ?? [],
        networkFlowSummaries: context?.networkFlowSummaries ?? [],
        provider: analysis.provider,
        model: analysis.model,
        requestedProvider: analysis.requestedProvider ?? analysis.requested_provider ?? null,
        fallbackUsed: analysis.fallbackUsed ?? analysis.fallback_used ?? false,
        fallbackReasonCode: analysis.fallbackReasonCode ?? analysis.fallback_reason_code ?? null,
        fallbackReason: analysis.fallbackReason ?? analysis.fallback_reason ?? null,
        contextFingerprint: analysis.contextFingerprint ?? analysis.context_fingerprint ?? null,
        cacheHit: analysis.cacheHit ?? analysis.cache_hit ?? false,
        analysisDurationMs: analysis.analysisDurationMs ?? analysis.analysis_duration_ms ?? null,
      }),
    });
  },
};
