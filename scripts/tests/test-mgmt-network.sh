#!/usr/bin/env bash
# Unit tests for backend attachment to banklab-mgmt (Compose + startup + Sentinel dest).
# Does not require a live lab. Does not mutate Docker networks or volumes.
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

compose_file="$PROJECT_ROOT/docker-compose.yml"
start_file="$PROJECT_ROOT/scripts/start-pfe.sh"
status_file="$PROJECT_ROOT/scripts/status-pfe.sh"
env_file="$PROJECT_ROOT/scripts/lib/pfe-env.sh"
dest_file="$PROJECT_ROOT/containerlab/services/firewall/sentinel-dest.sh"
clab_file="$PROJECT_ROOT/containerlab/bank-lab.clab.yml"

service_block() {
  local name=$1
  awk -v svc="$name" '
    $0 ~ "^  " svc ":" {grab=1; print; next}
    grab && $0 ~ /^  [a-z0-9-]+:/ {exit}
    grab {print}
  ' "$compose_file"
}

# --- Compose: backend on default + external banklab-mgmt, no static IP ---
backend_yaml="$(service_block backend)"
assert_contains "$backend_yaml" "default:" "backend joins Compose default network"
assert_contains "$backend_yaml" "banklab-mgmt:" "backend joins banklab-mgmt"
assert_not_contains "$backend_yaml" "ipv4_address:" "backend has no static management IP"
assert_not_contains "$backend_yaml" "172.30.30.3" "backend yaml does not hardcode 172.30.30.3"

netflow_yaml="$(service_block netflow-tools)"
assert_contains "$netflow_yaml" "default:" "netflow-tools joins Compose default network"
assert_contains "$netflow_yaml" "banklab-mgmt:" "netflow-tools joins banklab-mgmt"

postgres_yaml="$(service_block postgres)"
assert_not_contains "$postgres_yaml" "banklab-mgmt:" "postgres stays off the lab management plane"

frontend_yaml="$(service_block frontend)"
assert_not_contains "$frontend_yaml" "banklab-mgmt:" "frontend stays off the lab management plane"
assert_contains "$frontend_yaml" "3000:80" "frontend publishes host port 3000"

compose_all="$(cat "$compose_file")"
assert_contains "$compose_all" "external: true" "banklab-mgmt is declared external"
assert_contains "$compose_all" "name: banklab-mgmt" "external network keeps the Containerlab name"
assert_contains "$compose_all" "5514:5514/udp" "Syslog UDP 5514 remains published"
assert_not_contains "$compose_all" "172.30.30.3" "compose file does not hardcode 172.30.30.3"

# --- Startup order: network exists before compose up; repair after ---
ensure_line="$(grep -n 'ensure_mgmt_network' "$start_file" | head -n1 | cut -d: -f1)"
compose_line="$(grep -n 'compose up -d' "$start_file" | head -n1 | cut -d: -f1)"
attach_line="$(grep -n 'ensure_mgmt_attachment' "$start_file" | head -n1 | cut -d: -f1)"
if [[ -n "$ensure_line" && -n "$compose_line" && "$ensure_line" -lt "$compose_line" ]]; then
  printf 'PASS  start-pfe creates/reuses banklab-mgmt before compose up\n'
  PASS=$((PASS + 1))
else
  printf 'FAIL  ensure_mgmt_network (line %s) must run before compose up (line %s)\n' \
    "$ensure_line" "$compose_line"
  FAIL=$((FAIL + 1))
fi
if [[ -n "$attach_line" && -n "$compose_line" && "$compose_line" -lt "$attach_line" ]]; then
  printf 'PASS  start-pfe repairs mgmt attachment after compose up\n'
  PASS=$((PASS + 1))
else
  printf 'FAIL  ensure_mgmt_attachment (line %s) must run after compose up (line %s)\n' \
    "$attach_line" "$compose_line"
  FAIL=$((FAIL + 1))
fi

