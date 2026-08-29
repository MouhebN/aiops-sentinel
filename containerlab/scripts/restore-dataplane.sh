#!/usr/bin/env bash
# Re-apply bank-lab data-plane addresses/routes from bank-lab.clab.yml.
# Idempotent when veths exist. Exits non-zero if an expected interface is missing
# (veth recreation requires Containerlab topology redeploy).
set -euo pipefail

RESTORE_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../../scripts/lib/pfe-env.sh
. "$RESTORE_DIR/../../scripts/lib/pfe-env.sh"

restore_dataplane
