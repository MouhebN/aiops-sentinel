#!/usr/bin/env bash
# Static checks for the Dockerized frontend (Nginx + Compose + start/status).
# Does not rebuild images. Optional live HTTP checks run when :3000 answers.
set -u

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../lib/pfe-env.sh
. "$SCRIPT_DIR/../lib/pfe-env.sh"

PASS=0
FAIL=0

assert_eq() {
  local got=$1 expected=$2 msg=$3
  if [[ "$got" == "$expected" ]]; then
    printf 'PASS  %s\n' "$msg"
    PASS=$((PASS + 1))
  else
    printf 'FAIL  %s\n      got:      %s\n      expected: %s\n' "$msg" "$got" "$expected"
    FAIL=$((FAIL + 1))
  fi
}

assert_contains() {
  local haystack=$1 needle=$2 msg=$3
  if [[ "$haystack" == *"$needle"* ]]; then
    printf 'PASS  %s\n' "$msg"
    PASS=$((PASS + 1))
  else
    printf 'FAIL  %s\n      missing: %s\n' "$msg" "$needle"
    FAIL=$((FAIL + 1))
  fi
}

assert_not_contains() {
  local haystack=$1 needle=$2 msg=$3
  if [[ "$haystack" == *"$needle"* ]]; then
    printf 'FAIL  %s\n      unexpectedly found: %s\n' "$msg" "$needle"
    FAIL=$((FAIL + 1))
  else
    printf 'PASS  %s\n' "$msg"
    PASS=$((PASS + 1))
  fi
}

assert_true() {
  local msg=$1
  shift
  if "$@"; then
    printf 'PASS  %s\n' "$msg"
    PASS=$((PASS + 1))
  else
    printf 'FAIL  %s\n' "$msg"
    FAIL=$((FAIL + 1))
  fi
}

assert_false() {
  local msg=$1
  shift
  if "$@"; then
    printf 'FAIL  %s (expected failure)\n' "$msg"
    FAIL=$((FAIL + 1))
  else
    printf 'PASS  %s\n' "$msg"
    PASS=$((PASS + 1))
  fi
}

compose_file="$PROJECT_ROOT/docker-compose.yml"
dockerfile="$PROJECT_ROOT/frontend/Dockerfile"
nginx_conf="$PROJECT_ROOT/frontend/nginx.conf"
start_file="$PROJECT_ROOT/scripts/start-pfe.sh"
status_file="$PROJECT_ROOT/scripts/status-pfe.sh"
env_file="$PROJECT_ROOT/scripts/lib/pfe-env.sh"
api_file="$PROJECT_ROOT/frontend/src/api/aiopsApi.ts"
ai_file="$PROJECT_ROOT/frontend/src/api/aiServiceApi.ts"

service_block() {
  local name=$1
  awk -v svc="$name" '
    $0 ~ "^  " svc ":" {grab=1; print; next}
    grab && $0 ~ /^  [a-z0-9-]+:/ {exit}
    grab {print}
  ' "$compose_file"
}

# --- Files exist ---
assert_true "frontend Dockerfile exists" test -f "$dockerfile"
assert_true "frontend nginx.conf exists" test -f "$nginx_conf"
assert_true "frontend .dockerignore exists" test -f "$PROJECT_ROOT/frontend/.dockerignore"

# --- Multi-stage production image ---
dockerfile_all="$(cat "$dockerfile")"
assert_contains "$dockerfile_all" "FROM node:20-alpine AS build" "Dockerfile has a Node build stage"
assert_contains "$dockerfile_all" "npm ci" "Dockerfile uses npm ci"
assert_contains "$dockerfile_all" "npm run build" "Dockerfile runs production build"
assert_contains "$dockerfile_all" "FROM nginx:1.27-alpine" "Dockerfile uses Nginx runtime"
assert_not_contains "$dockerfile_all" "npm run dev" "production image does not run Vite dev server"

# --- Nginx SPA + API proxy ---
nginx_all="$(cat "$nginx_conf")"
assert_contains "$nginx_all" "try_files \$uri \$uri/ /index.html" "Nginx SPA fallback to index.html"
assert_contains "$nginx_all" "location /api/" "Nginx proxies /api/"
assert_contains "$nginx_all" "http://backend:8080" "Nginx /api goes to backend:8080"
assert_contains "$nginx_all" "location = /api/analyze-incident" "Nginx has a dedicated FastAPI location"
assert_contains "$nginx_all" "http://fastapi:8001" "analyze-incident is proxied to fastapi:8001"
assert_contains "$nginx_all" "proxy_read_timeout 330s" "AI proxy timeout covers Ollama"
assert_contains "$nginx_all" "Authorization \$http_authorization" "Nginx forwards Authorization"
assert_contains "$nginx_all" "client_max_body_size 25m" "Nginx allows PCAP-sized uploads"
assert_not_contains "$nginx_all" "ws://" "no WebSocket upstream (app does not use WS)"
assert_not_contains "$nginx_all" "text/event-stream" "no SSE special-case (app does not use SSE)"

# --- Compose: frontend on default only, port 3000 ---
frontend_yaml="$(service_block frontend)"
assert_contains "$frontend_yaml" "context: ./frontend" "frontend builds from ./frontend"
assert_contains "$frontend_yaml" "3000:80" "frontend publishes host :3000"
assert_not_contains "$frontend_yaml" "banklab-mgmt:" "frontend is not attached to banklab-mgmt"

