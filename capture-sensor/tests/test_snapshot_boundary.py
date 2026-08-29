"""Regression: AUTO_ROLLING must not drop packets at a 5s ring/snapshot boundary."""

from __future__ import annotations

import os
import tempfile
import time
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

from app.captures import CaptureStore
from app.pcaputil import read_packets, write_minimal_pcap
from app.rolling import RollingBuffer

SCAN_PORTS = (21, 22, 23, 80, 443, 3306, 5432)


def payload(port: int) -> bytes:
    return f"SYN{port:04d}".encode("ascii")


def wait_done(store: CaptureStore, capture_id: str, timeout: float = 3.0):
    deadline = time.time() + timeout
    while time.time() < deadline:
        job = store.get(capture_id)
        if job.status not in {"PENDING", "RUNNING"}:
            return job
        time.sleep(0.02)
    return store.get(capture_id)


class FakeProc:
    def __init__(self, on_communicate):
        self.returncode = 0
        self.pid = 1
        self._on_communicate = on_communicate

    def communicate(self, input=None, timeout=None):
        self._on_communicate()
        return b"", b""

    def poll(self):
        return 0

    def terminate(self):
        return None

    def kill(self):
        return None

    def wait(self, timeout=None):
        return 0


class SnapshotBoundaryTests(unittest.TestCase):
    def test_copy_window_keeps_packets_around_five_second_rotation(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            ring = Path(tmp) / "ring"
            dest = Path(tmp) / "snap"
            ring.mkdir()
            buffer = RollingBuffer(ring_dir=ring, pre_trigger_seconds=60, segment_seconds=5)
            now = time.time()
            t0 = 1_700_000_000
            write_minimal_pcap(
                ring / "buffer-seg0.pcap",
                [(t0, 0, payload(21)), (t0 + 2, 0, payload(22)), (t0 + 4, 0, payload(23))],
            )
            write_minimal_pcap(
                ring / "buffer-seg1.pcap",
                [(t0 + 5, 0, payload(80)), (t0 + 8, 0, payload(443))],
            )
            write_minimal_pcap(
                ring / "buffer-seg2.pcap",
                [(t0 + 10, 0, payload(3306)), (t0 + 12, 0, payload(5432))],
            )
            for index, name in enumerate(("buffer-seg0.pcap", "buffer-seg1.pcap", "buffer-seg2.pcap")):
                stamp = now - 15 + index * 5
                os.utime(ring / name, (stamp, stamp))
            copied = buffer.copy_window(dest, now=now)
            self.assertEqual(len(copied), 3)
            frames = []
            for path in copied:
                _header, packets = read_packets(path)
                frames.extend(pkt[2] for pkt in packets)
            self.assertEqual(set(frames), {payload(port) for port in SCAN_PORTS})

    def test_copy_window_drops_torn_active_segment_tail(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            ring = Path(tmp) / "ring"
            dest = Path(tmp) / "snap"
            ring.mkdir()
            buffer = RollingBuffer(ring_dir=ring, pre_trigger_seconds=20, segment_seconds=5)
            live = ring / "buffer-active.pcap"
            write_minimal_pcap(live, [(1, 0, payload(21)), (2, 0, payload(22)), (4, 0, payload(23))])
            live.write_bytes(live.read_bytes()[:-4])
            copied = buffer.copy_window(dest)
            self.assertEqual(len(copied), 1)
            _header, packets = read_packets(copied[0])
            self.assertEqual([pkt[2] for pkt in packets], [payload(21), payload(22)])

    def test_snapshot_recovers_packet_missing_from_post_at_segment_boundary(self) -> None:
        """Port 443 lands after the first ring copy and is omitted from post.pcap.

        That is the observed AUTO_ROLLING hole: copy-then-post left a visibility
        gap, and a later rolling ``-G`` rotation / torn copy could drop the packet
        from the pre snapshot as well. The overlap copy after post must restore it.
        """
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            ring = root / "ring"
            capture_dir = root / "captures"
            ring.mkdir()
            capture_dir.mkdir()
            buffer = RollingBuffer(ring_dir=ring, interface="eth1", enabled=True, pre_trigger_seconds=60, segment_seconds=5)
            buffer._process = MagicMock()
            buffer._process.poll.return_value = None
            t0 = 1_700_000_000
            write_minimal_pcap(
                ring / "buffer-pre.pcap",
                [(t0, 0, payload(21)), (t0 + 2, 0, payload(22)), (t0 + 4, 0, payload(23))],
            )
            order: list[str] = []
            real_copy = buffer.copy_window

            def tracked_copy(destination, now=None):
                order.append("copy")
                return real_copy(destination, now=now)

            buffer.copy_window = tracked_copy  # type: ignore[method-assign]

            def post_path() -> Path:
                snaps = [path for path in capture_dir.iterdir() if path.is_dir() and path.name.startswith("snap-")]
                self.assertEqual(len(snaps), 1)
                return snaps[0] / "post.pcap"

            def on_communicate():
                write_minimal_pcap(
                    ring / "buffer-mid.pcap",
                    [(t0 + 6, 0, payload(80)), (t0 + 8, 0, payload(443))],
                )
                write_minimal_pcap(
                    ring / "buffer-late.pcap",
                    [(t0 + 10, 0, payload(3306)), (t0 + 12, 0, payload(5432))],
                )
                write_minimal_pcap(
                    post_path(),
                    [
                        (t0 + 6, 0, payload(80)),
                        (t0 + 10, 0, payload(3306)),
                        (t0 + 12, 0, payload(5432)),
                    ],
                )

            def fake_popen(argv, *args, **kwargs):
                order.append("popen")
                return FakeProc(on_communicate)

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
                    pre_trigger_seconds=60,
                )
                finished = wait_done(store, job.id)
                self.assertEqual(finished.status, "COMPLETED", finished.error_message)
                self.assertEqual(order[0], "popen")
                self.assertGreaterEqual(order.count("copy"), 2)
                self.assertEqual(order[:3], ["popen", "copy", "copy"])
                _header, packets = read_packets(finished.file_path)
                frames = [pkt[2] for pkt in packets]
                self.assertEqual(set(frames), {payload(port) for port in SCAN_PORTS})
                self.assertEqual(len(frames), 7)
                self.assertIsNotNone(finished.triggered_at)
                self.assertIsNotNone(finished.capture_window_start)
                self.assertIsNotNone(finished.capture_window_end)
                assert finished.triggered_at is not None
                assert finished.capture_window_start is not None
                assert finished.capture_window_end is not None
                pre = (finished.triggered_at - finished.capture_window_start).total_seconds()
                post = (finished.capture_window_end - finished.triggered_at).total_seconds()
                self.assertEqual(pre, 60)
                self.assertEqual(post, 5)
            self.assertTrue(buffer.running)
            buffer._process.terminate.assert_not_called()

    def test_snapshot_has_no_gap_when_post_starts_before_ring_copy(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            ring = root / "ring"
            capture_dir = root / "captures"
            ring.mkdir()
            capture_dir.mkdir()
            buffer = RollingBuffer(ring_dir=ring, interface="eth1", enabled=True)
            buffer._process = MagicMock()
            buffer._process.poll.return_value = None
            t0 = 1_700_000_000
            # Packet at trigger (t=4) sits in the active segment that would be
            # torn if copied with shutil.copy2 while tcpdump still writes.
            live = ring / "buffer-live.pcap"
            write_minimal_pcap(
                live,
                [(t0, 0, payload(21)), (t0 + 2, 0, payload(22)), (t0 + 4, 0, payload(23))],
            )
            live.write_bytes(live.read_bytes()[:-3])

            def on_communicate():
                write_minimal_pcap(
                    live,
                    [
                        (t0, 0, payload(21)),
                        (t0 + 2, 0, payload(22)),
                        (t0 + 4, 0, payload(23)),
                        (t0 + 6, 0, payload(80)),
                    ],
                )
                snaps = [path for path in capture_dir.iterdir() if path.is_dir() and path.name.startswith("snap-")]
                write_minimal_pcap(
                    snaps[0] / "post.pcap",
                    [(t0 + 4, 0, payload(23)), (t0 + 6, 0, payload(80))],
                )

            with patch("app.captures.CAPTURE_DIR", capture_dir), patch(
                "app.captures.tcpdump_argv", return_value=["true"]
            ), patch("app.captures.subprocess.Popen", side_effect=lambda *a, **k: FakeProc(on_communicate)), patch(
                "app.pcaputil.extract_hosts", return_value=False
            ), patch("app.pcaputil._which", return_value=None):
                store = CaptureStore(rolling=buffer)
                job = store.start_snapshot(
                    interface="eth1",
                    source_ip=None,
                    destination_ip=None,
                    post_trigger_seconds=5,
                    pre_trigger_seconds=20,
                )
                finished = wait_done(store, job.id)
                self.assertEqual(finished.status, "COMPLETED", finished.error_message)
                frames = [pkt[2] for pkt in read_packets(finished.file_path)[1]]
                self.assertIn(payload(21), frames)
                self.assertIn(payload(22), frames)
                self.assertIn(payload(23), frames)
                self.assertIn(payload(80), frames)


if __name__ == "__main__":
    unittest.main()
