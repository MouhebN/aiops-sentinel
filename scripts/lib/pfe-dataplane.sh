# Data-plane inventory and health checks for bank-lab.
# Sourced from pfe-env.sh. Do not execute this file directly.
[[ -n "${PFE_DATAPLANE_LOADED:-}" ]] && return 0
PFE_DATAPLANE_LOADED=1

# node|iface|cidr  (empty cidr = L2-only; interface must still exist)
DATAPLANE_EXPECTATIONS=(
  "attacker-01|eth1|10.0.0.10/24"
  "bank-fw-01|eth1|10.0.0.1/24"
  "bank-fw-01|eth2|10.0.1.1/30"
  "core-rtr-01|eth1|10.0.1.2/30"
  "core-rtr-01|eth2|10.10.10.1/24"
  "core-sw-01|eth1|"
  "core-sw-01|eth2|"
  "core-sw-01|eth3|"
  "core-sw-01|eth4|"
  "bank-srv-01|eth1|10.10.10.20/24"
  "atm-01|eth1|10.10.10.30/24"
  "camera-01|eth1|10.10.10.40/24"
)

container_has_iface() {
  local container=$1
  local iface=$2
  container_running "$container" || return 1
  docker exec "$container" ip link show "$iface" >/dev/null 2>&1
}

container_has_cidr() {
  local container=$1
  local iface=$2
  local cidr=$3
  local out
  container_has_iface "$container" "$iface" || return 1
  [[ -n "$cidr" ]] || return 0
  out="$(docker exec "$container" ip -4 -o addr show dev "$iface" 2>/dev/null || true)"
  printf '%s\n' "$out" | grep -F "inet $cidr" >/dev/null
}

# Prints one of: healthy | missing_addrs | missing_ifaces
classify_dataplane() {
  local spec node iface cidr container missing_ifaces=0 missing_addrs=0
  for spec in "${DATAPLANE_EXPECTATIONS[@]}"; do
    IFS='|' read -r node iface cidr <<<"$spec"
    container="$(lab_container "$node")"
    if ! container_has_iface "$container" "$iface"; then
      missing_ifaces=1
      continue
    fi
    if [[ -n "$cidr" ]] && ! container_has_cidr "$container" "$iface" "$cidr"; then
      missing_addrs=1
    fi
  done
  if [[ "$missing_ifaces" -eq 1 ]]; then
    printf 'missing_ifaces\n'
    return 0
  fi
  if [[ "$missing_addrs" -eq 1 ]]; then
    printf 'missing_addrs\n'
    return 0
  fi
  printf 'healthy\n'
}

# Prints "NODE IFACE" lines. Exit 0 if any missing, 1 if all present.
dataplane_missing_ifaces() {
  local spec node iface cidr container found=0
  for spec in "${DATAPLANE_EXPECTATIONS[@]}"; do
    IFS='|' read -r node iface cidr <<<"$spec"
    container="$(lab_container "$node")"
    if ! container_has_iface "$container" "$iface"; then
      printf '%s %s\n' "$(node_label "$node")" "$iface"
      found=1
    fi
  done
  [[ "$found" -eq 1 ]]
}

# Prints "NODE IFACE CIDR" for existing ifaces missing IPv4. Exit 0 if any.
dataplane_missing_cidrs() {
  local spec node iface cidr container found=0
  for spec in "${DATAPLANE_EXPECTATIONS[@]}"; do
    IFS='|' read -r node iface cidr <<<"$spec"
    [[ -n "$cidr" ]] || continue
    container="$(lab_container "$node")"
    if container_has_iface "$container" "$iface" && ! container_has_cidr "$container" "$iface" "$cidr"; then
      printf '%s %s %s\n' "$(node_label "$node")" "$iface" "$cidr"
      found=1
    fi
  done
  [[ "$found" -eq 1 ]]
}

report_dataplane_status() {
  local spec node iface cidr container
  DATAPLANE_DEGRADED=0
  for spec in "${DATAPLANE_EXPECTATIONS[@]}"; do
    IFS='|' read -r node iface cidr <<<"$spec"
    container="$(lab_container "$node")"
    if ! container_running "$container"; then
      warn "$(node_label "$node") $iface: NODE DOWN"
      DATAPLANE_DEGRADED=1
      continue
    fi
    if ! container_has_iface "$container" "$iface"; then
      warn "$(node_label "$node") $iface: MISSING"
      DATAPLANE_DEGRADED=1
      continue
    fi
    if [[ -n "$cidr" ]] && ! container_has_cidr "$container" "$iface" "$cidr"; then
      warn "$(node_label "$node") $iface: NO ADDR (expected $cidr)"
      DATAPLANE_DEGRADED=1
      continue
    fi
    if [[ -n "$cidr" ]]; then
      ok "$(node_label "$node") $iface: OK ($cidr)"
    else
      ok "$(node_label "$node") $iface: OK"
    fi
  done
  return 0
}

