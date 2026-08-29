from __future__ import annotations

import os
from dataclasses import dataclass

from app.config import MAX_PROMPT_CHARS, ContextLimits
from app.schemas import IncidentAnalysisRequest, RelatedEventContext
from app.services.context_compressor import (
    CompressedContext,
    EventGroup,
    compress_incident_context,
)
from app.services.prompt_templates import (
    AVAILABILITY_INSTRUCTIONS,
    BASE_INSTRUCTIONS,
    METRICS_INSTRUCTIONS,
    MULTI_EVIDENCE_INSTRUCTIONS,
    NETFLOW_INSTRUCTIONS,
    PCAP_INSTRUCTIONS,
    RESPONSE_SCHEMA,
    SYSLOG_INSTRUCTIONS,
)

ORGANIZATION_CONTEXT = os.getenv(
    "ORGANIZATION_CONTEXT",
    "banking IT infrastructure with servers, network devices, firewalls, UPS systems, IP cameras, and business applications",
)

AVAILABILITY_EVENT_MARKERS = (
    "PING",
    "HTTP_HEALTH",
    "HTTP_CHECK",
    "TCP_CHECK",
    "TCP_PORT",
    "RTSP",
    "SNMP",
    "UNREACHABLE",
    "CAMERA_STREAM",
    "SERVICE_DOWN",
    "INTERFACE_DOWN",
    "HEALTH_CHECK",
)
AVAILABILITY_CATEGORIES = {"AVAILABILITY", "VIDEO_STREAM", "METRIC_THRESHOLD"}
AVAILABILITY_METHODS = {"PING", "HTTP_HEALTH", "HTTP", "TCP", "RTSP", "SNMP"}


@dataclass(frozen=True)
class EvidenceFlags:
    syslog: bool = False
    netflow: bool = False
    pcap: bool = False
    metrics: bool = False
    availability: bool = False

    @property
    def independent_source_count(self) -> int:
        return sum((self.syslog, self.netflow, self.pcap))

    @property
    def multi_evidence(self) -> bool:
        return self.independent_source_count >= 2 or (
            self.independent_source_count >= 1 and (self.metrics or self.availability)
        )


def build_prompt(
    request: IncidentAnalysisRequest,
    limits: ContextLimits | None = None,
) -> dict:
    prompt, stats = render_prompt(request, compact=False, drop_optional=False, limits=limits)
    trimmed = False
    if len(prompt) > MAX_PROMPT_CHARS:
        prompt, stats = render_prompt(request, compact=True, drop_optional=False, limits=limits)
        trimmed = True
    if len(prompt) > MAX_PROMPT_CHARS:
        prompt, stats = render_prompt(request, compact=True, drop_optional=True, limits=limits)
        trimmed = True
    stats["prompt_characters"] = len(prompt)
    stats["trimmed"] = trimmed
    return {
        "prompt": prompt,
        "related_events_included": stats["event_groups_sent"],
        "related_events_total": stats["related_events_raw"],
        "netflow_evidence_included": stats["netflow_groups_sent"] > 0 or flags_netflow(stats),
        "trimmed": trimmed,
        "evidence": stats["evidence"],
        "related_events_raw": stats["related_events_raw"],
        "event_groups_sent": stats["event_groups_sent"],
        "pcap_summaries_raw": stats["pcap_summaries_raw"],
        "pcap_summaries_sent": stats["pcap_summaries_sent"],
        "netflow_groups_raw": stats["netflow_groups_raw"],
        "netflow_groups_sent": stats["netflow_groups_sent"],
        "metrics_raw": stats["metrics_raw"],
        "metric_series_sent": stats["metric_series_sent"],
        "prompt_characters": len(prompt),
        "context_compressed": stats["context_compressed"],
    }


def flags_netflow(stats: dict) -> bool:
    evidence = stats.get("evidence")
    return bool(evidence and evidence.netflow)


def render_prompt(
    request: IncidentAnalysisRequest,
    compact: bool = False,
    drop_optional: bool = False,
    limits: ContextLimits | None = None,
) -> tuple[str, dict]:
    incident_context, stats = format_incident_context(
        request,
        compact=compact,
        drop_optional=drop_optional,
        limits=limits,
    )
    flags: EvidenceFlags = stats["evidence"]
    prompt = "\n\n".join(
        part
        for part in (
            BASE_INSTRUCTIONS.replace("{schema}", RESPONSE_SCHEMA),
            f"The monitored environment is a bank network: {ORGANIZATION_CONTEXT}.",
            build_instruction_sections(flags),
            format_incident_header(request, compact=compact),
            incident_context.strip() or None,
        )
        if part
    )
    stats["prompt_characters"] = len(prompt)
    return prompt, stats