backend_yaml="$(service_block backend)"
assert_contains "$backend_yaml" "banklab-mgmt:" "backend still joins banklab-mgmt"

postgres_yaml="$(service_block postgres)"
assert_not_contains "$postgres_yaml" "banklab-mgmt:" "postgres stays off the lab management plane"

# --- API clients: relative URLs in production, localhost in Vite DEV ---
api_all="$(cat "$api_file")"
ai_all="$(cat "$ai_file")"
assert_contains "$api_all" "import.meta.env.DEV" "Spring API client keeps a Vite DEV default"
assert_contains "$ai_all" "import.meta.env.DEV" "AI client keeps a Vite DEV default"
assert_contains "$api_all" "http://localhost:8080" "DEV default still points at Spring :8080"
assert_contains "$ai_all" "http://localhost:8001" "DEV default still points at FastAPI :8001"
assert_not_contains "$api_all" '|| '\''http://localhost:8080'\''' "API client does not treat empty env as localhost (||)"

# --- start-pfe treats frontend as a required Compose service with HTTP wait ---
start_all="$(cat "$start_file")"
assert_contains "$start_all" "Frontend running (Docker) on :3000" "start-pfe reports Docker frontend"
assert_contains "$start_all" "wait_http" "start-pfe waits for frontend HTTP"
assert_not_contains "$start_all" "npm run dev" "start-pfe no longer tells the operator to run npm run dev"
assert_not_contains "$start_all" "UI (if started locally)" "start-pfe no longer treats UI as optional/local"

status_all="$(cat "$status_file")"
assert_contains "$status_all" "frontend HTTP :3000" "status checks frontend HTTP, not only container state"
assert_contains "$status_all" "FRONTEND_DEGRADED=1" "status can mark frontend HTTP as degraded"
assert_not_contains "$status_all" "Frontend not in Compose" "status no longer treats frontend as optional"

env_all="$(cat "$env_file")"
assert_contains "$env_all" "frontend" "REQUIRED_COMPOSE_SERVICES includes frontend"
assert_contains "$env_all" "wait_http()" "pfe-env defines wait_http"
assert_eq "$FRONTEND_HTTP_URL" "http://127.0.0.1:3000/" "FRONTEND_HTTP_URL is :3000"

printf '%s\n' "${REQUIRED_COMPOSE_SERVICES[*]}" | grep -qw frontend
if [[ $? -eq 0 ]]; then
  printf 'PASS  frontend is in REQUIRED_COMPOSE_SERVICES\n'
  PASS=$((PASS + 1))
else
  printf 'FAIL  frontend missing from REQUIRED_COMPOSE_SERVICES\n'
  FAIL=$((FAIL + 1))
fi

# --- Dangerous commands still absent from start/status ---
assert_not_contains "$start_all" "compose down" "start-pfe does not compose down"
assert_not_contains "$start_all" "clab destroy" "start-pfe does not destroy the lab as a normal path"
assert_not_contains "$status_all" "compose down" "status-pfe does not compose down"

# --- Shell syntax ---
if bash -n "$start_file" && bash -n "$status_file" && bash -n "$env_file" && bash -n "$0"; then
  printf 'PASS  bash -n start/status/pfe-env/this test\n'
  PASS=$((PASS + 1))
else
  printf 'FAIL  bash -n failed\n'
  FAIL=$((FAIL + 1))
fi

# --- docker compose config (no live mutation) ---
if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  cfg="$(compose config 2>/dev/null || true)"
  if [[ -n "$cfg" ]]; then
    assert_contains "$cfg" "frontend" "docker compose config includes frontend"
    frontend_cfg="$(printf '%s\n' "$cfg" | awk '
      $0 ~ /^  frontend:/ {grab=1; print; next}
      grab && $0 ~ /^  [a-z0-9-]+:/ {exit}
      grab {print}
    ')"
    assert_not_contains "$frontend_cfg" "banklab-mgmt" "resolved frontend config has no banklab-mgmt"
  else
    printf 'FAIL  docker compose config produced no output\n'
    FAIL=$((FAIL + 1))
  fi
else
  printf 'FAIL  docker compose is unavailable for config check\n'
  FAIL=$((FAIL + 1))
fi

# --- Optional live HTTP (do not fail the unit suite if the stack is down) ---
if http_open "$FRONTEND_HTTP_URL"; then
  printf 'PASS  live frontend HTTP :3000 answers\n'
  PASS=$((PASS + 1))
  if command -v curl >/dev/null 2>&1; then
    spa_code="$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:3000/incidents || true)"
    assert_eq "$spa_code" "200" "SPA route /incidents returns 200 via Nginx"
    api_code="$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:3000/api/auth/me || true)"
    if [[ "$api_code" == "401" || "$api_code" == "200" ]]; then
      printf 'PASS  /api/auth/me is proxied to Spring (%s)\n' "$api_code"
      PASS=$((PASS + 1))
    else
      printf 'FAIL  /api/auth/me expected 401 or 200, got %s\n' "$api_code"
      FAIL=$((FAIL + 1))
    fi
  fi
else
  printf 'SKIP  live frontend HTTP (container not serving yet)\n'
fi

printf '\n%s passed, %s failed\n' "$PASS" "$FAIL"
[[ "$FAIL" -eq 0 ]]
