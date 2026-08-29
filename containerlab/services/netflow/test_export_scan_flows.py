#!/usr/bin/env python3
"""Unit tests for the lab NetFlow exporter slot flush."""

from __future__ import annotations

import importlib.util
import io
import struct
import sys
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

EXPORTER_PATH = Path(__file__).resolve().parent / "export-scan-flows.py"


def load_exporter():
    spec = importlib.util.spec_from_file_location("export_scan_flows", EXPORTER_PATH)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


exporter = load_exporter()


class NfcapdSlotFlushTests(unittest.TestCase):
    def test_flush_payload_is_not_netflow_v5(self) -> None:
        payload = exporter.NFCAPD_SLOT_FLUSH
        self.assertTrue(payload)
        self.assertLess(len(payload), 24)
        if len(payload) >= 2:
            self.assertNotEqual(struct.unpack("!H", payload[:2])[0], 5)

    def test_flush_sends_tiny_datagram_to_collector(self) -> None:
        sock = MagicMock()
        with patch.object(exporter.socket, "socket", return_value=sock) as socket_cls:
            sock.__enter__.return_value = sock
            ok = exporter.send_nfcapd_slot_flush("172.30.30.2")
        self.assertTrue(ok)
        socket_cls.assert_called_once_with(exporter.socket.AF_INET, exporter.socket.SOCK_DGRAM)
        sock.sendto.assert_called_once_with(
            exporter.NFCAPD_SLOT_FLUSH,
            ("172.30.30.2", exporter.COLLECTOR_PORT),
        )

    def test_flush_failure_is_warning_not_exception(self) -> None:
        sock = MagicMock()
        sock.__enter__.return_value = sock
        sock.sendto.side_effect = OSError("network unreachable")
        stderr = io.StringIO()
        with patch.object(exporter.socket, "socket", return_value=sock):
            with patch.object(sys, "stdout", stderr):
                ok = exporter.send_nfcapd_slot_flush("172.30.30.2")
        self.assertFalse(ok)
        self.assertIn("warning: nfcapd flush datagram failed", stderr.getvalue())
        self.assertIn("172.30.30.2", stderr.getvalue())

    def test_real_v5_packet_is_unchanged_seven_records(self) -> None:
        boot = 1000.0
        flows = []
        for index, port in enumerate([21, 22, 23, 80, 443, 3306, 5432]):
            first = boot + 1 + index
            flows.append(
                {
                    "src": "10.0.0.10",
                    "dst": "10.10.10.20",
                    "spt": 40000 + index,
                    "dpt": port,
                    "proto": "TCP",
                    "packets": 1,
                    "bytes": 64,
                    "first": first,
                    "last": first,
                }
            )
        with patch.object(exporter.time, "time", return_value=1787470000.5):
            with patch.object(exporter.time, "monotonic", return_value=boot + 10):
                packet = exporter.build_packet(flows, sequence=7, boot=boot)
        self.assertEqual(len(packet), 360)
        version, count, _uptime, unix_secs, _nsecs, sequence = struct.unpack("!HHIIII", packet[:20])
        self.assertEqual(version, 5)
        self.assertEqual(count, 7)
        self.assertEqual(sequence, 7)
        self.assertEqual(unix_secs, 1787470000)
        self.assertEqual(packet[:2], b"\x00\x05")
        self.assertNotEqual(packet, exporter.NFCAPD_SLOT_FLUSH)


if __name__ == "__main__":
    unittest.main()
