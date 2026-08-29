from __future__ import annotations

import unittest

from app.config import ContextLimits
from app.schemas import (
    IncidentAnalysisRequest,
    IncidentContext,
    MetricContext,
    NetworkFlowSummaryContext,
    PacketCaptureSummaryContext,
    RelatedEventContext,
)
from app.services.context_compressor import compress_incident_context, truncate_marked
from app.services.prompt_builder import build_prompt, detect_evidence
from app.services.prompt_templates import METRICS_HEADING, NETFLOW_HEADING, PCAP_HEADING, SYSLOG_HEADING


SCAN_PORTS = [21, 22, 23, 80, 443, 3306, 5432]


def analysis_request(incident_context: IncidentContext | None, **overrides) -> IncidentAnalysisRequest:
    payload = {
        "eventType": "PORT_SCAN",
        "severity": "WARNING",
        "message": "Possible port scan from 192.168.0.15 to 192.168.0.1",
        "deviceType": "FIREWALL",
        "deviceName": "netflow-collector",
        "location": "lab",
        "occurredAt": "2026-08-19T12:00:00Z",
        "incident": incident_context,
    }
    payload.update(overrides)
    return IncidentAnalysisRequest.model_validate(payload)


def incident(**overrides) -> IncidentContext:
    payload = {
        "title": "Possible port scan from 192.168.0.15 to 192.168.0.1",
        "category": "SECURITY",
        "status": "ACTIVE",
        "relatedEvents": [],
        "recentMetrics": [],
        "packetCaptureSummaries": [],
        "networkFlowSummaries": [],
    }
    payload.update(overrides)
    return IncidentContext.model_validate(payload)


def netflow_event(index: int, destination_port: int = 22, destination: str = "192.168.0.1") -> RelatedEventContext:
    minute = f"{index:02d}"
    return RelatedEventContext(
        eventType="NETFLOW_PORT_SCAN",
        severity="WARNING",
        message="NetFlow detected repeated TCP connections across multiple destination ports.",
        eventSource="NETFLOW",
        sourceAddress="192.168.0.15",
        destinationAddress=destination,
        destinationPort=destination_port,
        protocol="TCP",
        occurredAt=f"2026-08-19T12:{minute}:00Z",
    )


def syslog_deny() -> RelatedEventContext:
    return RelatedEventContext(
        eventType="FIREWALL_DENY",
        severity="WARNING",
        message="DENY TCP 192.168.0.15:45122 -> 192.168.0.1:22",
        rawLog="<134>Aug 19 12:00:00 BANK-FW-01 firewall: DENY SRC=192.168.0.15 DST=192.168.0.1",
        eventSource="SYSLOG",
        parsingProfile="FIREWALL",
        sourceAddress="192.168.0.15",
        destinationAddress="192.168.0.1",
        destinationPort=22,
        occurredAt="2026-08-19T12:00:00Z",
    )


def auth_failure() -> RelatedEventContext:
    return RelatedEventContext(
        eventType="AUTH_FAILURE",
        severity="CRITICAL",
        message="ssh authentication failed for user admin",
        eventSource="SYSLOG",
        sourceAddress="192.168.0.15",
        destinationAddress="192.168.0.10",
        occurredAt="2026-08-19T12:05:00Z",
    )


def ping_event(index: int) -> RelatedEventContext:
    return RelatedEventContext(
        eventType="PING_OK",
        severity="INFO",
        message="icmp echo reply",
        eventSource="MONITORING",
        sourceAddress="192.168.0.50",
        destinationAddress=f"192.168.0.{60 + index}",
        occurredAt=f"2026-08-19T11:{index:02d}:00Z",
    )


def netflow_summary() -> NetworkFlowSummaryContext:
    return NetworkFlowSummaryContext(
        sourceIp="192.168.0.15",
        destinationIp="192.168.0.1",
        destinationPorts=SCAN_PORTS,
        protocols=["TCP"],
        flowCount=35,
        totalPackets=525,
        totalBytes=60375,
        anomalyType="PORT_SCAN",
        anomalyReason="Possible port scan: 192.168.0.15 probed 192.168.0.1 on 7 destination ports",
    )


