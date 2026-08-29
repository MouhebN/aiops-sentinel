#!/usr/bin/env bash
# Start the AIOps Sentinel Compose stack and the Containerlab bank lab.
# Safe to run multiple times. Does not rebuild images or destroy a healthy lab.
# A running lab with missing data-plane veths is NOT healthy (host reboot case).
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/pfe-env.sh
. "$SCRIPT_DIR/lib/pfe-env.sh"

usage() {
  cat <<EOF
Usage: ./scripts/start-pfe.sh

Start Docker Compose (no rebuild) and Containerlab bank-lab.
Idempotent: containers running AND data-plane veths present is left in place.
Missing veths after a host reboot trigger lab redeploy (Compose/volumes kept).
In-node daemons (SNMP, HTTP, TCP, RTSP, Syslog/NetFlow) are recovered
with their existing start scripts if containers survived a Docker restart.
The lab capture sensor is recreated if it is stale, lacks eth1, or fails /health.

Project root is derived from this script location:
  $PROJECT_ROOT
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

require_docker
require_file "$COMPOSE_FILE" "docker-compose.yml"
require_file "$TOPOLOGY_FILE" "Containerlab topology"
require_file "$ATTACH_SENTINEL" "attach-sentinel.sh"
require_file "$ATTACH_NETFLOW" "attach-netflow.sh"
require_containerlab

TOPOLOGY_ABS="$(topology_abs)"
BACKEND_CONTAINER=""
NETFLOW_CONTAINER=""
DEPLOYED_NOW=0

info "Project root: $PROJECT_ROOT"
info "Topology:     $TOPOLOGY_ABS"

section "AIOps Sentinel"
ensure_mgmt_network
info "Starting Docker Compose stack (no image rebuild)..."
compose up -d --no-build

deadline=$((SECONDS + 120))
while (( SECONDS < deadline )); do
  ready=1
  for svc in "${REQUIRED_COMPOSE_SERVICES[@]}"; do
    if ! compose_service_running "$svc"; then
      ready=0
      break
    fi
  done
  if [[ "$ready" -eq 1 ]]; then
    break
  fi
  sleep 2
done

for svc in "${REQUIRED_COMPOSE_SERVICES[@]}"; do
  if ! compose_service_running "$svc"; then
    die "required Compose service '$svc' is not running"
  fi
done

ok "Docker Compose stack running"

BACKEND_CONTAINER="$(compose_container_name backend)"
NETFLOW_CONTAINER="$(compose_container_name netflow-tools)"
[[ -n "$BACKEND_CONTAINER" ]] || die "could not resolve backend container name"
[[ -n "$NETFLOW_CONTAINER" ]] || die "could not resolve netflow-tools container name"

container_running "$BACKEND_CONTAINER" || die "backend container $BACKEND_CONTAINER is not running"
ok "Backend running ($BACKEND_CONTAINER)"
if ! container_on_network "$BACKEND_CONTAINER" "$MGMT_NET"; then
  info "backend missing from $MGMT_NET after Compose; attaching"
fi
ensure_mgmt_attachment "$BACKEND_CONTAINER" "backend"
ensure_mgmt_attachment "$NETFLOW_CONTAINER" "netflow-tools"
backend_mgmt_ip="$(container_network_ip "$BACKEND_CONTAINER" "$MGMT_NET")"
if ! is_mgmt_ipv4 "$backend_mgmt_ip"; then
  die "backend has no IPv4 on $MGMT_NET ($MGMT_SUBNET)"
fi
ok "backend $MGMT_NET IP $backend_mgmt_ip"

info "Waiting for frontend HTTP :3000..."
if wait_http "$FRONTEND_HTTP_URL" 30; then
  ok "Frontend running (Docker) on :3000"
else
  die "frontend container is running but $FRONTEND_HTTP_URL did not become ready"
fi

compose_service_running fastapi || die "FastAPI is not running"
ok "FastAPI running"
ok "Postgres running"
ok "Ollama running"
ok "netflow-tools running"

section "Bank Lab"
action="$(lab_recovery_action)"
case "$action" in
  restore)
    ok "Containerlab nodes running"
    dp="$(classify_dataplane)"
    if [[ "$dp" == "healthy" ]]; then
      ok "data-plane interfaces and addresses present"
    else
      info "data-plane veths present; addresses/routes will be restored"
    fi
    ;;
  deploy)
    deploy_bank_lab
    DEPLOYED_NOW=1
    ;;
  redeploy)
    warn "data-plane veths missing (containers running is not enough after a host reboot)"
    redeploy_bank_lab
    DEPLOYED_NOW=1
    ;;
  refuse)
    die "bank-lab is partially deployed; refusing to destroy it. Fix or run ./scripts/stop-pfe.sh then ./scripts/start-pfe.sh"
    ;;
  *)
    die "unknown lab recovery action: $action"
    ;;
esac

