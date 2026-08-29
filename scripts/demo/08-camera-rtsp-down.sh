#!/usr/bin/env bash
# Stop camera RTSP only. HTTP :8081 stays up.
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/demo-common.sh
. "$SCRIPT_DIR/lib/demo-common.sh"

scenario "BANK-CAMERA-01 degraded — RTSP down, HTTP up"

step 1 3 "Checking lab"
require_lab_nodes
if camera_http_ok; then
  ok "camera HTTP :${CAMERA_HTTP_PORT} up"
else
  warn "camera HTTP :${CAMERA_HTTP_PORT} not responding (will still stop RTSP)"
fi

step 2 3 "Stopping RTSP (MediaMTX + ffmpeg)"
node_exec camera-01 sh /opt/camera/stop-camera.sh
if wait_for_tcp_closed "$CAMERA_HTTP_HOST" "$CAMERA_RTSP_PORT" 10; then
  ok "RTSP :${CAMERA_RTSP_PORT} closed"
else
  die "RTSP :${CAMERA_RTSP_PORT} still open after stop-camera.sh"
fi
if wait_for_tcp "$CAMERA_HTTP_HOST" "$CAMERA_HTTP_PORT" 5; then
  ok "HTTP :${CAMERA_HTTP_PORT} still listening"
else
  die "camera HTTP listener died; node/HTTP should stay up"
fi

step 3 3 "Waiting for Sentinel monitoring"
info "RTSP_HEALTH fails; HTTP_HEALTH should stay UP (/health is liveness 200). Component DEGRADED (~30-45s)."

print_expected <<'EOF'
HTTP_HEALTH = healthy (TCP :8081, GET /health 200)
RTSP_HEALTH = failed
PING = healthy
BANK-CAMERA-01 operational status DEGRADED
EOF