# deploy | restore | redeploy | refuse
lab_recovery_action() {
  local state dp
  state="$(lab_state)"
  case "$state" in
    absent)
      printf 'deploy\n'
      ;;
    partial)
      printf 'refuse\n'
      ;;
    running)
      dp="$(classify_dataplane)"
      case "$dp" in
        missing_ifaces) printf 'redeploy\n' ;;
        missing_addrs|healthy) printf 'restore\n' ;;
        *) printf 'restore\n' ;;
      esac
      ;;
    *)
      printf 'refuse\n'
      ;;
  esac
}

fw_container_id() {
  docker inspect -f '{{.Id}}' "$(lab_container "$CAPTURE_SENSOR_FW_NODE")" 2>/dev/null || true
}

capture_sensor_network_mode() {
  docker inspect -f '{{.HostConfig.NetworkMode}}' "$CAPTURE_SENSOR_CONTAINER" 2>/dev/null || true
}

sensor_shares_fw_namespace() {
  local mode suffix fw_id fw_name
  mode="$(capture_sensor_network_mode)"
  [[ "$mode" == container:* ]] || return 1
  suffix="${mode#container:}"
  fw_id="$(fw_container_id)"
  fw_name="$(lab_container "$CAPTURE_SENSOR_FW_NODE")"
  [[ -n "$fw_id" ]] || return 1
  [[ "$suffix" == "$fw_id" || "$suffix" == "${fw_id:0:12}" || "$suffix" == "$fw_name" ]]
}

sensor_has_capture_iface() {
  container_running "$CAPTURE_SENSOR_CONTAINER" || return 1
  docker exec "$CAPTURE_SENSOR_CONTAINER" ip link show eth1 >/dev/null 2>&1
}

capture_sensor_healthy() {
  container_running "$CAPTURE_SENSOR_CONTAINER" || return 1
  sensor_shares_fw_namespace || return 1
  sensor_has_capture_iface || return 1
  http_open "$CAPTURE_SENSOR_HEALTH" || return 1
  if command -v python3 >/dev/null 2>&1; then
    python3 - "$CAPTURE_SENSOR_HEALTH" <<'PY' || return 1
import json, sys, urllib.request
url = sys.argv[1]
try:
    with urllib.request.urlopen(url, timeout=3) as resp:
        body = json.load(resp)
except Exception:
    raise SystemExit(1)
rolling = body.get("rolling")
if not isinstance(rolling, dict):
    raise SystemExit(1)
if rolling.get("enabled") and not rolling.get("running"):
    raise SystemExit(1)
PY
  fi
}

report_capture_sensor_status() {
  SENSOR_DEGRADED=0
  if container_running "$CAPTURE_SENSOR_CONTAINER"; then
    ok "container: RUNNING"
  elif container_exists "$CAPTURE_SENSOR_CONTAINER"; then
    warn "container: EXISTS (not running)"
    SENSOR_DEGRADED=1
  else
    warn "container: ABSENT"
    SENSOR_DEGRADED=1
  fi

  if ! container_exists "$CAPTURE_SENSOR_CONTAINER"; then
    warn "namespace: N/A"
    warn "interface eth1: N/A"
    warn "health API: N/A"
    return 0
  fi

  if sensor_shares_fw_namespace; then
    ok "namespace: CURRENT FIREWALL"
  else
    warn "namespace: STALE"
    SENSOR_DEGRADED=1
  fi

  if sensor_has_capture_iface; then
    ok "interface eth1: OK"
  else
    warn "interface eth1: MISSING"
    SENSOR_DEGRADED=1
  fi

  if http_open "$CAPTURE_SENSOR_HEALTH"; then
    ok "health API: OK ($CAPTURE_SENSOR_HEALTH)"
    if command -v python3 >/dev/null 2>&1; then
      rolling_json="$(python3 - "$CAPTURE_SENSOR_HEALTH" <<'PY'
import json, sys, urllib.request
url = sys.argv[1]
try:
    with urllib.request.urlopen(url, timeout=3) as resp:
        body = json.load(resp)
except Exception:
    raise SystemExit(2)
rolling = body.get("rolling") or {}
enabled = rolling.get("enabled")
running = rolling.get("running")
print(f"{enabled}|{running}|{rolling.get('segmentCount', 0)}|{rolling.get('diskBytes', 0)}")
PY
)"
      rc=$?
      if [[ "$rc" -eq 0 ]]; then
        IFS='|' read -r roll_enabled roll_running roll_count roll_bytes <<<"$rolling_json"
        if [[ "$roll_enabled" == "True" || "$roll_enabled" == "true" ]]; then
          if [[ "$roll_running" == "True" || "$roll_running" == "true" ]]; then
            ok "rolling buffer: RUNNING (${roll_count} segments, ${roll_bytes} bytes)"
          else
            warn "rolling buffer: NOT RUNNING"
            SENSOR_DEGRADED=1
          fi
        else
          ok "rolling buffer: disabled"
        fi
      fi
    fi
  else
    warn "health API: FAIL ($CAPTURE_SENSOR_HEALTH)"
    SENSOR_DEGRADED=1
  fi
  return 0
}

