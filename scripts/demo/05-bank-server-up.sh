#!/usr/bin/env bash
# Restore BANK-SRV-01 HTTP. Uses start-server.sh (stale-PID safe).
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/demo-common.sh
. "$SCRIPT_DIR/lib/demo-common.sh"

scenario "BANK-SRV-01 availability recovery"

step 1 3 "Checking lab"
require_lab_nodes
ok "lab nodes running"

step 2 3 "Starting HTTP service"
node_exec bank-srv-01 sh /opt/bank-server/start-server.sh
if wait_for_tcp "$BANK_HTTP_HOST" "$BANK_HTTP_PORT" 10 && bank_http_ok; then
  ok "GET /health succeeded"
else
  die "BANK-SRV-01 /health did not recover"
fi

step 3 3 "Waiting for Sentinel monitoring"
info "Next check cycle should mark BANK-SRV-01 UP. Existing availability incident follows lifecycle policy (do not edit it here)."

print_expected <<'EOF'
BANK-SRV-01 → UP after monitoring
HTTP_HEALTH and TCP_PORT recover
Availability condition clears according to incident lifecycle
EOF
