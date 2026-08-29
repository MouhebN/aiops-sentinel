from __future__ import annotations

import unittest

from app.schemas import (
    ComponentContext,
    IncidentAnalysisRequest,
    IncidentContext,
    MetricContext,
    NetworkFlowSummaryContext,
    PacketCaptureSummaryContext,
    RelatedEventContext,
)
from app.services.prompt_builder import build_prompt, detect_evidence, render_prompt
from app.services.prompt_templates import (
    AVAILABILITY_HEADING,
    COMMON_DIAGNOSTIC_TOOLS,
    CORROBORATION_RULE,
    METRICS_HEADING,
    MULTI_EVIDENCE_HEADING,
    NETFLOW_HEADING,
    NO_COMPROMISE_RULE,
    NO_GENERIC_NETFLOW_ONLY_RULE,
    OBSERVED_VS_INFERENCE_RULE,
    PCAP_HEADING,
    SOURCE_ATTRIBUTION_RULE,
    SOURCE_CONTRIBUTION_RULE,
    SYSLOG_HEADING,
    VENDOR_CLI_RULE,
)


def base_request(**overrides) -> dict:
    payload = {
        "eventType": "FIREWALL_DENY",
        "severity": "WARNING",
        "message": "DENY TCP 192.168.0.15:45122 -> 192.168.0.1:22",
        "details": "SRC=192.168.0.15 DST=192.168.0.1 SPT=45122 DPT=22 PROTO=TCP",
        "deviceType": "FIREWALL",
        "deviceName": "BANK-FW-01",
        "location": "lab",
        "occurredAt": "2026-08-19T12:00:00Z",
    }
    payload.update(overrides)
    return payload


def syslog_event() -> RelatedEventContext:
    return RelatedEventContext(
        eventType="FIREWALL_DENY",
        severity="WARNING",
        message="DENY TCP 192.168.0.15:45122 -> 192.168.0.1:22",
        details="SRC=192.168.0.15 DST=192.168.0.1",
        rawLog="<134>Aug 19 12:00:00 BANK-FW-01 firewall: DENY SRC=192.168.0.15 DST=192.168.0.1",
        eventSource="SYSLOG",
        parsingProfile="FIREWALL",
        sourceAddress="192.168.0.15",
        destinationAddress="192.168.0.1",
        destinationPort=22,
        occurredAt="2026-08-19T12:00:00Z",
    )


def lab_netflow_summary() -> NetworkFlowSummaryContext:
    return NetworkFlowSummaryContext(
        sourceIp="192.168.0.15",
        destinationIp="192.168.0.1",
        destinationPorts=[21, 22, 23, 80, 443, 3306, 5432],
        protocols=["TCP"],
        flowCount=35,
        totalPackets=525,
        totalBytes=32000,
        anomalyType="PORT_SCAN",
        anomalyReason="Possible port scan: 192.168.0.15 probed 192.168.0.1 on 7 destination ports",
    )


def lab_pcap_summary() -> PacketCaptureSummaryContext:
    return PacketCaptureSummaryContext(
        id=1,
        fileName="incident-portscan.pcap",
        totalPackets=28,
        totalBytes=1800,
        topSourceIps=["192.168.0.15 (14)"],
        topDestinationIps=["192.168.0.1 (14)"],
        topProtocols=["TCP (28)"],
        topDestinationPorts=["21 (4)", "22 (4)", "23 (4)", "80 (4)", "443 (4)"],
        suspiciousFindings=[
            "Possible port scan or reconnaissance from 192.168.0.15 to 192.168.0.1 "
            "on ports 21, 22, 23, 80, 443, 3306, 5432",
            "TCP retransmissions observed",
        ],
        summary="Analyzed packets. TCP SYN scan from 192.168.0.15 to 192.168.0.1. Retransmissions also observed.",
    )


def netflow_summary() -> NetworkFlowSummaryContext:
    return lab_netflow_summary()