add4() {
  local container=$1
  local dev=$2
  local cidr=$3
  docker exec "$container" ip addr add "$cidr" dev "$dev" 2>/dev/null || true
}

route_replace() {
  local container=$1
  local spec=$2
  # shellcheck disable=SC2086
  docker exec "$container" ip route replace $spec 2>/dev/null || true
}

route_add() {
  local container=$1
  local spec=$2
  # shellcheck disable=SC2086
  docker exec "$container" ip route add $spec 2>/dev/null || true
}

# Exit 1 if any expected veth is missing. Never prints "addresses restored".
assert_dataplane_ifaces() {
  local missing line first=1
  missing="$(dataplane_missing_ifaces || true)"
  if [[ -z "$missing" ]]; then
    return 0
  fi
  while IFS= read -r line; do
    [[ -n "$line" ]] || continue
    if [[ "$first" -eq 1 ]]; then
      printf 'Cannot restore data-plane addresses: %s is missing. Containerlab topology redeploy is required.\n' \
        "$line" >&2
      first=0
    else
      printf '%s also missing: %s\n' "$FAIL_MARK" "$line" >&2
    fi
  done <<<"$missing"
  return 1
}

apply_dataplane_addresses() {
  add4 "$(lab_container attacker-01)" eth1 10.0.0.10/24
  route_replace "$(lab_container attacker-01)" "default via 10.0.0.1"

  add4 "$(lab_container bank-fw-01)" eth1 10.0.0.1/24
  add4 "$(lab_container bank-fw-01)" eth2 10.0.1.1/30
  route_add "$(lab_container bank-fw-01)" "10.10.10.0/24 via 10.0.1.2"
  docker exec "$(lab_container bank-fw-01)" sysctl -w net.ipv4.ip_forward=1 >/dev/null 2>&1 || true

  add4 "$(lab_container core-rtr-01)" eth1 10.0.1.2/30
  add4 "$(lab_container core-rtr-01)" eth2 10.10.10.1/24
  route_add "$(lab_container core-rtr-01)" "10.0.0.0/24 via 10.0.1.1"
  docker exec "$(lab_container core-rtr-01)" sysctl -w net.ipv4.ip_forward=1 >/dev/null 2>&1 || true

  docker exec "$(lab_container core-sw-01)" sh -c '
    ip link add name br0 type bridge 2>/dev/null || true
    ip link set br0 up
    for i in 1 2 3 4; do
      ip link set eth$i master br0 2>/dev/null || true
      ip link set eth$i up 2>/dev/null || true
    done
  ' >/dev/null 2>&1 || true

  add4 "$(lab_container bank-srv-01)" eth1 10.10.10.20/24
  route_replace "$(lab_container bank-srv-01)" "default via 10.10.10.1"

  add4 "$(lab_container atm-01)" eth1 10.10.10.30/24
  route_replace "$(lab_container atm-01)" "default via 10.10.10.1"

  add4 "$(lab_container camera-01)" eth1 10.10.10.40/24
  route_replace "$(lab_container camera-01)" "default via 10.10.10.1"
}

restore_dataplane() {
  local missing line
  if ! assert_dataplane_ifaces; then
    return 1
  fi
  apply_dataplane_addresses
  missing="$(dataplane_missing_cidrs || true)"
  if [[ -n "$missing" ]]; then
    while IFS= read -r line; do
      [[ -n "$line" ]] || continue
      printf '%s data-plane address still missing after restore: %s\n' "$FAIL_MARK" "$line" >&2
    done <<<"$missing"
    return 1
  fi
  printf 'bank-lab data-plane addresses restored\n'
  return 0
}

deploy_bank_lab() {
  if ! image_exists "$CAMERA_IMAGE"; then
    die "Docker image $CAMERA_IMAGE is missing. Build once with: $CONTAINERLAB_DIR/scripts/build-lab-images.sh"
  fi
  info "Deploying Containerlab $LAB_NAME ..."
  clab deploy -t "$(topology_abs)"
  if [[ "$(lab_state)" != "running" ]]; then
    die "Containerlab deploy finished but not all $LAB_NAME nodes are running"
  fi
  ok "Containerlab deployed"
}

# Destroy+deploy lab only. Does not stop the Compose stack. Keeps banklab-mgmt and volumes.
redeploy_bank_lab() {
  stop_capture_sensor
  info "Data-plane veth interfaces are missing; redeploying Containerlab (Compose and volumes kept)..."
  clab destroy -t "$(topology_abs)" --keep-mgmt-net
  if [[ "$(lab_exists_count)" -ne 0 ]]; then
    die "Containerlab destroy left $LAB_NAME nodes behind"
  fi
  deploy_bank_lab
  if [[ "$(classify_dataplane)" == "missing_ifaces" ]]; then
    die "Containerlab redeploy finished but expected data-plane interfaces are still missing"
  fi
  ok "data-plane links present after redeploy"
}
