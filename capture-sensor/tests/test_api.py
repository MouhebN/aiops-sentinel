from __future__ import annotations

import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from fastapi.testclient import TestClient

from app import main
from app.captures import CaptureError, CaptureJob


class CaptureApiTests(unittest.TestCase):
    def setUp(self) -> None:
        main.rolling.enabled = False
        main.rolling.stop()
        self.client = TestClient(main.app)

    def test_health_does_not_require_active_capture(self) -> None:
        response = self.client.get("/health")
        self.assertEqual(response.status_code, 200)
        self.assertIn("status", response.json())
        self.assertIn("rolling", response.json())

    def test_health_exposes_sixty_second_rolling_window(self) -> None:
        response = self.client.get("/health")
        self.assertEqual(response.status_code, 200)
        rolling = response.json()["rolling"]
        self.assertEqual(rolling["preTriggerSeconds"], 60)
        self.assertEqual(rolling["segmentSeconds"], 5)
        self.assertEqual(rolling["expectedFiles"], 12)
        self.assertEqual(rolling["maxBytes"], 20 * 1024 * 1024)

    def test_extra_bpf_field_is_rejected(self) -> None:
        response = self.client.post(
            "/captures",
            json={
                "durationSeconds": 20,
                "interface": "eth1",
                "sourceIp": "10.0.0.10",
                "destinationIp": "10.10.10.20",
                "bpf": "host 1.2.3.4; id",
            },
        )
        self.assertEqual(response.status_code, 422)

    def test_snapshot_extra_bpf_field_is_rejected(self) -> None:
        response = self.client.post(
            "/snapshots",
            json={
                "interface": "eth1",
                "sourceIp": "10.0.0.10",
                "destinationIp": "10.10.10.20",
                "bpf": "host 1.2.3.4",
            },
        )
        self.assertEqual(response.status_code, 422)

    def test_invalid_interface_rejected(self) -> None:
        response = self.client.post(
            "/captures",
            json={
                "durationSeconds": 20,
                "interface": "eth1; rm -rf /",
                "sourceIp": "10.0.0.10",
                "destinationIp": "10.10.10.20",
            },
        )
        self.assertEqual(response.status_code, 400)

    def test_shell_injection_in_ip_rejected(self) -> None:
        response = self.client.post(
            "/captures",
            json={
                "durationSeconds": 20,
                "interface": "eth1",
                "sourceIp": "10.0.0.10; id",
                "destinationIp": "10.10.10.20",
            },
        )
        self.assertEqual(response.status_code, 400)

    def test_unknown_interface_rejected(self) -> None:
        response = self.client.post(
            "/captures",
            json={
                "durationSeconds": 20,
                "interface": "eth0",
                "sourceIp": "10.0.0.10",
                "destinationIp": "10.10.10.20",
            },
        )
        self.assertEqual(response.status_code, 400)

    @patch.object(main.store, "start")
    def test_safe_request_starts_capture(self, start) -> None:
        start.return_value = CaptureJob(
            id="job-1",
            status="PENDING",
            interface="eth1",
            source_ip="10.0.0.10",
            destination_ip="10.10.10.20",
            duration_seconds=20,
            filter_expression="host 10.0.0.10 and host 10.10.10.20",
            file_path=Path("/tmp/captures/job-1.pcap"),
        )
        response = self.client.post(
            "/captures",
            json={
                "durationSeconds": 20,
                "interface": "eth1",
                "sourceIp": "10.0.0.10",
                "destinationIp": "10.10.10.20",
            },
        )
        self.assertEqual(response.status_code, 201)
        start.assert_called_once()
        kwargs = start.call_args.kwargs
        self.assertEqual(kwargs["interface"], "eth1")
        self.assertEqual(kwargs["source_ip"], "10.0.0.10")
        self.assertEqual(kwargs["destination_ip"], "10.10.10.20")
        self.assertNotIn("bpf", kwargs)

    @patch.object(main.store, "start")
    def test_busy_capture_returns_conflict(self, start) -> None:
        start.side_effect = CaptureError("BUSY", "A capture is already running", 409)
        response = self.client.post(
            "/captures",
            json={
                "durationSeconds": 20,
                "interface": "eth1",
                "sourceIp": "10.0.0.10",
                "destinationIp": "10.10.10.20",
            },
        )
        self.assertEqual(response.status_code, 409)

    def test_capture_file_returned(self) -> None:
        handle = tempfile.NamedTemporaryFile(suffix=".pcap", delete=False)
        path = Path(handle.name)
        handle.close()
        path.write_bytes(b"\xd4\xc3\xb2\xa1" + b"\x00" * 20)
        job = CaptureJob(
            id="job-file",
            status="COMPLETED",
            interface="eth1",
            source_ip="10.0.0.10",
            destination_ip="10.10.10.20",
            duration_seconds=20,
            filter_expression="host 10.0.0.10 and host 10.10.10.20",
            file_path=path,
        )
        try:
            with patch.object(main.store, "get", return_value=job):
                response = self.client.get("/captures/job-file/file")
            self.assertEqual(response.status_code, 200)
            self.assertEqual(response.content[:4], b"\xd4\xc3\xb2\xa1")
        finally:
            path.unlink(missing_ok=True)


if __name__ == "__main__":
    unittest.main()
