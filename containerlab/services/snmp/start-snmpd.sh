#!/bin/sh
# Start net-snmp snmpd on a Containerlab linux node. Idempotent. Does not replace PID 1.
# Usage: start-snmpd.sh NODE_NAME

set -eu

NAME="${1:-lab-node}"
PIDFILE=/var/run/snmpd.pid
CONF=/etc/snmp/snmpd.conf
TEMPLATE=/opt/snmp/snmpd.conf

# Containerlab PID 1 is often sleep(1) and does not reap. A dead snmpd then
# stays as a zombie; pgrep/kill -0 still match it and skip the real start.
live_snmpd_pid() {
  for pid in $(pgrep -x snmpd 2>/dev/null || true); do
    state=$(awk '/^State:/{print $2}' "/proc/$pid/status" 2>/dev/null || true)
    if [ -n "$state" ] && [ "$state" != "Z" ]; then
      echo "$pid"
      return 0
    fi
  done
  return 1
}

pid="$(live_snmpd_pid || true)"
if [ -n "$pid" ]; then
  echo "$NAME snmpd already running pid=$pid"
  exit 0
fi

if ! command -v snmpd >/dev/null 2>&1; then
  echo "$NAME installing net-snmp"
  apk add --no-cache net-snmp net-snmp-tools
fi

mkdir -p /etc/snmp /var/lib/net-snmp
if [ ! -f /etc/hosts.allow ] || ! grep -q '^snmpd:' /etc/hosts.allow 2>/dev/null; then
  echo "snmpd: ALL" >> /etc/hosts.allow
fi
if [ ! -f /etc/hosts.deny ]; then
  : >/etc/hosts.deny
fi

sed "s/@SYSNAME@/${NAME}/g" "$TEMPLATE" > "$CONF"

# -C: ignore packaged localhost-only conf. UDP 161 on all interfaces.
rm -f "$PIDFILE"
snmpd -C -c "$CONF" -p "$PIDFILE" -Lo
echo "$NAME snmpd started pid=$(cat "$PIDFILE" 2>/dev/null || echo unknown) community=banklab udp/161"
