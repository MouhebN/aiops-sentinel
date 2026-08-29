#!/bin/sh
# Follow local nftables/iptables DENY lines and send RFC 3164 UDP syslog
# to Sentinel on port 5514. Only real BANK-FW-01 deny records are forwarded.

set -eu

. /opt/firewall/sentinel-dest.sh

LOG="${BANK_FW_LOG:-/var/log/bank-firewall.log}"
SENT_LOG="${BANK_FW_SYSLOG_SENT_LOG:-/var/log/bank-firewall-syslog-sent.log}"
STATE_DIR="${BANK_FW_STATE_DIR:-/var/lib/bank-fw}"
OFFSET_FILE="$STATE_DIR/forward.offset"
SEEN_FILE="$STATE_DIR/forward.seen"
HOSTNAME_ID="BANK-FW-01"

mkdir -p "$STATE_DIR"
touch "$LOG" "$SENT_LOG" "$OFFSET_FILE" "$SEEN_FILE"

field() {
  printf '%s\n' "$1" | awk -v k="$2=" '{
    for (i = 1; i <= NF; i++) {
      if (index($i, k) == 1) {
        print substr($i, length(k) + 1)
        exit
      }
    }
  }'
}

already_seen() {
  hash="$1"
  grep -qx "$hash" "$SEEN_FILE" 2>/dev/null
}

mark_seen() {
  hash="$1"
  echo "$hash" >> "$SEEN_FILE"
  tail -n 2000 "$SEEN_FILE" > "$SEEN_FILE.tmp"
  mv "$SEEN_FILE.tmp" "$SEEN_FILE"
}

# RFC 3164 has no timezone. Sentinel interprets this stamp in
# app.syslog.default-timezone (Africa/Tunis = UTC+1, no DST) and stores UTC.
# Alpine/BusyBox often has TZ=UTC and no tzdata, so naive `date` emitted UTC
# wall-clock while the parser subtracted one hour. Emit the parser's zone.
rfc3164_stamp() {
  if [ -n "${SYSLOG_TIMESTAMP_TZ:-}" ]; then
    TZ="$SYSLOG_TIMESTAMP_TZ" LC_ALL=C date '+%b %d %H:%M:%S'
    return
  fi
  if [ -f /usr/share/zoneinfo/Africa/Tunis ]; then
    TZ=Africa/Tunis LC_ALL=C date '+%b %d %H:%M:%S'
    return
  fi
  # POSIX UTC+1; does not need tzdata. Matches Africa/Tunis (no DST).
  TZ=CET-1 LC_ALL=C date '+%b %d %H:%M:%S'
}

build_syslog() {
  src="$1"
  dst="$2"
  spt="$3"
  dpt="$4"
  proto="$5"
  ts=$(rfc3164_stamp)
  body="${HOSTNAME_ID} firewall: DENY SRC=${src} DST=${dst} DPT=${dpt} PROTO=${proto} DENY ${proto} ${src}:${spt} -> ${dst}:${dpt}"
  printf '<134>%s %s %s' "$ts" "$HOSTNAME_ID" "$body"
}

send_udp() {
  ip="$1"
  msg="$2"
  printf '%s\n' "$msg" | nc -u -n -w1 "$ip" "$SENTINEL_SYSLOG_PORT" >/dev/null 2>&1
}

forward_line() {
  line="$1"
  echo "$line" | grep -q "BANK-FW-01" || return 0
  echo "$line" | grep -q "DENY" || return 0
  echo "$line" | grep -q "SRC=" || return 0
  echo "$line" | grep -q "DST=" || return 0

  hash=$(printf '%s' "$line" | md5sum | awk '{ print $1 }')
  if already_seen "$hash"; then
    return 0
  fi

  src=$(field "$line" SRC)
  dst=$(field "$line" DST)
  dpt=$(field "$line" DPT)
  spt=$(field "$line" SPT)
  proto=$(field "$line" PROTO)
  [ -n "$spt" ] || spt=0
  [ -n "$proto" ] || proto=TCP
  proto=$(printf '%s' "$proto" | tr 'a-z' 'A-Z' | awk '{ print $1 }')

  if [ -z "$src" ] || [ -z "$dst" ] || [ -z "$dpt" ]; then
    return 0
  fi

  msg=$(build_syslog "$src" "$dst" "$spt" "$dpt" "$proto")
  dest=$(resolve_sentinel_ip)
  if ! send_udp "$dest" "$msg"; then
    echo "syslog send failed dest=$dest port=$SENTINEL_SYSLOG_PORT"
    invalidate_sentinel_cache
    return 1
  fi

  mark_seen "$hash"
  printf '%s\n' "$msg" >> "$SENT_LOG"
  echo "forwarded DENY SRC=$src DST=$dst DPT=$dpt PROTO=$proto dest=$dest"
  return 0
}

echo "bank-fw-01 syslog forwarder started dest_port=$SENTINEL_SYSLOG_PORT"

while true; do
  processed=$(awk 'NF { print $1; exit }' "$OFFSET_FILE" 2>/dev/null || echo 0)
  [ -n "$processed" ] || processed=0
  n=0
  if [ -s "$LOG" ]; then
    while IFS= read -r line || [ -n "$line" ]; do
      n=$((n + 1))
      [ "$n" -le "$processed" ] && continue
      if ! forward_line "$line"; then
        break
      fi
      echo "$n" > "$OFFSET_FILE"
    done < "$LOG"
  fi
  sleep 1
done
