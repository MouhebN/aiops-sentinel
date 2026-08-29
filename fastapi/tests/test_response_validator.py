from __future__ import annotations

import json
import unittest
from unittest.mock import MagicMock, patch

from app.schemas import IncidentAnalysisRequest, IncidentContext
from app.services.evidence_index import build_evidence_index
from app.services.ollama_client import analyze_with_ollama
from app.services.response_validator import (
    OLLAMA_INVALID_PRIORITY,
    OLLAMA_LOW_QUALITY,
    OLLAMA_SCHEMA_VALIDATION_FAILED,
    OLLAMA_UNGROUNDED_IP,
    OLLAMA_UNSUPPORTED_CLAIMS,
    _run_validation,
    validate_model_output,
)


SCAN_PORTS = [21, 22, 23, 80, 443, 3306, 5432]


def port_scan_request(**overrides) -> IncidentAnalysisRequest:
    payload = {
        "eventType": "PORT_SCAN",
        "severity": "WARNING",
        "message": "Possible port scan from 192.168.0.15 to 192.168.0.1",
        "deviceType": "FIREWALL",
        "deviceName": "netflow-collector",
        "location": "lab",
        "occurredAt": "2026-08-20T12:00:00Z",
        "incident": IncidentContext.model_validate(
            {
                "title": "Possible port scan from 192.168.0.15 to 192.168.0.1",
                "category": "SECURITY",
                "status": "ACTIVE",
                "relatedEvents": [],
                "networkFlowSummaries": [
                    {
                        "sourceIp": "192.168.0.15",
                        "destinationIp": "192.168.0.1",
                        "destinationPorts": SCAN_PORTS,
                        "protocols": ["TCP"],
                        "flowCount": 35,
                        "totalPackets": 525,
                        "totalBytes": 60375,
                        "anomalyType": "PORT_SCAN",
                        "anomalyReason": "Possible port scan: 192.168.0.15 probed 192.168.0.1 on 7 destination ports",
                    }
                ],
                "packetCaptureSummaries": [
                    {
                        "id": 1,
                        "fileName": "incident-portscan.pcap",
                        "totalPackets": 367,
                        "totalBytes": 133756,
                        "topSourceIps": ["192.168.0.15 (200)"],
                        "topDestinationIps": ["192.168.0.1 (167)"],
                        "topProtocols": ["TCP (360)"],
                        "topDestinationPorts": ["22 (50)", "80 (50)"],
                        "suspiciousFindings": [
                            "Possible port scan or reconnaissance from 192.168.0.15 to 192.168.0.1 "
                            "on ports 21, 22, 23, 80, 443, 3306, 5432",
                            "TCP retransmissions observed",
                        ],
                        "summary": "TCP SYN scan from 192.168.0.15 to 192.168.0.1.",
                    }
                ],
            }
        ),
    }
    payload.update(overrides)
    return IncidentAnalysisRequest.model_validate(payload)


def valid_payload(**overrides) -> dict:
    payload = {
        "summary": (
            "Multiple independent evidence sources support a port-scan/reconnaissance event "
            "from 192.168.0.15 to 192.168.0.1. NetFlow detected repeated connections across "
            "seven destination ports, while PCAP independently confirmed TCP SYN attempts."
        ),
        "priority": "P2",
        "impact": "This is consistent with reconnaissance against 192.168.0.1 and does not establish service outage.",
        "risk": "Risk of unauthorized access if scanning continues or is followed by exploitation attempts.",
        "probable_causes": [
            "Reconnaissance or port scanning from 192.168.0.15",
            "Unauthorized connection attempts toward common service ports",
        ],
        "suggested_actions": [
            "Review whether 192.168.0.15 is an authorized scanner",
            "Inspect firewall counters for the same source and destination",
        ],
        "diagnostic_commands": [
            "ss -lntp",
            "ping 192.168.0.1",
            "tcpdump -nn -i any host 192.168.0.15 and host 192.168.0.1 -w /tmp/scan.pcap",
            "journalctl -n 100",
        ],
    }
    payload.update(overrides)
    return payload


def mock_ollama_response(payload: dict | str) -> MagicMock:
    inner = payload if isinstance(payload, str) else json.dumps(payload)
    ctx = MagicMock()
    ctx.read.return_value = json.dumps({"response": inner}).encode()
    ctx.__enter__.return_value = ctx
    ctx.__exit__.return_value = False
    return ctx


