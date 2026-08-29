#!/usr/bin/env bash
# Restore camera RTSP (+ HTTP if it was down).
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/demo-common.sh
. "$SCRIPT_DIR/lib/demo-common.sh"

scenario "BANK-CAMERA-01 recovery — RTSP + HTTP"

step 1 2 "Starting camera services"
require_lab_nodes
node_exec camera-01 sh /opt/camera/start-camera.sh
if wait_for_tcp "$CAMERA_HTTP_HOST" "$CAMERA_HTTP_PORT" 10 \
  && wait_for_tcp "$CAMERA_HTTP_HOST" "$CAMERA_RTSP_PORT" 15; then
  ok "HTTP :${CAMERA_HTTP_PORT} and RTSP :${CAMERA_RTSP_PORT} listening"
else
  die "camera HTTP or RTSP did not recover"
fi

step 2 2 "Waiting for Sentinel monitoring"
info "RTSP_HEALTH should recover on the next cycle (~30-45s)."

print_expected <<'EOF'
RTSP_HEALTH healthy
BANK-CAMERA-01 returns to UP if PING and HTTP_HEALTH are healthy
EOF
