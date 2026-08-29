from __future__ import annotations

import argparse
import json
import re
import socket
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


DEFAULT_BACKEND_URL = "http://localhost:8080"
DEFAULT_LISTEN_HOST = "0.0.0.0"
DEFAULT_LISTEN_PORT = 5514

SYSLOG_PATTERN = re.compile(
    r"^(?:<(?P<priority>\d{1,3})>)?"
    r"(?:(?P<timestamp>[A-Z][a-z]{2}\s+\d{1,2}\s+\d{2}:\d{2}:\d{2})\s+)?"
    r"(?P<host>[^\s:]+)?\s*:?\s*(?P<message>.*)$"
)

KEY_VALUE_PATTERN = re.compile(r"\b([A-Za-z][A-Za-z0-9_-]*)=([^\s,;]+)")

FACILITY_NAMES = {
    0: "kernel",
    1: "user",
    2: "mail",
    3: "daemon",
    4: "auth",
    10: "authpriv",
    16: "local0",
    17: "local1",
    18: "local2",
    19: "local3",
    20: "local4",
    21: "local5",
    22: "local6",
    23: "local7",
}


@dataclass(frozen=True)
class NormalizedSyslogEvent:
    device_id: str
    device_name: str
    device_type: str
    location: str
    event_type: str
    severity: str
    message: str
    details: str
    occurred_at: str


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def normalize_events_url(backend_url: str) -> str:
    base_url = backend_url.rstrip("/")
    if base_url.endswith("/api/events"):
        return base_url
    return f"{base_url}/api/events"


