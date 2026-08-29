#!/usr/bin/env bash
# Restore lab service processes. Does not wipe Sentinel history or destroy the lab.
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/demo-common.sh
. "$SCRIPT_DIR/lib/demo-common.sh"

scenario "Reset lab services to a healthy baseline"

step 1 4 "Checking nodes"
require_lab_nodes
ok "all lab containers running"

step 2 4 "Restoring in-node services (existing start scripts)"
recover_node_daemons
ok "BANK-SRV / ATM / camera / SNMP start scripts invoked"

step 3 4 "Verifying local endpoints"
wait_for_tcp "$BANK_HTTP_HOST" "$BANK_HTTP_PORT" 10 || die "BANK-SRV :8080 still down"
bank_http_ok || die "BANK-SRV /health failed"
wait_for_tcp "$ATM_TCP_HOST" "$ATM_TCP_PORT" 10 || die "ATM :9090 still down"
wait_for_tcp "$CAMERA_HTTP_HOST" "$CAMERA_HTTP_PORT" 10 || die "camera HTTP still down"
wait_for_tcp "$CAMERA_HTTP_HOST" "$CAMERA_RTSP_PORT" 15 || die "camera RTSP still down"
ok "BANK-SRV :8080, ATM :9090, camera :8081 and :8554 are up"

step 4 4 "Read-only status"
"$PFE_SCRIPTS_DIR/status-pfe.sh" || warn "status-pfe reported DEGRADED; services above were verified locally"

printf '\nHistorical Sentinel incidents/events are preserved.\n'
printf 'This reset does not delete Postgres data, events, incidents, or PCAPs.\n'
printf 'It does not destroy Containerlab or stop the Compose stack.\n'

print_expected <<'EOF'
Components return UP after the next monitoring cycle
Existing incidents stay in the database (ACK/RESOLVE them in the UI if needed)
EOF
