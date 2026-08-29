"""Bounded rotating tcpdump buffer (pre-trigger window). Overwrites old segments."""

from __future__ import annotations

import os
import shutil
import subprocess
import threading
import time
from datetime import datetime, timezone
from pathlib import Path

from app.bpf import validate_interface
from app.errors import CaptureError
from app.settings import ALLOWED_INTERFACES, TCPDUMP_BIN

RING_DIR = Path(os.environ.get("ROLLING_DIR", "/tmp/captures/ring"))
ROLLING_ENABLED = os.environ.get("ROLLING_ENABLED", "true").lower() in {"1", "true", "yes"}
ROLLING_INTERFACE = os.environ.get("ROLLING_INTERFACE", "eth1")
SEGMENT_SECONDS = int(os.environ.get("ROLLING_SEGMENT_SECONDS", "5"))
PRE_TRIGGER_SECONDS = int(os.environ.get("ROLLING_PRE_TRIGGER_SECONDS", "60"))
MAX_FILE_MB = int(os.environ.get("ROLLING_MAX_FILE_MB", "20"))
FILE_PREFIX = "buffer"


class RollingBuffer:
    def __init__(
        self,
        *,
        enabled: bool = ROLLING_ENABLED,
        interface: str = ROLLING_INTERFACE,
        segment_seconds: int = SEGMENT_SECONDS,
        pre_trigger_seconds: int = PRE_TRIGGER_SECONDS,
        max_file_mb: int = MAX_FILE_MB,
        ring_dir: Path = RING_DIR,
    ) -> None:
        self.enabled = enabled
        self.interface = interface
        self.segment_seconds = max(1, segment_seconds)
        self.pre_trigger_seconds = max(self.segment_seconds, pre_trigger_seconds)
        self.max_bytes = max(1, max_file_mb) * 1024 * 1024
        self.ring_dir = ring_dir
        self._lock = threading.Lock()
        self._stop = threading.Event()
        self._process: subprocess.Popen[bytes] | None = None
        self._janitor: threading.Thread | None = None
        self.started_at: datetime | None = None
        self.last_error: str | None = None

    @property
    def expected_files(self) -> int:
        return max(1, (self.pre_trigger_seconds + self.segment_seconds - 1) // self.segment_seconds)

    def start(self) -> None:
        if not self.enabled:
            return
        with self._lock:
            if self.running:
                self._ensure_janitor_locked()
                return
            try:
                iface = validate_interface(self.interface, ALLOWED_INTERFACES)
            except ValueError as exc:
                self.last_error = str(exc)
                self._ensure_janitor_locked()
                return
            if shutil.which(TCPDUMP_BIN) is None:
                self.last_error = "tcpdump is not available"
                self._ensure_janitor_locked()
                return
            if not Path(f"/sys/class/net/{iface}").exists():
                self.last_error = f"interface {iface} is not present"
                self._ensure_janitor_locked()
                return
            self.ring_dir.mkdir(parents=True, exist_ok=True)
            argv = rolling_tcpdump_argv(iface, self.ring_dir, self.segment_seconds)
            try:
                self._process = subprocess.Popen(
                    argv,
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.PIPE,
                )
            except OSError as exc:
                self.last_error = str(exc)
                self._process = None
                self._ensure_janitor_locked()
                return
            self.started_at = datetime.now(timezone.utc)
            self.last_error = None
            self._ensure_janitor_locked()

    def _ensure_janitor_locked(self) -> None:
        if self._janitor is not None and self._janitor.is_alive():
            return
        self._stop.clear()
        self._janitor = threading.Thread(target=self._janitor_loop, name="rolling-janitor", daemon=True)
        self._janitor.start()

    def stop(self) -> None:
        self._stop.set()
        with self._lock:
            process = self._process
            self._process = None
        if process is not None and process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()

    @property
    def running(self) -> bool:
        process = self._process
        return process is not None and process.poll() is None

    def status(self) -> dict:
        files = self.segment_paths()
        disk = sum(path.stat().st_size for path in files if path.exists())
        return {
            "enabled": self.enabled,
            "running": self.running,
            "interface": self.interface,
            "segmentSeconds": self.segment_seconds,
            "preTriggerSeconds": self.pre_trigger_seconds,
            "expectedFiles": self.expected_files,
            "segmentCount": len(files),
            "diskBytes": disk,
            "maxBytes": self.max_bytes,
            "lastError": self.last_error,
        }

    def segment_paths(self) -> list[Path]:
        if not self.ring_dir.exists():
            return []
        files = [path for path in self.ring_dir.iterdir() if path.is_file() and path.suffix == ".pcap"]
        files.sort(key=lambda path: path.stat().st_mtime)
        return files

    def copy_window(self, destination: Path, *, now: float | None = None) -> list[Path]:
        """Copy ring segments as complete packets only. Does not stop tcpdump.

        The active ``-G`` file may still be growing. ``shutil.copy2`` of that
        file can tear the last record and make mergecap skip the whole segment.
        """
        from app.pcaputil import copy_complete_pcap

        destination.mkdir(parents=True, exist_ok=True)
        cutoff = (now if now is not None else time.time()) - self.pre_trigger_seconds - self.segment_seconds
        copied: list[Path] = []
        with self._lock:
            for source in self.segment_paths():
                try:
                    mtime = source.stat().st_mtime
                    if mtime < cutoff:
                        continue
                    target = destination / source.name
                    if copy_complete_pcap(source, target) <= 0:
                        target.unlink(missing_ok=True)
                        continue
                    copied.append(target)
                except OSError:
                    continue
        return copied

    def prune(self, *, now: float | None = None) -> None:
        """Delete old ring files and enforce the disk cap. Keeps the newest file."""
        with self._lock:
            files = self.segment_paths()
            if not files:
                return
            current = now if now is not None else time.time()
            cutoff = current - self.pre_trigger_seconds - self.segment_seconds
            newest = files[-1]
            for path in files[:-1]:
                try:
                    if path.stat().st_mtime < cutoff:
                        path.unlink(missing_ok=True)
                except OSError:
                    continue
            files = self.segment_paths()
            total = sum(path.stat().st_size for path in files if path.exists())
            for path in files:
                if path == newest:
                    continue
                if total <= self.max_bytes:
                    break
                try:
                    size = path.stat().st_size
                    path.unlink(missing_ok=True)
                    total -= size
                except OSError:
                    continue

    def _janitor_loop(self) -> None:
        while not self._stop.wait(1.0):
            if self._stop.is_set():
                return
            if not self.running:
                self.start()
                continue
            self.prune()


def rolling_tcpdump_argv(interface: str, ring_dir: Path, segment_seconds: int) -> list[str]:
    # -G rotates by time. Filename uses strftime. No -W: -W+ -G exits after N files.
    # Janitor deletes stale files so retention stays bounded.
    pattern = str(ring_dir / f"{FILE_PREFIX}-%Y%m%d%H%M%S.pcap")
    return [
        TCPDUMP_BIN,
        "-n",
        "-s",
        "0",
        "-i",
        interface,
        "-U",
        "-G",
        str(segment_seconds),
        "-w",
        pattern,
    ]


def require_rolling(buffer: RollingBuffer | None) -> RollingBuffer:
    if buffer is None or not buffer.enabled:
        raise CaptureError("PROVIDER_UNAVAILABLE", "Rolling capture buffer is disabled", 503)
    if not buffer.running:
        raise CaptureError("PROVIDER_UNAVAILABLE", "Rolling capture buffer is not running", 503)
    return buffer
