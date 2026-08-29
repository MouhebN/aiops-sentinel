"""Shared sensor environment. Imported by captures/rolling without cycles."""

from __future__ import annotations

import os
from pathlib import Path

CAPTURE_DIR = Path(os.environ.get("CAPTURE_DIR", "/tmp/captures"))
ALLOWED_INTERFACES = {
    item.strip()
    for item in os.environ.get("CAPTURE_ALLOWED_INTERFACES", "eth1").split(",")
    if item.strip()
}
MIN_DURATION = int(os.environ.get("CAPTURE_MIN_DURATION_SECONDS", "5"))
MAX_DURATION = int(os.environ.get("CAPTURE_MAX_DURATION_SECONDS", "60"))
MAX_FILE_MB = int(os.environ.get("CAPTURE_MAX_FILE_MB", "20"))
MAX_CONCURRENT = int(os.environ.get("CAPTURE_MAX_CONCURRENT", "1"))
TCPDUMP_BIN = os.environ.get("CAPTURE_TCPDUMP_BIN", "tcpdump")
TIMEOUT_BIN = os.environ.get("CAPTURE_TIMEOUT_BIN", "timeout")
POST_TRIGGER_SECONDS = int(os.environ.get("ROLLING_POST_TRIGGER_SECONDS", "20"))
