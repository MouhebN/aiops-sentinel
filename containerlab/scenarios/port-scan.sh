#!/bin/sh
# Controlled TCP reconnaissance from attacker-01 toward bank-srv-01.
# Lab-only. Expect these ports to be DENY-logged on bank-fw-01.

TARGET="${SCAN_TARGET:-10.10.10.20}"
PORTS="21 22 23 80 443 3306 5432"

echo "starting scan"
echo "target=$TARGET"
echo "ports=$PORTS"
echo "results"

for port in $PORTS; do
  if nc -z -w 1 "$TARGET" "$port" >/dev/null 2>&1; then
    echo "port $port OPEN"
  else
    echo "port $port BLOCKED or closed"
  fi
done

echo "scan complete"
