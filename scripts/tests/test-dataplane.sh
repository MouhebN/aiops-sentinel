#!/usr/bin/env bash
# Unit tests for Containerlab data-plane health and recovery decisions.
# Mocks docker/containerlab; does not touch a live lab or Compose volumes.
set -u

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../lib/pfe-env.sh
. "$SCRIPT_DIR/../lib/pfe-env.sh"

PASS=0
FAIL=0

assert_eq() {
  local got=$1 expected=$2 msg=$3
  if [[ "$got" == "$expected" ]]; then
    printf 'PASS  %s\n' "$msg"
    PASS=$((PASS + 1))
  else
    printf 'FAIL  %s\n      got:      %s\n      expected: %s\n' "$msg" "$got" "$expected"
    FAIL=$((FAIL + 1))
  fi
}

assert_contains() {
  local haystack=$1 needle=$2 msg=$3
  if printf '%s' "$haystack" | grep -Fq -- "$needle"; then
    printf 'PASS  %s\n' "$msg"
    PASS=$((PASS + 1))
  else
    printf 'FAIL  %s\n      missing: %s\n      in: %s\n' "$msg" "$needle" "$haystack"
    FAIL=$((FAIL + 1))
  fi
}

assert_not_contains() {
  local haystack=$1 needle=$2 msg=$3
  if printf '%s' "$haystack" | grep -Fq -- "$needle"; then
    printf 'FAIL  %s\n      unexpectedly found: %s\n' "$msg" "$needle"
    FAIL=$((FAIL + 1))
  else
    printf 'PASS  %s\n' "$msg"
    PASS=$((PASS + 1))
  fi
}

assert_true() {
  local msg=$1
  shift
  if "$@"; then
    printf 'PASS  %s\n' "$msg"
    PASS=$((PASS + 1))
  else
    printf 'FAIL  %s\n' "$msg"
    FAIL=$((FAIL + 1))
  fi
}

assert_false() {
  local msg=$1
  shift
  if "$@"; then
    printf 'FAIL  %s (expected failure)\n' "$msg"
    FAIL=$((FAIL + 1))
  else
    printf 'PASS  %s\n' "$msg"
    PASS=$((PASS + 1))
  fi
}

declare -A MOCK_EXISTS=()
declare -A MOCK_RUNNING=()
declare -A MOCK_IFACES=()
declare -A MOCK_ADDRS=()
declare -A MOCK_ID=()
declare -A MOCK_NETMODE=()

FW_ID="aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899"
STALE_FW_ID="deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef"
MOCK_HTTP_OK=0
CLAB_CMDS=()
DOCKER_RUNS=0
DOCKER_RMS=0
COMPOSE_CALLED=0

reset_mocks() {
  MOCK_EXISTS=()
  MOCK_RUNNING=()
  MOCK_IFACES=()
  MOCK_ADDRS=()
  MOCK_ID=()
  MOCK_NETMODE=()
  MOCK_HTTP_OK=0
  CLAB_CMDS=()
  DOCKER_RUNS=0
  DOCKER_RMS=0
  COMPOSE_CALLED=0
}

mark_node() {
  local node=$1
  local c
  c="$(lab_container "$node")"
  MOCK_EXISTS["$c"]=1
  MOCK_RUNNING["$c"]=1
}

seed_all_nodes_running() {
  local node
  for node in "${LAB_NODES[@]}"; do
    mark_node "$node"
  done
  MOCK_ID["$(lab_container bank-fw-01)"]="$FW_ID"
}

seed_all_ifaces() {
  MOCK_IFACES["$(lab_container attacker-01)"]="eth0 eth1"
  MOCK_IFACES["$(lab_container bank-fw-01)"]="eth0 eth1 eth2"
  MOCK_IFACES["$(lab_container core-rtr-01)"]="eth0 eth1 eth2"
  MOCK_IFACES["$(lab_container core-sw-01)"]="eth0 eth1 eth2 eth3 eth4"
  MOCK_IFACES["$(lab_container bank-srv-01)"]="eth0 eth1"
  MOCK_IFACES["$(lab_container atm-01)"]="eth0 eth1"
  MOCK_IFACES["$(lab_container camera-01)"]="eth0 eth1"
}

