#!/bin/sh
# Best-effort registration of BANK-FW-01 as a Sentinel FIREWALL syslog source.
# Without this mapping, UDP messages still arrive but are parsed as GENERIC.
# Does not print tokens or passwords.

set -eu

. /opt/firewall/sentinel-dest.sh

EMAIL="${SENTINEL_ADMIN_EMAIL:-admin@aiops.local}"
PASSWORD="${SENTINEL_ADMIN_PASSWORD:-admin123}"
SOURCE_NAME="BANK-FW-01"
EXPECTED_HOST="${SENTINEL_SOURCE_EXPECTED_HOST:-172.30.30.10}"

source_exists() {
  api="$1"
  body=$(wget -qO- -T 3 "${api}/api/syslog-sources/enabled" 2>/dev/null || true)
  echo "$body" | grep -q "$SOURCE_NAME"
}

extract_token() {
  printf '%s' "$1" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p'
}

register_once() {
  ip=$(resolve_sentinel_ip)
  api="http://${ip}:${SENTINEL_API_PORT}"

  if source_exists "$api"; then
    echo "syslog source $SOURCE_NAME already present on $api"
    return 0
  fi

  login=$(wget -qO- -T 5 \
    --header='Content-Type: application/json' \
    --post-data="{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\"}" \
    "${api}/api/auth/login" 2>/dev/null || true)
  token=$(extract_token "$login")
  unset login
  if [ -z "$token" ]; then
    echo "could not register syslog source on $api (login failed or Sentinel not ready)"
    return 1
  fi

  payload="{\"name\":\"${SOURCE_NAME}\",\"expectedHost\":\"${EXPECTED_HOST}\",\"deviceId\":\"bank-fw-01\",\"deviceName\":\"${SOURCE_NAME}\",\"deviceType\":\"FIREWALL\",\"location\":\"Mini-bank lab\",\"parserProfile\":\"FIREWALL\",\"enabled\":true}"
  wget -qO /dev/null -T 5 \
    --header="Authorization: Bearer ${token}" \
    --header='Content-Type: application/json' \
    --post-data="$payload" \
    "${api}/api/syslog-sources" 2>/dev/null || true
  unset token

  if source_exists "$api"; then
    echo "registered syslog source $SOURCE_NAME expectedHost=$EXPECTED_HOST on $api"
    return 0
  fi
  echo "syslog source registration did not appear on $api yet"
  return 1
}

echo "bank-fw-01 syslog source registrar started"
while true; do
  if register_once; then
    exit 0
  fi
  invalidate_sentinel_cache
  sleep 15
done
