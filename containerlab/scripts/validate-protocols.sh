#!/bin/sh
# Protocol checks against bank-lab management IPs from the host (172.30.30.0/24).
# SNMP uses docker exec + net-snmp-tools already installed on the three agents.

set -eu

ROOT=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
cd "$ROOT"

ok=0
fail=0

pass() {
  ok=$((ok + 1))
  echo "OK   $*"
}

fail_msg() {
  fail=$((fail + 1))
  echo "FAIL $*"
}

python_check() {
  python3 - "$@" <<'PY'
import socket, sys, urllib.request

kind, target = sys.argv[1], sys.argv[2]
if kind == "tcp":
    host, port = target.split(":")
    s = socket.create_connection((host, int(port)), 3)
    s.close()
elif kind == "http":
    with urllib.request.urlopen(target, timeout=5) as resp:
        if resp.status >= 400:
            raise SystemExit(f"HTTP {resp.status}")
elif kind == "rtsp":
    host, rest = target.split(":", 1)
    port, path = rest.split("/", 1)
    req = f"OPTIONS rtsp://{host}:{port}/{path} RTSP/1.0\r\nCSeq: 1\r\nUser-Agent: bank-lab-validate\r\n\r\n"
    s = socket.create_connection((host, int(port)), 3)
    s.settimeout(3)
    s.sendall(req.encode("ascii"))
    data = s.recv(512).decode("utf-8", "replace")
    s.close()
    if not data.startswith("RTSP/"):
        raise SystemExit(repr(data[:120]))
else:
    raise SystemExit(f"unknown kind {kind}")
PY
}

echo "== TCP / HTTP / RTSP (host -> mgmt IPs) =="

if python_check tcp 172.30.30.20:8080; then
  pass "TCP 172.30.30.20:8080"
else
  fail_msg "TCP 172.30.30.20:8080"
fi

if python_check tcp 172.30.30.30:9090; then
  pass "TCP 172.30.30.30:9090"
else
  fail_msg "TCP 172.30.30.30:9090"
fi

if python_check http http://172.30.30.20:8080/health; then
  pass "HTTP http://172.30.30.20:8080/health"
else
  fail_msg "HTTP http://172.30.30.20:8080/health"
fi

if python_check http http://172.30.30.40:8081/health; then
  pass "HTTP http://172.30.30.40:8081/health"
else
  fail_msg "HTTP http://172.30.30.40:8081/health"
fi

if python_check rtsp 172.30.30.40:8554/live; then
  pass "RTSP OPTIONS rtsp://172.30.30.40:8554/live"
else
  fail_msg "RTSP OPTIONS rtsp://172.30.30.40:8554/live"
fi

echo
echo "== SNMP v2c community=banklab (docker exec) =="

snmp_node() {
  name=$1
  ip=$2
  container="clab-bank-lab-${name}"
  if docker exec "$container" snmpget -v2c -c banklab "$ip" 1.3.6.1.2.1.1.1.0 >/tmp/banklab-snmp-$$.out 2>&1; then
    pass "SNMP sysDescr $name ($ip) $(tr '\n' ' ' < /tmp/banklab-snmp-$$.out)"
  else
    fail_msg "SNMP sysDescr $name ($ip) $(tr '\n' ' ' < /tmp/banklab-snmp-$$.out)"
  fi
  docker exec "$container" snmpget -v2c -c banklab "$ip" 1.3.6.1.2.1.1.3.0 >/dev/null 2>&1 \
    && pass "SNMP sysUpTime $name" \
    || fail_msg "SNMP sysUpTime $name"
  docker exec "$container" snmpwalk -v2c -c banklab "$ip" 1.3.6.1.2.1.2.2.1.10 >/dev/null 2>&1 \
    && pass "SNMP ifInOctets $name" \
    || fail_msg "SNMP ifInOctets $name"
}

snmp_node bank-fw-01 127.0.0.1
snmp_node core-rtr-01 127.0.0.1
snmp_node core-sw-01 127.0.0.1

rm -f /tmp/banklab-snmp-$$.out

echo
echo "passed=$ok failed=$fail"
[ "$fail" -eq 0 ]
