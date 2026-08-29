#!/usr/bin/env bash
# Regression tests for BANK-SRV-01 start/stop stale-PID handling.
# Uses a local stub HTTP server. Does not require Containerlab.
set -u

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)"
START="$ROOT/containerlab/services/bank-server/start-server.sh"
STOP="$ROOT/containerlab/services/bank-server/stop-server.sh"
STUB="$SCRIPT_DIR/bank-server-stub.py"
FAIL_STUB="$SCRIPT_DIR/bank-server-fail.py"

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

WORKDIR=$(mktemp -d)
PORT=18780
export BANK_SERVER_PIDFILE="$WORKDIR/bank-server.pid"
export BANK_SERVER_LOG="$WORKDIR/bank-server.log"
export BANK_SERVER_SCRIPT="$STUB"
export BANK_SERVER_PORT="$PORT"
export BANK_SERVER_HEALTH_HOST="127.0.0.1"
export BANK_SERVER_BIND="127.0.0.1"

cleanup() {
  BANK_SERVER_PIDFILE="$WORKDIR/bank-server.pid" \
    BANK_SERVER_SCRIPT="$STUB" \
    BANK_SERVER_PORT="$PORT" \
    sh "$STOP" >/dev/null 2>&1 || true
  rm -rf "$WORKDIR"
}
trap cleanup EXIT

count_servers() {
  local n=0 pid cmd
  for pid in /proc/[0-9]*; do
    pid=${pid#/proc/}
    [[ -r "/proc/${pid}/cmdline" ]] || continue
    cmd=$(tr '\0' ' ' < "/proc/${pid}/cmdline" 2>/dev/null || true)
    if printf '%s' "$cmd" | grep -Fq "$STUB" && printf '%s' "$cmd" | grep -qi python; then
      n=$((n + 1))
    fi
  done
  printf '%s\n' "$n"
}

health() {
  python3 - "$PORT" <<'PY'
import sys, urllib.request
port = sys.argv[1]
try:
    with urllib.request.urlopen(f"http://127.0.0.1:{port}/health", timeout=1) as resp:
        body = resp.read().decode()
    raise SystemExit(0 if "bank-srv-01" in body else 1)
except Exception:
    raise SystemExit(1)
PY
}

assert_true "start-server.sh is valid sh" sh -n "$START"
assert_true "stop-server.sh is valid sh" sh -n "$STOP"
assert_true "bank-server-lib.sh is valid sh" sh -n "$ROOT/containerlab/services/bank-server/bank-server-lib.sh"
assert_not_contains "$(cat "$START")" 'kill -0 "$(cat "$PIDFILE")"' "start does not trust PID file via kill -0 alone"

# --- missing PID file ---
out=$(sh "$START" 2>&1) || { printf 'FAIL  start with missing pidfile\n%s\n' "$out"; FAIL=$((FAIL + 1)); out=""; }
assert_contains "$out" "started pid=" "missing PID file starts the server"
assert_true "health ok after first start" health
first_pid=$(cat "$BANK_SERVER_PIDFILE")
assert_eq "$(count_servers)" "1" "one server process after first start"

# --- healthy existing process ---
out=$(sh "$START" 2>&1)
assert_contains "$out" "already running" "healthy process is not restarted"
assert_eq "$(cat "$BANK_SERVER_PIDFILE")" "$first_pid" "PID file unchanged when already healthy"
assert_eq "$(count_servers)" "1" "no duplicate service process"

# --- restart after stop ---
out=$(sh "$STOP" 2>&1)
assert_contains "$out" "stopped" "stop reports stopped"
assert_false "health fails after stop" health
assert_false "pidfile removed after stop" test -f "$BANK_SERVER_PIDFILE"
assert_eq "$(count_servers)" "0" "no server process after stop"
out=$(sh "$START" 2>&1)
assert_contains "$out" "started pid=" "start after stop launches a new process"
assert_true "health ok after restart" health
assert_eq "$(count_servers)" "1" "one server process after restart"

# --- stale PID pointing to a dead process ---
sh "$STOP" >/dev/null 2>&1 || true
printf '999999\n' > "$BANK_SERVER_PIDFILE"
out=$(sh "$START" 2>&1)
assert_contains "$out" "stale PID" "dead PID is reported stale"
assert_contains "$out" "started pid=" "dead PID is replaced by a new start"
assert_true "health ok after dead-PID recovery" health

# --- stale PID pointing to an unrelated process ---
sh "$STOP" >/dev/null 2>&1 || true
sleep 60 &
unrelated=$!
printf '%s\n' "$unrelated" > "$BANK_SERVER_PIDFILE"
out=$(sh "$START" 2>&1)
assert_contains "$out" "stale PID" "unrelated PID is reported stale"
assert_contains "$out" "started pid=" "unrelated PID does not block start"
assert_true "unrelated sleep still running after start" kill -0 "$unrelated"
assert_true "health ok after unrelated-PID recovery" health
assert_eq "$(count_servers)" "1" "exactly one server after unrelated-PID recovery"
kill "$unrelated" 2>/dev/null || true
wait "$unrelated" 2>/dev/null || true

# --- invalid PID file contents ---
sh "$STOP" >/dev/null 2>&1 || true
printf 'not-a-pid\n' > "$BANK_SERVER_PIDFILE"
out=$(sh "$START" 2>&1)
assert_contains "$out" "invalid contents" "invalid PID contents are rejected"
assert_contains "$out" "started pid=" "invalid PID file is replaced by a start"
assert_true "health ok after invalid PID recovery" health

# --- stop does not kill unrelated process recorded in pidfile ---
sh "$STOP" >/dev/null 2>&1 || true
sleep 60 &
unrelated=$!
printf '%s\n' "$unrelated" > "$BANK_SERVER_PIDFILE"
out=$(sh "$STOP" 2>&1)
assert_contains "$out" "stopped" "stop is idempotent with foreign PID"
assert_true "stop does not kill unrelated pidfile process" kill -0 "$unrelated"
assert_false "pidfile removed even when it was foreign" test -f "$BANK_SERVER_PIDFILE"
kill "$unrelated" 2>/dev/null || true
wait "$unrelated" 2>/dev/null || true

# --- start failure leaves no PID file ---
export BANK_SERVER_SCRIPT="$FAIL_STUB"
export BANK_SERVER_LOG="$WORKDIR/fail.log"
: > "$WORKDIR/fail.log"
set +e
out=$(sh "$START" 2>&1)
rc=$?
set +e
assert_eq "$rc" "1" "failed start exits non-zero"
assert_contains "$out" "exited during startup" "failed start prints a clear error"
assert_false "failed start removes PID file" test -f "$BANK_SERVER_PIDFILE"
export BANK_SERVER_SCRIPT="$STUB"
export BANK_SERVER_LOG="$WORKDIR/bank-server.log"

printf '\n%s passed, %s failed\n' "$PASS" "$FAIL"
[[ "$FAIL" -eq 0 ]]
