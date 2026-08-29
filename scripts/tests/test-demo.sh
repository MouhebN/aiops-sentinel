#!/usr/bin/env bash
# Static checks for scripts/demo (no live lab required).
set -u

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)"
DEMO="$ROOT/scripts/demo"

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
    printf 'FAIL  %s\n      missing: %s\n' "$msg" "$needle"
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

SCRIPTS=(
  "$DEMO/lib/demo-common.sh"
  "$DEMO/demo.sh"
  "$DEMO/01-baseline.sh"
  "$DEMO/02-sensitive-port.sh"
  "$DEMO/03-port-scan.sh"
  "$DEMO/04-bank-server-down.sh"
  "$DEMO/05-bank-server-up.sh"
  "$DEMO/06-atm-degraded.sh"
  "$DEMO/07-atm-recover.sh"
  "$DEMO/08-camera-rtsp-down.sh"
  "$DEMO/09-camera-recover.sh"
  "$DEMO/99-reset-lab.sh"
)

all=""
for f in "${SCRIPTS[@]}"; do
  assert_true "$(basename "$f") exists" test -f "$f"
  if [[ "$(basename "$f")" != "demo-common.sh" ]]; then
    assert_true "$(basename "$f") is executable" test -x "$f"
  fi
  assert_true "bash -n $(basename "$f")" bash -n "$f"
  all+="$(cat "$f")"$'\n'
done

assert_contains "$(cat "$DEMO/lib/demo-common.sh")" 'pfe-env.sh' "demo-common sources pfe-env.sh"
assert_contains "$(cat "$DEMO/01-baseline.sh")" 'demo-common.sh' "01-baseline sources demo-common"
assert_contains "$(cat "$DEMO/01-baseline.sh")" 'SCRIPT_DIR' "01-baseline resolves its directory"
assert_contains "$(cat "$DEMO/lib/demo-common.sh")" 'require_lab_nodes' "lab node validation helper exists"
assert_contains "$(cat "$DEMO/lib/demo-common.sh")" 'container_running' "reuses container_running"

scan_hits="$(grep -c '/opt/scenarios/port-scan.sh' "$DEMO/03-port-scan.sh" || true)"
assert_eq "$scan_hits" "1" "03-port-scan invokes existing scenario exactly once"
assert_not_contains "$(cat "$DEMO/03-port-scan.sh")" 'for port in' "03 does not reimplement the scan loop"
assert_not_contains "$(cat "$DEMO/02-sensitive-port.sh")" '/opt/scenarios/port-scan.sh' "02 does not call port-scan.sh"

assert_contains "$(cat "$DEMO/04-bank-server-down.sh")" '/opt/bank-server/stop-server.sh' "04 uses stop-server.sh"
assert_contains "$(cat "$DEMO/05-bank-server-up.sh")" '/opt/bank-server/start-server.sh' "05 uses start-server.sh"
assert_contains "$(cat "$DEMO/06-atm-degraded.sh")" '/opt/atm/stop-atm-service.sh' "06 uses stop-atm-service.sh"
assert_contains "$(cat "$DEMO/07-atm-recover.sh")" '/opt/atm/start-atm-service.sh' "07 uses start-atm-service.sh"
assert_contains "$(cat "$DEMO/08-camera-rtsp-down.sh")" '/opt/camera/stop-camera.sh' "08 uses stop-camera.sh"
assert_contains "$(cat "$DEMO/09-camera-recover.sh")" '/opt/camera/start-camera.sh' "09 uses start-camera.sh"
assert_contains "$(cat "$DEMO/99-reset-lab.sh")" 'recover_node_daemons' "reset reuses recover_node_daemons"

assert_not_contains "$all" 'compose down' "demo suite never compose down"
assert_not_contains "$all" 'docker compose down' "demo suite never docker compose down"
assert_not_contains "$all" 'clab destroy' "demo suite never destroys Containerlab"
assert_not_contains "$all" 'DROP TABLE' "demo suite never drops tables"
assert_not_contains "$all" 'DELETE FROM' "demo suite never deletes DB rows"
assert_not_contains "$all" 'flyway' "demo suite never runs Flyway"
assert_not_contains "$all" 'postgres_data' "demo suite never touches Postgres volume"
assert_not_contains "$all" '/api/incidents' "demo suite does not call incident APIs"
assert_not_contains "$all" 'psql' "demo suite does not use psql"
assert_contains "$(cat "$DEMO/99-reset-lab.sh")" 'preserved' "reset states history is preserved"

assert_contains "$(cat "$DEMO/03-port-scan.sh")" 'guard_port_scan' "03 refuses a silent second scan"
assert_contains "$(cat "$DEMO/03-port-scan.sh")" 'print_rolling_warmth' "03 checks rolling buffer warmth"

assert_contains "$(cat "$DEMO/demo.sh")" 'port-scan' "wrapper lists port-scan"
assert_true "docs/demo-scenarios.md exists" test -f "$ROOT/docs/demo-scenarios.md"

printf '\n%s passed, %s failed\n' "$PASS" "$FAIL"
[[ "$FAIL" -eq 0 ]]
