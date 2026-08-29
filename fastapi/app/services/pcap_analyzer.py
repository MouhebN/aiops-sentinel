from __future__ import annotations

import logging
import shutil
import subprocess
import tempfile
from collections import Counter, defaultdict
from pathlib import Path

from fastapi import HTTPException, UploadFile

from app.schemas import PacketCaptureAnalysisResponse

logger = logging.getLogger(__name__)

SCAN_WINDOW_SECONDS = 120
MIN_SCAN_PORTS = 3
STATS_FIELD_COUNT = 13
SYN_FIELD_COUNT = 4
TCP_SYN_BIT = 0x02
TCP_ACK_BIT = 0x10
# tshark display filter: initial TCP handshake only. SYN-ACK (syn=1,ack=1) is excluded.
TCP_INITIAL_SYN_FILTER = "tcp.flags.syn==1 && tcp.flags.ack==0"


def analyze_pcap(file: UploadFile) -> PacketCaptureAnalysisResponse:
    tshark_path = shutil.which("tshark")
    if not tshark_path:
        raise HTTPException(status_code=503, detail="tshark is not installed. Please install Wireshark CLI tools.")

    suffix = Path(file.filename or "capture.pcap").suffix or ".pcap"
    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as temp_file:
        temp_path = Path(temp_file.name)
        temp_file.write(file.file.read())

    try:
        rows = run_tshark_rows(tshark_path, temp_path)
        syn_rows = run_tshark_initial_syn_rows(tshark_path, temp_path)
        return summarize_rows(rows, syn_rows=syn_rows)
    finally:
        try:
            temp_path.unlink(missing_ok=True)
        except Exception:
            pass


def run_tshark_rows(tshark_path: str, file_path: Path) -> list[dict[str, str]]:
    command = [
        tshark_path,
        "-r",
        str(file_path),
        "-T",
        "fields",
        "-E",
        "separator=\t",
        "-E",
        "quote=n",
        "-e",
        "frame.len",
        "-e",
        "ip.src",
        "-e",
        "ip.dst",
        "-e",
        "_ws.col.Protocol",
        "-e",
        "tcp.dstport",
        "-e",
        "udp.dstport",
        "-e",
        "dns.flags.rcode",
        "-e",
        "tcp.analysis.retransmission",
        "-e",
        "icmp.type",
        "-e",
        "tcp.flags",
        "-e",
        "tcp.flags.syn",
        "-e",
        "tcp.flags.ack",
        "-e",
        "frame.time_epoch",
    ]
    return parse_tshark_field_rows(run_tshark(command), STATS_FIELD_COUNT, stats_row_from_parts)


def run_tshark_initial_syn_rows(tshark_path: str, file_path: Path) -> list[dict[str, str]]:
    command = [
        tshark_path,
        "-r",
        str(file_path),
        "-Y",
        TCP_INITIAL_SYN_FILTER,
        "-T",
        "fields",
        "-E",
        "separator=\t",
        "-E",
        "quote=n",
        "-e",
        "ip.src",
        "-e",
        "ip.dst",
        "-e",
        "tcp.dstport",
        "-e",
        "frame.time_epoch",
    ]
    return parse_tshark_field_rows(run_tshark(command), SYN_FIELD_COUNT, syn_row_from_parts)


def run_tshark(command: list[str]) -> str:
    try:
        result = subprocess.run(command, capture_output=True, text=True, check=True, timeout=60)
    except subprocess.CalledProcessError as exception:
        detail = exception.stderr.strip() or exception.stdout.strip() or "tshark failed to analyze the packet capture."
        raise HTTPException(status_code=502, detail=detail)
    except subprocess.TimeoutExpired:
        raise HTTPException(status_code=504, detail="tshark analysis timed out.")
    return result.stdout


def parse_tshark_field_rows(
    stdout: str,
    field_count: int,
    builder,
) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    for line in stdout.splitlines():
        parts = line.split("\t")
        if len(parts) < field_count:
            parts.extend([""] * (field_count - len(parts)))
        rows.append(builder(parts))
    return rows


