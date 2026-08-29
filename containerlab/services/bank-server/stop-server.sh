#!/bin/sh
# BANK SERVER DOWN: stop only the HTTP API. The Containerlab node stays up (PING still works).
# Kills only python processes whose command line is /opt/bank-server/server.py.
# Idempotent: missing/stale PID file is not an error.

set -eu

# shellcheck source=bank-server-lib.sh
. "$(CDPATH= cd -- "$(dirname "$0")" && pwd)/bank-server-lib.sh"

if [ -f "$PIDFILE" ]; then
  pid=$(read_pidfile || true)
  if pid_is_our_server "$pid"; then
    stop_server_pid "$pid"
  fi
  clear_pidfile
fi

for pid in $(list_server_pids); do
  stop_server_pid "$pid"
done
clear_pidfile

echo "bank-srv-01 service stopped (:$PORT closed, node still up)"