def pcap_summary() -> PacketCaptureSummaryContext:
    return PacketCaptureSummaryContext(
        id=1,
        fileName="incident-portscan.pcap",
        totalPackets=28,
        totalBytes=1800,
        topSourceIps=["192.168.0.15 (14)"],
        topDestinationIps=["192.168.0.1 (14)"],
        topProtocols=["TCP (28)"],
        topDestinationPorts=["22 (4)", "80 (4)"],
        suspiciousFindings=[
            "Possible port scan or reconnaissance from 192.168.0.15 to 192.168.0.1 "
            "on ports 21, 22, 23, 80, 443, 3306, 5432"
        ],
        summary="Analyzed 28 packets. TCP SYN scan from 192.168.0.15 to 192.168.0.1.",
    )


def metric_sample() -> MetricContext:
    return MetricContext(
        metricName="cpu.usage",
        metricValue=92.5,
        unit="%",
        source="snmp",
        sampledAt="2026-08-19T12:00:00Z",
    )


def incident(**overrides) -> IncidentContext:
    payload = {
        "title": "BANK-FW-01 security alert",
        "category": "SECURITY",
        "status": "ACTIVE",
        "relatedEvents": [],
        "recentMetrics": [],
        "packetCaptureSummaries": [],
        "networkFlowSummaries": [],
    }
    payload.update(overrides)
    return IncidentContext.model_validate(payload)


def prompt_for(incident_context: IncidentContext | None, **request_overrides) -> str:
    request = IncidentAnalysisRequest.model_validate(
        base_request(incident=incident_context, **request_overrides)
    )
    return build_prompt(request)["prompt"]


