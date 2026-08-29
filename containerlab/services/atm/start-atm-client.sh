#!/bin/sh
# Start periodic ATM -> bank-srv-01 HTTP traffic. Independent of the TCP :9090 service.

set -eu

PIDFILE=/var/run/atm-client.pid
LOG=/var/log/atm-client.log

if [ -f "$PIDFILE" ] && kill -0 "$(cat "$PIDFILE")" 2>/dev/null; then
  echo "atm-01 client already running pid=$(cat "$PIDFILE")"
  exit 0
fi

if pgrep -f /opt/atm/atm-client.sh >/dev/null 2>&1; then
  echo "atm-01 client already running"
  exit 0
fi

nohup sh /opt/atm/atm-client.sh >> "$LOG" 2>&1 < /dev/null &
echo $! > "$PIDFILE"
echo "atm-01 client started pid=$(cat "$PIDFILE") log=$LOG"