seed_all_cidrs() {
  MOCK_ADDRS["$(lab_container attacker-01):eth1"]="10.0.0.10/24"
  MOCK_ADDRS["$(lab_container bank-fw-01):eth1"]="10.0.0.1/24"
  MOCK_ADDRS["$(lab_container bank-fw-01):eth2"]="10.0.1.1/30"
  MOCK_ADDRS["$(lab_container core-rtr-01):eth1"]="10.0.1.2/30"
  MOCK_ADDRS["$(lab_container core-rtr-01):eth2"]="10.10.10.1/24"
  MOCK_ADDRS["$(lab_container bank-srv-01):eth1"]="10.10.10.20/24"
  MOCK_ADDRS["$(lab_container atm-01):eth1"]="10.10.10.30/24"
  MOCK_ADDRS["$(lab_container camera-01):eth1"]="10.10.10.40/24"
}

seed_healthy_lab() {
  seed_all_nodes_running
  seed_all_ifaces
  seed_all_cidrs
}

seed_reboot_broken_dataplane() {
  seed_all_nodes_running
  MOCK_IFACES["$(lab_container attacker-01)"]="eth0"
  MOCK_IFACES["$(lab_container bank-fw-01)"]="eth0"
  MOCK_IFACES["$(lab_container core-rtr-01)"]="eth0"
  MOCK_IFACES["$(lab_container core-sw-01)"]="eth0"
  MOCK_IFACES["$(lab_container bank-srv-01)"]="eth0"
  MOCK_IFACES["$(lab_container atm-01)"]="eth0"
  MOCK_IFACES["$(lab_container camera-01)"]="eth0"
}

docker() {
  local cmd=$1
  shift
  case "$cmd" in
    inspect)
      local fmt=""
      if [[ "${1:-}" == "-f" ]]; then
        fmt=$2
        shift 2
      fi
      local name=$1
      if [[ -z "${MOCK_EXISTS[$name]:-}" && -z "${MOCK_RUNNING[$name]:-}" ]]; then
        return 1
      fi
      if [[ -z "$fmt" ]]; then
        return 0
      fi
      if [[ "$fmt" == *'State.Running'* ]]; then
        if [[ "${MOCK_RUNNING[$name]:-}" == "1" ]]; then
          printf 'true\n'
        else
          printf 'false\n'
        fi
        return 0
      fi
      if [[ "$fmt" == *'HostConfig.NetworkMode'* ]]; then
        printf '%s\n' "${MOCK_NETMODE[$name]:-}"
        return 0
      fi
      if [[ "$fmt" == *'.Id'* ]]; then
        printf '%s\n' "${MOCK_ID[$name]:-}"
        return 0
      fi
      printf '\n'
      return 0
      ;;
    exec)
      local c=$1
      shift
      if [[ "${1:-}" == "ip" && "${2:-}" == "link" && "${3:-}" == "show" ]]; then
        local iface=$4
        local ifaces=" ${MOCK_IFACES[$c]:-} "
        [[ "$ifaces" == *" $iface "* ]]
        return
      fi
      if [[ "${1:-}" == "ip" && "${2:-}" == "-4" ]]; then
        local iface=""
        if [[ "${6:-}" == "dev" ]]; then
          iface=${7:-}
        fi
        local cidr="${MOCK_ADDRS[$c:$iface]:-}"
        if [[ -n "$cidr" ]]; then
          printf '2: %s    inet %s brd 0.0.0.0 scope global %s\n' "$iface" "$cidr" "$iface"
        fi
        return 0
      fi
      if [[ "${1:-}" == "ip" && "${2:-}" == "addr" && "${3:-}" == "add" ]]; then
        local cidr=$4
        local iface=$6
        MOCK_ADDRS["$c:$iface"]="$cidr"
        return 0
      fi
      return 0
      ;;
    rm)
      DOCKER_RMS=$((DOCKER_RMS + 1))
      local name=${2:-$1}
      unset "MOCK_EXISTS[$name]"
      unset "MOCK_RUNNING[$name]"
      unset "MOCK_NETMODE[$name]"
      unset "MOCK_IFACES[$name]"
      return 0
      ;;
    images)
      printf 'sha256:mock\n'
      return 0
      ;;
    build)
      return 0
      ;;
    run)
      DOCKER_RUNS=$((DOCKER_RUNS + 1))
      MOCK_EXISTS["$CAPTURE_SENSOR_CONTAINER"]=1
      MOCK_RUNNING["$CAPTURE_SENSOR_CONTAINER"]=1
      MOCK_NETMODE["$CAPTURE_SENSOR_CONTAINER"]="container:$FW_ID"
      MOCK_IFACES["$CAPTURE_SENSOR_CONTAINER"]="${MOCK_IFACES[$(lab_container bank-fw-01)]:-eth0 eth1 eth2}"
      MOCK_HTTP_OK=1
      return 0
      ;;
    *)
      return 0
      ;;
  esac
}

