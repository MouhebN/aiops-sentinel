from pydantic import BaseModel, ConfigDict, Field


class RelatedEventContext(BaseModel):
    event_type: str = Field(alias="eventType")
    severity: str
    message: str
    details: str | None = None
    raw_log: str | None = Field(default=None, alias="rawLog")
    source_ip: str | None = Field(default=None, alias="sourceIp")
    syslog_source_name: str | None = Field(default=None, alias="syslogSourceName")
    parsing_profile: str | None = Field(default=None, alias="parsingProfile")
    event_source: str | None = Field(default=None, alias="eventSource")
    protocol: str | None = None
    source_address: str | None = Field(default=None, alias="sourceAddress")
    source_port: int | None = Field(default=None, alias="sourcePort")
    destination_address: str | None = Field(default=None, alias="destinationAddress")
    destination_port: int | None = Field(default=None, alias="destinationPort")
    occurred_at: str | None = Field(default=None, alias="occurredAt")
    resulting_status: str | None = Field(default=None, alias="resultingStatus")


class ComponentContext(BaseModel):
    id: int | None = None
    name: str | None = None
    type: str | None = None
    ip_address: str | None = Field(default=None, alias="ipAddress")
    http_url: str | None = Field(default=None, alias="httpUrl")
    tcp_port: int | None = Field(default=None, alias="tcpPort")
    location: str | None = None
    criticality: str | None = None
    monitoring_methods: list[str] = Field(default_factory=list, alias="monitoringMethods")
    last_status: str | None = Field(default=None, alias="lastStatus")
    last_error: str | None = Field(default=None, alias="lastError")
    last_check_details: str | None = Field(default=None, alias="lastCheckDetails")
    matched_network_ip: str | None = Field(default=None, alias="matchedNetworkIp")
    matched_interface_name: str | None = Field(default=None, alias="matchedInterfaceName")


class MetricContext(BaseModel):
    metric_name: str = Field(alias="metricName")
    metric_value: float = Field(alias="metricValue")
    unit: str
    source: str
    sampled_at: str | None = Field(default=None, alias="sampledAt")


class SimilarIncidentContext(BaseModel):
    id: int
    correlation_key: str = Field(alias="correlationKey")
    title: str
    category: str
    severity: str
    status: str
    device_id: str = Field(alias="deviceId")
    device_name: str = Field(alias="deviceName")
    first_seen_at: str | None = Field(default=None, alias="firstSeenAt")
    last_seen_at: str | None = Field(default=None, alias="lastSeenAt")
    last_activity_at: str | None = Field(default=None, alias="lastActivityAt")
    resolved_at: str | None = Field(default=None, alias="resolvedAt")
    event_count: int | None = Field(default=None, alias="eventCount")


class PreviousReportContext(BaseModel):
    id: int
    event_id: int | None = Field(default=None, alias="eventId")
    device_id: str = Field(alias="deviceId")
    device_name: str = Field(alias="deviceName")
    event_type: str = Field(alias="eventType")
    severity: str
    summary: str
    provider: str
    model: str | None = None
    generated_at: str | None = Field(default=None, alias="generatedAt")


class PacketCaptureSummaryContext(BaseModel):
    id: int
    file_name: str = Field(alias="fileName")
    total_packets: int = Field(alias="totalPackets")
    total_bytes: int = Field(alias="totalBytes")
    top_source_ips: list[str] = Field(default_factory=list, alias="topSourceIps")
    top_destination_ips: list[str] = Field(default_factory=list, alias="topDestinationIps")
    top_protocols: list[str] = Field(default_factory=list, alias="topProtocols")
    top_destination_ports: list[str] = Field(default_factory=list, alias="topDestinationPorts")
    suspicious_findings: list[str] = Field(default_factory=list, alias="suspiciousFindings")
    summary: str
    created_at: str | None = Field(default=None, alias="createdAt")