def detect_evidence(
    request: IncidentAnalysisRequest,
    drop_optional: bool = False,
    compressed: CompressedContext | None = None,
    limits: ContextLimits | None = None,
) -> EvidenceFlags:
    if compressed is None:
        compressed = compress_incident_context(request, limits=limits, drop_optional=drop_optional)
    syslog = any(group.is_syslog for group in compressed.event_groups)
    netflow = bool(compressed.netflow_groups) or any(group.is_netflow for group in compressed.event_groups)
    pcap = bool(compressed.pcap_summaries)
    metrics = bool(compressed.metric_series)
    return EvidenceFlags(
        syslog=syslog,
        netflow=netflow,
        pcap=pcap,
        metrics=metrics,
        availability=is_availability_incident(request),
    )


def build_instruction_sections(flags: EvidenceFlags) -> str:
    sections: list[str] = []
    if flags.syslog:
        sections.append(SYSLOG_INSTRUCTIONS)
    if flags.netflow:
        sections.append(NETFLOW_INSTRUCTIONS)
    if flags.pcap:
        sections.append(PCAP_INSTRUCTIONS)
    if flags.metrics:
        sections.append(METRICS_INSTRUCTIONS)
    if flags.availability:
        sections.append(AVAILABILITY_INSTRUCTIONS)
    if flags.multi_evidence:
        sections.append(MULTI_EVIDENCE_INSTRUCTIONS)
    return "\n\n".join(sections)


def is_syslog_event(event: RelatedEventContext) -> bool:
    if (event.event_source or "").upper() == "SYSLOG":
        return True
    if event.syslog_source_name or event.parsing_profile or event.raw_log:
        return True
    return False


def is_availability_incident(request: IncidentAnalysisRequest) -> bool:
    event_type = (request.event_type or "").upper()
    if any(marker in event_type for marker in AVAILABILITY_EVENT_MARKERS):
        return True
    incident = request.incident
    if incident is None:
        return False
    if (incident.category or "").upper() in AVAILABILITY_CATEGORIES:
        return True
    component = incident.component
    if component is None:
        return False
    last_status = (component.last_status or "").upper()
    methods = {method.upper() for method in component.monitoring_methods}
    return last_status in {"DOWN", "UNREACHABLE", "FAILED", "CRITICAL"} and bool(methods & AVAILABILITY_METHODS)


def format_incident_header(request: IncidentAnalysisRequest, compact: bool) -> str:
    return "\n".join(
        [
            "Incident:",
            f"- Device name: {request.device_name}",
            f"- Device type: {request.device_type}",
            f"- Location: {request.location or 'unknown'}",
            f"- Event type: {request.event_type}",
            f"- Severity: {request.severity}",
            f"- Message: {request.message}",
            f"- Details: {truncate(request.details or 'none', 240 if compact else 400)}",
            f"- Occurred at: {request.occurred_at or 'unknown'}",
        ]
    )


