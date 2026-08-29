#!/usr/bin/env bash
# Restore ATM TCP :9090. Does not restart the ATM client unless it is already managed in-node.
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/demo-common.sh
. "$SCRIPT_DIR/lib/demo-common.sh"

scenario "ATM-01 recovery — TCP :9090"

step 1 2 "Starting ATM TCP service"
require_lab_nodes
node_exec atm-01 sh /opt/atm/start-atm-service.sh
if wait_for_tcp "$ATM_TCP_HOST" "$ATM_TCP_PORT" 10; then
  ok "ATM TCP :${ATM_TCP_PORT} listening"
else
  die "ATM :${ATM_TCP_PORT} did not come back"
fi

step 2 2 "Waiting for Sentinel monitoring"
info "TCP_PORT should recover on the next cycle (~30-45s)."

print_expected <<'EOF'
TCP_PORT healthy
ATM-01 returns to UP if PING is still healthy
EOF
