#!/bin/sh
# Normal ATM traffic toward bank-srv-01 on the internal bank LAN.
# GET-only so Alpine BusyBox wget is enough (no curl install).

BASE="${BANK_SERVER_URL:-http://10.10.10.20:8080}"

log() {
  echo "$(date '+%Y-%m-%dT%H:%M:%S') [atm-01] $*"
}

request() {
  url=$1
  body=$(wget -qO- -T 5 "$url" 2>/tmp/atm-wget.err) || {
    log "FAIL GET $url $(cat /tmp/atm-wget.err 2>/dev/null | tr '\n' ' ')"
    return 0
  }
  log "OK GET $url body=$body"
}

log "starting normal ATM traffic toward $BASE"
while true; do
  request "$BASE/health"
  request "$BASE/api/account"
  sleep 10
done