def pcap_summary() -> PacketCaptureSummaryContext:
    return PacketCaptureSummaryContext(
        id=1,
        fileName="incident-portscan.pcap",
        totalPackets=367,
        totalBytes=133756,
        topSourceIps=["192.168.0.15 (200)", "192.168.0.1 (167)"] + [f"10.0.0.{i} (1)" for i in range(12)],
        topDestinationIps=["192.168.0.1 (200)", "192.168.0.15 (167)"] + [f"10.0.0.{i} (1)" for i in range(12)],
        topProtocols=["TCP (360)", "UDP (7)"],
        topDestinationPorts=["21 (50)", "22 (50)", "23 (50)", "80 (50)", "443 (50)", "3306 (40)", "5432 (40)"],
        suspiciousFindings=[
            "Possible port scan or reconnaissance from 192.168.0.15 to 192.168.0.1 "
            "on ports 21, 22, 23, 80, 443, 3306, 5432",
            "TCP retransmissions observed",
        ],
        summary="Large packet dump with scan confirmation and background traffic.",
    )


class ContextCompressionTests(unittest.TestCase):
    def test_identical_netflow_events_are_grouped(self) -> None:
        events = [netflow_event(index, destination_port=SCAN_PORTS[index % len(SCAN_PORTS)]) for index in range(35)]
        compressed = compress_incident_context(analysis_request(incident(relatedEvents=events)))
        self.assertEqual(compressed.related_events_raw, 35)
        self.assertEqual(compressed.event_groups_sent, 1)
        group = compressed.event_groups[0]
        self.assertEqual(group.count, 35)
        self.assertEqual(group.source, "192.168.0.15")
        self.assertEqual(group.destination, "192.168.0.1")
        self.assertEqual(list(group.ports), SCAN_PORTS)

    def test_different_event_types_remain_separate(self) -> None:
        events = [netflow_event(0), syslog_deny()]
        compressed = compress_incident_context(analysis_request(incident(relatedEvents=events)))
        types = {group.event_type for group in compressed.event_groups}
        self.assertEqual(types, {"NETFLOW_PORT_SCAN", "FIREWALL_DENY"})

    def test_different_source_destination_pairs_are_not_merged(self) -> None:
        events = [netflow_event(0, destination="192.168.0.1"), netflow_event(1, destination="192.168.0.8")]
        compressed = compress_incident_context(analysis_request(incident(relatedEvents=events)))
        destinations = {group.destination for group in compressed.event_groups}
        self.assertEqual(destinations, {"192.168.0.1", "192.168.0.8"})

    def test_unique_security_events_survive_limits(self) -> None:
        events = [ping_event(index) for index in range(12)] + [auth_failure()]
        limits = ContextLimits(max_event_groups=5)
        compressed = compress_incident_context(analysis_request(incident(relatedEvents=events)), limits=limits)
        self.assertLessEqual(compressed.event_groups_sent, 5)
        types = {group.event_type for group in compressed.event_groups}
        self.assertIn("AUTH_FAILURE", types)

    def test_pcap_evidence_remains_after_compression(self) -> None:
        compressed = compress_incident_context(
            analysis_request(incident(packetCaptureSummaries=[pcap_summary()]))
        )
        self.assertEqual(compressed.pcap_summaries_raw, 1)
        self.assertEqual(compressed.pcap_summaries_sent, 1)
        capture = compressed.pcap_summaries[0]
        self.assertEqual(capture.scan_summary, "192.168.0.15 -> 192.168.0.1")
        self.assertEqual(list(capture.ports)[:7], ["21", "22", "23", "80", "443", "3306", "5432"])
        self.assertIn("TCP retransmissions observed", capture.findings)
        self.assertLessEqual(len(capture.findings), 4)

    def test_netflow_evidence_remains_after_compression(self) -> None:
        compressed = compress_incident_context(
            analysis_request(incident(networkFlowSummaries=[netflow_summary()]))
        )
        self.assertEqual(compressed.netflow_groups_sent, 1)
        self.assertEqual(compressed.netflow_groups[0].anomaly_type, "PORT_SCAN")
        self.assertEqual(compressed.netflow_groups[0].flow_count, 35)

    def test_syslog_evidence_remains_after_compression(self) -> None:
        compressed = compress_incident_context(analysis_request(incident(relatedEvents=[syslog_deny()])))
        self.assertTrue(compressed.event_groups[0].is_syslog)
        flags = detect_evidence(analysis_request(incident(relatedEvents=[syslog_deny()])))
        self.assertTrue(flags.syslog)

    def test_metrics_are_bounded_and_aggregated(self) -> None:
        samples = [
            MetricContext(metricName="cpu.usage", metricValue=float(60 + index), unit="%", source="snmp", sampledAt=f"2026-08-19T12:{index:02d}:00Z")
            for index in range(20)
        ]
        extra = [
            MetricContext(metricName="mem.usage", metricValue=float(40 + index), unit="%", source="snmp", sampledAt=f"2026-08-19T12:{index:02d}:00Z")
            for index in range(8)
        ]
        limits = ContextLimits(max_metric_series=1)
        compressed = compress_incident_context(
            analysis_request(incident(recentMetrics=samples + extra)),
            limits=limits,
        )
        self.assertEqual(compressed.metrics_raw, 28)
        self.assertEqual(compressed.metric_series_sent, 1)
        series = compressed.metric_series[0]
        self.assertEqual(series.metric_name, "cpu.usage")
        self.assertEqual(series.sample_count, 20)
        self.assertEqual(series.min_value, 60)
        self.assertEqual(series.max_value, 79)

    def test_long_raw_logs_are_truncated_with_marker(self) -> None:
        raw = "A" * 80
        event = syslog_deny().model_copy(update={"raw_log": raw})
        limits = ContextLimits(max_message_chars=40)
        compressed = compress_incident_context(
            analysis_request(incident(relatedEvents=[event])),
            limits=limits,
        )
        self.assertTrue(compressed.event_groups[0].raw_log_truncated)
        self.assertTrue(compressed.event_groups[0].raw_log.endswith(" [truncated]"))
        self.assertLessEqual(len(compressed.event_groups[0].raw_log), 40 + len(" [truncated]"))
        text, truncated = truncate_marked("short", 40)
        self.assertEqual(text, "short")
        self.assertFalse(truncated)

    def test_context_limits_are_configurable(self) -> None:
        events = [ping_event(index) for index in range(8)]
        default_compressed = compress_incident_context(analysis_request(incident(relatedEvents=events)))
        limited = compress_incident_context(
            analysis_request(incident(relatedEvents=events)),
            limits=ContextLimits(max_event_groups=2),
        )
        self.assertEqual(default_compressed.event_groups_sent, 8)
        self.assertEqual(limited.event_groups_sent, 2)

    def test_evidence_aware_sections_survive_lab_compression(self) -> None:
        events = [netflow_event(index, destination_port=SCAN_PORTS[index % len(SCAN_PORTS)]) for index in range(35)]
        request = analysis_request(
            incident(
                relatedEvents=events,
                networkFlowSummaries=[netflow_summary()],
                packetCaptureSummaries=[pcap_summary()],
            )
        )
        result = build_prompt(request)
        prompt = result["prompt"]
        self.assertEqual(result["related_events_raw"], 35)
        self.assertEqual(result["event_groups_sent"], 0)
        self.assertEqual(result["netflow_groups_sent"], 1)
        self.assertEqual(result["pcap_summaries_sent"], 1)
        self.assertTrue(result["context_compressed"])
        self.assertIn(NETFLOW_HEADING, prompt)
        self.assertIn(PCAP_HEADING, prompt)
        self.assertNotIn(SYSLOG_HEADING, prompt)
        self.assertEqual(prompt.count("eventType=NETFLOW_PORT_SCAN"), 0)
        self.assertIn("flowCount=35", prompt)
        self.assertIn("packets=525", prompt)
        self.assertIn("bytes=60375", prompt)
        self.assertIn("21, 22, 23, 80, 443, 3306, 5432", prompt)
        self.assertIn("scan confirmed: 192.168.0.15 -> 192.168.0.1", prompt)
        self.assertIn("21, 22, 23, 80, 443, 3306, 5432", prompt)
        self.assertIn("TCP retransmissions", prompt)
        self.assertIn("omitted 35 repeated NetFlow/port-scan events", prompt)
        self.assertNotIn(METRICS_HEADING, prompt)
        self.assertLess(result["prompt_characters"], 9000)

    def test_grouped_netflow_without_summary_still_activates_netflow_prompt(self) -> None:
        events = [netflow_event(index) for index in range(35)]
        result = build_prompt(analysis_request(incident(relatedEvents=events)))
        self.assertEqual(result["event_groups_sent"], 1)
        self.assertIn(NETFLOW_HEADING, prompt_or(result))
        self.assertIn("count=35", prompt_or(result))
        self.assertTrue(result["context_compressed"])


def prompt_or(result: dict) -> str:
    return result["prompt"]


if __name__ == "__main__":
    unittest.main()