class NetworkFlowSummaryContext(BaseModel):
    source_name: str | None = Field(default=None, alias="sourceName")
    source_ip: str | None = Field(default=None, alias="sourceIp")
    destination_ip: str | None = Field(default=None, alias="destinationIp")
    destination_ports: list[int] = Field(default_factory=list, alias="destinationPorts")
    protocols: list[str] = Field(default_factory=list)
    flow_count: int = Field(alias="flowCount")
    total_packets: int = Field(alias="totalPackets")
    total_bytes: int = Field(alias="totalBytes")
    anomaly_type: str | None = Field(default=None, alias="anomalyType")
    anomaly_reason: str | None = Field(default=None, alias="anomalyReason")
    first_seen_at: str | None = Field(default=None, alias="firstSeenAt")
    last_seen_at: str | None = Field(default=None, alias="lastSeenAt")


class IncidentContext(BaseModel):
    id: int | None = Field(default=None, alias="incidentId")
    correlation_key: str | None = Field(default=None, alias="correlationKey")
    title: str
    category: str
    status: str
    first_seen_at: str | None = Field(default=None, alias="firstSeenAt")
    last_seen_at: str | None = Field(default=None, alias="lastSeenAt")
    created_at: str | None = Field(default=None, alias="createdAt")
    last_activity_at: str | None = Field(default=None, alias="lastActivityAt")
    resolved_at: str | None = Field(default=None, alias="resolvedAt")
    duration_minutes: int | None = Field(default=None, alias="durationMinutes")
    event_count: int | None = Field(default=None, alias="eventCount")
    acknowledged: bool | None = None
    previous_similar_incident_id: int | None = Field(default=None, alias="previousSimilarIncidentId")
    component: ComponentContext | None = None
    related_events: list[RelatedEventContext] = Field(default_factory=list, alias="relatedEvents")
    recent_metrics: list[MetricContext] = Field(default_factory=list, alias="recentMetrics")
    packet_capture_summaries: list[PacketCaptureSummaryContext] = Field(
        default_factory=list,
        alias="packetCaptureSummaries",
    )
    network_flow_summaries: list[NetworkFlowSummaryContext] = Field(
        default_factory=list,
        alias="networkFlowSummaries",
    )
    previous_similar_incidents: list[SimilarIncidentContext] = Field(
        default_factory=list,
        alias="previousSimilarIncidents",
    )
    previous_reports: list[PreviousReportContext] = Field(
        default_factory=list,
        alias="previousReports",
    )


class IncidentAnalysisRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    event_type: str = Field(alias="eventType")
    severity: str
    message: str
    details: str | None = None
    device_type: str = Field(alias="deviceType")
    device_name: str = Field(alias="deviceName")
    location: str | None = None
    occurred_at: str | None = Field(default=None, alias="occurredAt")
    incident: IncidentContext | None = None


class IncidentAnalysisResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True, ser_json_by_alias=True)

    summary: str
    priority: str
    impact: str
    risk: str
    probable_causes: list[str]
    suggested_actions: list[str]
    diagnostic_commands: list[str]
    provider: str
    model: str | None = None
    requested_provider: str | None = Field(default=None, alias="requestedProvider")
    fallback_used: bool = Field(default=False, alias="fallbackUsed")
    fallback_reason_code: str | None = Field(default=None, alias="fallbackReasonCode")
    fallback_reason: str | None = Field(default=None, alias="fallbackReason")
    cache_hit: bool = Field(default=False, alias="cacheHit")
    analysis_duration_ms: int | None = Field(default=None, alias="analysisDurationMs")
    context_fingerprint: str | None = Field(default=None, alias="contextFingerprint")


class PacketCaptureAnalysisResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    total_packets: int = Field(alias="totalPackets")
    total_bytes: int = Field(alias="totalBytes")
    top_source_ips: list[str] = Field(default_factory=list, alias="topSourceIps")
    top_destination_ips: list[str] = Field(default_factory=list, alias="topDestinationIps")
    top_protocols: list[str] = Field(default_factory=list, alias="topProtocols")
    top_destination_ports: list[str] = Field(default_factory=list, alias="topDestinationPorts")
    suspicious_findings: list[str] = Field(default_factory=list, alias="suspiciousFindings")
    summary: str