# --- ensure_mgmt_network does not invent a second network name ---
create_block="$(awk '/^ensure_mgmt_network\(\)/,/^}/' "$env_file")"
assert_contains "$create_block" 'docker network create' "missing network is created, not skipped"
assert_contains "$create_block" '"$MGMT_NET"' "created network uses MGMT_NET (banklab-mgmt)"
assert_not_contains "$create_block" 'banklab-mgmt-2' "no duplicate management network name"
assert_eq "$MGMT_NET" "banklab-mgmt" "MGMT_NET is banklab-mgmt"
assert_eq "$MGMT_SUBNET" "172.30.30.0/24" "management subnet unchanged"
assert_eq "$MGMT_GATEWAY" "172.30.30.1" "management gateway unchanged"

# --- Status fails when a running backend is off the management network ---
status_all="$(cat "$status_file")"
assert_contains "$status_all" "MGMT_DEGRADED=1" "status records management-plane failure"
assert_contains "$status_all" 'backend not attached to $MGMT_NET' "status detects missing backend attachment"
assert_contains "$status_all" "STATUS_RC=1" "status can exit DEGRADED"
assert_contains "$status_all" "management network" "DEGRADED message includes management network"

# --- Containerlab node management IPs unchanged ---
clab_all="$(cat "$clab_file")"
assert_contains "$clab_all" "mgmt-ipv4: 172.30.30.10" "BANK-FW-01 mgmt IP unchanged"
assert_contains "$clab_all" "mgmt-ipv4: 172.30.30.11" "CORE-RTR-01 mgmt IP unchanged"
assert_contains "$clab_all" "mgmt-ipv4: 172.30.30.12" "CORE-SW-01 mgmt IP unchanged"
assert_contains "$clab_all" "network: banklab-mgmt" "lab still uses banklab-mgmt"
assert_contains "$clab_all" 'SENTINEL_SYSLOG_PORT: "5514"' "firewall Syslog port remains 5514"

# --- is_mgmt_ipv4 ---
assert_true "172.30.30.2 is a mgmt address" is_mgmt_ipv4 172.30.30.2
assert_true "172.30.30.254 is a mgmt address" is_mgmt_ipv4 172.30.30.254
assert_false "172.22.0.6 is not a mgmt address" is_mgmt_ipv4 172.22.0.6
assert_false "empty is not a mgmt address" is_mgmt_ipv4 ""

# --- Sentinel dest: runtime wins; stale cache is not reused ---
dest_dir="$(mktemp -d)"
export SENTINEL_RUNTIME_FILE="$dest_dir/runtime"
export SENTINEL_CACHE_FILE="$dest_dir/cache"
export SENTINEL_SYSLOG_HOST=""
# shellcheck disable=SC1090
. "$dest_file"

is_sentinel_api() {
  [ "$1" = "172.30.30.7" ]
}

sentinel_ip_current() {
  [ "$1" = "172.30.30.7" ]
}

discover_on_mgmt() {
  echo "172.30.30.8"
}

printf '172.30.30.7\n' > "$SENTINEL_RUNTIME_FILE"
printf '172.30.30.99\n' > "$SENTINEL_CACHE_FILE"
assert_eq "$(resolve_sentinel_ip)" "172.30.30.7" "runtime mgmt IP wins over stale cache"

: > "$SENTINEL_RUNTIME_FILE"
printf '172.30.30.99\n' > "$SENTINEL_CACHE_FILE"
assert_eq "$(resolve_sentinel_ip)" "172.30.30.8" "stale cache is discarded and re-resolved"

printf '172.30.30.99\n' > "$SENTINEL_RUNTIME_FILE"
printf '172.30.30.99\n' > "$SENTINEL_CACHE_FILE"
assert_eq "$(resolve_sentinel_ip)" "172.30.30.8" "stale runtime IP is not reused"

: > "$SENTINEL_RUNTIME_FILE"
printf '172.30.30.7\n' > "$SENTINEL_CACHE_FILE"
assert_eq "$(resolve_sentinel_ip)" "172.30.30.7" "live cached backend IP is kept when it still answers"

assert_not_contains "$(cat "$dest_file")" "172.30.30.3" "sentinel-dest.sh does not hardcode 172.30.30.3"

rm -rf "$dest_dir"

printf '\n%s passed, %s failed\n' "$PASS" "$FAIL"
[[ "$FAIL" -eq 0 ]]
