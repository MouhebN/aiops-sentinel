#!/bin/sh
# Resolve the Sentinel syslog destination without hardcoding the backend
# container IP (dynamic on banklab-mgmt).
#
# Order:
#   1. /opt/runtime/sentinel-syslog-host (written by attach-sentinel.sh / Compose)
#   2. SENTINEL_SYSLOG_HOST if it is already an IPv4 address
#   3. Cached IP only if it still answers Spring /actuator/health
#   4. Docker DNS for SENTINEL_SYSLOG_HOST, pfe-backend-1, backend
#   5. Probe banklab-mgmt (172.30.30.0/24) for Spring /actuator/health
#   6. Management gateway 172.30.30.1 (Compose published UDP 5514) as last resort
#
# Containerlab linux nodes typically cannot reach Docker's embedded DNS at
# 127.0.0.11:53, so step 4 often fails. Steps 1 and 5 are the stable paths.
# Never keep a stale cache after the backend container or its mgmt IP changes.

SENTINEL_SYSLOG_PORT="${SENTINEL_SYSLOG_PORT:-5514}"
SENTINEL_API_PORT="${SENTINEL_API_PORT:-8080}"
SENTINEL_MGMT_PREFIX="${SENTINEL_MGMT_PREFIX:-172.30.30}"
SENTINEL_RUNTIME_FILE="${SENTINEL_RUNTIME_FILE:-/opt/runtime/sentinel-syslog-host}"
SENTINEL_CACHE_FILE="${SENTINEL_CACHE_FILE:-/var/lib/bank-fw/sentinel.host}"

is_ipv4() {
  echo "$1" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$'
}

read_first_token() {
  [ -f "$1" ] || return 1
  awk 'NF { print $1; exit }' "$1" 2>/dev/null
}

dns_lookup() {
  name="$1"
  [ -n "$name" ] || return 1
  is_ipv4 "$name" && { echo "$name"; return 0; }

  # Containerlab linux nodes usually cannot reach Docker DNS at 127.0.0.11:53.
  # nslookup then blocks for tens of seconds. Cap lookups so forwarding stays live.
  if command -v timeout >/dev/null 2>&1; then
    ip=$( (timeout 1 getent hosts "$name") 2>/dev/null | awk '{ print $1; exit }') || true
    if is_ipv4 "$ip"; then
      echo "$ip"
      return 0
    fi
  fi
  return 1
}

is_sentinel_api() {
  ip="$1"
  body=$(wget -qO- -T 1 "http://${ip}:${SENTINEL_API_PORT}/actuator/health" 2>/dev/null || true)
  echo "$body" | grep -q "aiops-sentinel-backend"
}

discover_on_mgmt() {
  tmp="/tmp/bank-fw-alive.$$"
  : > "$tmp"

  for i in 2 3 4 5 6 7 8 9; do
    ip="${SENTINEL_MGMT_PREFIX}.$i"
    (
      ping -c 1 -W 1 "$ip" >/dev/null 2>&1 || exit 0
      echo "$ip" >> "$tmp"
    ) &
  done
  wait || true
  while IFS= read -r ip; do
    if is_sentinel_api "$ip"; then
      echo "$ip"
      rm -f "$tmp"
      return 0
    fi
  done < "$tmp"
  : > "$tmp"

  i=2
  running=0
  while [ "$i" -le 254 ]; do
    case "$i" in
      10|11|12|20|30|40|50) i=$((i + 1)); continue ;;
    esac
    ip="${SENTINEL_MGMT_PREFIX}.$i"
    (
      ping -c 1 -W 1 "$ip" >/dev/null 2>&1 || exit 0
      echo "$ip" >> "$tmp"
    ) &
    running=$((running + 1))
    if [ "$running" -ge 40 ]; then
      wait || true
      running=0
    fi
    i=$((i + 1))
  done
  wait || true

  while IFS= read -r ip; do
    is_ipv4 "$ip" || continue
    if is_sentinel_api "$ip"; then
      echo "$ip"
      rm -f "$tmp"
      return 0
    fi
  done < "$tmp"
  rm -f "$tmp"
  return 1
}

cache_host() {
  mkdir -p "$(dirname "$SENTINEL_CACHE_FILE")"
  echo "$1" > "$SENTINEL_CACHE_FILE"
}

invalidate_sentinel_cache() {
  rm -f "$SENTINEL_CACHE_FILE"
}

# True when ip is still the live backend on the management network.
sentinel_ip_current() {
  ip="$1"
  is_ipv4 "$ip" || return 1
  is_sentinel_api "$ip" && return 0
  ping -c 1 -W 1 "$ip" >/dev/null 2>&1
}

resolve_sentinel_ip() {
  runtime=$(read_first_token "$SENTINEL_RUNTIME_FILE" || true)
  if is_ipv4 "$runtime"; then
    if sentinel_ip_current "$runtime"; then
      cache_host "$runtime"
      echo "$runtime"
      return 0
    fi
    # Stale runtime file (backend recreated / mgmt IP changed).
    invalidate_sentinel_cache
  fi

  if is_ipv4 "${SENTINEL_SYSLOG_HOST:-}"; then
    if sentinel_ip_current "$SENTINEL_SYSLOG_HOST"; then
      cache_host "$SENTINEL_SYSLOG_HOST"
      echo "$SENTINEL_SYSLOG_HOST"
      return 0
    fi
  fi

  cached=$(read_first_token "$SENTINEL_CACHE_FILE" 2>/dev/null || true)
  if is_ipv4 "$cached"; then
    if is_sentinel_api "$cached"; then
      echo "$cached"
      return 0
    fi
    invalidate_sentinel_cache
  fi

  if [ -n "$runtime" ] && ! is_ipv4 "$runtime"; then
    ip=$(dns_lookup "$runtime" || true)
    if is_ipv4 "$ip"; then
      cache_host "$ip"
      echo "$ip"
      return 0
    fi
  fi

  if [ -n "${SENTINEL_SYSLOG_HOST:-}" ] && ! is_ipv4 "$SENTINEL_SYSLOG_HOST"; then
    ip=$(dns_lookup "$SENTINEL_SYSLOG_HOST" || true)
    if is_ipv4 "$ip"; then
      cache_host "$ip"
      echo "$ip"
      return 0
    fi
  fi

  ip=$(discover_on_mgmt || true)
  if is_ipv4 "$ip"; then
    cache_host "$ip"
    echo "$ip"
    return 0
  fi

  # Last resort: Compose publishes 5514/udp on the Docker host. The
  # banklab-mgmt gateway is stable (not the backend container address).
  echo "${SENTINEL_MGMT_PREFIX}.1"
  return 0
}
