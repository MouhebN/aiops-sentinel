#!/usr/bin/env bash
# Shared paths and helpers for start-pfe.sh / stop-pfe.sh / status-pfe.sh.
# Do not execute this file directly.

PFE_LIB_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PFE_SCRIPTS_DIR="$(CDPATH= cd -- "$PFE_LIB_DIR/.." && pwd)"
PROJECT_ROOT="$(CDPATH= cd -- "$PFE_SCRIPTS_DIR/.." && pwd)"
COMPOSE_FILE="$PROJECT_ROOT/docker-compose.yml"
CONTAINERLAB_DIR="$PROJECT_ROOT/containerlab"
TOPOLOGY_FILE="$CONTAINERLAB_DIR/bank-lab.clab.yml"
ATTACH_SENTINEL="$CONTAINERLAB_DIR/scripts/attach-sentinel.sh"
ATTACH_NETFLOW="$CONTAINERLAB_DIR/scripts/attach-netflow.sh"

LAB_NAME="bank-lab"
MGMT_NET="banklab-mgmt"
MGMT_SUBNET="172.30.30.0/24"
MGMT_GATEWAY="172.30.30.1"
CAMERA_IMAGE="banklab-camera:local"

LAB_NODES=(
  attacker-01
  bank-fw-01
  core-rtr-01
  core-sw-01
  bank-srv-01
  atm-01
  camera-01
)

# Compose services that must stay running (frontend is Nginx on :3000).
REQUIRED_COMPOSE_SERVICES=(postgres fastapi ollama backend netflow-tools frontend)

FRONTEND_HTTP_URL="http://127.0.0.1:3000/"

CAPTURE_SENSOR_IMAGE="pfe-capture-sensor:local"
CAPTURE_SENSOR_CONTAINER="pfe-capture-sensor"
CAPTURE_SENSOR_HEALTH="http://172.30.30.10:8090/health"
CAPTURE_SENSOR_FW_NODE="bank-fw-01"

OK_MARK="[OK]"
WARN_MARK="[WARN]"
FAIL_MARK="[FAIL]"

ok() { printf '%s %s\n' "$OK_MARK" "$*"; }
warn() { printf '%s %s\n' "$WARN_MARK" "$*" >&2; }
info() { printf '%s\n' "$*"; }
die() { printf '%s %s\n' "$FAIL_MARK" "$*" >&2; exit 1; }

section() {
  printf '\n[%s]\n' "$1"
}

require_file() {
  local path=$1
  local label=$2
  [[ -f "$path" ]] || die "$label is missing: $path"
}

require_docker() {
  command -v docker >/dev/null 2>&1 || die "Docker is not installed or not on PATH"
  docker info >/dev/null 2>&1 || die "Docker is unavailable (daemon not running or no permission)"
  docker compose version >/dev/null 2>&1 || die "docker compose is unavailable"
}

compose() {
  docker compose -f "$COMPOSE_FILE" --project-directory "$PROJECT_ROOT" "$@"
}

compose_has_service() {
  local service=$1
  compose config --services 2>/dev/null | grep -qx "$service"
}

compose_container_name() {
  local service=$1
  local name
  name="$(compose ps -a --format '{{.Name}}' "$service" 2>/dev/null | head -n1 || true)"
  if [[ -n "$name" ]]; then
    printf '%s\n' "$name"
    return 0
  fi
  case "$service" in
    backend) printf '%s\n' "${SENTINEL_CONTAINER:-pfe-backend-1}" ;;
    netflow-tools) printf '%s\n' "${SENTINEL_NETFLOW_CONTAINER:-pfe-netflow-tools-1}" ;;
    *) return 1 ;;
  esac
}

compose_service_running() {
  local service=$1
  local id
  id="$(compose ps -q "$service" 2>/dev/null || true)"
  [[ -n "$id" ]] || return 1
  [[ "$(docker inspect -f '{{.State.Running}}' "$id" 2>/dev/null || echo false)" == "true" ]]
}

container_exists() {
  docker inspect "$1" >/dev/null 2>&1
}

