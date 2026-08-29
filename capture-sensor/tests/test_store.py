from __future__ import annotations

import subprocess
import time
import unittest
from unittest.mock import MagicMock, patch

from app.captures import CaptureStore


class CaptureStoreTests(unittest.TestCase):
    @patch("app.captures.subprocess.Popen")
    def test_tcpdump_timeout_cleanup(self, popen: MagicMock) -> None:
        process = MagicMock()
        process.communicate.side_effect = subprocess.TimeoutExpired(cmd=["tcpdump"], timeout=1)
        process.poll.return_value = None
        popen.return_value = process

        store = CaptureStore()
        job = store.start(
            interface="eth1",
            source_ip="10.0.0.10",
            destination_ip="10.10.10.20",
            duration_seconds=20,
        )
        deadline = time.time() + 2
        while time.time() < deadline:
            current = store.get(job.id)
            if current.status in {"FAILED", "COMPLETED", "CANCELLED"}:
                break
            time.sleep(0.05)
        current = store.get(job.id)
        self.assertEqual(current.status, "FAILED")
        self.assertEqual(current.error_code, "TIMEOUT")
        process.kill.assert_called()


if __name__ == "__main__":
    unittest.main()
