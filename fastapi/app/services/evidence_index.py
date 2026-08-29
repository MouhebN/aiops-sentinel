from __future__ import annotations

import re
from dataclasses import dataclass, field
from ipaddress import ip_address

from app.schemas import IncidentAnalysisRequest
from app.services.context_compressor import compress_incident_context

IPV4_RE = re.compile(
    r"\b(?:(?:25[0-5]|2[0-4]\d|[01]?\d\d?)\.){3}(?:25[0-5]|2[0-4]\d|[01]?\d\d?)\b"
)
VENDOR_MARKERS = (
    "CISCO",
    "ASA",
    "IOS",
    "JUNIPER",
    "JUNOS",
    "FORTI",
    "PALO",
    "PAN-OS",
    "CHECKPOINT",
    "HUAWEI",
    "MIKROTIK",
    "SONICWALL",
)
AUTH_SUCCESS_MARKERS = ("AUTH_SUCCESS", "LOGIN_SUCCESS", "AUTHENTICATION_SUCCESS", "SUCCESSFUL_LOGIN")
EXFIL_MARKERS = ("EXFIL", "DATA_EXFIL", "DLP_ALERT", "DATA_THEFT")
COMPROMISE_MARKERS = ("MALWARE", "RANSOMWARE", "COMPROMISE", "C2_", "EDR_ALERT", "IOC_HIT", "TROJAN")
ACCESS_MARKERS = AUTH_SUCCESS_MARKERS + ("SESSION_ESTABLISHED", "VPN_CONNECTED")


@dataclass
class EvidenceIndex:
    ips: set[str] = field(default_factory=set)
    device_names: set[str] = field(default_factory=set)
    ports: set[str] = field(default_factory=set)
    protocols: set[str] = field(default_factory=set)
    event_types: set[str] = field(default_factory=set)
    anomaly_types: set[str] = field(default_factory=set)
    component_types: set[str] = field(default_factory=set)
    states: set[str] = field(default_factory=set)
    evidence_sources: set[str] = field(default_factory=set)
    vendor_known: bool = False
    has_auth_success: bool = False
    has_exfiltration: bool = False
    has_compromise: bool = False
    has_successful_access: bool = False


def build_evidence_index(request: IncidentAnalysisRequest) -> EvidenceIndex:
    index = EvidenceIndex()
    _add_text_ips(index, request.message)
    _add_text_ips(index, request.details or "")
    _add_name(index, request.device_name)
    _add_name(index, request.device_type)
    if request.event_type:
        index.event_types.add(request.event_type.upper())
    _add_vendor_text(index, f"{request.device_type} {request.device_name}")

    incident = request.incident
    if incident is not None:
        _add_name(index, incident.title)
        if incident.component is not None:
            component = incident.component
            _add_name(index, component.name)
            _add_name(index, component.type)
            if component.type:
                index.component_types.add(component.type.upper())
            if component.ip_address:
                _add_ip(index, component.ip_address)
            if component.matched_network_ip:
                _add_ip(index, component.matched_network_ip)
            if component.tcp_port is not None:
                index.ports.add(str(component.tcp_port))
            if component.last_status:
                index.states.add(component.last_status.upper())
            _add_vendor_text(index, f"{component.type} {component.name}")
        for event in incident.related_events:
            if event.event_type:
                index.event_types.add(event.event_type.upper())
            if event.protocol:
                index.protocols.add(event.protocol.upper())
            if event.destination_port is not None:
                index.ports.add(str(event.destination_port))
            if event.source_port is not None:
                index.ports.add(str(event.source_port))
            if event.resulting_status:
                index.states.add(event.resulting_status.upper())
            _add_ip(index, event.source_address)
            _add_ip(index, event.source_ip)
            _add_ip(index, event.destination_address)
            if (event.event_source or "").upper() == "SYSLOG":
                index.evidence_sources.add("SYSLOG")
            if (event.event_source or "").upper() == "NETFLOW":
                index.evidence_sources.add("NETFLOW")
        for flow in incident.network_flow_summaries:
            index.evidence_sources.add("NETFLOW")
            _add_ip(index, flow.source_ip)
            _add_ip(index, flow.destination_ip)
            if flow.anomaly_type:
                index.anomaly_types.add(flow.anomaly_type.upper())
            index.ports.update(str(port) for port in flow.destination_ports)
            index.protocols.update(item.upper() for item in flow.protocols)
        for capture in incident.packet_capture_summaries:
            index.evidence_sources.add("PCAP")
            for labeled in capture.top_source_ips + capture.top_destination_ips:
                _add_ip(index, labeled.split()[0] if labeled else None)
            _add_text_ips(index, " ".join(capture.suspicious_findings))
            _add_text_ips(index, capture.summary)
        if incident.recent_metrics:
            index.evidence_sources.add("METRICS")

    compressed = compress_incident_context(request)
    for group in compressed.event_groups:
        if group.is_syslog:
            index.evidence_sources.add("SYSLOG")
        if group.is_netflow:
            index.evidence_sources.add("NETFLOW")
        if group.event_type:
            index.event_types.add(group.event_type.upper())
        _add_ip(index, group.source)
        _add_ip(index, group.destination)
        index.ports.update(str(port) for port in group.ports)
        if group.protocol:
            index.protocols.add(group.protocol.upper())
    if compressed.netflow_groups:
        index.evidence_sources.add("NETFLOW")
    if compressed.pcap_summaries:
        index.evidence_sources.add("PCAP")
    if compressed.metric_series:
        index.evidence_sources.add("METRICS")

    event_blob = " ".join(index.event_types | index.anomaly_types)
    index.has_auth_success = any(marker in event_blob for marker in AUTH_SUCCESS_MARKERS)
    index.has_successful_access = index.has_auth_success or any(marker in event_blob for marker in ACCESS_MARKERS)
    index.has_exfiltration = any(marker in event_blob for marker in EXFIL_MARKERS)
    index.has_compromise = any(marker in event_blob for marker in COMPROMISE_MARKERS)
    return index


def extract_ips(text: str) -> set[str]:
    found: set[str] = set()
    for match in IPV4_RE.findall(text or ""):
        _try_add_ip(found, match)
    return found


def _add_text_ips(index: EvidenceIndex, text: str) -> None:
    for ip in extract_ips(text):
        index.ips.add(ip)


def _add_ip(index: EvidenceIndex, value: str | None) -> None:
    if not value:
        return
    _try_add_ip(index.ips, value.strip())


def _try_add_ip(target: set[str], value: str) -> None:
    try:
        parsed = ip_address(value)
    except ValueError:
        return
    target.add(str(parsed))


def _add_name(index: EvidenceIndex, value: str | None) -> None:
    if value and value.strip():
        index.device_names.add(value.strip())


def _add_vendor_text(index: EvidenceIndex, text: str) -> None:
    blob = (text or "").upper()
    if any(marker in blob for marker in VENDOR_MARKERS):
        index.vendor_known = True