container_running() {
  [[ "$(docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null || echo false)" == "true" ]]
}

lab_container() {
  printf 'clab-%s-%s\n' "$LAB_NAME" "$1"
}

node_label() {
  printf '%s\n' "$1" | tr '[:lower:]' '[:upper:]'
}

lab_running_count() {
  local n=0
  local node
  for node in "${LAB_NODES[@]}"; do
    if container_running "$(lab_container "$node")"; then
      n=$((n + 1))
    fi
  done
  printf '%s\n' "$n"
}

lab_exists_count() {
  local n=0
  local node
  for node in "${LAB_NODES[@]}"; do
    if container_exists "$(lab_container "$node")"; then
      n=$((n + 1))
    fi
  done
  printf '%s\n' "$n"
}

# absent | running | partial
lab_state() {
  local running exists total
  running="$(lab_running_count)"
  exists="$(lab_exists_count)"
  total="${#LAB_NODES[@]}"
  if [[ "$running" -eq "$total" ]]; then
    printf 'running\n'
  elif [[ "$exists" -eq 0 ]]; then
    printf 'absent\n'
  else
    printf 'partial\n'
  fi
}

require_containerlab() {
  if command -v containerlab >/dev/null 2>&1; then
    return 0
  fi
  if sudo -n containerlab version >/dev/null 2>&1; then
    return 0
  fi
  die "Containerlab is not installed (containerlab not found on PATH)"
}

# sudo only for containerlab; Docker stays as the current user.
clab() {
  if [[ "$(id -u)" -eq 0 ]]; then
    command containerlab "$@"
  else
    sudo containerlab "$@"
  fi
}

topology_abs() {
  require_file "$TOPOLOGY_FILE" "Containerlab topology"
  if command -v realpath >/dev/null 2>&1; then
    realpath "$TOPOLOGY_FILE"
  else
    printf '%s\n' "$TOPOLOGY_FILE"
  fi
}

network_exists() {
  docker network inspect "$1" >/dev/null 2>&1
}

# Create banklab-mgmt only if missing, with the same subnet/gateway Containerlab uses.
# Never invent a second management network name.
ensure_mgmt_network() {
  if network_exists "$MGMT_NET"; then
    ok "$MGMT_NET exists ($MGMT_SUBNET)"
    return 0
  fi
  info "Creating $MGMT_NET ($MGMT_SUBNET) for Compose + Containerlab"
  docker network create \
    --driver bridge \
    --subnet "$MGMT_SUBNET" \
    --gateway "$MGMT_GATEWAY" \
    "$MGMT_NET" >/dev/null
  network_exists "$MGMT_NET" || die "failed to create $MGMT_NET"
  ok "$MGMT_NET created ($MGMT_SUBNET)"
}

container_on_network() {
  local container=$1
  local net=$2
  local nets
  nets="$(docker inspect -f '{{range $k, $v := .NetworkSettings.Networks}}{{println $k}}{{end}}' "$container" 2>/dev/null || true)"
  printf '%s\n' "$nets" | grep -qx "$net"
}

container_network_ip() {
  local container=$1
  local net=$2
  docker inspect -f "{{with index .NetworkSettings.Networks \"$net\"}}{{.IPAddress}}{{end}}" "$container" 2>/dev/null || true
}

is_mgmt_ipv4() {
  local ip=$1
  [[ "$ip" =~ ^172\.30\.30\.([0-9]{1,3})$ ]] || return 1
  local last="${BASH_REMATCH[1]}"
  (( last >= 1 && last <= 254 ))
}

ensure_mgmt_attachment() {
  local container=$1
  local label=$2
  container_running "$container" || die "$container is not running"
  network_exists "$MGMT_NET" || die "management network $MGMT_NET does not exist (deploy the lab first)"
  if container_on_network "$container" "$MGMT_NET"; then
    ok "$label attached to $MGMT_NET"
    return 0
  fi
  if docker network connect "$MGMT_NET" "$container"; then
    ok "$label attached to $MGMT_NET"
    return 0
  fi
  if container_on_network "$container" "$MGMT_NET"; then
    ok "$label attached to $MGMT_NET"
    return 0
  fi
  die "could not attach $container to $MGMT_NET"
}