http_open() {
  [[ "${MOCK_HTTP_OK:-0}" -eq 1 ]]
}

python3() {
  # Do not probe the live sensor /health JSON from dataplane unit tests.
  return 0
}

sleep() {
  return 0
}

image_exists() {
  return 0
}

topology_abs() {
  printf '%s\n' "$TOPOLOGY_FILE"
}

clab() {
  CLAB_CMDS+=("$*")
  case "$1" in
    destroy)
      local node c
      for node in "${LAB_NODES[@]}"; do
        c="$(lab_container "$node")"
        unset "MOCK_EXISTS[$c]"
        unset "MOCK_RUNNING[$c]"
        unset "MOCK_IFACES[$c]"
        unset "MOCK_ADDRS[$c:eth1]"
        unset "MOCK_ADDRS[$c:eth2]"
      done
      ;;
    deploy)
      seed_healthy_lab
      ;;
  esac
}

compose() {
  COMPOSE_CALLED=1
  return 1
}

# --- 1. all containers absent -> deploy ---
reset_mocks
assert_eq "$(lab_state)" "absent" "absent lab_state when no nodes exist"
assert_eq "$(lab_recovery_action)" "deploy" "absent containers => deploy"

# --- 2. containers healthy -> restore (no redeploy) ---
reset_mocks
seed_healthy_lab
assert_eq "$(lab_state)" "running" "healthy lab_state is running"
assert_eq "$(classify_dataplane)" "healthy" "healthy dataplane classification"
assert_eq "$(lab_recovery_action)" "restore" "healthy lab => restore, not redeploy"

# second decision is idempotent
assert_eq "$(lab_recovery_action)" "restore" "second recovery action stays restore"

run_restore() {
  local file
  file="$(mktemp "${TMPDIR:-/tmp}/pfe-restore.XXXXXX")"
  restore_dataplane >"$file" 2>&1
  RESTORE_RC=$?
  RESTORE_OUT="$(cat "$file")"
  rm -f "$file"
}

# --- 3. interfaces present but IP missing -> restore-dataplane ---
reset_mocks
seed_all_nodes_running
seed_all_ifaces
assert_eq "$(classify_dataplane)" "missing_addrs" "ifaces present without IPs => missing_addrs"
assert_eq "$(lab_recovery_action)" "restore" "missing IPs with veths present => restore"

run_restore
assert_eq "$RESTORE_RC" "0" "restore_dataplane succeeds when veths exist"
assert_contains "$RESTORE_OUT" "bank-lab data-plane addresses restored" "restore prints restored when links exist"
assert_eq "$(classify_dataplane)" "healthy" "IPs applied by restore_dataplane"

# --- 4. critical interface missing -> redeploy ---
reset_mocks
seed_reboot_broken_dataplane
assert_eq "$(lab_state)" "running" "reboot case: containers still running"
assert_eq "$(classify_dataplane)" "missing_ifaces" "reboot case: BANK-FW-01 eth1 missing => missing_ifaces"
assert_eq "$(lab_recovery_action)" "redeploy" "missing veths => redeploy, not restore-only"

# --- 5. restore-dataplane fails if interface missing ---
reset_mocks
seed_reboot_broken_dataplane
out="$(restore_dataplane 2>&1)"
rc=$?
assert_eq "$rc" "1" "restore_dataplane exits non-zero when veth missing"
assert_contains "$out" "Cannot restore data-plane addresses:" "restore names the missing interface"
assert_contains "$out" "is missing. Containerlab topology redeploy is required." "restore tells operator to redeploy"
assert_not_contains "$out" "data-plane addresses restored" "restore does not claim success when links are gone"

