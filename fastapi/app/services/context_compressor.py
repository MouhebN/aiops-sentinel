from __future__ import annotations

import re
from dataclasses import dataclass, field

from app.config import ContextLimits
from app.schemas import (
    IncidentAnalysisRequest,
    IncidentContext,
    MetricContext,
    NetworkFlowSummaryContext,
    PacketCaptureSummaryContext,
    PreviousReportContext,
    RelatedEventContext,
    SimilarIncidentContext,
)

SECURITY_EVENT_MARKERS = (
    "FIREWALL",
    "AUTH",
    "DENY",
    "SCAN",
    "MALWARE",
    "IDS",
    "IPS",
    "PORT_SCAN",
    "FAIL",
    "INTRUSION",
)
NETFLOW_EVENT_MARKERS = ("NETFLOW", "PORT_SCAN")
SCAN_PAIR_RE = re.compile(
    r"(?:from\s+)?(\d{1,3}(?:\.\d{1,3}){3})\s*(?:->|to)\s*(\d{1,3}(?:\.\d{1,3}){3})",
    re.IGNORECASE,
)
PORT_LIST_RE = re.compile(r"ports\s*[=:]?\s*((?:\d+\s*,\s*)*\d+)", re.IGNORECASE)


@dataclass
class EventGroup:
    event_type: str
    severity: str
    event_source: str | None
    source: str | None
    destination: str | None
    ports: tuple[int, ...]
    protocol: str | None
    count: int
    first_seen: str | None
    last_seen: str | None
    representative_message: str
    raw_log: str | None
    parsing_profile: str | None
    syslog_source_name: str | None
    resulting_status: str | None
    message_truncated: bool = False
    raw_log_truncated: bool = False

    @property
    def is_syslog(self) -> bool:
        if (self.event_source or "").upper() == "SYSLOG":
            return True
        return bool(self.raw_log or self.parsing_profile or self.syslog_source_name)

    @property
    def is_netflow(self) -> bool:
        source = (self.event_source or "").upper()
        event_type = (self.event_type or "").upper()
        return source == "NETFLOW" or any(marker in event_type for marker in NETFLOW_EVENT_MARKERS)

    @property
    def is_security(self) -> bool:
        event_type = (self.event_type or "").upper()
        return any(marker in event_type for marker in SECURITY_EVENT_MARKERS)


@dataclass
class CompressedPcap:
    file_name: str
    total_packets: int
    total_bytes: int
    scan_summary: str | None
    ports: tuple[str, ...]
    findings: tuple[str, ...]
    top_protocols: tuple[str, ...]
    created_at: str | None


@dataclass
class MetricSeries:
    metric_name: str
    unit: str
    source: str
    current: float
    min_value: float
    max_value: float
    average: float
    sample_count: int
    first_sampled_at: str | None
    last_sampled_at: str | None


@dataclass
class CompressedContext:
    event_groups: list[EventGroup] = field(default_factory=list)
    netflow_groups: list[NetworkFlowSummaryContext] = field(default_factory=list)
    pcap_summaries: list[CompressedPcap] = field(default_factory=list)
    metric_series: list[MetricSeries] = field(default_factory=list)
    previous_incidents: list[SimilarIncidentContext] = field(default_factory=list)
    previous_reports: list[PreviousReportContext] = field(default_factory=list)
    related_events_raw: int = 0
    event_groups_sent: int = 0
    pcap_summaries_raw: int = 0
    pcap_summaries_sent: int = 0
    netflow_groups_raw: int = 0
    netflow_groups_sent: int = 0
    metrics_raw: int = 0
    metric_series_sent: int = 0
    omitted_covered_events: int = 0
    any_truncated: bool = False

    @property
    def context_compressed(self) -> bool:
        return (
            self.related_events_raw > self.event_groups_sent
            or self.pcap_summaries_raw > self.pcap_summaries_sent
            or self.netflow_groups_raw > self.netflow_groups_sent
            or self.metrics_raw > self.metric_series_sent
            or self.omitted_covered_events > 0
            or self.any_truncated
        )