def post_event(endpoint: str, event: NormalizedSyslogEvent) -> None:
    payload = {
        "deviceId": event.device_id,
        "deviceName": event.device_name,
        "deviceType": event.device_type,
        "location": event.location,
        "eventType": event.event_type,
        "severity": event.severity,
        "message": event.message,
        "details": event.details,
        "occurredAt": event.occurred_at,
    }
    request = Request(
        endpoint,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urlopen(request, timeout=5) as response:
        if response.status >= 400:
            raise RuntimeError(f"Backend returned HTTP {response.status}")


def api_get(url: str) -> Any:
    request = Request(url, method="GET")
    with urlopen(request, timeout=5) as response:
        return json.loads(response.read().decode("utf-8"))


def parse_priority(priority: str | None) -> tuple[int | None, int | None]:
    if priority is None:
        return None, None
    value = int(priority)
    return value // 8, value % 8


def parse_key_values(message: str) -> dict[str, str]:
    return {match.group(1).lower(): match.group(2) for match in KEY_VALUE_PATTERN.finditer(message)}


def infer_device_type(message: str, host: str, values: dict[str, str]) -> str:
    text = f"{host} {message}".lower()
    explicit = values.get("device_type") or values.get("type")
    if explicit:
        normalized = explicit.upper().replace("-", "_")
        if normalized in {"SERVER", "ROUTER", "SWITCH", "IP_CAMERA", "UPS", "FIREWALL", "APPLICATION", "DATABASE", "ATM", "OTHER"}:
            return normalized
    if "firewall" in text or "fortigate" in text or "pfsense" in text or "vpn" in text:
        return "FIREWALL"
    if "switch" in text or "interface" in text or "port" in text:
        return "SWITCH"
    if "camera" in text or "rtsp" in text:
        return "IP_CAMERA"
    if "ups" in text or "battery" in text:
        return "UPS"
    if "app" in text or "api" in text or "service" in text:
        return "APPLICATION"
    return "SERVER"


def infer_event_type(message: str, severity_number: int | None) -> str:
    text = message.lower()
    if "port scan" in text or "scan detected" in text:
        return "PORT_SCAN_DETECTED"
    if "blocked" in text and ("suspicious" in text or "malicious" in text or "threat" in text):
        return "SUSPICIOUS_IP_BLOCKED"
    if "failed login" in text or "authentication failed" in text or "invalid password" in text:
        return "FAILED_LOGIN_ATTEMPTS"
    if "vpn" in text and ("down" in text or "disconnected" in text):
        return "VPN_TUNNEL_DOWN"
    if "interface" in text and "down" in text:
        return "SWITCH_PORT_DOWN"
    if "link down" in text:
        return "SWITCH_PORT_DOWN"
    if "disk" in text and ("full" in text or "usage" in text):
        return "DISK_FULL"
    if "cpu" in text and ("high" in text or "usage" in text):
        return "CPU_HIGH"
    if "battery" in text and ("low" in text or "critical" in text):
        return "UPS_BATTERY_LOW"
    if "rtsp" in text and ("down" in text or "failed" in text):
        return "RTSP_STREAM_DOWN"
    if severity_number is not None and severity_number <= 3:
        return "SYSLOG_CRITICAL"
    if severity_number is not None and severity_number <= 4:
        return "SYSLOG_WARNING"
    return "SYSLOG_INFO"


def infer_severity(event_type: str, severity_number: int | None, message: str) -> str:
    text = message.lower()
    if event_type in {"SUSPICIOUS_IP_BLOCKED", "UPS_BATTERY_LOW", "SYSLOG_CRITICAL"}:
        return "CRITICAL"
    if severity_number is not None:
        if severity_number <= 3:
            return "CRITICAL"
        if severity_number <= 4:
            return "WARNING"
    if any(token in text for token in ["critical", "emergency", "alert", "blocked"]):
        return "CRITICAL"
    if any(token in text for token in ["warning", "failed", "down", "denied", "error"]):
        return "WARNING"
    return "INFO"


def normalize_syslog(
    raw_message: str,
    sender_ip: str,
    default_location: str,
    device_map: dict[str, dict[str, str]],
) -> NormalizedSyslogEvent:
    match = SYSLOG_PATTERN.match(raw_message.strip())
    groups = match.groupdict() if match else {}
    host = (groups.get("host") or sender_ip).strip()
    message = (groups.get("message") or raw_message).strip()
    priority = groups.get("priority")
    facility, severity_number = parse_priority(priority)
    values = parse_key_values(message)
    mapped_device = device_map.get(host) or device_map.get(sender_ip) or {}

    device_type = mapped_device.get("deviceType") or infer_device_type(message, host, values)
    event_type = infer_event_type(message, severity_number)
    severity = infer_severity(event_type, severity_number, message)
    device_name = mapped_device.get("deviceName") or values.get("device_name") or host
    location = mapped_device.get("location") or values.get("location") or default_location
    device_id = mapped_device.get("deviceId") or f"syslog-{host.replace('.', '-')}"

    facility_name = FACILITY_NAMES.get(facility, str(facility) if facility is not None else "unknown")
    details = (
        f"syslog_sender={sender_ip}, syslog_host={host}, facility={facility_name}, "
        f"severity_number={severity_number if severity_number is not None else 'unknown'}, "
        f"raw={raw_message.strip()}"
    )

    return NormalizedSyslogEvent(
        device_id=device_id,
        device_name=device_name,
        device_type=device_type,
        location=location,
        event_type=event_type,
        severity=severity,
        message=message[:1000],
        details=details[:4000],
        occurred_at=utc_now(),
    )


def load_device_map(path: str | None) -> dict[str, dict[str, str]]:
    if not path:
        return {}
    with open(path, "r", encoding="utf-8") as file:
        data = json.load(file)
    if isinstance(data, dict):
        return data
    raise ValueError("Device map must be a JSON object keyed by syslog host or sender IP")


def fetch_backend_device_map(backend_url: str) -> dict[str, dict[str, str]]:
    try:
        sources = api_get(f"{backend_url.rstrip('/')}/api/syslog-sources/enabled")
    except (HTTPError, URLError, TimeoutError, json.JSONDecodeError) as exc:
        print(f"Cannot fetch syslog source config from backend: {exc}")
        return {}

    device_map: dict[str, dict[str, str]] = {}
    for source in sources:
        expected_host = source.get("expectedHost")
        if not expected_host:
            continue
        device_map[expected_host] = {
            "deviceId": source["deviceId"],
            "deviceName": source["deviceName"],
            "deviceType": source["deviceType"],
            "location": source["location"],
            "parserProfile": source.get("parserProfile", "GENERIC"),
        }
    return device_map


def run_collector(
    backend_url: str,
    listen_host: str,
    listen_port: int,
    default_location: str,
    device_map_path: str | None,
    dry_run: bool,
) -> None:
    endpoint = normalize_events_url(backend_url)
    device_map = {
        **load_device_map(device_map_path),
        **fetch_backend_device_map(backend_url),
    }
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind((listen_host, listen_port))
    print(f"Syslog collector listening on udp://{listen_host}:{listen_port}")
    print(f"Forwarding normalized events to {endpoint}")

    while True:
        data, address = sock.recvfrom(8192)
        sender_ip = address[0]
        raw_message = data.decode("utf-8", errors="replace")
        event = normalize_syslog(raw_message, sender_ip, default_location, device_map)
        print(f"{event.severity:8} {event.device_name} {event.event_type} - {event.message}")

        if dry_run:
            continue

        try:
            post_event(endpoint, event)
        except (HTTPError, URLError, TimeoutError, RuntimeError) as exc:
            print(f"Cannot send syslog event to backend: {exc}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Listen for UDP syslog and forward normalized AIOps events.")
    parser.add_argument("--backend-url", default=DEFAULT_BACKEND_URL)
    parser.add_argument("--listen-host", default=DEFAULT_LISTEN_HOST)
    parser.add_argument("--listen-port", type=int, default=DEFAULT_LISTEN_PORT)
    parser.add_argument("--default-location", default="Syslog Network Zone")
    parser.add_argument("--device-map", help="Optional JSON file mapping syslog host/IP to device metadata.")
    parser.add_argument("--dry-run", action="store_true", help="Print normalized events without sending them.")
    return parser.parse_args()


if __name__ == "__main__":
    args = parse_args()
    run_collector(
        backend_url=args.backend_url,
        listen_host=args.listen_host,
        listen_port=args.listen_port,
        default_location=args.default_location,
        device_map_path=args.device_map,
        dry_run=args.dry_run,
    )