for node in "${LAB_NODES[@]}"; do
  c="$(lab_container "$node")"
  if container_running "$c"; then
    ok "$(node_label "$node")"
  else
    die "$(node_label "$node") is not running ($c)"
  fi
done

section "Lab services"
dp="$(classify_dataplane)"
if [[ "$dp" == "missing_ifaces" ]]; then
  die "data-plane veths still missing; refusing to continue with a half-broken lab"
fi
info "Restoring data-plane addresses/routes (idempotent)..."
if ! restore_dataplane; then
  die "data-plane restore failed (missing veths require a Containerlab redeploy)"
fi
ok "data-plane verified"
info "Recovering in-node daemons (Containerlab exec does not re-run after Docker restart)..."
recover_node_daemons

section "Integration"
network_exists "$MGMT_NET" || die "$MGMT_NET does not exist"
ok "$MGMT_NET exists ($MGMT_SUBNET) — not recreated"

ensure_mgmt_attachment "$BACKEND_CONTAINER" "backend"
ensure_mgmt_attachment "$NETFLOW_CONTAINER" "netflow-tools"
backend_mgmt_ip="$(container_network_ip "$BACKEND_CONTAINER" "$MGMT_NET")"
if ! is_mgmt_ipv4 "$backend_mgmt_ip"; then
  die "backend lost its $MGMT_NET address"
fi
ok "backend reachable on $MGMT_NET as $backend_mgmt_ip"

info "Refreshing Sentinel / NetFlow destinations..."
SENTINEL_CONTAINER="$BACKEND_CONTAINER" SENTINEL_NETWORK="$MGMT_NET" \
  sh "$ATTACH_SENTINEL"
ok "Sentinel destination refreshed"
SENTINEL_NETFLOW_CONTAINER="$NETFLOW_CONTAINER" SENTINEL_NETWORK="$MGMT_NET" \
  sh "$ATTACH_NETFLOW"
ok "NetFlow destination refreshed"

recover_firewall_forwarding
recover_softflowd

section "Packet capture sensor"
ensure_capture_sensor

section "Services"
attempts=10
delay=2
if [[ "$DEPLOYED_NOW" -eq 1 ]]; then
  attempts=20
  delay=3
fi

bank_ok=0
atm_ok=0
cam_http_ok=0
cam_rtsp_ok=0
snmp_fw_ok=0
snmp_rtr_ok=0
snmp_sw_ok=0
i=0
while [[ "$i" -lt "$attempts" ]]; do
  tcp_open 172.30.30.20 8080 && bank_ok=1
  tcp_open 172.30.30.30 9090 && atm_ok=1
  if http_open http://172.30.30.40:8081/health || tcp_open 172.30.30.40 8081; then
    cam_http_ok=1
  fi
  tcp_open 172.30.30.40 8554 && cam_rtsp_ok=1
  snmpd_running bank-fw-01 && snmp_fw_ok=1
  snmpd_running core-rtr-01 && snmp_rtr_ok=1
  snmpd_running core-sw-01 && snmp_sw_ok=1
  if [[ "$bank_ok" -eq 1 && "$atm_ok" -eq 1 && "$cam_http_ok" -eq 1 && "$cam_rtsp_ok" -eq 1 \
      && "$snmp_fw_ok" -eq 1 && "$snmp_rtr_ok" -eq 1 && "$snmp_sw_ok" -eq 1 ]]; then
    break
  fi
  i=$((i + 1))
  if [[ "$i" -lt "$attempts" ]]; then
    sleep "$delay"
  fi
done

[[ "$bank_ok" -eq 1 ]] && ok "Bank server :8080" || warn "Bank server :8080 not ready yet"
[[ "$atm_ok" -eq 1 ]] && ok "ATM :9090" || warn "ATM :9090 not ready yet"
[[ "$cam_http_ok" -eq 1 ]] && ok "Camera HTTP :8081" || warn "Camera HTTP :8081 not ready yet"
[[ "$cam_rtsp_ok" -eq 1 ]] && ok "Camera RTSP :8554" || warn "Camera RTSP :8554 not ready yet"
[[ "$snmp_fw_ok" -eq 1 ]] && ok "SNMP BANK-FW-01 :161" || warn "SNMP BANK-FW-01 :161 not ready yet"
[[ "$snmp_rtr_ok" -eq 1 ]] && ok "SNMP CORE-RTR-01 :161" || warn "SNMP CORE-RTR-01 :161 not ready yet"
[[ "$snmp_sw_ok" -eq 1 ]] && ok "SNMP CORE-SW-01 :161" || warn "SNMP CORE-SW-01 :161 not ready yet"

printf '\nStartup complete.\n'
printf 'UI:                      http://localhost:3000\n'
printf 'Backend:                 http://localhost:8080\n'
printf 'FastAPI:                 http://localhost:8001\n'
printf 'Capture sensor:          %s\n' "$CAPTURE_SENSOR_HEALTH"
printf 'After reboot, run:       %s/scripts/start-pfe.sh\n' "$PROJECT_ROOT"