def stats_row_from_parts(parts: list[str]) -> dict[str, str]:
    return {
        "frame_len": parts[0],
        "ip_src": parts[1],
        "ip_dst": parts[2],
        "protocol": parts[3],
        "tcp_dstport": parts[4],
        "udp_dstport": parts[5],
        "dns_rcode": parts[6],
        "tcp_retransmission": parts[7],
        "icmp_type": parts[8],
        "tcp_flags": parts[9],
        "tcp_syn": parts[10],
        "tcp_ack": parts[11],
        "frame_time": parts[12],
    }


def syn_row_from_parts(parts: list[str]) -> dict[str, str]:
    return {
        "ip_src": parts[0],
        "ip_dst": parts[1],
        "tcp_dstport": parts[2],
        "frame_time": parts[3],
        "tcp_syn": "True",
        "tcp_ack": "False",
    }


def summarize_rows(
    rows: list[dict[str, str]],
    syn_rows: list[dict[str, str]] | None = None,
) -> PacketCaptureAnalysisResponse:
    total_packets = len(rows)
    total_bytes = 0
    source_ips: Counter[str] = Counter()
    destination_ips: Counter[str] = Counter()
    protocols: Counter[str] = Counter()
    destination_ports: Counter[str] = Counter()
    icmp_packets = 0
    dns_errors = 0
    retransmissions = 0

    for row in rows:
        frame_len = parse_int(row.get("frame_len", ""))
        if frame_len is not None:
            total_bytes += frame_len

        ip_src = first_field(row.get("ip_src", ""))
        ip_dst = first_field(row.get("ip_dst", ""))
        protocol = first_field(row.get("protocol", "")) or "UNKNOWN"
        dst_port = first_field(row.get("tcp_dstport", "")) or first_field(row.get("udp_dstport", ""))

        if ip_src:
            source_ips[ip_src] += 1
        if ip_dst:
            destination_ips[ip_dst] += 1
        if protocol:
            protocols[protocol] += 1
        if dst_port:
            destination_ports[dst_port] += 1
        if first_field(row.get("icmp_type", "")):
            icmp_packets += 1
        dns_rcode = first_field(row.get("dns_rcode", ""))
        if dns_rcode and dns_rcode != "0":
            dns_errors += 1
        if first_field(row.get("tcp_retransmission", "")):
            retransmissions += 1

    scan_ports = tcp_syn_scan_candidate_sets(syn_rows if syn_rows is not None else rows)
    logger.info("TCP SYN scan candidate sets: %s", format_candidate_sets(scan_ports))

    suspicious_findings: list[str] = []
    for (src, dst), ports in sorted(scan_ports.items()):
        suspicious_findings.append(
            f"Possible port scan or reconnaissance from {src} to {dst} on ports {', '.join(sort_ports(ports))}"
        )
    if icmp_packets >= 20:
        suspicious_findings.append("High ICMP activity")
    if dns_errors > 0:
        suspicious_findings.append("DNS resolution errors observed")
    if retransmissions > 0:
        suspicious_findings.append("TCP retransmissions observed")

    top_source_ips = format_counter(source_ips)
    top_destination_ips = format_counter(destination_ips)
    top_protocols = format_counter(protocols)
    top_destination_ports = format_counter(destination_ports)

    summary_parts = [
        f"Analyzed {total_packets} packets",
        f"capturing approximately {total_bytes} bytes",
    ]
    if top_protocols:
        summary_parts.append(f"top protocols: {', '.join(top_protocols[:3])}")
    if suspicious_findings:
        summary_parts.append(f"findings: {'; '.join(suspicious_findings[:3])}")

    return PacketCaptureAnalysisResponse(
        totalPackets=total_packets,
        totalBytes=total_bytes,
        topSourceIps=top_source_ips,
        topDestinationIps=top_destination_ips,
        topProtocols=top_protocols,
        topDestinationPorts=top_destination_ports,
        suspiciousFindings=suspicious_findings,
        summary=". ".join(summary_parts) + ".",
    )


