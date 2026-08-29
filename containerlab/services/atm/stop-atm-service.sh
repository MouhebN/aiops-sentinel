#!/bin/sh
# ATM service DOWN: stop only the TCP listener on 9090.
# The Containerlab node and atm-client.sh keep running (PING still works).

set -eu

PIDFILE=/var/run/atm-service.pid
PORT="${ATM_TCP_PORT:-9090}"

if [ -f "$PIDFILE" ]; then
  pid=$(cat "$PIDFILE")
  kill "$pid" 2>/dev/null || true
  rm -f "$PIDFILE"
fi

pkill -f "/opt/atm/atm-service.sh" 2>/dev/null || true
pkill -f "nc -n -lk -p ${PORT}" 2>/dev/null || true

echo "atm-01 TCP service stopped (:$PORT closed, node still up)"