reset_mocks
seed_all_nodes_running
seed_all_ifaces
MOCK_IFACES["$(lab_container bank-fw-01)"]="eth0"
out="$(restore_dataplane 2>&1)"
rc=$?
assert_eq "$rc" "1" "restore fails when only BANK-FW-01 eth1/eth2 are missing"
assert_contains "$out" "Cannot restore data-plane addresses: BANK-FW-01 eth1 is missing. Containerlab topology redeploy is required." \
  "restore message matches BANK-FW-01 eth1 missing"

# --- 6. sensor namespace points to stale firewall container ---
reset_mocks
seed_healthy_lab
MOCK_EXISTS["$CAPTURE_SENSOR_CONTAINER"]=1
MOCK_RUNNING["$CAPTURE_SENSOR_CONTAINER"]=1
MOCK_NETMODE["$CAPTURE_SENSOR_CONTAINER"]="container:$STALE_FW_ID"
MOCK_IFACES["$CAPTURE_SENSOR_CONTAINER"]="eth0 eth1 eth2"
MOCK_HTTP_OK=1
assert_false "stale NetworkMode is not current firewall" sensor_shares_fw_namespace
assert_false "stale sensor is not healthy" capture_sensor_healthy

ensure_capture_sensor >/dev/null
assert_eq "$DOCKER_RMS" "1" "stale sensor is removed"
assert_eq "$DOCKER_RUNS" "1" "stale sensor is recreated"
assert_true "recreated sensor shares current FW namespace" sensor_shares_fw_namespace
assert_true "recreated sensor is healthy" capture_sensor_healthy

# --- 7. sensor lacks eth1 -> recreate / fail health ---
reset_mocks
seed_healthy_lab
MOCK_EXISTS["$CAPTURE_SENSOR_CONTAINER"]=1
MOCK_RUNNING["$CAPTURE_SENSOR_CONTAINER"]=1
MOCK_NETMODE["$CAPTURE_SENSOR_CONTAINER"]="container:$FW_ID"
MOCK_IFACES["$CAPTURE_SENSOR_CONTAINER"]="eth0"
MOCK_HTTP_OK=1
assert_false "sensor without eth1 is not healthy" capture_sensor_healthy
assert_false "sensor_has_capture_iface fails without eth1" sensor_has_capture_iface

ensure_capture_sensor >/dev/null
assert_eq "$DOCKER_RUNS" "1" "sensor without eth1 is recreated"
assert_true "recreated sensor sees eth1" sensor_has_capture_iface

# --- 8. second start-pfe decision is idempotent ---
reset_mocks
seed_healthy_lab
first="$(lab_recovery_action)"
second="$(lab_recovery_action)"
assert_eq "$first" "restore" "first healthy pass is restore"
assert_eq "$second" "restore" "second healthy pass is restore (no redeploy)"

# --- 9. PostgreSQL/application volumes untouched (redeploy never compose down) ---
reset_mocks
seed_reboot_broken_dataplane
COMPOSE_CALLED=0
redeploy_bank_lab >/dev/null
assert_eq "$COMPOSE_CALLED" "0" "redeploy_bank_lab does not call docker compose"
assert_contains "${CLAB_CMDS[*]}" "destroy" "redeploy destroys Containerlab"
assert_contains "${CLAB_CMDS[*]}" "deploy" "redeploy deploys Containerlab"
assert_contains "${CLAB_CMDS[*]}" "--keep-mgmt-net" "redeploy keeps banklab-mgmt"
assert_eq "$(classify_dataplane)" "healthy" "dataplane healthy after mocked redeploy"
assert_eq "$(lab_recovery_action)" "restore" "after redeploy, next start is restore"

if grep -E '^[[:space:]]*(compose|docker compose)[[:space:]]+down' \
    "$PROJECT_ROOT/scripts/start-pfe.sh" \
    "$PROJECT_ROOT/scripts/lib/pfe-dataplane.sh" \
    "$PROJECT_ROOT/scripts/lib/pfe-env.sh" >/dev/null; then
  printf 'FAIL  start/recovery scripts must not invoke compose down\n'
  FAIL=$((FAIL + 1))
else
  printf 'PASS  start/recovery scripts never compose down\n'
  PASS=$((PASS + 1))
