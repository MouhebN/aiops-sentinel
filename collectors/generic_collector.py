from __future__ import annotations

import argparse
import json
import platform
import subprocess
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


DEFAULT_BACKEND_URL = "http://localhost:8080"


@dataclass(frozen=True)
class CheckResult:
    method: str
    status: str
    event_type: str
    severity: str
    message: str
    raw_log: str
    seen: bool


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def api_request(method: str, url: str, payload: dict[str, Any] | None = None) -> Any:
    body = json.dumps(payload).encode("utf-8") if payload is not None else None
    request = Request(
        url,
        data=body,
        headers={"Content-Type": "application/json"},
        method=method,
    )

    with urlopen(request, timeout=10) as response:
        if response.status == 204:
            return None
        return json.loads(response.read().decode("utf-8"))


def fetch_enabled_components(backend_url: str) -> list[dict[str, Any]]:
    return api_request("GET", f"{backend_url.rstrip('/')}/api/components/enabled")


def patch_component_status(
    backend_url: str,
    component_id: int,
    status: str,
    error: str | None,
    seen: bool,
) -> None:
    timestamp = utc_now()
    api_request(
        "PATCH",
        f"{backend_url.rstrip('/')}/api/components/{component_id}/status",
        {
            "status": status,
            "checkedAt": timestamp,
            "seenAt": timestamp if seen else None,
            "error": error,
        },
    )


def send_event(backend_url: str, component: dict[str, Any], result: CheckResult) -> None:
    api_request(
        "POST",
        f"{backend_url.rstrip('/')}/api/events",
        {
            "deviceId": f"component-{component['id']}",
            "deviceName": component["name"],
            "deviceType": component["type"],
            "location": component["location"],
            "eventType": result.event_type,
            "severity": result.severity,
            "message": result.message,
            "details": result.raw_log,
            "occurredAt": utc_now(),
        },
    )


def ping_check(component: dict[str, Any]) -> CheckResult:
    ip_address = component.get("ipAddress")
    system = platform.system().lower()
    command = ["ping", "-n", "1", "-w", "2000", ip_address] if "windows" in system else [
        "ping",
        "-c",
        "1",
        "-W",
        "2",
        ip_address,
    ]

    try:
        completed = subprocess.run(command, capture_output=True, text=True, timeout=5, check=False)
    except (OSError, subprocess.TimeoutExpired) as exc:
        return CheckResult(
            method="PING",
            status="DOWN",
            event_type="DEVICE_UNREACHABLE",
            severity=severity_for(component),
            message=f"{component['name']} is unreachable by ICMP ping",
            raw_log=f"ping {ip_address} failed: {exc}",
            seen=False,
        )

    raw_log = (completed.stdout or completed.stderr).strip()
    if completed.returncode == 0:
        return CheckResult(
            method="PING",
            status="UP",
            event_type="DEVICE_RECOVERED",
            severity="INFO",
            message=f"{component['name']} is reachable by ICMP ping",
            raw_log=raw_log or f"ping {ip_address} succeeded",
            seen=True,
        )

    return CheckResult(
        method="PING",
        status="DOWN",
        event_type="DEVICE_UNREACHABLE",
        severity=severity_for(component),
        message=f"{component['name']} is unreachable by ICMP ping",
        raw_log=raw_log or f"ping {ip_address} failed",
        seen=False,
    )


