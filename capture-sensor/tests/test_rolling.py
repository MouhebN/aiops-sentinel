from __future__ import annotations

import tempfile
import time
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

from app.captures import CaptureStore
from app.pcaputil import write_minimal_pcap
from app.rolling import RollingBuffer, rolling_tcpdump_argv


class RollingBufferTests(unittest.TestCase):
    def test_start_noops_when_tcpdump_missing(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            buffer = RollingBuffer(
                enabled=True,
                interface="eth1",
                ring_dir=Path(tmp) / "ring",
            )
            with patch("app.rolling.shutil.which", return_value=None):
                buffer.start()
            self.assertFalse(buffer.running)
            self.assertIn("tcpdump", (buffer.last_error or "").lower())
            buffer.stop()

    def test_default_window_is_twelve_segments(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            buffer = RollingBuffer(ring_dir=Path(tmp))
            self.assertEqual(buffer.pre_trigger_seconds, 60)
            self.assertEqual(buffer.segment_seconds, 5)
            self.assertEqual(buffer.expected_files, 12)
            status = buffer.status()
            self.assertEqual(status["expectedFiles"], 12)
            self.assertEqual(status["preTriggerSeconds"], 60)
            self.assertEqual(status["maxBytes"], 20 * 1024 * 1024)

    def test_prune_sixty_second_window_drops_rotated_out_segments(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            ring = Path(tmp)
            buffer = RollingBuffer(
                enabled=True,
                interface="eth1",
                segment_seconds=5,
                pre_trigger_seconds=60,
                max_file_mb=1,
                ring_dir=ring,
            )
            now = time.time()
            import os

            for index in range(16):
                path = ring / f"buffer-{index}.pcap"
                write_minimal_pcap(path, [(index, 0, b"x" * 20)])
                os.utime(path, (now - (75 - index * 5), now - (75 - index * 5)))
            buffer.prune(now=now)
            remaining = {path.name for path in buffer.segment_paths()}
            self.assertNotIn("buffer-0.pcap", remaining)
            self.assertIn("buffer-15.pcap", remaining)
            self.assertLessEqual(len(remaining), 14)

    def test_rolling_argv_rotates_by_time_without_w_limit(self) -> None:
        argv = rolling_tcpdump_argv("eth1", Path("/tmp/captures/ring"), 5)
        self.assertIn("-G", argv)
        self.assertIn("5", argv)
        self.assertNotIn("-W", argv)
        self.assertTrue(any("buffer-%Y%m%d%H%M%S.pcap" in part for part in argv))
        self.assertNotIn("10.0.0.10", " ".join(argv))

    def test_prune_overwrites_old_segments_and_stays_bounded(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            ring = Path(tmp)
            buffer = RollingBuffer(
                enabled=True,
                interface="eth1",
                segment_seconds=5,
                pre_trigger_seconds=20,
                max_file_mb=1,
                ring_dir=ring,
            )
            now = time.time()
            files = []
            for index in range(6):
                path = ring / f"buffer-{index}.pcap"
                write_minimal_pcap(path, [(index, 0, b"x" * 20)])
                stamp = now - (30 - index)
                os_utime = path
                import os

                os.utime(os_utime, (stamp, stamp))
                files.append(path)
            buffer.prune(now=now)
            remaining = buffer.segment_paths()
            self.assertLessEqual(len(remaining), 5)
            self.assertTrue((ring / "buffer-5.pcap").exists())
            self.assertFalse((ring / "buffer-0.pcap").exists())

    def test_copy_window_does_not_stop_rolling(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            ring = Path(tmp) / "ring"
            dest = Path(tmp) / "snap"
            ring.mkdir()
            buffer = RollingBuffer(ring_dir=ring, pre_trigger_seconds=20, segment_seconds=5)
            buffer._process = MagicMock()
            buffer._process.poll.return_value = None
            write_minimal_pcap(ring / "buffer-1.pcap", [(1, 0, b"pre1")])
            write_minimal_pcap(ring / "buffer-2.pcap", [(2, 0, b"pre2")])
            copied = buffer.copy_window(dest)
            self.assertEqual(len(copied), 2)
            self.assertTrue(buffer.running)
            buffer._process.terminate.assert_not_called()

    def test_snapshot_leaves_rolling_process_running(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            ring = root / "ring"
            capture_dir = root / "captures"
            ring.mkdir()
            capture_dir.mkdir()
            buffer = RollingBuffer(ring_dir=ring, interface="eth1", enabled=True)
            buffer._process = MagicMock()
            buffer._process.poll.return_value = None
            write_minimal_pcap(ring / "buffer-now.pcap", [(5, 0, b"SYN..........")])

            class FakeProc:
                def __init__(self, argv=None):
                    self.args = argv or ["true"]
                    self.returncode = 0
                    self.stdin = None
                    self.stdout = None
                    self.stderr = None
                    self.pid = 1

                def communicate(self, input=None, timeout=None):
                    return b"", b""

                def poll(self):
                    return 0

                def terminate(self):
                    return None

                def kill(self):
                    return None

                def wait(self, timeout=None):
                    return 0

                def __enter__(self):
                    return self

                def __exit__(self, exc_type, exc, tb):
                    return False

            def fake_popen(argv, *args, **kwargs):
                return FakeProc(argv)

            with patch("app.captures.CAPTURE_DIR", capture_dir), patch(
                "app.captures.tcpdump_argv", return_value=["true"]
            ), patch("app.captures.subprocess.Popen", side_effect=fake_popen), patch(
                "app.pcaputil.extract_hosts", return_value=False
            ), patch("app.pcaputil._which", return_value=None):
                store = CaptureStore(rolling=buffer)
                job = store.start_snapshot(
                    interface="eth1",
                    source_ip="10.0.0.10",
                    destination_ip="10.10.10.20",
                    post_trigger_seconds=5,
                    pre_trigger_seconds=20,
                )
                deadline = time.time() + 3
                while time.time() < deadline and store.get(job.id).status in {"PENDING", "RUNNING"}:
                    time.sleep(0.05)
                finished = store.get(job.id)
                self.assertEqual(finished.status, "COMPLETED", finished.error_message)
                self.assertGreater(finished.packet_count or 0, 0)
                self.assertTrue(finished.file_path.exists())
                self.assertFalse((finished.work_dir / "pre").exists() if finished.work_dir else True)
            self.assertTrue(buffer.running)
            buffer._process.terminate.assert_not_called()


if __name__ == "__main__":
    unittest.main()
