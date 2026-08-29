from __future__ import annotations

import hashlib
import json
from dataclasses import asdict
from typing import Any

import app.config as config
from app.schemas import IncidentAnalysisRequest
from app.services.context_compressor import EventGroup, compress_incident_context


def context_fingerprint(request: IncidentAnalysisRequest) -> str:
    return sha256_hex(canonical_json(fingerprint_document(request)))


def cache_key(fingerprint: str, requested_provider: str, model: str | None) -> str:
    identity = {
        "pipelineVersion": config.AI_ANALYSIS_VERSION,
        "requestedProvider": requested_provider,
        "model": model or "",
        "contextFingerprint": fingerprint,
    }
    return sha256_hex(canonical_json(identity))


def short_fingerprint(fingerprint: str | None, length: int = 12) -> str:
    if not fingerprint:
        return "<none>"
    return fingerprint[:length]


def fingerprint_document(request: IncidentAnalysisRequest) -> dict[str, Any]:
    compressed = compress_incident_context(request)
    incident = request.incident
    return {
        "primaryEvent": {
            "eventType": request.event_type,
            "severity": request.severity,
            "message": request.message,
            "details": request.details,
            "deviceType": request.device_type,
            "deviceName": request.device_name,
            "location": request.location,
        },
        "incident": incident_identity(incident),
        "component": component_state(incident),
        "eventGroups": [event_group_state(group) for group in sorted_event_groups(compressed.event_groups)],
        "netflow": [netflow_state(flow) for flow in compressed.netflow_groups],
        "pcap": [pcap_state(capture) for capture in compressed.pcap_summaries],
        "metrics": [metric_state(series) for series in compressed.metric_series],
        "similarIncidents": [similar_incident_state(item) for item in compressed.previous_incidents],
    }


def incident_identity(incident: Any) -> dict[str, Any] | None:
    if incident is None:
        return None
    return {
        "id": incident.id,
        "correlationKey": incident.correlation_key,
        "title": incident.title,
        "category": incident.category,
        "status": incident.status,
        "acknowledged": incident.acknowledged,
        "firstSeenAt": incident.first_seen_at,
    }


def component_state(incident: Any) -> dict[str, Any] | None:
    if incident is None or incident.component is None:
        return None
    component = incident.component
    return {
        "id": component.id,
        "name": component.name,
        "type": component.type,
        "ipAddress": component.ip_address,
        "matchedNetworkIp": component.matched_network_ip,
        "httpUrl": component.http_url,
        "tcpPort": component.tcp_port,
        "location": component.location,
        "criticality": component.criticality,
        "monitoringMethods": sorted(component.monitoring_methods or []),
        "lastStatus": component.last_status,
        "lastError": component.last_error,
    }


def sorted_event_groups(groups: list[EventGroup]) -> list[EventGroup]:
    return sorted(
        groups,
        key=lambda group: (
            group.event_type or "",
            group.event_source or "",
            group.source or "",
            group.destination or "",
            group.protocol or "",
        ),
    )


def event_group_state(group: EventGroup) -> dict[str, Any]:
    return {
        "eventType": group.event_type,
        "severity": group.severity,
        "eventSource": group.event_source,
        "source": group.source,
        "destination": group.destination,
        "ports": list(group.ports),
        "protocol": group.protocol,
        "count": group.count,
        "message": group.representative_message,
        "resultingStatus": group.resulting_status,
        "parsingProfile": group.parsing_profile,
        "syslogSourceName": group.syslog_source_name,
    }


def netflow_state(flow: Any) -> dict[str, Any]:
    return {
        "sourceIp": flow.source_ip,
        "destinationIp": flow.destination_ip,
        "destinationPorts": sorted(flow.destination_ports or []),
        "protocols": sorted(flow.protocols or []),
        "flowCount": flow.flow_count,
        "totalPackets": flow.total_packets,
        "totalBytes": flow.total_bytes,
        "anomalyType": flow.anomaly_type,
        "anomalyReason": flow.anomaly_reason,
    }


def pcap_state(capture: Any) -> dict[str, Any]:
    payload = asdict(capture)
    payload.pop("created_at", None)
    payload["ports"] = list(payload.get("ports") or [])
    payload["findings"] = list(payload.get("findings") or [])
    payload["top_protocols"] = list(payload.get("top_protocols") or [])
    return payload


def metric_state(series: Any) -> dict[str, Any]:
    return {
        "metricName": series.metric_name,
        "unit": series.unit,
        "source": series.source,
        "current": series.current,
        "min": series.min_value,
        "max": series.max_value,
        "average": series.average,
        "sampleCount": series.sample_count,
    }


def similar_incident_state(item: Any) -> dict[str, Any]:
    return {
        "id": item.id,
        "status": item.status,
        "severity": item.severity,
        "category": item.category,
        "eventCount": item.event_count,
        "title": item.title,
    }


def canonical_json(value: Any) -> str:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True, default=str)


def sha256_hex(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()
