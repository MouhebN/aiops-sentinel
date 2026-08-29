#!/bin/sh
# Resolve nfcapd collector IPv4 on banklab-mgmt without hardcoding a Docker address.
# Order:
#   1. SENTINEL_NETFLOW_HOST if it is already IPv4
#   2. /opt/runtime/sentinel-netflow-host (attach-netflow.sh)
#   3. last cached value
#   4. management gateway 172.30.30.1 (Compose publishes UDP 2055)

SENTINEL_NETFLOW_PORT="${SENTINEL_NETFLOW_PORT:-2055}"
SENTINEL_NETFLOW_RUNTIME_FILE="${SENTINEL_NETFLOW_RUNTIME_FILE:-/opt/runtime/sentinel-netflow-host}"
SENTINEL_NETFLOW_CACHE_FILE="${SENTINEL_NETFLOW_CACHE_FILE:-/var/lib/bank-fw/netflow.host}"
SENTINEL_MGMT_GATEWAY="${SENTINEL_MGMT_GATEWAY:-172.30.30.1}"

is_ipv4() {
  echo "$1" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$'
}

read_first_token() {
  [ -f "$1" ] || return 1
  awk 'NF { print $1; exit }' "$1" 2>/dev/null
}

resolve_netflow_collector() {
  if is_ipv4 "${SENTINEL_NETFLOW_HOST:-}"; then
    echo "$SENTINEL_NETFLOW_HOST"
    return 0
  fi
  runtime=$(read_first_token "$SENTINEL_NETFLOW_RUNTIME_FILE" || true)
  if is_ipv4 "$runtime"; then
    mkdir -p "$(dirname "$SENTINEL_NETFLOW_CACHE_FILE")"
    echo "$runtime" > "$SENTINEL_NETFLOW_CACHE_FILE"
    echo "$runtime"
    return 0
  fi
  cached=$(read_first_token "$SENTINEL_NETFLOW_CACHE_FILE" || true)
  if is_ipv4 "$cached"; then
    echo "$cached"
    return 0
  fi
  echo "$SENTINEL_MGMT_GATEWAY"
}
