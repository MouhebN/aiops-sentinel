from __future__ import annotations

import shutil
import struct
import tempfile
import unittest
from pathlib import Path

from app.services.pcap_analyzer import (
    TCP_INITIAL_SYN_FILTER,
    format_candidate_sets,
    is_initial_tcp_syn,
    run_tshark_initial_syn_rows,
    run_tshark_rows,
    summarize_rows,
    tcp_syn_scan_candidate_sets,
)

SCANNER = "192.168.0.15"
TARGET = "192.168.0.1"
SERVICE_PORTS = ["21", "22", "23", "80", "443", "3306", "5432"]
EPHEMERAL_PORTS = ["36724", "41978", "48474", "48686", "49328", "49430", "57036"]


def packet(**overrides: str) -> dict[str, str]:
    row = {
        "frame_len": "60",
        "ip_src": "",
        "ip_dst": "",
        "protocol": "TCP",
        "tcp_dstport": "",
        "udp_dstport": "",
        "dns_rcode": "",
        "tcp_retransmission": "",
        "icmp_type": "",
        "tcp_flags": "",
        "tcp_syn": "",
        "tcp_ack": "",
        "frame_time": "",
    }
    row.update(overrides)
    return row


def syn(src: str, dst: str, port: str, epoch: str) -> dict[str, str]:
    return packet(
        ip_src=src,
        ip_dst=dst,
        tcp_dstport=port,
        tcp_flags="0x0002",
        tcp_syn="True",
        tcp_ack="False",
        frame_time=epoch,
    )


def syn_ack(src: str, dst: str, port: str, epoch: str) -> dict[str, str]:
    return packet(
        ip_src=src,
        ip_dst=dst,
        tcp_dstport=port,
        tcp_flags="0x0012",
        tcp_syn="True",
        tcp_ack="True",
        frame_time=epoch,
    )


def ack(src: str, dst: str, port: str, epoch: str) -> dict[str, str]:
    return packet(
        ip_src=src,
        ip_dst=dst,
        tcp_dstport=port,
        tcp_flags="0x0010",
        tcp_syn="False",
        tcp_ack="True",
        frame_time=epoch,
    )


def lab_scan_rows() -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    for index, port in enumerate(SERVICE_PORTS):
        rows.append(syn(SCANNER, TARGET, port, str(1_700_000_000 + index)))
        rows.append(syn_ack(TARGET, SCANNER, EPHEMERAL_PORTS[index], str(1_700_000_000 + index + 0.05)))
        rows.append(ack(SCANNER, TARGET, port, str(1_700_000_000 + index + 0.1)))
        rows.append(ack(TARGET, SCANNER, EPHEMERAL_PORTS[index], str(1_700_000_000 + index + 0.15)))
    return rows


class PcapPortScanDetectionTests(unittest.TestCase):
    def test_tshark_true_false_flags_match_initial_syn_only(self) -> None:
        self.assertTrue(is_initial_tcp_syn(syn(SCANNER, TARGET, "22", "1")))
        self.assertFalse(is_initial_tcp_syn(syn_ack(TARGET, SCANNER, "36724", "2")))
        self.assertFalse(is_initial_tcp_syn(ack(TARGET, SCANNER, "36724", "3")))
        self.assertFalse(is_initial_tcp_syn(packet(tcp_flags="0x0012", tcp_syn="1", tcp_ack="1")))
        self.assertTrue(is_initial_tcp_syn(packet(tcp_flags="0x0002", tcp_syn="1", tcp_ack="0")))

    def test_multi_port_syn_scan_is_detected(self) -> None:
        rows = [syn("10.0.0.8", "10.0.0.1", port, str(1_700_000_000 + index)) for index, port in enumerate(["22", "80", "443"])]
        findings = summarize_rows(rows).suspicious_findings
        self.assertEqual(
            findings,
            ["Possible port scan or reconnaissance from 10.0.0.8 to 10.0.0.1 on ports 22, 80, 443"],
        )

    def test_syn_ack_reply_traffic_does_not_create_reverse_scan(self) -> None:
        rows = lab_scan_rows()
        candidates = tcp_syn_scan_candidate_sets(rows)
        self.assertEqual(set(candidates), {(SCANNER, TARGET)})
        self.assertEqual(candidates[(SCANNER, TARGET)], set(SERVICE_PORTS))
        self.assertNotIn((TARGET, SCANNER), candidates)
        findings = summarize_rows(rows).suspicious_findings
        self.assertEqual(len(findings), 1)
        self.assertNotIn(f"from {TARGET} to {SCANNER}", findings[0])
        for port in EPHEMERAL_PORTS:
            self.assertNotIn(port, findings[0])

    def test_normal_tcp_response_traffic_is_ignored(self) -> None:
        rows = [
            ack(SCANNER, TARGET, "443", "1700000000"),
            ack(TARGET, SCANNER, "50001", "1700000001"),
            ack(SCANNER, TARGET, "443", "1700000002"),
            ack(TARGET, SCANNER, "50002", "1700000003"),
            ack(SCANNER, TARGET, "443", "1700000004"),
            ack(TARGET, SCANNER, "50003", "1700000005"),
        ]
        self.assertEqual(tcp_syn_scan_candidate_sets(rows), {})
        self.assertEqual(summarize_rows(rows).suspicious_findings, [])

    def test_lab_scan_candidate_set_excludes_ephemeral_reverse_ports(self) -> None:
        rows = lab_scan_rows()
        candidates = tcp_syn_scan_candidate_sets(rows)
        self.assertEqual(
            format_candidate_sets(candidates),
            "192.168.0.15 -> 192.168.0.1 {21,22,23,80,443,3306,5432}",
        )
        self.assertNotIn((TARGET, SCANNER), candidates)
        result = summarize_rows(rows)
        self.assertEqual(
            result.suspicious_findings,
            [
                "Possible port scan or reconnaissance from 192.168.0.15 to 192.168.0.1 "
                "on ports 21, 22, 23, 80, 443, 3306, 5432"
            ],
        )
        self.assertEqual(result.total_packets, 28)

    def test_retransmissions_are_reported_independently_of_scans(self) -> None:
        rows = [
            syn(SCANNER, TARGET, "22", "1700000000"),
            packet(
                ip_src=SCANNER,
                ip_dst=TARGET,
                tcp_dstport="22",
                tcp_flags="0x0002",
                tcp_syn="True",
                tcp_ack="False",
                tcp_retransmission="1",
                frame_time="1700000001",
            ),
            packet(ip_src=SCANNER, ip_dst=TARGET, protocol="DNS", udp_dstport="53", frame_time="1700000002"),
        ]
        result = summarize_rows(rows)
        self.assertEqual(tcp_syn_scan_candidate_sets(rows), {})
        self.assertEqual(result.suspicious_findings, ["TCP retransmissions observed"])
        self.assertTrue(any("TCP" in item for item in result.top_protocols))
        self.assertEqual(result.total_packets, 3)


class TsharkInitialSynFilterTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.tshark = shutil.which("tshark")
        if not cls.tshark:
            raise unittest.SkipTest("tshark is not installed")

    def test_real_tshark_filter_keeps_only_forward_syn_ports(self) -> None:
        pcap = write_lab_scan_pcap()
        try:
            syn_rows = run_tshark_initial_syn_rows(self.tshark, pcap)
            stats_rows = run_tshark_rows(self.tshark, pcap)
            candidates = tcp_syn_scan_candidate_sets(syn_rows)
            self.assertEqual(
                format_candidate_sets(candidates),
                "192.168.0.15 -> 192.168.0.1 {21,22,23,80,443,3306,5432}",
            )
            self.assertNotIn((TARGET, SCANNER), candidates)
            result = summarize_rows(stats_rows, syn_rows=syn_rows)
            scan_findings = [item for item in result.suspicious_findings if "port scan" in item]
            self.assertEqual(len(scan_findings), 1)
            self.assertIn("from 192.168.0.15 to 192.168.0.1", scan_findings[0])
            self.assertTrue(any("TCP retransmissions observed" in item for item in result.suspicious_findings))
            self.assertEqual(TCP_INITIAL_SYN_FILTER, "tcp.flags.syn==1 && tcp.flags.ack==0")
        finally:
            pcap.unlink(missing_ok=True)


def ip_to_bytes(address: str) -> bytes:
    return bytes(int(part) for part in address.split("."))


def checksum(data: bytes) -> int:
    if len(data) % 2:
        data += b"\x00"
    total = sum(struct.unpack(f"!{len(data) // 2}H", data))
    total = (total >> 16) + (total & 0xFFFF)
    total += total >> 16
    return ~total & 0xFFFF


def ethernet_ipv4_tcp(
    src: str,
    dst: str,
    sport: int,
    dport: int,
    flags: int,
    seq: int,
    ack_num: int = 0,
) -> bytes:
    ethernet = bytes.fromhex("0200000000020200000000010800")
    ip_header_wo_checksum = struct.pack(
        "!BBHHHBBH4s4s",
        0x45,
        0,
        40,
        1,
        0,
        64,
        6,
        0,
        ip_to_bytes(src),
        ip_to_bytes(dst),
    )
    ip_header = ip_header_wo_checksum[:10] + struct.pack("!H", checksum(ip_header_wo_checksum)) + ip_header_wo_checksum[12:]
    tcp_wo_checksum = struct.pack("!HHIIBBHHH", sport, dport, seq, ack_num, 0x50, flags, 64240, 0, 0)
    pseudo = struct.pack("!4s4sBBH", ip_to_bytes(src), ip_to_bytes(dst), 0, 6, 20) + tcp_wo_checksum
    tcp_header = tcp_wo_checksum[:16] + struct.pack("!H", checksum(pseudo)) + tcp_wo_checksum[18:]
    return ethernet + ip_header + tcp_header


def pcap_bytes(frames: list[bytes]) -> bytes:
    header = struct.pack("=IHHIIII", 0xA1B2C3D4, 2, 4, 0, 0, 65535, 1)
    records = b""
    for index, frame in enumerate(frames):
        records += struct.pack("=IIII", 1_700_000_000 + index, 0, len(frame), len(frame)) + frame
    return header + records


def write_lab_scan_pcap() -> Path:
    frames: list[bytes] = []
    for index, port in enumerate(SERVICE_PORTS):
        sport = int(EPHEMERAL_PORTS[index])
        dport = int(port)
        frames.append(ethernet_ipv4_tcp(SCANNER, TARGET, sport, dport, 0x02, 1))
        frames.append(ethernet_ipv4_tcp(SCANNER, TARGET, sport, dport, 0x02, 1))
        frames.append(ethernet_ipv4_tcp(TARGET, SCANNER, dport, sport, 0x12, 100, 2))
        frames.append(ethernet_ipv4_tcp(SCANNER, TARGET, sport, dport, 0x10, 2, 101))
        frames.append(ethernet_ipv4_tcp(TARGET, SCANNER, dport, sport, 0x10, 101, 2))
    handle = tempfile.NamedTemporaryFile(delete=False, suffix=".pcap")
    handle.write(pcap_bytes(frames))
    handle.close()
    return Path(handle.name)


if __name__ == "__main__":
    unittest.main()
