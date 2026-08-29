"""Read/write/merge libpcap files. Never concatenates dump files as raw bytes."""

from __future__ import annotations

import struct
import subprocess
from pathlib import Path

from app.bpf import filter_argv
from app.settings import TCPDUMP_BIN

MAGIC_LE = b"\xd4\xc3\xb2\xa1"
MAGIC_BE = b"\xa1\xb2\xc3\xd4"
MAGIC_NS_LE = b"\x4d\x3c\xb2\xa1"
MAGIC_NS_BE = b"\xa1\xb2\x3c\x4d"
GLOBAL_HEADER_LEN = 24
PACKET_HEADER_LEN = 16


class PcapFormatError(ValueError):
    pass


def _endian(magic: bytes) -> str:
    if magic in {MAGIC_LE, MAGIC_NS_LE}:
        return "<"
    if magic in {MAGIC_BE, MAGIC_NS_BE}:
        return ">"
    raise PcapFormatError("not a libpcap file")


def write_minimal_pcap(path: Path, packets: list[tuple[int, int, bytes]]) -> None:
    """Write a little-endian Ethernet libpcap with (ts_sec, ts_usec, data) packets."""
    header = MAGIC_LE + struct.pack("<HHIIII", 2, 4, 0, 0, 65535, 1)
    body = bytearray()
    for ts_sec, ts_usec, data in packets:
        incl = len(data)
        body.extend(struct.pack("<IIII", ts_sec, ts_usec, incl, incl))
        body.extend(data)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(header + body)


def read_packets(path: Path) -> tuple[bytes, list[tuple[int, int, bytes]]]:
    data = path.read_bytes()
    if len(data) < GLOBAL_HEADER_LEN:
        raise PcapFormatError("pcap too small")
    magic = data[:4]
    endian = _endian(magic)
    header = data[:GLOBAL_HEADER_LEN]
    packets: list[tuple[int, int, bytes]] = []
    offset = GLOBAL_HEADER_LEN
    unpack = struct.Struct(endian + "IIII").unpack_from
    while offset + PACKET_HEADER_LEN <= len(data):
        ts_sec, ts_usec, incl_len, _orig_len = unpack(data, offset)
        frame_start = offset + PACKET_HEADER_LEN
        frame_end = frame_start + incl_len
        if incl_len < 0 or frame_end > len(data):
            break
        packets.append((ts_sec, ts_usec, data[frame_start:frame_end]))
        offset = frame_end
    return header, packets


def write_pcap(path: Path, header: bytes, packets: list[tuple[int, int, bytes]]) -> None:
    """Rewrite a libpcap using an existing global header and complete packet records."""
    if len(header) < GLOBAL_HEADER_LEN:
        raise PcapFormatError("pcap header too small")
    endian = _endian(header[:4])
    pack = struct.Struct(endian + "IIII").pack
    body = bytearray()
    for ts_sec, ts_usec, frame in packets:
        incl = len(frame)
        body.extend(pack(ts_sec, ts_usec, incl, incl))
        body.extend(frame)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(header[:GLOBAL_HEADER_LEN] + body)


def copy_complete_pcap(source: Path, destination: Path) -> int:
    """Copy a pcap, dropping a truncated trailing record. Safe for a live tcpdump file."""
    if not source.exists() or source.stat().st_size < GLOBAL_HEADER_LEN:
        return 0
    try:
        header, packets = read_packets(source)
    except (OSError, PcapFormatError):
        return 0
    write_pcap(destination, header, packets)
    return len(packets)


def merge_pcap_files(paths: list[Path], destination: Path, *, dedupe: bool = False) -> int:
    """Merge PCAPs by packet timestamp. Uses mergecap when present, else a libpcap merge."""
    existing = [path for path in paths if path.exists() and path.stat().st_size >= GLOBAL_HEADER_LEN]
    if not existing:
        raise PcapFormatError("no pcap input files to merge")
    destination.parent.mkdir(parents=True, exist_ok=True)
    mergecap = _which("mergecap")
    merged_ok = False
    if mergecap is not None:
        result = subprocess.run(
            [mergecap, "-F", "pcap", "-w", str(destination), *[str(path) for path in existing]],
            check=False,
            capture_output=True,
            timeout=30,
        )
        merged_ok = result.returncode == 0 and destination.exists() and destination.stat().st_size >= GLOBAL_HEADER_LEN
    if not merged_ok:
        header = None
        merged: list[tuple[int, int, bytes]] = []
        for path in existing:
            try:
                file_header, packets = read_packets(path)
            except PcapFormatError:
                continue
            if header is None:
                header = file_header
            merged.extend(packets)
        if header is None:
            raise PcapFormatError("no readable pcap records")
        merged.sort(key=lambda item: (item[0], item[1]))
        write_pcap(destination, header, merged)
    if dedupe:
        return dedupe_pcap(destination)
    return count_packets(destination)


def dedupe_pcap(path: Path) -> int:
    """Keep the first copy of each (timestamp, payload). Overlapping ring/post files are expected."""
    header, packets = read_packets(path)
    unique: list[tuple[int, int, bytes]] = []
    seen: set[tuple[int, int, bytes]] = set()
    for packet in packets:
        if packet in seen:
            continue
        seen.add(packet)
        unique.append(packet)
    write_pcap(path, header, unique)
    return len(unique)


def extract_hosts(source: Path, destination: Path, source_ip: str | None, destination_ip: str | None) -> bool:
    """Filter a PCAP with tcpdump argv tokens. Returns True if the filtered file has packets."""
    argv = [TCPDUMP_BIN, "-n", "-r", str(source), "-w", str(destination), *filter_argv(source_ip, destination_ip)]
    result = subprocess.run(argv, check=False, capture_output=True, timeout=30)
    if result.returncode not in {0, 1}:
        return False
    return destination.exists() and destination.stat().st_size > GLOBAL_HEADER_LEN and count_packets(destination) > 0


def count_packets(path: Path) -> int:
    try:
        _header, packets = read_packets(path)
        return len(packets)
    except (OSError, PcapFormatError):
        return 0


def _which(name: str) -> str | None:
    import shutil

    return shutil.which(name)
