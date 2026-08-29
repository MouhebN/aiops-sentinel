#!/bin/sh
# Apply bank-fw-01 filter policy, collect local DENY logs, and forward them
# as UDP syslog to Sentinel. nftables preferred; iptables is the fallback.

set -eu
LOG=/var/log/bank-firewall.log
STATE_DIR=/var/lib/bank-fw
mkdir -p "$STATE_DIR"
touch "$LOG"

sysctl -w net.ipv4.ip_forward=1 >/dev/null

# IANA zone for RFC 3164 stamps (forward-syslog.sh). Alpine often lacks tzdata
# and would otherwise print UTC, which Sentinel would store one hour early.
if [ ! -f /usr/share/zoneinfo/Africa/Tunis ]; then
  apk add --no-cache tzdata >/dev/null 2>&1 || true
fi

if command -v nft >/dev/null 2>&1; then
  nft delete table inet bank_fw 2>/dev/null || true
  nft -f /opt/firewall/bank-fw.nft
  if [ -w /proc/sys/net/netfilter/nf_log/2 ]; then
    echo nf_log_ipv4 > /proc/sys/net/netfilter/nf_log/2 2>/dev/null || true
  fi
  echo "bank-fw-01 nftables policy loaded"
else
  echo "nft not found, loading iptables fallback"
  iptables -C FORWARD -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT 2>/dev/null \
    || iptables -I FORWARD 1 -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT
  for port in 21 22 23 80 443 3306 5432; do
    iptables -C FORWARD -s 10.0.0.10 -d 10.10.10.20 -p tcp --dport "$port" -j DROP 2>/dev/null && continue
    iptables -A FORWARD -s 10.0.0.10 -d 10.10.10.20 -p tcp --dport "$port" \
      -j LOG --log-prefix "BANK-FW-01 firewall: DENY "
    iptables -A FORWARD -s 10.0.0.10 -d 10.10.10.20 -p tcp --dport "$port" -j DROP
  done
fi

start_once() {
  script="$1"
  log="$2"
  pgrep -f "/opt/firewall/${script}" >/dev/null 2>&1 && return 0
  nohup sh "/opt/firewall/${script}" >> "$log" 2>&1 < /dev/null &
}

start_once collect-firewall-logs.sh /var/log/bank-firewall-collector.log
start_once ensure-syslog-source.sh /var/log/bank-firewall-source.log
start_once forward-syslog.sh /var/log/bank-firewall-forwarder.log

# NetFlow is fprobe on eth1 (start-softflowd.sh). Do not start the DENY-log exporter.
for pid in $(pgrep -f /opt/netflow/export-scan-flows.py 2>/dev/null || true); do
  kill "$pid" 2>/dev/null || true
done

echo "bank-fw-01 firewall ready, logs=$LOG syslog-sent=/var/log/bank-firewall-syslog-sent.log"
