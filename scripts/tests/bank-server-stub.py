#!/usr/bin/env python3
"""Test double for BANK-SRV-01. Same /health JSON, configurable port."""

import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(os.environ.get("BANK_SERVER_PORT", "18780"))
HOST = os.environ.get("BANK_SERVER_BIND", "127.0.0.1")
BODY = b'{"status": "ok", "service": "bank-srv-01"}'


class Handler(BaseHTTPRequestHandler):
    def do_GET(self) -> None:
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(BODY)))
        self.end_headers()
        self.wfile.write(BODY)

    def log_message(self, fmt: str, *args) -> None:
        return


if __name__ == "__main__":
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
