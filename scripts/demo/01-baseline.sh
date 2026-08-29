#!/usr/bin/env bash
# Healthy platform check. Does not create incidents.
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/demo-common.sh
. "$SCRIPT_DIR/lib/demo-common.sh"

scenario "Baseline — healthy lab, no attack"

step 1 4 "Lab status (read-only)"
require_lab_nodes
"$PFE_SCRIPTS_DIR/status-pfe.sh"
ok "status-pfe.sh completed"

step 2 4 "BANK-SRV-01 HTTP health"
require_bank_http
ok "BANK-SRV-01 http://${BANK_HTTP_HOST}:${BANK_HTTP_PORT}/health"

step 3 4 "Normal HTTP from ATTACKER-01 to BANK-SRV-01 :8080"
node_exec attacker-01 wget -qO- -T 2 "http://${BANK_LAN_IP}:8080/health" >/dev/null
ok "ATTACKER-01 GET http://${BANK_LAN_IP}:8080/health succeeded (port 8080 is not DENY-listed)"

step 4 4 "Normal ping ATTACKER-01 → firewall WAN"
node_exec attacker-01 ping -c 1 -W 1 "$FW_WAN_IP" >/dev/null
ok "ping ${FW_WAN_IP} ok"

print_expected <<'EOF'
Infrastructure healthy; BANK-SRV-01 UP
Normal WAN traffic may appear in NetFlow
suspiciousFlows should stay 0
No security incident expected from this scenario
EOF
