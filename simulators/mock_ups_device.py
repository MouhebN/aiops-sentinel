#!/usr/bin/env python3
import argparse
import json
import signal
import threading
from datetime import datetime, timezone
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class UpsState:
    def __init__(self):
        self.status = "UP"
        self.power_source = "MAINS"
        self.battery_percent = 96
        self.runtime_minutes = 85
        self.load_percent = 42
        self.input_voltage = 230
        self.output_voltage = 230
        self.lock = threading.Lock()

    def snapshot(self):
        with self.lock:
            return {
                "device": "Branch UPS 01",
                "type": "UPS",
                "status": self.status,
                "powerSource": self.power_source,
                "batteryPercent": self.battery_percent,
                "runtimeMinutes": self.runtime_minutes,
                "loadPercent": self.load_percent,
                "inputVoltage": self.input_voltage,
                "outputVoltage": self.output_voltage,
                "protectedAssets": "branch-router,branch-switch,camera-nvr,atm-terminal",
                "timestamp": datetime.now(timezone.utc).isoformat(),
            }

    def reset(self):
        with self.lock:
            self.status = "UP"
            self.power_source = "MAINS"
            self.battery_percent = 96
            self.runtime_minutes = 85
            self.load_percent = 42
            self.input_voltage = 230
            self.output_voltage = 230

    def on_battery(self):
        with self.lock:
            self.status = "ON_BATTERY"
            self.power_source = "BATTERY"
            self.battery_percent = 62
            self.runtime_minutes = 35
            self.input_voltage = 0
            self.output_voltage = 230

    def low_battery(self):
        with self.lock:
            self.status = "ON_BATTERY"
            self.power_source = "BATTERY"
            self.battery_percent = 12
            self.runtime_minutes = 6
            self.input_voltage = 0
            self.output_voltage = 230

    def high_load(self):
        with self.lock:
            self.status = "UP"
            self.power_source = "MAINS"
            self.battery_percent = 83
            self.runtime_minutes = 48
            self.load_percent = 91
            self.input_voltage = 230
            self.output_voltage = 230

    def down(self):
        with self.lock:
            self.status = "DOWN"
            self.power_source = "UNKNOWN"
            self.battery_percent = 0
            self.runtime_minutes = 0
            self.load_percent = 0
            self.input_voltage = 0
            self.output_voltage = 0


class UpsHttpHandler(BaseHTTPRequestHandler):
    server_version = "AIOpsMockUPS/1.0"

    def do_GET(self):
        if self.path in {"/", "/health", "/metrics", "/status"}:
            payload = self.server.ups_state.snapshot()
            body = json.dumps(payload, indent=2).encode("utf-8")
            self.send_response(HTTPStatus.OK if payload["status"] != "DOWN" else HTTPStatus.SERVICE_UNAVAILABLE)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return

        self.send_error(HTTPStatus.NOT_FOUND, "Not found")

    def do_POST(self):
        actions = {
            "/control/reset": self.server.ups_state.reset,
            "/control/on-battery": self.server.ups_state.on_battery,
            "/control/low-battery": self.server.ups_state.low_battery,
            "/control/high-load": self.server.ups_state.high_load,
            "/control/down": self.server.ups_state.down,
        }
        action = actions.get(self.path)
        if action is None:
            self.send_error(HTTPStatus.NOT_FOUND, "Not found")
            return

        action()
        payload = self.server.ups_state.snapshot()
        body = json.dumps(payload, indent=2).encode("utf-8")
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format, *args):
        print(f"[UPS] {self.address_string()} - {format % args}")


def run_http(host: str, port: int, ups_state: UpsState, stop_event: threading.Event):
    server = ThreadingHTTPServer((host, port), UpsHttpHandler)
    server.ups_state = ups_state
    server.timeout = 1
    print(f"[UPS] mock UPS metrics listening on http://{host}:{port}/health")
    print("[UPS] controls: POST /control/on-battery, /control/low-battery, /control/high-load, /control/down, /control/reset")
    while not stop_event.is_set():
        server.handle_request()
    server.server_close()


def main():
    parser = argparse.ArgumentParser(description="Run a mock UPS/onduleur device for AIOps Sentinel checks.")
    parser.add_argument("--host", default="127.0.0.1", help="Bind address. Use 0.0.0.0 to expose on LAN.")
    parser.add_argument("--port", type=int, default=8095, help="Mock UPS HTTP metrics port.")
    args = parser.parse_args()

    stop_event = threading.Event()
    ups_state = UpsState()

    def stop(*_):
        stop_event.set()

    signal.signal(signal.SIGINT, stop)
    signal.signal(signal.SIGTERM, stop)

    thread = threading.Thread(target=run_http, args=(args.host, args.port, ups_state, stop_event), daemon=True)
    thread.start()

    print("Mock UPS is running. Press Ctrl+C to stop.")
    try:
        while not stop_event.is_set():
            stop_event.wait(0.5)
    finally:
        stop_event.set()
        thread.join(timeout=2)
        print("Mock UPS stopped.")


if __name__ == "__main__":
    main()
