from __future__ import annotations

import tempfile
import threading
import unittest
from pathlib import Path
from unittest.mock import patch

from app.schemas import MetricContext, PacketCaptureSummaryContext, PreviousReportContext, RelatedEventContext
from app.services.analysis_cache import reset_cache_state
from app.services.analyzer import analyze
from app.services.context_fingerprint import context_fingerprint
from tests.test_response_validator import mock_ollama_response, port_scan_request, valid_payload


class AiAnalysisCacheTests(unittest.TestCase):
    def setUp(self) -> None:
        self.tempdir = tempfile.TemporaryDirectory()
        self.cache_path = str(Path(self.tempdir.name) / "ai_analysis_cache.sqlite")
        reset_cache_state()
        self.cache_enabled = patch("app.config.AI_CACHE_ENABLED", True)
        self.cache_path_patch = patch("app.config.AI_CACHE_PATH", self.cache_path)
        self.cache_enabled.start()
        self.cache_path_patch.start()

    def tearDown(self) -> None:
        self.cache_enabled.stop()
        self.cache_path_patch.stop()
        reset_cache_state()
        self.tempdir.cleanup()

    def test_first_lab_analysis_is_cache_miss_and_stores_validated_report(self) -> None:
        with patch(
            "app.services.ollama_client.urlopen",
            return_value=mock_ollama_response(valid_payload()),
        ) as mocked:
            response = analyze(port_scan_request())
        self.assertFalse(response.cache_hit)
        self.assertEqual(response.provider, "ollama")
        self.assertIsNotNone(response.context_fingerprint)
        self.assertEqual(len(response.context_fingerprint or ""), 64)
        self.assertIsNotNone(response.analysis_duration_ms)
        self.assertEqual(mocked.call_count, 1)

    def test_second_identical_lab_request_is_cache_hit(self) -> None:
        with patch(
            "app.services.ollama_client.urlopen",
            return_value=mock_ollama_response(valid_payload()),
        ) as mocked:
            first = analyze(port_scan_request())
            second = analyze(port_scan_request())
        self.assertFalse(first.cache_hit)
        self.assertTrue(second.cache_hit)
        self.assertEqual(first.summary, second.summary)
        self.assertEqual(first.context_fingerprint, second.context_fingerprint)
        self.assertEqual(mocked.call_count, 1)
        self.assertLess(second.analysis_duration_ms or 0, first.analysis_duration_ms or 10_000)

    def test_new_syslog_event_changes_fingerprint(self) -> None:
        original = port_scan_request()
        updated = with_incident(original, related_events=[syslog_event()])
        self.assertNotEqual(context_fingerprint(original), context_fingerprint(updated))
        with patch(
            "app.services.ollama_client.urlopen",
            return_value=mock_ollama_response(valid_payload()),
        ) as mocked:
            analyze(original)
            response = analyze(updated)
        self.assertFalse(response.cache_hit)
        self.assertEqual(mocked.call_count, 2)

    def test_new_netflow_evidence_is_cache_miss(self) -> None:
        original = port_scan_request()
        updated = with_netflow(original, flow_count=200)
        self.assertNotEqual(context_fingerprint(original), context_fingerprint(updated))
        with patch(
            "app.services.ollama_client.urlopen",
            return_value=mock_ollama_response(valid_payload()),
        ) as mocked:
            analyze(original)
            response = analyze(updated)
        self.assertFalse(response.cache_hit)
        self.assertEqual(mocked.call_count, 2)

    def test_new_pcap_analysis_is_cache_miss(self) -> None:
        original = port_scan_request()
        extra = PacketCaptureSummaryContext.model_validate(
            {
                "id": 2,
                "fileName": "follow-up.pcap",
                "totalPackets": 90,
                "totalBytes": 12000,
                "topSourceIps": ["192.168.0.15 (90)"],
                "topDestinationIps": ["192.168.0.1 (90)"],
                "topProtocols": ["TCP (90)"],
                "topDestinationPorts": ["22 (90)"],
                "suspiciousFindings": ["Additional SYN attempts on port 22"],
                "summary": "Follow-up capture.",
            }
        )
        updated = with_incident(
            original,
            packet_capture_summaries=original.incident.packet_capture_summaries + [extra],
        )
        self.assertNotEqual(context_fingerprint(original), context_fingerprint(updated))
        with patch(
            "app.services.ollama_client.urlopen",
            return_value=mock_ollama_response(valid_payload()),
        ) as mocked:
            analyze(original)
            self.assertFalse(analyze(updated).cache_hit)
        self.assertEqual(mocked.call_count, 2)

    def test_changed_metric_summary_is_cache_miss(self) -> None:
        original = with_metrics(port_scan_request(), 12.0)
        updated = with_metrics(port_scan_request(), 91.0)
        self.assertNotEqual(context_fingerprint(original), context_fingerprint(updated))
        with patch(
            "app.services.ollama_client.urlopen",
            return_value=mock_ollama_response(valid_payload()),
        ) as mocked:
            analyze(original)
            self.assertFalse(analyze(updated).cache_hit)
        self.assertEqual(mocked.call_count, 2)

    def test_incident_status_change_is_cache_miss(self) -> None:
        original = port_scan_request()
        updated = with_incident(original, status="RECOVERED")
        self.assertNotEqual(context_fingerprint(original), context_fingerprint(updated))
        with patch(
            "app.services.ollama_client.urlopen",
            return_value=mock_ollama_response(valid_payload()),
        ) as mocked:
            analyze(original)
            self.assertFalse(analyze(updated).cache_hit)
        self.assertEqual(mocked.call_count, 2)

    def test_retrieval_timestamps_do_not_change_fingerprint(self) -> None:
        original = port_scan_request()
        updated = with_incident(
            original,
            duration_minutes=999,
            last_seen_at="2026-08-20T23:59:59Z",
            previous_reports=[
                PreviousReportContext.model_validate(
                    {
                        "id": 9,
                        "deviceId": "fw-01",
                        "deviceName": "BANK-FW-01",
                        "eventType": "PORT_SCAN",
                        "severity": "WARNING",
                        "summary": "Previous report",
                        "provider": "ollama",
                        "generatedAt": "2026-08-20T18:00:00Z",
                    }
                )
            ],
        )
        self.assertEqual(context_fingerprint(original), context_fingerprint(updated))

    def test_different_model_is_cache_miss(self) -> None:
        with patch(
            "app.services.ollama_client.urlopen",
            return_value=mock_ollama_response(valid_payload()),
        ) as mocked:
            analyze(port_scan_request())
            with patch("app.config.OLLAMA_MODEL", "qwen3:14b"):
                response = analyze(port_scan_request())
        self.assertFalse(response.cache_hit)
        self.assertEqual(mocked.call_count, 2)

    def test_different_provider_is_cache_miss(self) -> None:
        with patch(
            "app.services.ollama_client.urlopen",
            return_value=mock_ollama_response(valid_payload()),
        ) as mocked:
            ollama = analyze(port_scan_request())
            with patch("app.services.analyzer.config.should_try_ollama", return_value=False):
                rules = analyze(port_scan_request())
        self.assertEqual(ollama.provider, "ollama")
        self.assertEqual(rules.provider, "rules")
        self.assertFalse(rules.cache_hit)
        self.assertFalse(rules.fallback_used)
        self.assertEqual(mocked.call_count, 1)

    def test_different_pipeline_version_is_cache_miss(self) -> None:
        with patch(
            "app.services.ollama_client.urlopen",
            return_value=mock_ollama_response(valid_payload()),
        ) as mocked:
            analyze(port_scan_request())
            with patch("app.config.AI_ANALYSIS_VERSION", "v2"):
                response = analyze(port_scan_request())
        self.assertFalse(response.cache_hit)
        self.assertEqual(mocked.call_count, 2)

    def test_validation_failure_is_not_cached(self) -> None:
        with patch(
            "app.services.ollama_client.urlopen",
            side_effect=[
                mock_ollama_response(valid_payload(priority="P5")),
                mock_ollama_response(valid_payload()),
            ],
        ) as mocked:
            failed = analyze(port_scan_request())
            retry = analyze(port_scan_request())
        self.assertTrue(failed.fallback_used)
        self.assertFalse(failed.cache_hit)
        self.assertFalse(retry.cache_hit)
        self.assertEqual(retry.provider, "ollama")
        self.assertEqual(mocked.call_count, 2)

    def test_timeout_fallback_is_not_cached(self) -> None:
        with patch(
            "app.services.ollama_client.urlopen",
            side_effect=[TimeoutError("timed out"), mock_ollama_response(valid_payload())],
        ) as mocked:
            fallback = analyze(port_scan_request())
            retry = analyze(port_scan_request())
        self.assertTrue(fallback.fallback_used)
        self.assertEqual(fallback.fallback_reason_code, "OLLAMA_TIMEOUT")
        self.assertFalse(fallback.cache_hit)
        self.assertFalse(retry.cache_hit)
        self.assertEqual(retry.provider, "ollama")
        self.assertEqual(mocked.call_count, 2)

    def test_cache_metadata_aliases_are_serialized(self) -> None:
        with patch(
            "app.services.ollama_client.urlopen",
            return_value=mock_ollama_response(valid_payload()),
        ):
            miss = analyze(port_scan_request()).model_dump(by_alias=True)
            hit = analyze(port_scan_request()).model_dump(by_alias=True)
        self.assertFalse(miss["cacheHit"])
        self.assertTrue(hit["cacheHit"])
        self.assertIn("analysisDurationMs", hit)
        self.assertEqual(len(hit["contextFingerprint"]), 64)

    def test_concurrent_identical_requests_share_one_ollama_call(self) -> None:
        started = threading.Event()
        release = threading.Event()
        call_count = {"n": 0}

        def slow_urlopen(*args, **kwargs):
            call_count["n"] += 1
            started.set()
            release.wait(timeout=2)
            return mock_ollama_response(valid_payload())

        results: list = []

        def worker() -> None:
            results.append(analyze(port_scan_request()))

        with patch("app.services.ollama_client.urlopen", side_effect=slow_urlopen):
            first = threading.Thread(target=worker)
            second = threading.Thread(target=worker)
            first.start()
            self.assertTrue(started.wait(timeout=2))
            second.start()
            release.set()
            first.join(timeout=3)
            second.join(timeout=3)

        self.assertEqual(call_count["n"], 1)
        self.assertEqual(len(results), 2)
        self.assertEqual(sum(1 for item in results if item.cache_hit), 1)
        self.assertEqual(sum(1 for item in results if not item.cache_hit), 1)


def with_incident(request, **updates):
    incident = request.incident.model_copy(update=updates)
    return request.model_copy(update={"incident": incident})


def with_netflow(request, flow_count: int):
    flow = request.incident.network_flow_summaries[0].model_copy(update={"flow_count": flow_count})
    return with_incident(request, network_flow_summaries=[flow])


def with_metrics(request, value: float):
    metric = MetricContext.model_validate(
        {
            "metricName": "cpu_usage",
            "metricValue": value,
            "unit": "%",
            "source": "SNMP",
            "sampledAt": "2026-08-20T12:00:00Z",
        }
    )
    return with_incident(request, recent_metrics=[metric])


def syslog_event() -> RelatedEventContext:
    return RelatedEventContext.model_validate(
        {
            "eventType": "FIREWALL_DENY",
            "severity": "WARNING",
            "message": "DENY TCP 192.168.0.15:45122 -> 192.168.0.1:22",
            "eventSource": "SYSLOG",
            "parsingProfile": "FIREWALL",
            "sourceAddress": "192.168.0.15",
            "destinationAddress": "192.168.0.1",
            "destinationPort": 22,
            "occurredAt": "2026-08-20T12:01:00Z",
        }
    )


if __name__ == "__main__":
    unittest.main()
