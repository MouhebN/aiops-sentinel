#!/bin/sh
# Deprecated. DENY-log NetFlow (export-scan-flows.py) is demo-only and must
# not run beside fprobe. Lab path is start-softflowd.sh (starts fprobe).

set -eu
echo "deprecated: /opt/netflow/start-exporter.sh now starts fprobe (NetFlow v5)" >&2
exec sh /opt/netflow/start-softflowd.sh
