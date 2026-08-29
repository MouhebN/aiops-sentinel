from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from app.pcaputil import MAGIC_LE, copy_complete_pcap, count_packets, dedupe_pcap, merge_pcap_files, read_packets, write_minimal_pcap


class PcapUtilTests(unittest.TestCase):
    def test_merge_sorts_packets_and_keeps_single_header(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            first = root / "a.pcap"
            second = root / "b.pcap"
            merged = root / "merged.pcap"
            write_minimal_pcap(first, [(10, 0, b"AAAA"), (30, 0, b"CCCC")])
            write_minimal_pcap(second, [(20, 0, b"BBBB")])
            count = merge_pcap_files([first, second], merged)
            self.assertEqual(count, 3)
            data = merged.read_bytes()
            self.assertEqual(data[:4], MAGIC_LE)
            self.assertEqual(data.count(MAGIC_LE), 1)
            _header, packets = read_packets(merged)
            self.assertEqual([pkt[0] for pkt in packets], [10, 20, 30])
            self.assertEqual([pkt[2] for pkt in packets], [b"AAAA", b"BBBB", b"CCCC"])
            self.assertNotEqual(data, first.read_bytes() + second.read_bytes())

    def test_truncated_last_packet_is_skipped(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "trunc.pcap"
            write_minimal_pcap(path, [(1, 0, b"0123456789")])
            path.write_bytes(path.read_bytes()[:-3])
            _header, packets = read_packets(path)
            self.assertEqual(packets, [])

    def test_copy_complete_pcap_keeps_full_records_drops_torn_tail(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "live.pcap"
            dest = Path(tmp) / "snap.pcap"
            write_minimal_pcap(source, [(1, 0, b"keep-me!!!!"), (2, 0, b"torn-payload")])
            source.write_bytes(source.read_bytes()[:-5])
            copied = copy_complete_pcap(source, dest)
            self.assertEqual(copied, 1)
            _header, packets = read_packets(dest)
            self.assertEqual([pkt[2] for pkt in packets], [b"keep-me!!!!"])

    def test_merge_dedupes_overlapping_ring_and_post(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            pre = root / "pre.pcap"
            post = root / "post.pcap"
            merged = root / "merged.pcap"
            shared = (20, 0, b"port0443")
            write_minimal_pcap(pre, [(10, 0, b"port0021"), shared])
            write_minimal_pcap(post, [shared, (30, 0, b"port0080")])
            count = merge_pcap_files([pre, post], merged, dedupe=True)
            self.assertEqual(count, 3)
            _header, packets = read_packets(merged)
            self.assertEqual([pkt[2] for pkt in packets], [b"port0021", b"port0443", b"port0080"])

    def test_dedupe_pcap_keeps_first_copy(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "dup.pcap"
            write_minimal_pcap(path, [(1, 0, b"AAAA"), (1, 0, b"AAAA"), (2, 0, b"BBBB")])
            self.assertEqual(dedupe_pcap(path), 2)
            _header, packets = read_packets(path)
            self.assertEqual([pkt[2] for pkt in packets], [b"AAAA", b"BBBB"])

    def test_count_packets(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "one.pcap"
            write_minimal_pcap(path, [(1, 0, b"x" * 10), (2, 0, b"y" * 10)])
            self.assertEqual(count_packets(path), 2)
