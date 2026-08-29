#!/usr/bin/env bash
# Stop ATM TCP :9090 only. PING and atm-client stay up.
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/demo-common.sh
. "$SCRIPT_DIR/lib/demo-common.sh"

scenario "ATM-01 degraded — TCP :9090 down, PING up"

step 1 3 "Checking lab"
require_lab_nodes
if atm_tcp_ok; then
  ok "ATM TCP :${ATM_TCP_PORT} was listening"
else
  warn "ATM TCP :${ATM_TCP_PORT} already closed"
fi

step 2 3 "Stopping ATM TCP service only"
node_exec atm-01 sh /opt/atm/stop-atm-service.sh
if wait_for_tcp_closed "$ATM_TCP_HOST" "$ATM_TCP_PORT" 10; then
  ok ":${ATM_TCP_PORT} closed"
else
  die "ATM :${ATM_TCP_PORT} still open after stop-atm-service.sh"
fi
if node_exec atm-01 ping -c 1 -W 1 127.0.0.1 >/dev/null \
  && container_running "$(lab_container atm-01)"; then
  ok "ATM node still up (PING-capable)"
else
  die "ATM container is not running"
fi

step 3 3 "Waiting for Sentinel monitoring"
info "TCP_PORT fails; PING should stay healthy → component DEGRADED (~30-45s)."

print_expected <<'EOF'
PING = healthy
TCP_PORT = failed
ATM-01 operational status DEGRADED
Node remains on the topology (not DOWN)
EOF