def compress_incident_context(
    request: IncidentAnalysisRequest,
    limits: ContextLimits | None = None,
    drop_optional: bool = False,
) -> CompressedContext:
    limits = limits or ContextLimits.from_env()
    incident = request.incident
    if incident is None:
        return CompressedContext()

    related_raw = len(incident.related_events)
    if incident.event_count is not None:
        related_raw = max(related_raw, incident.event_count)

    candidates, omitted = events_not_covered_by_netflow(incident)
    groups = group_related_events(candidates, limits)
    groups = select_event_groups(groups, limits.max_event_groups)

    netflow_groups = select_netflow_groups(incident.network_flow_summaries, limits)
    pcap_summaries = select_pcap_summaries(incident.packet_capture_summaries, limits)
    metric_series = aggregate_metrics(incident.recent_metrics)[: limits.max_metric_series]
    previous_incidents = [] if drop_optional else list(incident.previous_similar_incidents[: limits.max_previous_similar_incidents])
    previous_reports = [] if drop_optional else list(incident.previous_reports[: limits.max_previous_reports])

    any_truncated = any(group.message_truncated or group.raw_log_truncated for group in groups)
    return CompressedContext(
        event_groups=groups,
        netflow_groups=netflow_groups,
        pcap_summaries=pcap_summaries,
        metric_series=metric_series,
        previous_incidents=previous_incidents,
        previous_reports=previous_reports,
        related_events_raw=related_raw,
        event_groups_sent=len(groups),
        pcap_summaries_raw=len(incident.packet_capture_summaries),
        pcap_summaries_sent=len(pcap_summaries),
        netflow_groups_raw=len(incident.network_flow_summaries),
        netflow_groups_sent=len(netflow_groups),
        metrics_raw=len(incident.recent_metrics),
        metric_series_sent=len(metric_series),
        omitted_covered_events=omitted,
        any_truncated=any_truncated,
    )


def events_not_covered_by_netflow(incident: IncidentContext) -> tuple[list[RelatedEventContext], int]:
    summaries = incident.network_flow_summaries
    if not summaries:
        return list(incident.related_events), 0
    kept: list[RelatedEventContext] = []
    omitted = 0
    for event in incident.related_events:
        if is_covered_by_netflow_summary(event, summaries):
            omitted += 1
            continue
        kept.append(event)
    return kept, omitted


def is_covered_by_netflow_summary(
    event: RelatedEventContext,
    summaries: list[NetworkFlowSummaryContext],
) -> bool:
    event_type = (event.event_type or "").upper()
    event_source = (event.event_source or "").upper()
    looks_like_netflow = event_source == "NETFLOW" or any(marker in event_type for marker in NETFLOW_EVENT_MARKERS)
    if not looks_like_netflow:
        return False
    source = event_source_ip(event)
    destination = event.destination_address
    for summary in summaries:
        if source and summary.source_ip and source != summary.source_ip:
            continue
        if destination and summary.destination_ip and destination != summary.destination_ip:
            continue
        return True
    return event_source == "NETFLOW"


def group_related_events(events: list[RelatedEventContext], limits: ContextLimits) -> list[EventGroup]:
    buckets: dict[tuple, list[RelatedEventContext]] = {}
    for event in events:
        buckets.setdefault(event_group_key(event), []).append(event)
    groups = [build_event_group(members, limits) for members in buckets.values()]
    groups.sort(key=lambda group: group.last_seen or "", reverse=True)
    return groups


def event_group_key(event: RelatedEventContext) -> tuple:
    return (
        (event.event_type or "").upper(),
        (event.event_source or "").upper(),
        event_source_ip(event) or "",
        event.destination_address or "",
        (event.protocol or "").upper(),
        (event.severity or "").upper(),
        (event.resulting_status or "").upper(),
    )


def build_event_group(events: list[RelatedEventContext], limits: ContextLimits) -> EventGroup:
    ordered = sorted(events, key=lambda event: event.occurred_at or "")
    first = ordered[0]
    last = ordered[-1]
    ports = tuple(sorted({event.destination_port for event in ordered if event.destination_port is not None}))
    message, message_truncated = truncate_marked(first.message, limits.max_message_chars)
    raw_log = next((event.raw_log for event in ordered if event.raw_log), None)
    raw_log, raw_log_truncated = truncate_marked(raw_log, limits.max_message_chars) if raw_log else (None, False)
    return EventGroup(
        event_type=first.event_type,
        severity=first.severity,
        event_source=first.event_source,
        source=event_source_ip(first),
        destination=first.destination_address,
        ports=ports,
        protocol=first.protocol,
        count=len(ordered),
        first_seen=first.occurred_at,
        last_seen=last.occurred_at,
        representative_message=message,
        raw_log=raw_log,
        parsing_profile=next((event.parsing_profile for event in ordered if event.parsing_profile), None),
        syslog_source_name=next((event.syslog_source_name for event in ordered if event.syslog_source_name), None),
        resulting_status=first.resulting_status,
        message_truncated=message_truncated,
        raw_log_truncated=raw_log_truncated,
    )


