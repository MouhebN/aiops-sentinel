#!/bin/sh
# Attach the Sentinel nfcapd collector to banklab-mgmt and record its current
# management IP for bank-fw-01. Do not hardcode a transient Docker address.

set -eu

ROOT=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
CONTAINER="${SENTINEL_NETFLOW_CONTAINER:-pfe-netflow-tools-1}"
NETWORK="${SENTINEL_NETWORK:-banklab-mgmt}"
RUNTIME="$ROOT/.runtime"
mkdir -p "$RUNTIME"

if ! docker inspect "$CONTAINER" >/dev/null 2>&1; then
  echo "container $CONTAINER is not running" >&2
  echo "start it with: cd ~/Desktop/pfe && docker compose up -d netflow-tools" >&2
  exit 1
fi

connected=$(docker inspect "$CONTAINER" --format '{{range $k, $v := .NetworkSettings.Networks}}{{$k}} {{end}}')
case " $connected " in
  *" $NETWORK "*) echo "$CONTAINER already on $NETWORK" ;;
  *)
    docker network connect --alias sentinel-netflow "$NETWORK" "$CONTAINER"
    echo "connected $CONTAINER to $NETWORK as sentinel-netflow"
    ;;
esac

ip=$(docker inspect "$CONTAINER" --format "{{(index .NetworkSettings.Networks \"$NETWORK\").IPAddress}}")
if [ -z "$ip" ]; then
  echo "could not read $CONTAINER address on $NETWORK" >&2
  exit 1
fi

printf '%s\n' "$ip" > "$RUNTIME/sentinel-netflow-host"
echo "wrote $ip to $RUNTIME/sentinel-netflow-host"
echo "bank-fw-01 will export NetFlow v5 to this IP on UDP 2055"
