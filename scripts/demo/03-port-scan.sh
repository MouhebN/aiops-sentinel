#!/usr/bin/env bash
# One controlled port scan via the existing lab scenario. Does not call AI or change incidents.
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/demo-common.sh
. "$SCRIPT_DIR/lib/demo-common.sh"

scenario "PORT_SCAN — ATTACKER-01 reconnaissance of BANK-SRV-01"

step 1 3 "Checking lab and capture sensor"
require_lab_nodes
require_bank_http
print_rolling_warmth
guard_port_scan
ok "ready for a single scan"

step 2 3 "Running the existing lab port-scan scenario once"
node_exec attacker-01 sh /opt/scenarios/port-scan.sh
record_port_scan
ok "one port-scan completed (21 22 23 80 443 3306 5432)"

step 3 3 "Waiting for Sentinel monitoring"
info "Syslog is near-real-time; NetFlow import ~15-30s; AUTO_ROLLING PCAP ~80s after eligibility."
info "Refresh Incident Detail. Do not rerun this script (cooldown ${PORT_SCAN_COOLDOWN_SECONDS}s, or DEMO_FORCE=1)."

print_expected <<'EOF'
Syslog FIREWALL_DENY evidence
NetFlow PORT_SCAN
One AUTO_ROLLING PCAP (ports 21,22,23,80,443,3306,5432)
One correlated SECURITY incident targeting BANK-SRV-01
Topology attack edge from ATTACKER-01
AI can analyze multi-source evidence (run Analyze in the UI; this script does not)
EOF