def format_incident_context(
    request: IncidentAnalysisRequest,
    compact: bool = False,
    drop_optional: bool = False,
    limits: ContextLimits | None = None,
) -> tuple[str, dict]:
    compressed = compress_incident_context(request, limits=limits, drop_optional=drop_optional)
    flags = detect_evidence(request, drop_optional=drop_optional, compressed=compressed, limits=limits)
    stats = compression_stats(compressed, flags)
    if request.incident is None:
        return "", stats

    incident = request.incident
    component = incident.component
    component_context = "Component context: unavailable"
    if component is not None:
        component_context = "\n".join(
            [
                "Component context:",
                f"- Target component: {component.name or 'unknown'}",
                f"- Component type: {component.type or 'unknown'}",
                f"- Component criticality: {component.criticality or 'unknown'}",
                f"- Current status: {component.last_status or 'unknown'}",
                f"- Primary IP: {component.ip_address or 'unknown'}",
                f"- Matched network IP: {component.matched_network_ip or component.ip_address or 'unknown'}",
                f"- Matched interface: {component.matched_interface_name or 'none'}",
                f"- URL: {component.http_url or 'unknown'}",
                f"- TCP port: {component.tcp_port or 'unknown'}",
                f"- Monitoring methods: {', '.join(component.monitoring_methods) or 'unknown'}",
                f"- Last error: {truncate(component.last_error or 'none', 160)}",
                f"- Last check details: {truncate(component.last_check_details or 'none', 160)}",
            ]
        )

    related_event_lines = [format_event_group_line(group) for group in compressed.event_groups]
    if compressed.omitted_covered_events:
        related_event_lines.append(
            f"- omitted {compressed.omitted_covered_events} repeated NetFlow/port-scan events "
            "because aggregated NetFlow evidence is included"
        )
    elif compressed.related_events_raw > compressed.event_groups_sent and compressed.event_groups_sent:
        related_event_lines.insert(
            0,
            f"- grouped {compressed.related_events_raw} related events into "
            f"{compressed.event_groups_sent} representations",
        )

    metrics = ""
    if flags.metrics:
        metrics = "\n".join(
            [
                f"- metric={series.metric_name} current={format_metric_value(series.current, series.unit)} "
                f"min={format_metric_value(series.min_value, series.unit)} "
                f"max={format_metric_value(series.max_value, series.unit)} "
                f"average={format_metric_value(series.average, series.unit)} "
                f"samples={series.sample_count} source={series.source} "
                f"last={series.last_sampled_at or 'unknown'}"
                for series in compressed.metric_series
            ]
        )
    previous_incidents = "\n".join(
        [
            f"- {previous.last_seen_at or 'unknown'} | {previous.severity} | "
            f"{previous.title} | status={previous.status} | events={previous.event_count or 'unknown'}"
            for previous in compressed.previous_incidents
        ]
    )
    previous_reports = "\n".join(
        [
            f"- {report.generated_at or 'unknown'} | {report.severity} | "
            f"{report.event_type} | provider={report.provider} | summary={truncate(report.summary, 160)}"
            for report in compressed.previous_reports
        ]
    )
    packet_capture_summaries = ""
    if flags.pcap:
        packet_capture_summaries = "\n".join(format_pcap_lines(capture) for capture in compressed.pcap_summaries)
    network_flow_summaries = ""
    if flags.netflow:
        network_flow_summaries = "\n".join(
            [
                f"- anomaly={flow.anomaly_type or 'none'} | source={flow.source_ip or 'unknown'} | "
                f"destination={flow.destination_ip or 'unknown'} | "
                f"ports={', '.join(str(port) for port in flow.destination_ports) or 'none'} | "
                f"protocol={', '.join(flow.protocols) or 'none'} | "
                f"flowCount={flow.flow_count} | packets={flow.total_packets} | bytes={flow.total_bytes} | "
                f"reason={truncate(flow.anomaly_reason or 'none', 180)}"
                for flow in compressed.netflow_groups
            ]
        )
        if not compressed.netflow_groups:
            network_flow_summaries = "\n".join(
                format_event_group_line(group) for group in compressed.event_groups if group.is_netflow
            )
    firewall_activity_summary = summarize_firewall_activity(incident.related_events)

    optional_sections = []
    if metrics:
        optional_sections.append(f"Recent metrics:\n{metrics}")
    if packet_capture_summaries:
        optional_sections.append(f"Packet capture summaries:\n{packet_capture_summaries}")
    if network_flow_summaries:
        optional_sections.append(f"NetFlow evidence:\n{network_flow_summaries}")
    if previous_incidents:
        optional_sections.append(f"Previous similar incidents:\n{previous_incidents}")
    if previous_reports:
        optional_sections.append(f"Previous diagnostic reports:\n{previous_reports}")

    context = f"""

Correlated incident context:
- Incident title: {incident.title}
- Category: {incident.category}
- Incident status: {incident.status}
- First seen: {incident.first_seen_at or "unknown"}
- Last seen: {incident.last_seen_at or "unknown"}
- Duration minutes: {incident.duration_minutes if incident.duration_minutes is not None else "unknown"}
- Related event count: {incident.event_count if incident.event_count is not None else compressed.related_events_raw}
- Event groups sent to AI: {compressed.event_groups_sent}

{component_context}

Firewall activity summary:
{firewall_activity_summary}

Related event groups:
{chr(10).join(related_event_lines) if related_event_lines else "- none"}
"""
    if optional_sections:
        context += "\n" + "\n\n".join(optional_sections)
    return context.rstrip(), stats