def tcp_syn_scan_candidate_sets(rows: list[dict[str, str]]) -> dict[tuple[str, str], set[str]]:
    attempts: list[tuple[str, str, str, float | None]] = []
    for row in rows:
        if not is_initial_tcp_syn(row):
            continue
        ip_src = first_field(row.get("ip_src", ""))
        ip_dst = first_field(row.get("ip_dst", ""))
        tcp_dst_port = first_field(row.get("tcp_dstport", ""))
        if not (ip_src and ip_dst and tcp_dst_port):
            continue
        attempts.append((ip_src, ip_dst, tcp_dst_port, parse_float(row.get("frame_time", ""))))
    return group_scan_ports(attempts)


def format_candidate_sets(scan_ports: dict[tuple[str, str], set[str]]) -> str:
    if not scan_ports:
        return "{}"
    parts = [
        f"{src} -> {dst} {{{','.join(sort_ports(ports))}}}"
        for (src, dst), ports in sorted(scan_ports.items())
    ]
    return "; ".join(parts)


def group_scan_ports(
    attempts: list[tuple[str, str, str, float | None]],
) -> dict[tuple[str, str], set[str]]:
    grouped: dict[tuple[str, str], list[tuple[str, float | None]]] = defaultdict(list)
    for src, dst, port, epoch in attempts:
        grouped[(src, dst)].append((port, epoch))

    scans: dict[tuple[str, str], set[str]] = {}
    for pair, items in grouped.items():
        ports = ports_in_busiest_window(items)
        if len(ports) >= MIN_SCAN_PORTS:
            scans[pair] = ports
    return scans


def ports_in_busiest_window(items: list[tuple[str, float | None]]) -> set[str]:
    if any(epoch is None for _, epoch in items):
        return {port for port, _ in items}

    ordered = sorted(items, key=lambda item: item[1] or 0.0)
    best: set[str] = set()
    left = 0
    for right, (_port, epoch) in enumerate(ordered):
        while (epoch or 0.0) - (ordered[left][1] or 0.0) > SCAN_WINDOW_SECONDS:
            left += 1
        window_ports = {ordered[index][0] for index in range(left, right + 1)}
        if len(window_ports) > len(best):
            best = window_ports
    return best


def is_initial_tcp_syn(row: dict[str, str]) -> bool:
    flags = parse_tcp_flags_mask(row.get("tcp_flags"))
    if flags is not None:
        return bool(flags & TCP_SYN_BIT) and not bool(flags & TCP_ACK_BIT)
    return is_flag_set(row.get("tcp_syn")) and is_flag_unset(row.get("tcp_ack"))


def parse_tcp_flags_mask(value: str | None) -> int | None:
    token = first_field(value).lower()
    if not token:
        return None
    if token.startswith("0x"):
        try:
            return int(token, 16)
        except ValueError:
            return None
    return parse_int(token)


def is_flag_set(value: str | None) -> bool:
    token = first_field(value).lower()
    return token in {"1", "true", "set", "yes"}


def is_flag_unset(value: str | None) -> bool:
    token = first_field(value).lower()
    return token in {"", "0", "false", "not set", "unset", "no"}


def first_field(value: str | None) -> str:
    return (value or "").strip().split(",")[0].strip()


def sort_ports(ports: set[str]) -> list[str]:
    def sort_key(port: str) -> tuple[bool, int, str]:
        parsed = parse_int(port)
        return (parsed is None, parsed if parsed is not None else 0, port)

    return sorted(ports, key=sort_key)


def format_counter(counter: Counter[str], limit: int = 5) -> list[str]:
    return [f"{value} ({count})" for value, count in counter.most_common(limit) if value]


def parse_int(value: str | None) -> int | None:
    try:
        return int(value) if value is not None and str(value).strip() != "" else None
    except (TypeError, ValueError):
        return None


def parse_float(value: str | None) -> float | None:
    try:
        return float(value) if value is not None and str(value).strip() != "" else None
    except (TypeError, ValueError):
        return None
