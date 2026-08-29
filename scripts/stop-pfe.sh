#!/usr/bin/env bash
# Stop Containerlab bank-lab and the Sentinel Docker Compose stack.
# Does not delete Compose volumes or banklab-mgmt unless requested.
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/pfe-env.sh
. "$SCRIPT_DIR/lib/pfe-env.sh"

DELETE_MGMT_NET=0

usage() {
  cat <<EOF
Usage: ./scripts/stop-pfe.sh [--delete-mgmt-net]

Stop the bank lab then the Compose stack.
  --keep-mgmt-net is the default (Sentinel/netflow may share banklab-mgmt).
  --delete-mgmt-net also removes banklab-mgmt. Do not use this in the normal flow.

Compose volumes/data are kept (no docker compose down -v).
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      usage
      exit 0
      ;;
    --delete-mgmt-net)
      DELETE_MGMT_NET=1
      shift
      ;;
    *)
      die "unknown argument: $1"
      ;;
  esac
done

require_docker
require_file "$COMPOSE_FILE" "docker-compose.yml"
require_file "$TOPOLOGY_FILE" "Containerlab topology"

TOPOLOGY_ABS="$(topology_abs)"

section "Bank Lab"
exists="$(lab_exists_count)"
info "Stopping capture sensor before Containerlab destroy (shared BANK-FW-01 netns)..."
stop_capture_sensor
if [[ "$exists" -eq 0 ]]; then
  ok "Containerlab $LAB_NAME already absent"
else
  require_containerlab
  info "Destroying $LAB_NAME (keeping management network by default)..."
  if [[ "$DELETE_MGMT_NET" -eq 1 ]]; then
    clab destroy -t "$TOPOLOGY_ABS"
  else
    clab destroy -t "$TOPOLOGY_ABS" --keep-mgmt-net
  fi
  if [[ "$(lab_exists_count)" -ne 0 ]]; then
    die "Containerlab destroy left $LAB_NAME nodes behind"
  fi
  ok "Containerlab $LAB_NAME destroyed"
fi

section "AIOps Sentinel"
info "Stopping Docker Compose stack (volumes kept)..."
compose down
ok "Docker Compose stack stopped"

if [[ "$DELETE_MGMT_NET" -eq 1 ]]; then
  if network_exists "$MGMT_NET"; then
    docker network rm "$MGMT_NET"
    ok "Removed $MGMT_NET"
  else
    ok "$MGMT_NET already absent"
  fi
else
  if network_exists "$MGMT_NET"; then
    ok "$MGMT_NET kept ($MGMT_SUBNET)"
  else
    warn "$MGMT_NET is already absent"
  fi
fi

printf '\nStop complete.\n'