def compression_stats(compressed: CompressedContext, flags: EvidenceFlags) -> dict:
    return {
        "related_events_included": compressed.event_groups_sent,
        "related_events_total": compressed.related_events_raw,
        "related_events_raw": compressed.related_events_raw,
        "event_groups_sent": compressed.event_groups_sent,
        "pcap_summaries_raw": compressed.pcap_summaries_raw,
        "pcap_summaries_sent": compressed.pcap_summaries_sent,
        "netflow_groups_raw": compressed.netflow_groups_raw,
        "netflow_groups_sent": compressed.netflow_groups_sent,
        "metrics_raw": compressed.metrics_raw,
        "metric_series_sent": compressed.metric_series_sent,
        "netflow_evidence_included": flags.netflow,
        "context_compressed": compressed.context_compressed,
        "evidence": flags,
    }


def format_event_group_line(group: EventGroup) -> str:
    ports = ",".join(str(port) for port in group.ports) or "none"
    parts = [
        f"eventType={group.event_type}",
        f"count={group.count}",
        f"severity={group.severity}",
        f"src={group.source or 'unknown'}",
        f"dst={group.destination or 'unknown'}",
        f"ports={ports}",
        f"firstSeen={group.first_seen or 'unknown'}",
        f"lastSeen={group.last_seen or 'unknown'}",
        f"message={group.representative_message}",
    ]
    if group.event_source:
        parts.append(f"source={group.event_source}")
    if group.raw_log:
        parts.append(f"rawLog={group.raw_log}")
    return "- " + " | ".join(parts)


def format_pcap_lines(capture) -> str:
    lines = [
        f"- file={capture.file_name} | packets={capture.total_packets} | bytes={capture.total_bytes}"
        + (f" | protocols={', '.join(capture.top_protocols)}" if capture.top_protocols else "")
    ]
    if capture.scan_summary:
        lines.append(f"  scan confirmed: {capture.scan_summary}")
    if capture.ports:
        lines.append(f"  ports={', '.join(capture.ports)}")
    extra_findings = [finding for finding in capture.findings if "port scan" not in finding.lower() and "reconnaissance" not in finding.lower()]
    if extra_findings:
        lines.append(f"  additional findings={'; '.join(extra_findings)}")
    elif capture.findings and not capture.scan_summary:
        lines.append(f"  findings={'; '.join(capture.findings)}")
    return "\n".join(lines)


def format_metric_value(value: float, unit: str) -> str:
    if abs(value - round(value)) < 0.05:
        rendered = str(int(round(value)))
    else:
        rendered = f"{value:.1f}"
    return f"{rendered}{unit}"


def truncate(value: str, limit: int) -> str:
    compact = " ".join(value.split())
    if len(compact) <= limit:
        return compact
    return compact[: limit - 3] + "..."


def summarize_firewall_activity(related_events: list) -> str:
    firewall_events = [
        event
        for event in related_events
        if event.source_address and event.destination_address and event.destination_port is not None
    ]
    if not firewall_events:
        return "- none"

    grouped: dict[tuple[str, str], set[int]] = {}
    for event in firewall_events:
        key = (event.source_address, event.destination_address)
        grouped.setdefault(key, set()).add(event.destination_port)

    source_address, destination_address = max(grouped, key=lambda key: len(grouped[key]))
    destination_ports = sorted(grouped[(source_address, destination_address)])
    displayed_ports = destination_ports[:15]
    ports_text = ", ".join(str(port) for port in displayed_ports)
    if len(destination_ports) > len(displayed_ports):
        ports_text += f" (+{len(destination_ports) - len(displayed_ports)} more)"

    summary_lines = [
        f"- Source IP: {source_address}",
        f"- Destination IP: {destination_address}",
        f"- Distinct destination ports observed: {ports_text}",
    ]
    if len(destination_ports) >= 3:
        summary_lines.append(
            "- This pattern may indicate reconnaissance or port scanning against the destination host."
        )
    else:
        summary_lines.append(
            "- Destination-port spread is limited; review repeated denies and business criticality."
        )
    return "\n".join(summary_lines)
