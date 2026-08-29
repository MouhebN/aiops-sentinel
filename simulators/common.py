from __future__ import annotations

import argparse
import json
import random
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Callable
from urllib.error import URLError, HTTPError
from urllib.request import Request, urlopen


DEFAULT_BACKEND_URL = "http://localhost:8080"


@dataclass(frozen=True)
class Device:
    device_id: str
    name: str
    device_type: str
    location: str


@dataclass(frozen=True)
class EventTemplate:
    event_type: str
    severity: str
    message: str
    details_factory: Callable[[], str]
    weight: int


class DeviceSimulator:
    def __init__(self, device: Device, events: list[EventTemplate]):
        self.device = device
        self.events = events

    def build_event(self) -> dict:
        template = random.choices(
            self.events,
            weights=[event.weight for event in self.events],
            k=1,
        )[0]

        return {
            "deviceId": self.device.device_id,
            "deviceName": self.device.name,
            "deviceType": self.device.device_type,
            "location": self.device.location,
            "eventType": template.event_type,
            "severity": template.severity,
            "message": template.message,
            "details": template.details_factory(),
            "occurredAt": datetime.now(timezone.utc).isoformat(),
        }

    def run(self, backend_url: str, interval: float, once: bool, dry_run: bool) -> None:
        endpoint = normalize_events_url(backend_url)

        while True:
            event = self.build_event()
            if dry_run:
                print(json.dumps(event, indent=2))
            else:
                send_event(endpoint, event)
                print(f"{event['severity']:8} {event['deviceId']} {event['eventType']} - {event['message']}")

            if once:
                return

            time.sleep(interval)


def random_metrics(**ranges: tuple[int, int]) -> str:
    return ",".join(
        f"{name}={random.randint(low, high)}"
        for name, (low, high) in ranges.items()
    )


def static_details(value: str) -> Callable[[], str]:
    return lambda: value


def normalize_events_url(backend_url: str) -> str:
    base_url = backend_url.rstrip("/")
    if base_url.endswith("/api/events"):
        return base_url
    return f"{base_url}/api/events"


def send_event(endpoint: str, payload: dict) -> None:
    body = json.dumps(payload).encode("utf-8")
    request = Request(
        endpoint,
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    try:
        with urlopen(request, timeout=5) as response:
            if response.status >= 400:
                raise RuntimeError(f"Backend returned HTTP {response.status}")
    except HTTPError as exc:
        raise RuntimeError(f"Backend rejected event with HTTP {exc.code}: {exc.read().decode()}") from exc
    except URLError as exc:
        raise RuntimeError(f"Cannot reach backend at {endpoint}: {exc.reason}") from exc


def parse_args(description: str) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=description)
    parser.add_argument("--backend-url", default=DEFAULT_BACKEND_URL)
    parser.add_argument("--interval", type=float, default=5.0)
    parser.add_argument("--once", action="store_true")
    parser.add_argument("--dry-run", action="store_true", help="Print generated events without sending them.")
    return parser.parse_args()


def run_simulator(simulator: DeviceSimulator, description: str) -> None:
    args = parse_args(description)
    simulator.run(args.backend_url, args.interval, args.once, args.dry_run)
