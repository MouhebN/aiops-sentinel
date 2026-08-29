#!/usr/bin/env python3
"""IP-camera simulator for camera-01. HTTP status on :8081 plus MediaMTX RTSP on :8554."""

from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from datetime import datetime, timezone
import json
import socket
import time

HOST = "0.0.0.0"
PORT = 8081
RTSP_PORT = 8554
STARTED_AT = time.time()


def utc_now() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def rtsp_listening() -> bool:
    try:
        with socket.create_connection(("127.0.0.1", RTSP_PORT), timeout=1):
            return True
    except OSError:
        return False


class CameraHandler(BaseHTTPRequestHandler):
    def _send_json(self, status: int, payload: dict) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self) -> None:
        path = self.path.split("?", 1)[0]
        uptime = int(time.time() - STARTED_AT)
        stream_ok = rtsp_listening()
        stream = "available" if stream_ok else "unavailable"
        if path == "/health":
            # HTTP liveness is independent of RTSP so Sentinel HTTP_HEALTH stays UP
            # when only the stream is down (component DEGRADED via RTSP_HEALTH).
            self._send_json(
                200,
                {
                    "status": "ok" if stream_ok else "degraded",
                    "service": "camera-01",
                    "stream": stream,
                    "rtsp": f"rtsp://10.10.10.40:{RTSP_PORT}/live",
                },
            )
            return
        if path == "/status":
            self._send_json(
                200 if stream_ok else 503,
                {
                    "status": "up" if stream_ok else "degraded",
                    "service": "camera-01",
                    "stream": stream,
                    "uptimeSeconds": uptime,
                    "startedAt": utc_now(),
                    "ip": "10.10.10.40",
                    "httpPort": PORT,
                    "rtspPort": RTSP_PORT,
                    "rtspPath": "/live",
                },
            )
            return
        self._send_json(404, {"error": "not found"})

    def log_message(self, fmt: str, *args) -> None:
        print(f"[camera-01] {utc_now()} {self.address_string()} {fmt % args}", flush=True)


def main() -> None:
    print(f"[camera-01] {utc_now()} HTTP listening on {HOST}:{PORT}", flush=True)
    ThreadingHTTPServer((HOST, PORT), CameraHandler).serve_forever()


if __name__ == "__main__":
    main()
