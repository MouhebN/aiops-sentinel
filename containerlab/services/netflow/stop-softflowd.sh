#!/bin/sh
# Stop the lab NetFlow exporter (fprobe). Does not destroy the node or nftables/Syslog.

set -eu

PIDFILE=/var/run/fprobe.pid
DESTFILE=/var/run/softflowd.dest

live_pid() {
  pid=$1
  [ -n "$pid" ] || return 1
  [ -d "/proc/$pid" ] || return 1
  state=$(awk '/^State:/{print $2}' "/proc/$pid/status" 2>/dev/null || true)
  [ -n "$state" ] && [ "$state" != "Z" ]
}

kill_name() {
  name=$1
  if [ -f "$PIDFILE" ]; then
    kill "$(cat "$PIDFILE")" 2>/dev/null || true
  fi
  for pid in $(pgrep -x "$name" 2>/dev/null || true); do
    live_pid "$pid" || continue
    kill "$pid" 2>/dev/null || true
  done
  i=0
  while [ "$i" -lt 15 ]; do
    left=
    for pid in $(pgrep -x "$name" 2>/dev/null || true); do
      live_pid "$pid" || continue
      left=1
    done
    [ -z "$left" ] && break
    i=$((i + 1))
    sleep 0.1 2>/dev/null || sleep 1
  done
  for pid in $(pgrep -x "$name" 2>/dev/null || true); do
    live_pid "$pid" || continue
    kill -9 "$pid" 2>/dev/null || true
  done
}

kill_name fprobe
kill_name softflowd
rm -f "$PIDFILE" "$DESTFILE" /var/run/softflowd.pid /var/run/softflowd.ctl
echo "BANK-FW-01 NetFlow exporter stopped"
