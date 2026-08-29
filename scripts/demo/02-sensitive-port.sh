#!/usr/bin/env bash
# Five TCP attempts to SSH only. Not a port scan.
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/demo-common.sh
. "$SCRIPT_DIR/lib/demo-common.sh"

scenario "SENSITIVE_PORT_ACCESS — SSH :22 only (not PORT_SCAN)"

step 1 3 "Checking lab"
require_lab_nodes
require_bank_http
ok "lab nodes running; BANK-SRV HTTP up"

step 2 3 "Five TCP attempts from ATTACKER-01 to ${BANK_LAN_IP}:22 (1s apart)"
i=1
while [[ "$i" -le 5 ]]; do
  node_exec attacker-01 nc -z -w1 "$BANK_LAN_IP" 22 >/dev/null 2>&1 || true
  info "  attempt ${i}/5"
  if [[ "$i" -lt 5 ]]; then
    sleep 1
  fi
  i=$((i + 1))
done
ok "five blocked SSH attempts sent"

step 3 3 "Waiting for Sentinel monitoring"
info "Firewall DENY + NetFlow import usually land within ~30-45s. Refresh the UI; do not rerun this script immediately."

print_expected <<'EOF'
Firewall Syslog DENY for tcp/22
NetFlow anomaly SENSITIVE_PORT_ACCESS (not PORT_SCAN)
One SECURITY incident
Source 10.0.0.10 → destination 10.10.10.20 port 22
EOF