def http_health_check(component: dict[str, Any]) -> CheckResult:
    http_url = component.get("httpUrl")
    request = Request(http_url, method="GET")

    try:
        with urlopen(request, timeout=5) as response:
            if 200 <= response.status < 400:
                return CheckResult(
                    method="HTTP_HEALTH",
                    status="UP",
                    event_type="HTTP_HEALTH_RECOVERED",
                    severity="INFO",
                    message=f"{component['name']} HTTP health endpoint is responding",
                    raw_log=f"GET {http_url} returned HTTP {response.status}",
                    seen=True,
                )
            return CheckResult(
                method="HTTP_HEALTH",
                status="DOWN",
                event_type="HTTP_HEALTH_FAILED",
                severity=severity_for(component),
                message=f"{component['name']} HTTP health endpoint returned HTTP {response.status}",
                raw_log=f"GET {http_url} returned HTTP {response.status}",
                seen=False,
            )
    except HTTPError as exc:
        return CheckResult(
            method="HTTP_HEALTH",
            status="DOWN",
            event_type="HTTP_HEALTH_FAILED",
            severity=severity_for(component),
            message=f"{component['name']} HTTP health endpoint returned HTTP {exc.code}",
            raw_log=f"GET {http_url} returned HTTP {exc.code}: {exc.reason}",
            seen=False,
        )
    except (URLError, TimeoutError, OSError) as exc:
        return CheckResult(
            method="HTTP_HEALTH",
            status="DOWN",
            event_type="HTTP_HEALTH_FAILED",
            severity=severity_for(component),
            message=f"{component['name']} HTTP health endpoint is unreachable",
            raw_log=f"GET {http_url} failed: {exc}",
            seen=False,
        )


def severity_for(component: dict[str, Any]) -> str:
    return "CRITICAL" if component.get("criticality") in {"HIGH", "CRITICAL"} else "WARNING"


def aggregate_status(results: list[CheckResult]) -> str:
    if any(result.status == "DOWN" for result in results):
        return "DOWN"
    if any(result.status == "DEGRADED" for result in results):
        return "DEGRADED"
    return "UP"


def should_emit_event(previous_status: str | None, result: CheckResult) -> bool:
    if result.status == "DOWN":
        return previous_status != "DOWN"
    if result.status == "UP" and previous_status == "DOWN":
        return True
    return False


def check_component(component: dict[str, Any]) -> list[CheckResult]:
    results: list[CheckResult] = []
    methods = set(component.get("monitoringMethods", []))

    if "PING" in methods:
        results.append(ping_check(component))
    if "HTTP_HEALTH" in methods:
        results.append(http_health_check(component))

    return results


def run_collector(backend_url: str, poll_interval: float, once: bool, dry_run: bool) -> None:
    last_status_by_component: dict[int, str] = {}
    next_check_by_component: dict[int, float] = {}

    while True:
        try:
            components = fetch_enabled_components(backend_url)
        except (HTTPError, URLError, TimeoutError, json.JSONDecodeError) as exc:
            print(f"Cannot fetch enabled components: {exc}")
            if once:
                return
            time.sleep(poll_interval)
            continue

        now = time.time()
        for component in components:
            component_id = int(component["id"])
            interval = int(component.get("checkIntervalSeconds") or 30)
            if now < next_check_by_component.get(component_id, 0):
                continue

            results = check_component(component)
            if not results:
                continue

            previous_status = last_status_by_component.get(component_id, component.get("lastStatus"))
            current_status = aggregate_status(results)
            failed_results = [result for result in results if result.status == "DOWN"]
            status_error = failed_results[0].raw_log if failed_results else None

            print(f"{component['name']}: {current_status}")
            if not dry_run:
                patch_component_status(
                    backend_url,
                    component_id,
                    current_status,
                    status_error,
                    seen=current_status == "UP",
                )

            for result in results:
                if should_emit_event(previous_status, result):
                    print(f"  event {result.severity} {result.event_type}: {result.message}")
                    if not dry_run:
                        send_event(backend_url, component, result)

            last_status_by_component[component_id] = current_status
            next_check_by_component[component_id] = now + interval

        if once:
            return
        time.sleep(poll_interval)


def main() -> None:
    parser = argparse.ArgumentParser(description="Generic AIOps Sentinel component collector.")
    parser.add_argument("--backend-url", default=DEFAULT_BACKEND_URL)
    parser.add_argument("--poll-interval", type=float, default=5.0)
    parser.add_argument("--once", action="store_true")
    parser.add_argument("--dry-run", action="store_true", help="Run checks and print output without updating backend.")
    args = parser.parse_args()

    run_collector(args.backend_url, args.poll_interval, args.once, args.dry_run)


if __name__ == "__main__":
    main()
