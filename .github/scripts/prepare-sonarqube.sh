#!/usr/bin/env bash
# Bootstrap an ephemeral SonarQube: replace the default admin password,
# install a quality gate that fails on blocker issues and vulnerabilities,
# and export SONAR_TOKEN for the scanner.
set -euo pipefail

BASE_URL="${SONAR_HOST_URL:-http://127.0.0.1:9000}"
PASS="${SONAR_ADMIN_PASSWORD:?SONAR_ADMIN_PASSWORD is required}"
GATE_NAME="AIOps Sentinel"

if [[ -n "${GITHUB_ENV:-}" ]]; then
  echo "::add-mask::${PASS}"
fi

change_password() {
  local code
  for _ in $(seq 1 20); do
    code="$(curl -s -o /tmp/sonar-pw.json -w "%{http_code}" \
      -u "admin:admin" -X POST \
      --data-urlencode "login=admin" \
      --data-urlencode "previousPassword=admin" \
      --data-urlencode "password=${PASS}" \
      "${BASE_URL}/api/users/change_password" || true)"
    if [[ "$code" == "204" || "$code" == "200" ]]; then
      echo "admin password updated" >&2
      return 0
    fi
    sleep 5
  done
  echo "change_password failed with HTTP ${code}" >&2
  cat /tmp/sonar-pw.json >&2 || true
  return 1
}

auth_curl() {
  curl -s -u "admin:${PASS}" "$@"
}

add_first_condition() {
  local op="$1"
  local threshold="$2"
  shift 2
  local metric code
  for metric in "$@"; do
    code="$(curl -s -o /tmp/sonar-condition.json -w "%{http_code}" \
      -u "admin:${PASS}" -X POST \
      --data-urlencode "gateName=${GATE_NAME}" \
      --data-urlencode "metric=${metric}" \
      --data-urlencode "op=${op}" \
      --data-urlencode "error=${threshold}" \
      "${BASE_URL}/api/qualitygates/create_condition" || true)"
    if [[ "$code" == "200" ]]; then
      echo "quality gate condition: ${metric} ${op} ${threshold}" >&2
      return 0
    fi
    echo "skip metric ${metric} (HTTP ${code})" >&2
  done
  return 1
}

change_password

create_code="$(curl -s -o /tmp/sonar-gate.json -w "%{http_code}" \
  -u "admin:${PASS}" -X POST \
  --data-urlencode "name=${GATE_NAME}" \
  "${BASE_URL}/api/qualitygates/create")"
if [[ "$create_code" != "200" ]]; then
  echo "quality gate create failed with HTTP ${create_code}" >&2
  cat /tmp/sonar-gate.json >&2 || true
  exit 1
fi

add_first_condition GT 0 \
  software_quality_blocker_issues \
  blocker_violations

if ! add_first_condition GT 0 \
  vulnerabilities \
  software_quality_security_issues; then
  add_first_condition GT 1 \
    software_quality_security_rating \
    security_rating
fi

default_code="$(curl -s -o /tmp/sonar-default.json -w "%{http_code}" \
  -u "admin:${PASS}" -X POST \
  --data-urlencode "name=${GATE_NAME}" \
  "${BASE_URL}/api/qualitygates/set_as_default")"
if [[ "$default_code" != "204" && "$default_code" != "200" ]]; then
  echo "set_as_default failed with HTTP ${default_code}" >&2
  cat /tmp/sonar-default.json >&2 || true
  exit 1
fi
echo "default quality gate: ${GATE_NAME}" >&2

token_json="$(auth_curl -X POST \
  --data-urlencode "name=ci-scan" \
  "${BASE_URL}/api/user_tokens/generate")"
token="$(printf '%s' "$token_json" | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')"

if [[ -n "${GITHUB_ENV:-}" ]]; then
  echo "::add-mask::${token}"
  echo "SONAR_TOKEN=${token}" >> "$GITHUB_ENV"
  echo "SONAR_ADMIN_PASSWORD=${PASS}" >> "$GITHUB_ENV"
else
  echo "SONAR_TOKEN=${token}"
fi