tcp_open() {
  local host=$1
  local port=$2
  if command -v python3 >/dev/null 2>&1; then
    python3 - "$host" "$port" <<'PY'
import socket, sys
host, port = sys.argv[1], int(sys.argv[2])
try:
    s = socket.create_connection((host, port), 2)
    s.close()
except OSError:
    raise SystemExit(1)
PY
    return
  fi
  if command -v timeout >/dev/null 2>&1; then
    timeout 2 bash -c "echo >/dev/tcp/${host}/${port}" 2>/dev/null
    return
  fi
  bash -c "echo >/dev/tcp/${host}/${port}" 2>/dev/null
}

http_open() {
  local url=$1
  if command -v python3 >/dev/null 2>&1; then
    python3 - "$url" <<'PY'
import sys, urllib.request
try:
    with urllib.request.urlopen(sys.argv[1], timeout=3) as resp:
        if resp.status >= 400:
            raise SystemExit(1)
except Exception:
    raise SystemExit(1)
PY
    return
  fi
  local rest hostport
  rest="${url#http://}"
  hostport="${rest%%/*}"
  tcp_open "${hostport%%:*}" "${hostport##*:}"
}

wait_http() {
  local url=$1
  local timeout_s=${2:-30}
  local deadline=$((SECONDS + timeout_s))
  while (( SECONDS < deadline )); do
    if http_open "$url"; then
      return 0
    fi
    sleep 1
  done
  return 1
}

snmpd_running() {
  local node=$1
  local container
  container="$(lab_container "$node")"
  container_running "$container" || return 1
  docker exec "$container" sh -c '
    pidof snmpd >/dev/null 2>&1 && exit 0
    pgrep -x snmpd >/dev/null 2>&1 && exit 0
    grep -q ":00A1 " /proc/net/udp /proc/net/udp6 2>/dev/null
  ' >/dev/null 2>&1
}

# Run an existing in-node start script. Failure is a warning, not a topology destroy.
recover_exec() {
  local node=$1
  local label=$2
  shift 2
  local container out rc
  container="$(lab_container "$node")"
  if ! container_running "$container"; then
    warn "$label: node $node is not running"
    return 0
  fi
  set +e
  out="$(docker exec "$container" "$@" 2>&1)"
  rc=$?
  set -e
  if [[ "$rc" -eq 0 ]]; then
    ok "$label"
    return 0
  fi
  warn "$label recovery failed"
  if [[ -n "$out" ]]; then
    printf '%s\n' "$out" | tail -n 12 | sed 's/^/       /' >&2
  fi
  return 0
}

fw_forwarding_alive() {
  local container psout
  container="$(lab_container bank-fw-01)"
  container_running "$container" || return 1
  psout="$(docker exec "$container" ps -o args= 2>/dev/null || docker exec "$container" ps || true)"
  printf '%s\n' "$psout" | grep -q 'collect-firewall-logs.sh' || return 1
  printf '%s\n' "$psout" | grep -q 'forward-syslog.sh' || return 1
  return 0
}

softflowd_alive() {
  local container
  container="$(lab_container bank-fw-01)"
  container_running "$container" || return 1
  docker exec "$container" sh -c '
    for pid in $(pgrep -x fprobe 2>/dev/null || true); do
      state=$(awk "/^State:/{print \$2}" "/proc/$pid/status" 2>/dev/null || true)
      [ "$state" != "Z" ] && [ -n "$state" ] && exit 0
    done
    exit 1
  ' >/dev/null 2>&1
}

