#!/bin/sh
# ATM-01 TCP availability listener on 9090.
# Alpine BusyBox nc only. No apk, no Python, no Internet.

set -eu

PORT="${ATM_TCP_PORT:-9090}"
BANNER=/opt/atm/atm-banner.sh

if [ ! -x "$BANNER" ]; then
  echo "atm-01 TCP service: missing executable $BANNER" >&2
  exit 1
fi

if ! command -v nc >/dev/null 2>&1; then
  echo "atm-01 TCP service: BusyBox nc is required (Alpine image)" >&2
  exit 1
fi

echo "[atm-01] TCP service listening on 0.0.0.0:${PORT}"
# -lk -e: persistent listen; run banner after each connect. -e must be last.
exec nc -n -lk -p "$PORT" -e "$BANNER"
