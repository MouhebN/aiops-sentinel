#!/bin/sh
# Continuous NetFlow v5 from BANK-FW-01 WAN (eth1).
# Alpine softflowd 1.1.0 live capture uses pcap_set_timeout(0), so veth
# packets never reach the flow engine (libpcap counters rise, processed=0).
# fprobe is the compatible packet-to-NetFlow v5 exporter on this lab.
# Does not read firewall DENY logs. Idempotent. Stops the deprecated log-tail exporter.

set -eu

. /opt/netflow/netflow-dest.sh

IFACE="${SOFTFLOWD_IFACE:-eth1}"
PIDFILE=/var/run/fprobe.pid
DESTFILE=/var/run/softflowd.dest
LOG=/var/log/fprobe.log
# Idle/active/scan timeouts short enough for nfcapd -t 15 without dummy UDP.
FPROBE_IDLE="${FPROBE_IDLE:-5}"
FPROBE_ACTIVE="${FPROBE_ACTIVE:-15}"
FPROBE_SCAN="${FPROBE_SCAN:-2}"

live_pid() {
  pid=$1
  [ -n "$pid" ] || return 1
  [ -d "/proc/$pid" ] || return 1
  state=$(awk '/^State:/{print $2}' "/proc/$pid/status" 2>/dev/null || true)
  [ -n "$state" ] && [ "$state" != "Z" ]
}

live_exporter() {
  if [ -f "$PIDFILE" ] && live_pid "$(cat "$PIDFILE" 2>/dev/null || true)"; then
    cat "$PIDFILE"
    return 0
  fi
  for pid in $(pgrep -x fprobe 2>/dev/null || true); do
    if live_pid "$pid"; then
      echo "$pid"
      return 0
    fi
  done
  return 1
}

stop_legacy_log_exporter() {
  for pid in $(pgrep -f /opt/netflow/export-scan-flows.py 2>/dev/null || true); do
    live_pid "$pid" || continue
    kill "$pid" 2>/dev/null || true
  done
}

kill_name() {
  name=$1
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

stop_exporter() {
  kill_name fprobe
  kill_name softflowd
  rm -f "$PIDFILE" /var/run/softflowd.pid /var/run/softflowd.ctl
}

require_fprobe() {
  if command -v fprobe >/dev/null 2>&1; then
    return 0
  fi
  echo "BANK-FW-01 installing fprobe"
  apk add --no-cache fprobe
  command -v fprobe >/dev/null 2>&1
}

if ! ip link show "$IFACE" >/dev/null 2>&1; then
  echo "BANK-FW-01 missing data-plane interface $IFACE" >&2
  exit 1
fi

stop_legacy_log_exporter
require_fprobe
ip link set "$IFACE" up
ip link set "$IFACE" promisc on 2>/dev/null || true

DEST=$(resolve_netflow_collector)
PREV=$(cat "$DESTFILE" 2>/dev/null || true)
RUNNING=$(live_exporter || true)

if [ "${SOFTFLOWD_FORCE:-0}" != "1" ] && [ -n "$RUNNING" ] && [ "$PREV" = "$DEST:$SENTINEL_NETFLOW_PORT:$IFACE" ]; then
  echo "BANK-FW-01 fprobe already running pid=$RUNNING iface=$IFACE dest=$DEST:$SENTINEL_NETFLOW_PORT"
  exit 0
fi

if [ -n "$RUNNING" ] || [ "${SOFTFLOWD_FORCE:-0}" = "1" ]; then
  echo "BANK-FW-01 restarting fprobe (collector/interface/force)"
  stop_exporter
fi

mkdir -p /var/run /var/log /var/lib/bank-fw
: > "$LOG"
# fprobe 1.1 may daemonize or stay in the foreground; always detach from this script.
fprobe -i "$IFACE" -n 5 -s "$FPROBE_SCAN" -d "$FPROBE_IDLE" -e "$FPROBE_ACTIVE" \
  "$DEST:$SENTINEL_NETFLOW_PORT" >> "$LOG" 2>&1 &
sleep 0.4 2>/dev/null || sleep 1
pid=$(live_exporter || true)
if [ -z "$pid" ]; then
  echo "BANK-FW-01 fprobe did not stay running (see $LOG)" >&2
  tail -n 20 "$LOG" >&2 || true
  exit 1
fi
echo "$pid" > "$PIDFILE"
echo "$DEST:$SENTINEL_NETFLOW_PORT:$IFACE" > "$DESTFILE"
echo "BANK-FW-01 fprobe started pid=$pid iface=$IFACE dest=$DEST:$SENTINEL_NETFLOW_PORT (NetFlow v5)"