# PID 1 is sleep infinity; Containerlab exec does not re-run on docker/PC restart.
recover_node_daemons() {
  recover_exec bank-fw-01 "BANK-FW-01 SNMP" sh /opt/snmp/start-snmpd.sh BANK-FW-01
  recover_exec core-rtr-01 "CORE-RTR-01 SNMP" sh /opt/snmp/start-snmpd.sh CORE-RTR-01
  recover_exec core-sw-01 "CORE-SW-01 SNMP" sh /opt/snmp/start-snmpd.sh CORE-SW-01
  recover_exec bank-srv-01 "BANK-SRV-01 HTTP :8080" sh /opt/bank-server/start-server.sh
  recover_exec atm-01 "ATM-01 TCP :9090" sh /opt/atm/start-atm-service.sh
  recover_exec atm-01 "ATM-01 client" sh /opt/atm/start-atm-client.sh
  recover_exec camera-01 "BANK-CAMERA-01 HTTP+RTSP" sh /opt/camera/start-camera.sh
}

# After Sentinel/NetFlow destinations are refreshed, restore fw forwarders if they died.
recover_firewall_forwarding() {
  if fw_forwarding_alive; then
    ok "BANK-FW-01 Syslog forwarding already running"
    return 0
  fi
  recover_exec bank-fw-01 "BANK-FW-01 firewall+Syslog" sh /opt/firewall/setup-firewall.sh
}

recover_softflowd() {
  recover_exec bank-fw-01 "BANK-FW-01 fprobe (NetFlow)" env SOFTFLOWD_FORCE=1 sh /opt/netflow/start-softflowd.sh
}

image_exists() {
  [[ -n "$(docker images -q "$1" 2>/dev/null || true)" ]]
}

stop_capture_sensor() {
  if container_exists "$CAPTURE_SENSOR_CONTAINER"; then
    docker rm -f "$CAPTURE_SENSOR_CONTAINER" >/dev/null 2>&1 || true
    ok "capture sensor stopped ($CAPTURE_SENSOR_CONTAINER)"
    return 0
  fi
  ok "capture sensor already absent"
}

ensure_capture_sensor() {
  local fw
  fw="$(lab_container "$CAPTURE_SENSOR_FW_NODE")"
  if ! container_running "$fw"; then
    warn "capture sensor skipped: BANK-FW-01 is not running"
    return 0
  fi
  if capture_sensor_healthy; then
    ok "capture sensor healthy (current FW namespace, eth1 present, $CAPTURE_SENSOR_HEALTH)"
    return 0
  fi
  if container_exists "$CAPTURE_SENSOR_CONTAINER"; then
    info "recreating capture sensor (stale namespace, missing eth1, or health failed)"
    docker rm -f "$CAPTURE_SENSOR_CONTAINER" >/dev/null 2>&1 || true
  fi
  info "Building capture sensor image..."
  if ! docker build -t "$CAPTURE_SENSOR_IMAGE" "$PROJECT_ROOT/capture-sensor"; then
    warn "capture sensor image build failed (optional; live PCAP capture unavailable)"
    return 0
  fi
  if ! docker run -d \
      --name "$CAPTURE_SENSOR_CONTAINER" \
      --network "container:$fw" \
      --cap-add NET_RAW \
      --cap-add NET_ADMIN \
      --restart unless-stopped \
      "$CAPTURE_SENSOR_IMAGE" >/dev/null; then
    warn "could not start capture sensor (optional; live PCAP capture unavailable)"
    return 0
  fi
  local i=0
  while [[ "$i" -lt 12 ]]; do
    if capture_sensor_healthy; then
      ok "capture sensor started (current FW namespace, eth1 present, $CAPTURE_SENSOR_HEALTH)"
      return 0
    fi
    i=$((i + 1))
    sleep 1
  done
  if container_running "$CAPTURE_SENSOR_CONTAINER" && ! sensor_shares_fw_namespace; then
    die "capture sensor is attached to a stale firewall namespace after recreate"
  fi
  if container_running "$CAPTURE_SENSOR_CONTAINER" && ! sensor_has_capture_iface; then
    die "capture sensor cannot see BANK-FW-01 eth1 (data-plane still broken?)"
  fi
  warn "capture sensor started but health check failed at $CAPTURE_SENSOR_HEALTH"
}

# shellcheck source=pfe-dataplane.sh
. "$PFE_LIB_DIR/pfe-dataplane.sh"
