from __future__ import annotations

import argparse
import random
import socket
import time


DEFAULT_TARGET_HOST = "127.0.0.1"
DEFAULT_TARGET_PORT = 5514

MESSAGES = [
    "<134>Jul 31 20:10:01 bank-fw-01 firewall: port scan detected source_ip=198.51.100.22 target_zone=dmz ports=22|80|443|8443 action=monitored",
    "<131>Jul 31 20:10:05 bank-fw-01 firewall: suspicious malicious IP blocked source_ip=192.0.2.99 target=internet-banking-gateway action=blocked rule=banking-threat-feed",
    "<132>Jul 31 20:10:12 bank-fw-01 vpn: failed login attempts source_ip=203.0.113.45 account=network-ops attempts=12 window_minutes=5",
    "<132>Jul 31 20:11:20 branch-switch-01 switch: interface down interface=Gi0/12 vlan=40 device_type=SWITCH location=Branch-01",
    "<131>Jul 31 20:12:33 branch-ups-01 ups: battery low batteryPercent=14 runtimeMinutes=6 loadPercent=71 device_type=UPS location=Branch-01",
    "<134>Jul 31 20:13:21 branch-linux-01 sshd: authentication failed user=admin source_ip=203.0.113.88 attempts=5 device_type=SERVER location=Branch-01",
    "<133>Jul 31 20:14:03 camera-entrance-01 rtsp: rtsp stream down stream=main reason=timeout device_type=IP_CAMERA location=Branch-01",
]


def run_sender(target_host: str, target_port: int, count: int, interval: float) -> None:
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    for index in range(count):
        message = random.choice(MESSAGES)
        sock.sendto(message.encode("utf-8"), (target_host, target_port))
        print(f"sent {index + 1}/{count}: {message}")
        if index < count - 1:
            time.sleep(interval)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Send test syslog messages to AIOps Sentinel syslog collector.")
    parser.add_argument("--target-host", default=DEFAULT_TARGET_HOST)
    parser.add_argument("--target-port", type=int, default=DEFAULT_TARGET_PORT)
    parser.add_argument("--count", type=int, default=5)
    parser.add_argument("--interval", type=float, default=1.0)
    return parser.parse_args()


if __name__ == "__main__":
    args = parse_args()
    run_sender(args.target_host, args.target_port, args.count, args.interval)
