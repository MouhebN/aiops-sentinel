#!/usr/bin/env bash
# Shared helpers for scripts/demo/*. Sourced, not executed.
# Reuses scripts/lib/pfe-env.sh (tcp_open, lab_container, recover_node_daemons, …).

DEMO_LIB_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
DEMO_DIR="$(CDPATH= cd -- "$DEMO_LIB_DIR/.." && pwd)"
# shellcheck source=../../lib/pfe-env.sh
. "$DEMO_DIR/../lib/pfe-env.sh"

DEMO_RUNTIME="$CONTAINERLAB_DIR/.runtime"
PORT_SCAN_STAMP="$DEMO_RUNTIME/demo-port-scan.last"
PORT_SCAN_COOLDOWN_SECONDS="${DEMO_PORT_SCAN_COOLDOWN:-180}"
BANK_HTTP_HOST="172.30.30.20"
BANK_HTTP_PORT=8080
ATM_TCP_HOST="172.30.30.30"
ATM_TCP_PORT=9090
CAMERA_HTTP_HOST="172.30.30.40"
CAMERA_HTTP_PORT=8081
CAMERA_RTSP_PORT=8554
ATTACKER_LAN_IP="10.0.0.10"
BANK_LAN_IP="10.10.10.20"
FW_WAN_IP="10.0.0.1"

scenario() {
  printf '\n[Scenario] %s\n\n' "$1"
}

step() {
  printf '[%s/%s] %s\n' "$1" "$2" "$3"
}

print_expected() {
  printf '\nExpected in Sentinel (refresh in ~30-45s):\n'
  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ -z "$line" ]] && continue
    printf '  - %s\n' "$line"
  done
  printf '\nThis script does not create, ACK, or RESOLVE incidents.\n'
}

require_node() {
  local node=$1
  local c
  c="$(lab_container "$node")"
  container_running "$c" || die "$(node_label "$node") is not running ($c). Start with ./scripts/start-pfe.sh"
}

require_lab_nodes() {
  require_docker
  local node
  for node in "${LAB_NODES[@]}"; do
    require_node "$node"
  done
}

wait_for_tcp() {
  local host=$1
  local port=$2
  local timeout_s=${3:-10}
  local start=$SECONDS
  while (( SECONDS - start < timeout_s )); do
    if tcp_open "$host" "$port"; then
      return 0
    fi
    sleep 0.2
  done
  return 1
}

wait_for_tcp_closed() {
  local host=$1
  local port=$2
  local timeout_s=${3:-10}
  local start=$SECONDS
  while (( SECONDS - start < timeout_s )); do
    if ! tcp_open "$host" "$port"; then
      return 0
    fi
    sleep 0.2
  done
  return 1
}

node_exec() {
  local node=$1
  shift
  docker exec "$(lab_container "$node")" "$@"
}

bank_http_ok() {
  http_open "http://${BANK_HTTP_HOST}:${BANK_HTTP_PORT}/health"
}

atm_tcp_ok() {
  tcp_open "$ATM_TCP_HOST" "$ATM_TCP_PORT"
}

camera_http_ok() {
  http_open "http://${CAMERA_HTTP_HOST}:${CAMERA_HTTP_PORT}/health"
}

camera_rtsp_ok() {
  tcp_open "$CAMERA_HTTP_HOST" "$CAMERA_RTSP_PORT"
}

require_bank_http() {
  bank_http_ok || die "BANK-SRV-01 HTTP :${BANK_HTTP_PORT} is not healthy"
}

guard_port_scan() {
  mkdir -p "$DEMO_RUNTIME"
  [[ -f "$PORT_SCAN_STAMP" ]] || return 0
  local now last age
  now="$(date +%s)"
  last="$(awk 'NF { print $1; exit }' "$PORT_SCAN_STAMP" 2>/dev/null || echo 0)"
  [[ "$last" =~ ^[0-9]+$ ]] || return 0
  age=$((now - last))
  if (( age < PORT_SCAN_COOLDOWN_SECONDS )) && [[ "${DEMO_FORCE:-0}" != "1" ]]; then
    die "port-scan ran ${age}s ago (cooldown ${PORT_SCAN_COOLDOWN_SECONDS}s). Wait, or rerun with DEMO_FORCE=1"
  fi
}

record_port_scan() {
  mkdir -p "$DEMO_RUNTIME"
  date +%s > "$PORT_SCAN_STAMP"
}

rolling_warn_if_cold() {
  capture_sensor_healthy || die "capture sensor is not healthy ($CAPTURE_SENSOR_HEALTH)"
  python3 - "$CAPTURE_SENSOR_HEALTH" <<'PY' || true
import json, sys, urllib.request
url = sys.argv[1]
try:
    with urllib.request.urlopen(url, timeout=3) as resp:
        body = json.load(resp)
except Exception:
    raise SystemExit(0)
rolling = body.get("rolling") or {}
count = int(rolling.get("segmentCount") or 0)
expected = int(rolling.get("expectedFiles") or 12)
running = rolling.get("running")
if rolling.get("enabled") and not running:
    print("WARN")
    raise SystemExit(0)
# Fewer than half the ring ≈ buffer not yet covering the full pre-trigger window.
if count < max(3, expected // 2):
    print(f"{count}/{expected}")
PY
}

print_rolling_warmth() {
  local hint
  hint="$(rolling_warn_if_cold)"
  if [[ "$hint" == "WARN" ]]; then
    die "rolling buffer is enabled but not running"
  elif [[ -n "$hint" ]]; then
    warn "rolling buffer is still warming (${hint} segments). Pre-trigger 60s may be incomplete."
  else
    ok "capture sensor + rolling buffer running"
  fi
}
