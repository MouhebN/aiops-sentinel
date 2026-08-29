#!/bin/sh
# Collect BANK-FW-01 DENY evidence into /var/log/bank-firewall.log.
#
# Preferred source: `nft monitor trace` (works inside Containerlab even when
# kernel printk/dmesg hides nft log-level info).
# Fallback: dmesg / /dev/kmsg for iptables LOG / printk when present.

set -eu

LOG=/var/log/bank-firewall.log
STATE_DIR=/var/lib/bank-fw
SEEN="$STATE_DIR/dmesg.seen"
TRACE_DIR="$STATE_DIR/nft-trace"
mkdir -p "$STATE_DIR" "$TRACE_DIR"
touch "$LOG" "$SEEN"

append_unique() {
  line="$1"
  [ -n "$line" ] || return 0
  hash=$(printf '%s' "$line" | md5sum | awk '{ print $1 }')
  grep -qx "$hash" "$SEEN" 2>/dev/null && return 0
  echo "$line" >> "$LOG"
  echo "$hash" >> "$SEEN"
}

trace_after() {
  printf '%s\n' "$1" | awk -v a="$2" -v b="$3" '{
    for (i = 1; i < NF; i++) {
      if ($i == a && $(i+1) == b) { print $(i+2); exit }
    }
  }'
}

emit_from_packet() {
  packet="$1"
  src=$(trace_after "$packet" ip saddr)
  dst=$(trace_after "$packet" ip daddr)
  spt=$(trace_after "$packet" tcp sport)
  dpt=$(trace_after "$packet" tcp dport)
  proto=TCP
  if [ -z "$dpt" ]; then
    spt=$(trace_after "$packet" udp sport)
    dpt=$(trace_after "$packet" udp dport)
    proto=UDP
  fi
  [ -n "$spt" ] || spt=0
  [ -n "$dpt" ] || return 0
  [ -n "$src" ] || return 0
  [ -n "$dst" ] || return 0
  append_unique "BANK-FW-01 firewall: DENY SRC=${src} DST=${dst} SPT=${spt} DPT=${dpt} PROTO=${proto}"
}

collect_dmesg() {
  dmesg 2>/dev/null | grep "BANK-FW-01" | while IFS= read -r line; do
    append_unique "$line"
  done
}

collect_dmesg

if command -v nft >/dev/null 2>&1; then
  while true; do
    nft monitor trace 2>/dev/null | while IFS= read -r line; do
      id=$(printf '%s\n' "$line" | awk '$1=="trace" && $2=="id" { print $3; exit }')
      [ -n "$id" ] || continue
      pkt="$TRACE_DIR/$id"
      if echo "$line" | grep -q ' packet: '; then
        printf '%s\n' "$line" > "$pkt"
      fi
      if echo "$line" | grep -q 'verdict drop'; then
        if [ -f "$pkt" ]; then
          emit_from_packet "$(cat "$pkt")"
          rm -f "$pkt"
        fi
      fi
    done
    sleep 1
  done
fi

if dmesg --help 2>&1 | grep -q -- '-w'; then
  dmesg -w 2>/dev/null | grep --line-buffered "BANK-FW-01" | while IFS= read -r line; do
    append_unique "$line"
  done
  exit 0
fi

while true; do
  collect_dmesg
  sleep 2
done