class ResponseValidatorTests(unittest.TestCase):
    def test_valid_qwen_response_passes_unchanged(self) -> None:
        payload = valid_payload()
        result = validate_model_output(payload, port_scan_request())
        self.assertTrue(result.valid)
        self.assertFalse(result.repaired)
        self.assertEqual(result.payload, payload)

    def test_lowercase_priority_normalizes(self) -> None:
        result = validate_model_output(valid_payload(priority="p1"), port_scan_request())
        self.assertTrue(result.valid)
        self.assertTrue(result.repaired)
        self.assertEqual(result.payload["priority"], "P1")

    def test_invalid_priority_is_rejected(self) -> None:
        result = validate_model_output(valid_payload(priority="P5"), port_scan_request())
        self.assertFalse(result.valid)
        self.assertEqual(result.reason_code, OLLAMA_INVALID_PRIORITY)

    def test_blank_required_field_is_rejected(self) -> None:
        result = validate_model_output(valid_payload(summary="  "), port_scan_request())
        self.assertFalse(result.valid)
        self.assertEqual(result.reason_code, OLLAMA_SCHEMA_VALIDATION_FAILED)

    def test_blank_list_entries_are_removed(self) -> None:
        result = validate_model_output(
            valid_payload(probable_causes=["", "Reconnaissance from 192.168.0.15", "   "]),
            port_scan_request(),
        )
        self.assertTrue(result.valid)
        self.assertTrue(result.repaired)
        self.assertEqual(result.payload["probable_causes"], ["Reconnaissance from 192.168.0.15"])

    def test_duplicate_actions_are_deduplicated(self) -> None:
        result = validate_model_output(
            valid_payload(
                suggested_actions=[
                    "Review whether 192.168.0.15 is an authorized scanner",
                    "review whether 192.168.0.15 is an authorized scanner",
                    "Inspect firewall counters for the same source and destination",
                ]
            ),
            port_scan_request(),
        )
        self.assertTrue(result.valid)
        self.assertTrue(result.repaired)
        self.assertEqual(len(result.payload["suggested_actions"]), 2)

    def test_unknown_factual_ip_is_detected(self) -> None:
        result = validate_model_output(
            valid_payload(summary="The attacker at 203.0.113.88 scanned the bank core."),
            port_scan_request(),
        )
        self.assertFalse(result.valid)
        self.assertEqual(result.reason_code, OLLAMA_UNGROUNDED_IP)

    def test_port_scan_does_not_allow_compromise_or_exfil_or_auth_success(self) -> None:
        request = port_scan_request()
        for summary in (
            "Successful compromise of 192.168.0.1 was observed.",
            "The host exfiltrated sensitive banking data from 192.168.0.1.",
            "Successful authentication from 192.168.0.15 was confirmed.",
        ):
            result = validate_model_output(valid_payload(summary=summary), request)
            self.assertFalse(result.valid, summary)
            self.assertEqual(result.reason_code, OLLAMA_UNSUPPORTED_CLAIMS)

    def test_risk_of_unauthorized_access_remains_valid(self) -> None:
        result = validate_model_output(
            valid_payload(risk="Risk of unauthorized access remains if scanning continues."),
            port_scan_request(),
        )
        self.assertTrue(result.valid)

    def test_auth_success_evidence_allows_successful_login_wording(self) -> None:
        request = port_scan_request(
            eventType="AUTH_SUCCESS",
            incident=IncidentContext.model_validate(
                {
                    "title": "Successful SSH login",
                    "category": "SECURITY",
                    "status": "ACTIVE",
                    "relatedEvents": [
                        {
                            "eventType": "AUTH_SUCCESS",
                            "severity": "WARNING",
                            "message": "Accepted password for admin from 192.168.0.15",
                            "eventSource": "SYSLOG",
                            "sourceAddress": "192.168.0.15",
                            "destinationAddress": "192.168.0.10",
                        }
                    ],
                }
            ),
        )
        result = validate_model_output(
            valid_payload(
                summary="Successful login from 192.168.0.15 to 192.168.0.10 was recorded in Syslog.",
                probable_causes=["Successful authentication on the SSH service"],
                suggested_actions=["Verify whether the login was expected"],
                diagnostic_commands=["journalctl -u ssh -n 50"],
            ),
            request,
        )
        self.assertTrue(result.valid)
        self.assertEqual(result.unsupported_claims, [])

    def test_generic_linux_diagnostic_commands_pass(self) -> None:
        result = validate_model_output(valid_payload(), port_scan_request())
        self.assertTrue(result.valid)
        self.assertIn("ss -lntp", result.payload["diagnostic_commands"])
        self.assertIn("journalctl -n 100", result.payload["diagnostic_commands"])

    def test_unknown_vendor_cisco_commands_are_removed(self) -> None:
        result = validate_model_output(
            valid_payload(
                diagnostic_commands=[
                    "ss -lntp",
                    "show firewall statistics",
                    "show access-lists",
                ]
            ),
            port_scan_request(),
        )
        self.assertTrue(result.valid)
        self.assertTrue(result.repaired)
        self.assertEqual(result.payload["diagnostic_commands"], ["ss -lntp"])
        self.assertEqual(len(result.removed_commands), 2)

    def test_dangerous_commands_are_rejected(self) -> None:
        result = validate_model_output(
            valid_payload(
                diagnostic_commands=[
                    "ss -lntp",
                    "rm -rf /var/log",
                    "shutdown -h now",
                    "iptables -F",
                ]
            ),
            port_scan_request(),
        )
        self.assertTrue(result.valid)
        self.assertEqual(result.payload["diagnostic_commands"], ["ss -lntp"])
        self.assertEqual(len(result.removed_commands), 3)

    def test_tcpdump_write_capture_is_allowed(self) -> None:
        result = validate_model_output(
            valid_payload(
                diagnostic_commands=["tcpdump -nn -i any host 192.168.0.15 -w /tmp/capture.pcap"]
            ),
            port_scan_request(),
        )
        self.assertTrue(result.valid)
        self.assertEqual(
            result.payload["diagnostic_commands"],
            ["tcpdump -nn -i any host 192.168.0.15 -w /tmp/capture.pcap"],
        )

    def test_unrepairable_response_triggers_fallback(self) -> None:
        with patch(
            "app.services.ollama_client.urlopen",
            return_value=mock_ollama_response(valid_payload(priority="P5")),
        ):
            response = analyze_with_ollama(port_scan_request())
        self.assertEqual(response.provider, "rules")
        self.assertTrue(response.fallback_used)
        self.assertEqual(response.fallback_reason_code, "OLLAMA_INVALID_PRIORITY")

    def test_repaired_response_is_validated_a_second_time(self) -> None:
        request = port_scan_request()
        result = validate_model_output(
            valid_payload(priority="p2", diagnostic_commands=["ss -lntp", "show firewall statistics", ""]),
            request,
        )
        self.assertTrue(result.valid)
        self.assertTrue(result.repaired)
        second = _run_validation(result.payload, request, build_evidence_index(request), apply_repairs=False)
        self.assertTrue(second.valid)
        self.assertFalse(second.repaired)

    def test_lab_port_scan_report_accepts_recon_and_linux_commands(self) -> None:
        result = validate_model_output(valid_payload(), port_scan_request())
        self.assertTrue(result.valid)
        self.assertIn("reconnaissance", result.payload["summary"].lower())
        self.assertTrue(any("tcpdump" in item for item in result.payload["diagnostic_commands"]))
        bad = validate_model_output(
            valid_payload(summary="The attacker breached the firewall at 192.168.0.1."),
            port_scan_request(),
        )
        self.assertFalse(bad.valid)
        self.assertEqual(bad.reason_code, OLLAMA_UNSUPPORTED_CLAIMS)

    def test_generic_filler_summary_is_rejected(self) -> None:
        result = validate_model_output(valid_payload(summary="Investigate the issue"), port_scan_request())
        self.assertFalse(result.valid)
        self.assertEqual(result.reason_code, OLLAMA_LOW_QUALITY)

    def test_safe_downgrade_of_unauthorized_access_wording(self) -> None:
        result = validate_model_output(
            valid_payload(summary="Unauthorized access occurred from 192.168.0.15 to 192.168.0.1 during the scan."),
            port_scan_request(),
        )
        self.assertTrue(result.valid)
        self.assertTrue(result.repaired)
        self.assertIn("risk of unauthorized access", result.payload["summary"].lower())
        self.assertNotIn("unauthorized access occurred", result.payload["summary"].lower())
