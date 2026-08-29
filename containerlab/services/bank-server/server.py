#!/usr/bin/env python3
"""Tiny demo HTTP API for bank-srv-01. Stdlib only."""

from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json

HOST = "0.0.0.0"
PORT = 8080


class BankHandler(BaseHTTPRequestHandler):
    def _send_json(self, status: int, payload: dict) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self) -> None:
        path = self.path.split("?", 1)[0]
        if path == "/health":
            self._send_json(200, {"status": "ok", "service": "bank-srv-01"})
            return
        if path == "/api/account":
            self._send_json(
                200,
                {
                    "account": "FR76-0001",
                    "holder": "demo-customer",
                    "balance": 1520.40,
                    "currency": "EUR",
                },
            )
            return
        self._send_json(404, {"error": "not found"})

    def do_POST(self) -> None:
        path = self.path.split("?", 1)[0]
        if path == "/api/transaction":
            length = int(self.headers.get("Content-Length", "0") or 0)
            if length:
                self.rfile.read(length)
            self._send_json(
                200,
                {
                    "status": "accepted",
                    "transactionId": "txn-demo-001",
                    "source": "atm-01",
                },
            )
            return
        self._send_json(404, {"error": "not found"})

    def log_message(self, fmt: str, *args) -> None:
        print(f"[bank-srv-01] {self.address_string()} {fmt % args}", flush=True)


def main() -> None:
    print(f"[bank-srv-01] listening on {HOST}:{PORT}", flush=True)
    ThreadingHTTPServer((HOST, PORT), BankHandler).serve_forever()


if __name__ == "__main__":
    main()