def select_event_groups(groups: list[EventGroup], limit: int) -> list[EventGroup]:
    if len(groups) <= limit:
        return groups
    ranked = sorted(groups, key=event_group_rank, reverse=True)
    selected = ranked[:limit]
    selected.sort(key=lambda group: group.last_seen or "", reverse=True)
    return selected


def event_group_rank(group: EventGroup) -> tuple:
    severity_rank = {"CRITICAL": 3, "ERROR": 2, "WARNING": 1, "ALERT": 2}.get((group.severity or "").upper(), 0)
    unique_bonus = 1 if group.count == 1 else 0
    return (
        1 if group.is_security else 0,
        1 if group.is_syslog else 0,
        unique_bonus,
        severity_rank,
        group.last_seen or "",
    )


def select_netflow_groups(
    summaries: list[NetworkFlowSummaryContext],
    limits: ContextLimits,
) -> list[NetworkFlowSummaryContext]:
    ranked = sorted(
        summaries,
        key=lambda flow: (
            1 if flow.anomaly_type else 0,
            flow.flow_count,
            flow.total_packets,
        ),
        reverse=True,
    )
    selected = []
    for flow in ranked[: limits.max_netflow_groups]:
        ports = list(flow.destination_ports[: limits.max_netflow_ports])
        reason, _truncated = truncate_marked(flow.anomaly_reason or "", limits.max_message_chars)
        selected.append(
            flow.model_copy(
                update={
                    "destination_ports": ports,
                    "anomaly_reason": reason or flow.anomaly_reason,
                }
            )
        )
    return selected


def select_pcap_summaries(
    captures: list[PacketCaptureSummaryContext],
    limits: ContextLimits,
) -> list[CompressedPcap]:
    ranked = sorted(captures, key=lambda capture: (len(capture.suspicious_findings), capture.total_packets), reverse=True)
    compressed = [compress_pcap(capture, limits) for capture in ranked[: limits.max_pcap_summaries]]
    return compressed


def compress_pcap(capture: PacketCaptureSummaryContext, limits: ContextLimits) -> CompressedPcap:
    findings = tuple(
        truncate_marked(finding, limits.max_message_chars)[0]
        for finding in capture.suspicious_findings[: limits.max_pcap_findings]
    )
    scan_summary = None
    ports: list[str] = []
    for finding in capture.suspicious_findings:
        match = SCAN_PAIR_RE.search(finding)
        if match and scan_summary is None:
            scan_summary = f"{match.group(1)} -> {match.group(2)}"
        port_match = PORT_LIST_RE.search(finding)
        if port_match and not ports:
            ports = [part.strip() for part in port_match.group(1).split(",") if part.strip()]
    if not ports:
        ports = [normalize_port_label(item) for item in capture.top_destination_ports[: limits.max_pcap_top_n]]
    return CompressedPcap(
        file_name=capture.file_name,
        total_packets=capture.total_packets,
        total_bytes=capture.total_bytes,
        scan_summary=scan_summary,
        ports=tuple(ports[: limits.max_netflow_ports]),
        findings=findings,
        top_protocols=tuple(capture.top_protocols[:2]),
        created_at=capture.created_at,
    )


def aggregate_metrics(metrics: list[MetricContext]) -> list[MetricSeries]:
    buckets: dict[tuple[str, str, str], list[MetricContext]] = {}
    for metric in metrics:
        key = (metric.metric_name, metric.unit, metric.source)
        buckets.setdefault(key, []).append(metric)
    series: list[MetricSeries] = []
    for (name, unit, source), samples in buckets.items():
        ordered = sorted(samples, key=lambda item: item.sampled_at or "")
        values = [item.metric_value for item in ordered]
        series.append(
            MetricSeries(
                metric_name=name,
                unit=unit,
                source=source,
                current=values[-1],
                min_value=min(values),
                max_value=max(values),
                average=round(sum(values) / len(values), 2),
                sample_count=len(values),
                first_sampled_at=ordered[0].sampled_at,
                last_sampled_at=ordered[-1].sampled_at,
            )
        )
    series.sort(key=lambda item: (item.max_value, item.last_sampled_at or ""), reverse=True)
    return series


def event_source_ip(event: RelatedEventContext) -> str | None:
    return event.source_address or event.source_ip


def normalize_port_label(value: str) -> str:
    return value.split()[0].strip("() ,")


def truncate_marked(value: str, limit: int) -> tuple[str, bool]:
    compact = " ".join(value.split())
    if len(compact) <= limit:
        return compact, False
    return compact[:limit].rstrip() + " [truncated]", True
