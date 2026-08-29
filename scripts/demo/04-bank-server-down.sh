#!/usr/bin/env bash
# Stop BANK-SRV-01 HTTP only. Node and PING stay up.
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/demo-common.sh
. "$SCRIPT_DIR/lib/demo-common.sh"

scenario "BANK-SRV-01 availability outage"

step 1 3 "Checking lab"
require_lab_nodes
require_bank_http
ok "BANK-SRV-01 HTTP was healthy"

step 2 3 "Stopping HTTP service"
node_exec bank-srv-01 sh /opt/bank-server/stop-server.sh
if wait_for_tcp_closed "$BANK_HTTP_HOST" "$BANK_HTTP_PORT" 10; then
  ok ":${BANK_HTTP_PORT} unavailable (container still running)"
else
  die "BANK-SRV-01 :${BANK_HTTP_PORT} still open after stop-server.sh"
fi
container_running "$(lab_container bank-srv-01)" || die "bank-srv-01 container stopped unexpectedly"

step 3 3 "Waiting for Sentinel monitoring"
info "HTTP_HEALTH / TCP_PORT fail on the next check cycle (~30-45s). Refresh Components and Incidents."

print_expected <<'EOF'
HTTP_HEALTH fails; TCP_PORT fails
BANK-SRV-01 operational status DOWN
AVAILABILITY incident
Topology node shows DOWN
PING may still succeed (node is up)
EOF
