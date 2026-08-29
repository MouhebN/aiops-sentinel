from __future__ import annotations

import unittest
from io import BytesIO
from unittest.mock import patch
from urllib.error import HTTPError, URLError

from app.services.analyzer import analyze
from app.services.fallback_reasons import (
    OLLAMA_EMPTY_RESPONSE,
    OLLAMA_HTTP_ERROR,
    OLLAMA_INTERNAL_ERROR,
    OLLAMA_INVALID_JSON,
    OLLAMA_INVALID_PRIORITY,
    OLLAMA_MODEL_UNAVAILABLE,
    OLLAMA_RESPONSE_VALIDATION_FAILED,
    OLLAMA_TIMEOUT,
    OLLAMA_UNREACHABLE,
    OLLAMA_UNSUPPORTED_CLAIMS,
    employee_message,
)
from app.services.ollama_client import analyze_with_ollama
from tests.test_response_validator import mock_ollama_response, port_scan_request, valid_payload


def http_error(status: int, body: str) -> HTTPError:
    return HTTPError(
        "http://localhost:11434/api/generate",
        status,
        "error",
        hdrs=None,
        fp=BytesIO(body.encode("utf-8")),
    )


class FallbackReasonTests(unittest.TestCase):
    def test_successful_ollama_response_has_no_fallback(self) -> None:
        with patch(
            "app.services.ollama_client.urlopen",
            return_value=mock_ollama_response(valid_payload()),
        ):
            response = analyze_with_ollama(port_scan_request())
        self.assertEqual(response.provider, "ollama")
        self.assertEqual(response.requested_provider, "ollama")
        self.assertFalse(response.fallback_used)
        self.assertIsNone(response.fallback_reason_code)
        self.assertIsNone(response.fallback_reason)
        self.assertEqual(response.model, "llama3.2:3b")

    def test_ollama_unreachable(self) -> None:
        with patch("app.services.ollama_client.urlopen", side_effect=URLError("connection refused")):
            response = analyze_with_ollama(port_scan_request())
        self.assertEqual(response.provider, "rules")
        self.assertTrue(response.fallback_used)
        self.assertEqual(response.fallback_reason_code, OLLAMA_UNREACHABLE)
        self.assertEqual(response.requested_provider, "ollama")
        self.assertEqual(response.fallback_reason, employee_message(OLLAMA_UNREACHABLE))

    def test_ollama_timeout(self) -> None:
        with patch("app.services.ollama_client.urlopen", side_effect=TimeoutError("timed out")):
            response = analyze_with_ollama(port_scan_request())
        self.assertEqual(response.provider, "rules")
        self.assertTrue(response.fallback_used)
        self.assertEqual(response.fallback_reason_code, OLLAMA_TIMEOUT)
        self.assertIn("timeout", response.fallback_reason.lower())

    def test_model_unavailable(self) -> None:
        with patch(
            "app.services.ollama_client.urlopen",
            side_effect=http_error(404, '{"error":"model \\"llama3.2:3b\\" not found"}'),
        ):
            response = analyze_with_ollama(port_scan_request())
        self.assertEqual(response.fallback_reason_code, OLLAMA_MODEL_UNAVAILABLE)
        self.assertTrue(response.fallback_used)

    def test_http_error(self) -> None:
        with patch(
            "app.services.ollama_client.urlopen",
            side_effect=http_error(502, '{"error":"bad gateway"}'),
        ):
            response = analyze_with_ollama(port_scan_request())
        self.assertEqual(response.fallback_reason_code, OLLAMA_HTTP_ERROR)

    def test_invalid_json(self) -> None:
        with patch(
            "app.services.ollama_client.urlopen",
            return_value=mock_ollama_response("{not-json"),
        ):
            response = analyze_with_ollama(port_scan_request())
        self.assertEqual(response.fallback_reason_code, OLLAMA_INVALID_JSON)
        self.assertTrue(response.fallback_used)

    def test_invalid_priority(self) -> None:
        with patch(
            "app.services.ollama_client.urlopen",
            return_value=mock_ollama_response(valid_payload(priority="P5")),
        ):
            response = analyze_with_ollama(port_scan_request())
        self.assertEqual(response.fallback_reason_code, OLLAMA_INVALID_PRIORITY)
        self.assertTrue(response.fallback_used)
        self.assertEqual(response.provider, "rules")

    def test_unsupported_claims(self) -> None:
        with patch(
            "app.services.ollama_client.urlopen",
            return_value=mock_ollama_response(
                valid_payload(summary="The attacker breached the firewall at 192.168.0.1.")
            ),
        ):
            response = analyze_with_ollama(port_scan_request())
        self.assertEqual(response.fallback_reason_code, OLLAMA_UNSUPPORTED_CLAIMS)

    def test_generic_validation_failure(self) -> None:
        with patch(
            "app.services.ollama_client.urlopen",
            return_value=mock_ollama_response(valid_payload(summary="Investigate the issue")),
        ):
            response = analyze_with_ollama(port_scan_request())
        self.assertEqual(response.fallback_reason_code, OLLAMA_RESPONSE_VALIDATION_FAILED)
        self.assertTrue(response.fallback_used)

    def test_empty_response(self) -> None:
        with patch(
            "app.services.ollama_client.urlopen",
            return_value=mock_ollama_response(""),
        ):
            response = analyze_with_ollama(port_scan_request())
        self.assertEqual(response.fallback_reason_code, OLLAMA_EMPTY_RESPONSE)

    def test_internal_error(self) -> None:
        with patch("app.services.ollama_client.urlopen", side_effect=RuntimeError("unexpected crash")):
            response = analyze_with_ollama(port_scan_request())
        self.assertEqual(response.fallback_reason_code, OLLAMA_INTERNAL_ERROR)
        self.assertTrue(response.fallback_used)
        self.assertEqual(response.provider, "rules")
        self.assertNotIn("unexpected crash", response.fallback_reason)

    def test_direct_rules_provider_is_not_an_ollama_failure(self) -> None:
        with patch("app.services.analyzer.config.should_try_ollama", return_value=False):
            response = analyze(port_scan_request())
        self.assertEqual(response.provider, "rules")
        self.assertEqual(response.requested_provider, "rules")
        self.assertFalse(response.fallback_used)
        self.assertIsNone(response.fallback_reason_code)
        self.assertIsNone(response.fallback_reason)

    def test_schema_validation_failed(self) -> None:
        payload = valid_payload()
        del payload["summary"]
        with patch(
            "app.services.ollama_client.urlopen",
            return_value=mock_ollama_response(payload),
        ):
            response = analyze_with_ollama(port_scan_request())
        self.assertEqual(response.provider, "rules")
        self.assertTrue(response.fallback_used)
        self.assertEqual(response.fallback_reason_code, "OLLAMA_SCHEMA_VALIDATION_FAILED")

    def test_fallback_metadata_serializes_with_operator_aliases(self) -> None:
        with patch("app.services.ollama_client.urlopen", side_effect=TimeoutError("timed out")):
            response = analyze_with_ollama(port_scan_request())
        payload = response.model_dump(by_alias=True)
        self.assertEqual(payload["provider"], "rules")
        self.assertEqual(payload["requestedProvider"], "ollama")
        self.assertTrue(payload["fallbackUsed"])
        self.assertEqual(payload["fallbackReasonCode"], OLLAMA_TIMEOUT)
        self.assertIn("timeout", payload["fallbackReason"].lower())
        self.assertNotIn("timed out", payload["fallbackReason"])


if __name__ == "__main__":
    unittest.main()
