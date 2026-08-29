#!/usr/bin/env bash
# Read-only status of Compose, bank-lab, banklab-mgmt attachments, and lab services.
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/pfe-env.sh
. "$SCRIPT_DIR/lib/pfe-env.sh"

usage() {
  cat <<EOF
Usage: ./scripts/status-pfe.sh

Show Compose services, Containerlab nodes, data-plane veth health,
capture-sensor namespace/eth1, banklab-mgmt attachments, and service
reachability. Makes no changes. Exits 1 if the lab is running but
data-plane links, the capture sensor, backend management-network
attachment, or frontend HTTP are degraded.
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

require_docker
require_file "$COMPOSE_FILE" "docker-compose.yml"

STATUS_RC=0
DATAPLANE_DEGRADED=0
SENSOR_DEGRADED=0
MGMT_DEGRADED=0
FRONTEND_DEGRADED=0

section "AIOps Sentinel"
if [[ -f "$COMPOSE_FILE" ]]; then
  compose ps
else
  warn "docker-compose.yml missing"
fi
echo
for svc in "${REQUIRED_COMPOSE_SERVICES[@]}"; do
  if compose_service_running "$svc"; then
    ok "$svc running"
  else
    warn "$svc not running"
  fi
done
if compose_service_running frontend; then
  if http_open "$FRONTEND_HTTP_URL"; then
    ok "frontend HTTP :3000"
  else
    warn "frontend running but HTTP :3000 failed"
    FRONTEND_DEGRADED=1
  fi
fi

section "Bank Lab"
state="$(lab_state)"
info "lab state: $state (containers only; data-plane is checked below)"
for node in "${LAB_NODES[@]}"; do
  c="$(lab_container "$node")"
  if container_running "$c"; then
    ip="$(docker inspect -f "{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}" "$c" 2>/dev/null || true)"
    ok "$(node_label "$node") ($c) ${ip:-}"
  elif container_exists "$c"; then
    warn "$(node_label "$node") exists but is not running ($c)"
  else
    warn "$(node_label "$node") absent"
  fi
done

section "Containerlab data-plane"
report_dataplane_status
if [[ "$state" == "running" && "${DATAPLANE_DEGRADED:-0}" -eq 1 ]]; then
  STATUS_RC=1
fi

section "Integration"
backend="$(compose_container_name backend || true)"
netflow="$(compose_container_name netflow-tools || true)"
if network_exists "$MGMT_NET"; then
  ok "$MGMT_NET exists ($MGMT_SUBNET)"
  docker network inspect "$MGMT_NET" --format '{{range $id, $c := .Containers}}{{println $c.Name}}{{end}}' \
    | awk 'NF { print "     " $0 }'
  if [[ -n "$backend" ]] && container_running "$backend"; then
    if container_on_network "$backend" "$MGMT_NET"; then
      backend_ip="$(container_network_ip "$backend" "$MGMT_NET")"
      if is_mgmt_ipv4 "$backend_ip"; then
        ok "backend ($backend) attached to $MGMT_NET ($backend_ip)"
        fw="$(lab_container bank-fw-01)"
        if container_running "$fw"; then
          if docker exec "$fw" ping -c 1 -W 1 "$backend_ip" >/dev/null 2>&1; then
            ok "BANK-FW-01 reaches backend $backend_ip"
          else
            warn "BANK-FW-01 cannot ping backend $backend_ip"
            MGMT_DEGRADED=1
          fi
        fi
      else
        warn "backend attached to $MGMT_NET but has no $MGMT_SUBNET address"
        MGMT_DEGRADED=1
      fi
    else
      warn "backend not attached to $MGMT_NET"
      MGMT_DEGRADED=1
    fi
  fi
  if [[ -n "$netflow" ]] && container_running "$netflow"; then
    if container_on_network "$netflow" "$MGMT_NET"; then
      ok "netflow-tools ($netflow) attached to $MGMT_NET"
    else
      warn "netflow-tools not attached to $MGMT_NET"
      MGMT_DEGRADED=1
    fi
  fi
else
  warn "$MGMT_NET does not exist"
  if [[ -n "$backend" ]] && container_running "$backend"; then
    MGMT_DEGRADED=1
  fi
fi

section "Services"
if tcp_open 172.30.30.20 8080; then ok "Bank server :8080"; else warn "Bank server :8080 unreachable"; fi
if tcp_open 172.30.30.30 9090; then ok "ATM :9090"; else warn "ATM :9090 unreachable"; fi
if http_open http://172.30.30.40:8081/health || tcp_open 172.30.30.40 8081; then
  ok "Camera HTTP :8081"
else
  warn "Camera HTTP :8081 unreachable"
fi
if tcp_open 172.30.30.40 8554; then ok "Camera RTSP :8554"; else warn "Camera RTSP :8554 unreachable"; fi
if snmpd_running bank-fw-01; then
  ok "SNMP BANK-FW-01 :161"
else
  warn "SNMP BANK-FW-01 :161 not detected"
fi
if softflowd_alive; then
  ok "BANK-FW-01 fprobe (NetFlow v5)"
else
  warn "BANK-FW-01 fprobe not running"
fi
if snmpd_running core-rtr-01; then
  ok "SNMP CORE-RTR-01 :161"
else
  warn "SNMP CORE-RTR-01 :161 not detected"
fi
if snmpd_running core-sw-01; then
  ok "SNMP CORE-SW-01 :161"
else
  warn "SNMP CORE-SW-01 :161 not detected"
fi

section "Packet capture sensor"
report_capture_sensor_status
if [[ "$state" == "running" && "${SENSOR_DEGRADED:-0}" -eq 1 ]]; then
  STATUS_RC=1
fi

if [[ "$MGMT_DEGRADED" -eq 1 ]]; then
  STATUS_RC=1
fi
if [[ "$FRONTEND_DEGRADED" -eq 1 ]]; then
  STATUS_RC=1
fi

if [[ "$STATUS_RC" -eq 0 ]]; then
  printf '\nStatus complete. Lab health: OK. No changes were made.\n'
else
  printf '\nStatus complete. Lab health: DEGRADED (data-plane, capture sensor, management network, or frontend HTTP).\n' >&2
fi
exit "$STATUS_RC"
