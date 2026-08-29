#!/usr/bin/env bash
# Operator wrapper for scripts/demo/0*.sh
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

usage() {
  cat <<EOF
Usage: ./scripts/demo/demo.sh <scenario>

  baseline         01 — healthy lab, no attack
  sensitive-port   02 — five SSH attempts (SENSITIVE_PORT_ACCESS)
  port-scan        03 — one /opt/scenarios/port-scan.sh
  bank-down        04 — stop BANK-SRV HTTP
  bank-up          05 — start BANK-SRV HTTP
  atm-down         06 — stop ATM TCP :9090
  atm-up           07 — start ATM TCP :9090
  camera-down      08 — stop camera RTSP
  camera-up        09 — start camera RTSP
  reset            99 — restore services (does not wipe Sentinel history)

See docs/demo-scenarios.md
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" || $# -lt 1 ]]; then
  usage
  exit 0
fi

case "$1" in
  baseline) exec bash "$SCRIPT_DIR/01-baseline.sh" ;;
  sensitive-port) exec bash "$SCRIPT_DIR/02-sensitive-port.sh" ;;
  port-scan) exec bash "$SCRIPT_DIR/03-port-scan.sh" ;;
  bank-down) exec bash "$SCRIPT_DIR/04-bank-server-down.sh" ;;
  bank-up) exec bash "$SCRIPT_DIR/05-bank-server-up.sh" ;;
  atm-down) exec bash "$SCRIPT_DIR/06-atm-degraded.sh" ;;
  atm-up) exec bash "$SCRIPT_DIR/07-atm-recover.sh" ;;
  camera-down) exec bash "$SCRIPT_DIR/08-camera-rtsp-down.sh" ;;
  camera-up) exec bash "$SCRIPT_DIR/09-camera-recover.sh" ;;
  reset) exec bash "$SCRIPT_DIR/99-reset-lab.sh" ;;
  *)
    printf 'Unknown scenario: %s\n\n' "$1" >&2
    usage >&2
    exit 1
    ;;
esac
