#!/usr/bin/env python3
import argparse
import json
import signal
import socketserver
import threading
from datetime import datetime, timezone
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class CameraState:
    def __init__(self):
        self.camera_up = True
        self.rtsp_up = True
        self.lock = threading.Lock()

    def snapshot(self):
        with self.lock:
            return {
                "camera_up": self.camera_up,
                "rtsp_up": self.rtsp_up,
            }

    def set_camera(self, value: bool):
        with self.lock:
            self.camera_up = value

    def set_rtsp(self, value: bool):
        with self.lock:
            self.rtsp_up = value


class CameraHttpHandler(BaseHTTPRequestHandler):
    server_version = "AIOpsMockCamera/1.0"

    def do_GET(self):
        if self.path in {"/", "/health", "/status"}:
            state = self.server.camera_state.snapshot()
            payload = {
                "device": "Branch Camera 01",
                "type": "IP_CAMERA",
                "status": "UP" if state["camera_up"] else "DOWN",
                "stream": "AVAILABLE" if state["rtsp_up"] else "DOWN",
                "rtspPort": self.server.rtsp_port,
                "timestamp": datetime.now(timezone.utc).isoformat(),
            }
            body = json.dumps(payload, indent=2).encode("utf-8")
            self.send_response(HTTPStatus.OK if state["camera_up"] else HTTPStatus.SERVICE_UNAVAILABLE)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return

        if self.path == "/snapshot":
            state = self.server.camera_state.snapshot()
            if not state["camera_up"]:
                self.send_error(HTTPStatus.SERVICE_UNAVAILABLE, "Camera is down")
                return
            body = b"mock-camera-snapshot"
            self.send_response(HTTPStatus.OK)
            self.send_header("Content-Type", "text/plain")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return

        self.send_error(HTTPStatus.NOT_FOUND, "Not found")

    def do_POST(self):
        if self.path == "/control/rtsp/down":
            self.server.camera_state.set_rtsp(False)
            self.write_control_response("RTSP stream set to DOWN")
            return
        if self.path == "/control/rtsp/up":
            self.server.camera_state.set_rtsp(True)
            self.write_control_response("RTSP stream set to UP")
            return
        if self.path == "/control/camera/down":
            self.server.camera_state.set_camera(False)
            self.write_control_response("Camera health set to DOWN")
            return
        if self.path == "/control/camera/up":
            self.server.camera_state.set_camera(True)
            self.write_control_response("Camera health set to UP")
            return
        if self.path == "/control/reset":
            self.server.camera_state.set_camera(True)
            self.server.camera_state.set_rtsp(True)
            self.write_control_response("Camera reset to UP")
            return

        self.send_error(HTTPStatus.NOT_FOUND, "Not found")

    def write_control_response(self, message: str):
        state = self.server.camera_state.snapshot()
        payload = {
            "message": message,
            "cameraStatus": "UP" if state["camera_up"] else "DOWN",
            "rtspStatus": "UP" if state["rtsp_up"] else "DOWN",
            "timestamp": datetime.now(timezone.utc).isoformat(),
        }
        body = json.dumps(payload, indent=2).encode("utf-8")
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format, *args):
        print(f"[HTTP] {self.address_string()} - {format % args}")


class RtspMockHandler(socketserver.BaseRequestHandler):
    def handle(self):
        peer = f"{self.client_address[0]}:{self.client_address[1]}"
        print(f"[RTSP] connection from {peer}")
        if not self.server.camera_state.snapshot()["rtsp_up"]:
            try:
                self.request.recv(1024)
            except OSError:
                pass
            return
        try:
            self.request.sendall(
                b"RTSP/1.0 200 OK\r\n"
                b"Server: AIOpsMockCamera/1.0\r\n"
                b"CSeq: 1\r\n\r\n"
            )
            self.request.recv(1024)
        except OSError:
            pass


class ThreadingTcpServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True


def run_http(host: str, http_port: int, rtsp_port: int, camera_state: CameraState, stop_event: threading.Event):
    server = ThreadingHTTPServer((host, http_port), CameraHttpHandler)
    server.rtsp_port = rtsp_port
    server.camera_state = camera_state
    server.timeout = 1
    print(f"[HTTP] mock camera web/health listening on http://{host}:{http_port}")
    print(f"[HTTP] controls: POST /control/rtsp/down, /control/rtsp/up, /control/camera/down, /control/camera/up, /control/reset")
    while not stop_event.is_set():
        server.handle_request()
    server.server_close()


def run_rtsp(host: str, rtsp_port: int, camera_state: CameraState, stop_event: threading.Event):
    server = ThreadingTcpServer((host, rtsp_port), RtspMockHandler)
    server.camera_state = camera_state
    server.timeout = 1
    print(f"[RTSP] mock camera RTSP-like TCP listener on {host}:{rtsp_port}")
    while not stop_event.is_set():
        server.handle_request()
    server.server_close()


def main():
    parser = argparse.ArgumentParser(description="Run a mock IP camera device for AIOps Sentinel checks.")
    parser.add_argument("--host", default="127.0.0.1", help="Bind address. Use 0.0.0.0 to expose on LAN.")
    parser.add_argument("--http-port", type=int, default=8085, help="Mock camera HTTP port.")
    parser.add_argument("--rtsp-port", type=int, default=8554, help="Mock camera RTSP-like TCP port.")
    args = parser.parse_args()

    stop_event = threading.Event()
    camera_state = CameraState()

    def stop(*_):
        stop_event.set()

    signal.signal(signal.SIGINT, stop)
    signal.signal(signal.SIGTERM, stop)

    threads = [
        threading.Thread(target=run_http, args=(args.host, args.http_port, args.rtsp_port, camera_state, stop_event), daemon=True),
        threading.Thread(target=run_rtsp, args=(args.host, args.rtsp_port, camera_state, stop_event), daemon=True),
    ]

    for thread in threads:
        thread.start()

    print("Mock camera is running. Press Ctrl+C to stop.")
    try:
        while not stop_event.is_set():
            stop_event.wait(0.5)
    finally:
        stop_event.set()
        for thread in threads:
            thread.join(timeout=2)
        print("Mock camera stopped.")


if __name__ == "__main__":
    main()
