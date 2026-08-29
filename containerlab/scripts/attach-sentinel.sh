#!/bin/sh
# Attach the Sentinel backend container to banklab-mgmt and record its current
# management IP for bank-fw-01. Do not hardcode 172.30.30.9 in the lab image.

set -eu

ROOT=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
CONTAINER="${SENTINEL_CONTAINER:-pfe-backend-1}"
NETWORK="${SENTINEL_NETWORK:-banklab-mgmt}"
RUNTIME="$ROOT/.runtime"
mkdir -p "$RUNTIME"

if ! docker inspect "$CONTAINER" >/dev/null 2>&1; then
  echo "container $CONTAINER is not running" >&2
  exit 1
fi

connected=$(docker inspect "$CONTAINER" --format '{{range $k, $v := .NetworkSettings.Networks}}{{$k}} {{end}}')
case " $connected " in
  *" $NETWORK "*) echo "$CONTAINER already on $NETWORK" ;;
  *)
    docker network connect --alias sentinel-syslog "$NETWORK" "$CONTAINER"
    echo "connected $CONTAINER to $NETWORK as sentinel-syslog"
    ;;
esac

ip=$(docker inspect "$CONTAINER" --format "{{(index .NetworkSettings.Networks \"$NETWORK\").IPAddress}}")
if [ -z "$ip" ]; then
  echo "could not read $CONTAINER address on $NETWORK" >&2
  exit 1
fi

printf '%s\n' "$ip" > "$RUNTIME/sentinel-syslog-host"
echo "wrote $ip to $RUNTIME/sentinel-syslog-host"
echo "bank-fw-01 will use this IP if Docker DNS is unavailable"