class EvidenceAwarePromptTests(unittest.TestCase):
    def test_base_grounding_is_always_present(self) -> None:
        prompt = prompt_for(None)
        self.assertIn("AIOps/SOC diagnostic assistant", prompt)
        self.assertIn(NO_COMPROMISE_RULE, prompt)
        self.assertIn(VENDOR_CLI_RULE, prompt)
        self.assertIn(COMMON_DIAGNOSTIC_TOOLS, prompt)
        self.assertIn('"probable_causes"', prompt)
        self.assertNotIn(SYSLOG_HEADING, prompt)
        self.assertNotIn(NETFLOW_HEADING, prompt)
        self.assertNotIn(PCAP_HEADING, prompt)
        self.assertNotIn(METRICS_HEADING, prompt)

    def test_syslog_only_includes_syslog_but_not_pcap_or_netflow(self) -> None:
        prompt = prompt_for(incident(relatedEvents=[syslog_event()]))
        self.assertIn(SYSLOG_HEADING, prompt)
        self.assertIn("FIREWALL_DENY means traffic was blocked", prompt)
        self.assertNotIn(NETFLOW_HEADING, prompt)
        self.assertNotIn(PCAP_HEADING, prompt)
        self.assertNotIn(METRICS_HEADING, prompt)
        self.assertNotIn(MULTI_EVIDENCE_HEADING, prompt)
        self.assertNotIn("NetFlow evidence:", prompt)
        self.assertNotIn("Packet capture summaries:", prompt)

    def test_netflow_only_includes_netflow_instructions(self) -> None:
        prompt = prompt_for(incident(networkFlowSummaries=[netflow_summary()]))
        self.assertIn(NETFLOW_HEADING, prompt)
        self.assertIn("traffic-pattern metadata", prompt)
        self.assertIn("PORT_SCAN", prompt)
        self.assertNotIn(SYSLOG_HEADING, prompt)
        self.assertNotIn(PCAP_HEADING, prompt)
        self.assertNotIn(METRICS_HEADING, prompt)

    def test_pcap_evidence_includes_pcap_instructions(self) -> None:
        prompt = prompt_for(incident(packetCaptureSummaries=[pcap_summary()]))
        self.assertIn(PCAP_HEADING, prompt)
        self.assertIn("packet-level confirmation", prompt)
        self.assertIn("TCP SYN-based findings", prompt)
        self.assertNotIn(SYSLOG_HEADING, prompt)
        self.assertNotIn(NETFLOW_HEADING, prompt)

    def test_metrics_context_includes_metric_instructions(self) -> None:
        prompt = prompt_for(incident(recentMetrics=[metric_sample()]))
        self.assertIn(METRICS_HEADING, prompt)
        self.assertIn("temporary spike", prompt)
        self.assertIn("cpu.usage", prompt)
        self.assertNotIn(SYSLOG_HEADING, prompt)
        self.assertNotIn(PCAP_HEADING, prompt)

    def test_availability_incident_includes_availability_instructions(self) -> None:
        prompt = prompt_for(
            incident(category="AVAILABILITY", title="camera stream problem"),
            eventType="CAMERA_STREAM_LOST",
            message="RTSP stream lost",
        )
        self.assertIn(AVAILABILITY_HEADING, prompt)
        self.assertIn("RTSP", prompt)
        self.assertNotIn(SYSLOG_HEADING, prompt)

    def test_firewall_deny_does_not_add_availability_instructions(self) -> None:
        flags = detect_evidence(
            IncidentAnalysisRequest.model_validate(base_request(incident=incident(relatedEvents=[syslog_event()])))
        )
        self.assertTrue(flags.syslog)
        self.assertFalse(flags.availability)

    def test_down_ping_component_adds_availability_instructions(self) -> None:
        component = ComponentContext.model_validate(
            {
                "name": "CAM-01",
                "type": "CAMERA",
                "monitoringMethods": ["PING", "RTSP"],
                "lastStatus": "DOWN",
                "lastError": "no ICMP reply",
            }
        )
        prompt = prompt_for(incident(component=component, title="CAM-01 unreachable"))
        self.assertIn(AVAILABILITY_HEADING, prompt)
        self.assertIn("PING", prompt)

    def test_mixed_syslog_netflow_pcap_contains_all_sections_and_correlation(self) -> None:
        prompt = prompt_for(
            incident(
                relatedEvents=[syslog_event()],
                networkFlowSummaries=[netflow_summary()],
                packetCaptureSummaries=[pcap_summary()],
            )
        )
        self.assertIn(SYSLOG_HEADING, prompt)
        self.assertIn(NETFLOW_HEADING, prompt)
        self.assertIn(PCAP_HEADING, prompt)
        self.assertIn(MULTI_EVIDENCE_HEADING, prompt)
        self.assertIn(NO_COMPROMISE_RULE, prompt)
        self.assertIn(SOURCE_ATTRIBUTION_RULE, prompt)
        self.assertIn(SOURCE_CONTRIBUTION_RULE, prompt)
        self.assertIn(CORROBORATION_RULE, prompt)
        self.assertIn(NO_GENERIC_NETFLOW_ONLY_RULE, prompt)
        self.assertIn(OBSERVED_VS_INFERENCE_RULE, prompt)
        self.assertIn("Firewall Syslog shows the device-side response", prompt)
        self.assertNotIn(METRICS_HEADING, prompt)
        self.assertNotIn(AVAILABILITY_HEADING, prompt)
        self.assertIn("192.168.0.15", prompt)
        self.assertIn("192.168.0.1", prompt)
        self.assertIn("21, 22, 23, 80, 443, 3306, 5432", prompt)
        self.assertIn("PORT_SCAN", prompt)
        self.assertIn("FIREWALL_DENY", prompt)

    def test_json_schema_contract_is_preserved(self) -> None:
        prompt, _stats = render_prompt(
            IncidentAnalysisRequest.model_validate(base_request()),
            compact=False,
            drop_optional=False,
        )
        for field in (
            '"summary"',
            '"priority"',
            '"impact"',
            '"risk"',
            '"probable_causes"',
            '"suggested_actions"',
            '"diagnostic_commands"',
        ):
            self.assertIn(field, prompt)

    def test_netflow_and_pcap_prompt_requires_source_attribution(self) -> None:
        prompt = prompt_for(
            incident(
                title="Possible port scan from 192.168.0.15 to 192.168.0.1",
                networkFlowSummaries=[lab_netflow_summary()],
                packetCaptureSummaries=[lab_pcap_summary()],
            )
        )
        self.assertIn(MULTI_EVIDENCE_HEADING, prompt)
        self.assertIn(SOURCE_ATTRIBUTION_RULE, prompt)
        self.assertIn("Syslog, NetFlow, PCAP, metrics, or health checks", prompt)
        self.assertIn(NO_GENERIC_NETFLOW_ONLY_RULE, prompt)
        self.assertIn("flowCount=35", prompt)
        self.assertIn("packets=525", prompt)
        self.assertIn("21, 22, 23, 80, 443, 3306, 5432", prompt)
        self.assertNotIn(SYSLOG_HEADING, prompt)
        self.assertNotIn(METRICS_HEADING, prompt)

    def test_syslog_netflow_pcap_prompt_requires_each_source_contribution(self) -> None:
        prompt = prompt_for(
            incident(
                relatedEvents=[syslog_event()],
                networkFlowSummaries=[lab_netflow_summary()],
                packetCaptureSummaries=[lab_pcap_summary()],
            )
        )
        self.assertIn(SOURCE_CONTRIBUTION_RULE, prompt)
        self.assertIn("NetFlow shows the traffic pattern", prompt)
        self.assertIn("PCAP confirms the packet-level behavior", prompt)
        self.assertIn("Firewall Syslog shows the device-side response", prompt)

    def test_multi_source_prompt_requires_corroboration_wording(self) -> None:
        prompt = prompt_for(
            incident(
                networkFlowSummaries=[lab_netflow_summary()],
                packetCaptureSummaries=[lab_pcap_summary()],
            )
        )
        self.assertIn(CORROBORATION_RULE, prompt)
        self.assertIn("If one source does not confirm another, say that clearly", prompt)
        self.assertIn("they corroborate reconnaissance, not successful compromise", prompt)

    def test_unknown_vendor_does_not_encourage_vendor_specific_cli(self) -> None:
        prompt = prompt_for(
            incident(
                title="Possible port scan from 192.168.0.15 to 192.168.0.1",
                networkFlowSummaries=[lab_netflow_summary()],
                packetCaptureSummaries=[lab_pcap_summary()],
            ),
            deviceType="FIREWALL",
            deviceName="netflow-collector",
        )
        self.assertIn(VENDOR_CLI_RULE, prompt)
        self.assertIn(COMMON_DIAGNOSTIC_TOOLS, prompt)
        self.assertIn('Cisco "show firewall statistics"', prompt)
        self.assertIn("Only suggest Cisco or other vendor-specific commands when", prompt)

    def test_grounding_rules_remain_in_multi_source_prompt(self) -> None:
        prompt = prompt_for(
            incident(
                relatedEvents=[syslog_event()],
                networkFlowSummaries=[lab_netflow_summary()],
                packetCaptureSummaries=[lab_pcap_summary()],
            )
        )
        self.assertIn(NO_COMPROMISE_RULE, prompt)
        self.assertIn("lateral movement", prompt)
        self.assertIn(OBSERVED_VS_INFERENCE_RULE, prompt)
        self.assertIn("Not established = successful access, exploitation", prompt)
        self.assertIn('"summary"', prompt)
        self.assertIn('"diagnostic_commands"', prompt)

    def test_component_context_includes_primary_and_matched_network_ip(self) -> None:
        component = ComponentContext.model_validate(
            {
                "name": "BANK-SRV-01",
                "type": "SERVER",
                "ipAddress": "172.30.30.20",
                "matchedNetworkIp": "10.10.10.20",
                "matchedInterfaceName": "Service LAN",
                "lastStatus": "UP",
            }
        )
        prompt = prompt_for(incident(component=component))
        self.assertIn("Target component: BANK-SRV-01", prompt)
        self.assertIn("Primary IP: 172.30.30.20", prompt)
        self.assertIn("Matched network IP: 10.10.10.20", prompt)
        self.assertIn("Matched interface: Service LAN", prompt)


if __name__ == "__main__":
    unittest.main()