fi
if grep -E 'down[[:space:]]+-v' \
    "$PROJECT_ROOT/scripts/start-pfe.sh" \
    "$PROJECT_ROOT/scripts/lib/pfe-dataplane.sh" \
    "$PROJECT_ROOT/scripts/lib/pfe-env.sh" >/dev/null; then
  printf 'FAIL  start/recovery scripts must not use compose down -v\n'
  FAIL=$((FAIL + 1))
else
  printf 'PASS  start/recovery scripts never docker compose down -v\n'
  PASS=$((PASS + 1))
fi

# --- 10. telemetry recovery order after lab redeploy ---
start_file="$PROJECT_ROOT/scripts/start-pfe.sh"
lab_line="$(grep -n 'lab_recovery_action' "$start_file" | head -n1 | cut -d: -f1)"
restore_line="$(grep -n 'restore_dataplane' "$start_file" | head -n1 | cut -d: -f1)"
daemons_line="$(grep -n 'recover_node_daemons' "$start_file" | head -n1 | cut -d: -f1)"
fw_line="$(grep -n 'recover_firewall_forwarding' "$start_file" | head -n1 | cut -d: -f1)"
nf_line="$(grep -n 'recover_softflowd' "$start_file" | head -n1 | cut -d: -f1)"
sensor_line="$(grep -n 'ensure_capture_sensor' "$start_file" | head -n1 | cut -d: -f1)"
if [[ "$lab_line" -lt "$restore_line" && "$restore_line" -lt "$daemons_line" \
    && "$daemons_line" -lt "$fw_line" && "$fw_line" -lt "$nf_line" \
    && "$nf_line" -lt "$sensor_line" ]]; then
  printf 'PASS  start-pfe recovers lab then daemons then syslog/netflow then sensor\n'
  PASS=$((PASS + 1))
else
  printf 'FAIL  start-pfe recovery order (lab=%s restore=%s daemons=%s fw=%s nf=%s sensor=%s)\n' \
    "$lab_line" "$restore_line" "$daemons_line" "$fw_line" "$nf_line" "$sensor_line"
  FAIL=$((FAIL + 1))
fi

stop_file="$PROJECT_ROOT/scripts/stop-pfe.sh"
stop_sensor_line="$(grep -n 'stop_capture_sensor' "$stop_file" | head -n1 | cut -d: -f1)"
stop_destroy_line="$(grep -n 'clab destroy' "$stop_file" | head -n1 | cut -d: -f1)"
if [[ "$stop_sensor_line" -lt "$stop_destroy_line" ]]; then
  printf 'PASS  stop-pfe removes capture sensor before clab destroy\n'
  PASS=$((PASS + 1))
else
  printf 'FAIL  stop-pfe must stop sensor before destroying Containerlab\n'
  FAIL=$((FAIL + 1))
fi

# running+http is not enough without namespace/eth1
reset_mocks
seed_healthy_lab
MOCK_EXISTS["$CAPTURE_SENSOR_CONTAINER"]=1
MOCK_RUNNING["$CAPTURE_SENSOR_CONTAINER"]=1
MOCK_NETMODE["$CAPTURE_SENSOR_CONTAINER"]="container:$STALE_FW_ID"
MOCK_IFACES["$CAPTURE_SENSOR_CONTAINER"]="eth0 eth1 eth2"
MOCK_HTTP_OK=1
assert_false "running sensor + HTTP is not healthy when namespace is stale" capture_sensor_healthy

# current namespace by short id
reset_mocks
seed_healthy_lab
MOCK_EXISTS["$CAPTURE_SENSOR_CONTAINER"]=1
MOCK_RUNNING["$CAPTURE_SENSOR_CONTAINER"]=1
MOCK_NETMODE["$CAPTURE_SENSOR_CONTAINER"]="container:${FW_ID:0:12}"
MOCK_IFACES["$CAPTURE_SENSOR_CONTAINER"]="eth0 eth1 eth2"
MOCK_HTTP_OK=1
assert_true "short container id NetworkMode is current" sensor_shares_fw_namespace
assert_true "short-id sensor with eth1+health is healthy" capture_sensor_healthy

# partial lab refuses
reset_mocks
mark_node bank-fw-01
assert_eq "$(lab_state)" "partial" "some nodes present => partial"
assert_eq "$(lab_recovery_action)" "refuse" "partial lab => refuse (no destroy)"

printf '\n%s passed, %s failed\n' "$PASS" "$FAIL"
[[ "$FAIL" -eq 0 ]]
