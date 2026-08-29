import os
import socket
import struct
import time


DESTINATION_HOST = os.environ.get("NETFLOW_EXPORT_HOST", "netflow-tools")
DESTINATION_PORT = int(os.environ.get("NETFLOW_EXPORT_PORT", "2055"))
SOURCE_IP = "192.168.0.15"
DESTINATION_IP = "192.168.0.1"
DESTINATION_PORTS = [21, 22, 23, 80, 443, 3306, 5432]


def ip_to_int(ip_address: str) -> int:
    return struct.unpack("!I", socket.inet_aton(ip_address))[0]


def build_record(index: int, sys_uptime_ms: int) -> bytes:
    packets = 6 + index * 3
    octets = 900 + index * 275
    first_ms = max(0, sys_uptime_ms - 15_000 - index * 1_000)
    last_ms = max(first_ms + 250, sys_uptime_ms - 1_000 - index * 150)

    return struct.pack(
        "!IIIHHIIIIHHBBBBHHBBH",
        ip_to_int(SOURCE_IP),
        ip_to_int(DESTINATION_IP),
        0,
        1,
        2,
        packets,
        octets,
        first_ms,
        last_ms,
        40_000 + index,
        DESTINATION_PORTS[index],
        0,
        0x02,
        6,
        0,
        0,
        0,
        24,
        24,
        0,
    )


def build_packet(sequence: int) -> bytes:
    now = time.time()
    unix_seconds = int(now)
    unix_nanos = int((now - unix_seconds) * 1_000_000_000)
    sys_uptime_ms = 180_000

    header = struct.pack(
        "!HHIIIIBBH",
        5,
        len(DESTINATION_PORTS),
        sys_uptime_ms,
        unix_seconds,
        unix_nanos,
        sequence,
        0,
        0,
        0,
    )
    records = b"".join(build_record(index, sys_uptime_ms) for index in range(len(DESTINATION_PORTS)))
    return header + records


def main() -> None:
    address = (DESTINATION_HOST, DESTINATION_PORT)
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
        for sequence in range(3):
            packet = build_packet(sequence * len(DESTINATION_PORTS))
            sock.sendto(packet, address)
            print(
                f"Sent NetFlow v5 packet {sequence + 1}/3 with "
                f"{len(DESTINATION_PORTS)} port-scan records to {DESTINATION_HOST}:{DESTINATION_PORT}"
            )
            time.sleep(1)


if __name__ == "__main__":
    main()
