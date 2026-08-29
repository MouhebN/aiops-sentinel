from __future__ import annotations

import shutil
import subprocess
import threading
import time
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from pathlib import Path

from app.bpf import build_host_filter, filter_argv, validate_interface, validate_ip
from app.errors import CaptureError
from app.settings import (
    ALLOWED_INTERFACES,
    CAPTURE_DIR,
    MAX_CONCURRENT,
    MAX_DURATION,
    MAX_FILE_MB,
    MIN_DURATION,
    POST_TRIGGER_SECONDS,
    TCPDUMP_BIN,
    TIMEOUT_BIN,
)


@dataclass
class CaptureJob:
    id: str
    status: str
    interface: str
    source_ip: str | None
    destination_ip: str | None
    duration_seconds: int
    filter_expression: str
    file_path: Path
    started_at: datetime | None = None
    completed_at: datetime | None = None
    packet_count: int | None = None
    file_size_bytes: int | None = None
    error_code: str | None = None
    error_message: str | None = None
    process: subprocess.Popen[bytes] | None = field(default=None, repr=False)
    mode: str = "on_demand"
    pre_trigger_seconds: int = 0
    post_trigger_seconds: int = 0
    work_dir: Path | None = None
    triggered_at: datetime | None = None
    capture_window_start: datetime | None = None
    capture_window_end: datetime | None = None


class CaptureStore:
    def __init__(self, rolling=None) -> None:
        self.rolling = rolling
        self._jobs: dict[str, CaptureJob] = {}
        self._lock = threading.Lock()
        CAPTURE_DIR.mkdir(parents=True, exist_ok=True)

    def active_count(self) -> int:
        with self._lock:
            return sum(1 for job in self._jobs.values() if job.status in {"PENDING", "RUNNING"})

    def get(self, capture_id: str) -> CaptureJob:
        with self._lock:
            job = self._jobs.get(capture_id)
        if job is None:
            raise CaptureError("NOT_FOUND", "Capture not found", 404)
        return job

    def start(
        self,
        *,
        interface: str,
        source_ip: str | None,
        destination_ip: str | None,
        duration_seconds: int,
    ) -> CaptureJob:
        iface = validate_interface(interface, ALLOWED_INTERFACES)
        src = validate_ip(source_ip)
        dst = validate_ip(destination_ip)
        duration = clamp_duration(duration_seconds)
        if self.active_count() >= MAX_CONCURRENT:
            raise CaptureError("BUSY", "A capture is already running", 409)

        capture_id = str(uuid.uuid4())
        path = CAPTURE_DIR / f"{capture_id}.pcap"
        job = CaptureJob(
            id=capture_id,
            status="PENDING",
            interface=iface,
            source_ip=src,
            destination_ip=dst,
            duration_seconds=duration,
            filter_expression=build_host_filter(src, dst),
            file_path=path,
        )
        with self._lock:
            self._jobs[capture_id] = job
        thread = threading.Thread(target=self._run, args=(capture_id,), daemon=True)
        thread.start()
        return job

    def start_snapshot(
        self,
        *,
        interface: str,
        source_ip: str | None,
        destination_ip: str | None,
        post_trigger_seconds: int | None,
        pre_trigger_seconds: int | None,
    ) -> CaptureJob:
        from app.rolling import require_rolling

        rolling = require_rolling(self.rolling)
        iface = validate_interface(interface, ALLOWED_INTERFACES)
        if iface != rolling.interface:
            raise CaptureError("INVALID_REQUEST", "Snapshot interface does not match the rolling buffer", 400)
        src = validate_ip(source_ip)
        dst = validate_ip(destination_ip)
        post = clamp_duration(post_trigger_seconds if post_trigger_seconds is not None else POST_TRIGGER_SECONDS)
        pre = rolling.pre_trigger_seconds if pre_trigger_seconds is None else max(rolling.segment_seconds, pre_trigger_seconds)
        if self.active_count() >= MAX_CONCURRENT:
            raise CaptureError("BUSY", "A capture is already running", 409)

        capture_id = str(uuid.uuid4())
        work_dir = CAPTURE_DIR / f"snap-{capture_id}"
        work_dir.mkdir(parents=True, exist_ok=True)
        job = CaptureJob(
            id=capture_id,
            status="PENDING",
            interface=iface,
            source_ip=src,
            destination_ip=dst,
            duration_seconds=pre + post,
            filter_expression=build_host_filter(src, dst),
            file_path=work_dir / "final.pcap",
            mode="rolling_snapshot",
            pre_trigger_seconds=pre,
            post_trigger_seconds=post,
            work_dir=work_dir,
        )
        with self._lock:
            self._jobs[capture_id] = job
        thread = threading.Thread(target=self._run_snapshot, args=(capture_id,), daemon=True)
        thread.start()
        return job

    def cancel(self, capture_id: str) -> CaptureJob:
        job = self.get(capture_id)
        with self._lock:
            if job.status in {"COMPLETED", "FAILED", "CANCELLED"}:
                return job
            job.status = "CANCELLED"
            job.error_code = "CANCELLED"
            job.error_message = "Capture cancelled"
            job.completed_at = datetime.now(timezone.utc)
            process = job.process
        if process is not None and process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
        return job

    def delete(self, capture_id: str) -> None:
        job = self.get(capture_id)
        self.cancel(capture_id)
        if job.file_path.exists():
            job.file_path.unlink(missing_ok=True)
        self._remove_work_dir(job)
        with self._lock:
            self._jobs.pop(capture_id, None)

    def cleanup_old(self, max_age_seconds: int = 3600) -> None:
        now = time.time()
        with self._lock:
            stale = [
                job
                for job in self._jobs.values()
                if job.status in {"COMPLETED", "FAILED", "CANCELLED"}
                and job.file_path.exists()
                and now - job.file_path.stat().st_mtime > max_age_seconds
            ]
        for job in stale:
            try:
                job.file_path.unlink(missing_ok=True)
            except OSError:
                pass

    def _run(self, capture_id: str) -> None:
        job = self.get(capture_id)
        argv = tcpdump_argv(job)
        with self._lock:
            if job.status == "CANCELLED":
                return
            job.status = "RUNNING"
            job.started_at = datetime.now(timezone.utc)
        try:
            process = subprocess.Popen(
                argv,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
            )
            with self._lock:
                job.process = process
            _stdout, stderr = process.communicate(timeout=job.duration_seconds + 15)
            if job.status == "CANCELLED":
                return
            if process.returncode in (126, 127):
                message = (stderr or b"").decode("utf-8", errors="replace").strip() or "tcpdump is not available"
                self._fail(job, "TCPDUMP_FAILURE", message[:500])
                return
            if not job.file_path.exists() or job.file_path.stat().st_size == 0:
                self._fail(job, "EMPTY_CAPTURE", "Capture file is empty")
                return
            packet_count = count_packets(job.file_path)
            with self._lock:
                job.status = "COMPLETED"
                job.completed_at = datetime.now(timezone.utc)
                job.packet_count = packet_count
                job.file_size_bytes = job.file_path.stat().st_size
        except subprocess.TimeoutExpired:
            if job.process is not None:
                job.process.kill()
            self._fail(job, "TIMEOUT", "tcpdump did not stop within the capture timeout")
        except Exception as exc:  # noqa: BLE001
            self._fail(job, "TCPDUMP_FAILURE", str(exc)[:500])

    def _run_snapshot(self, capture_id: str) -> None:
        from app.pcaputil import PcapFormatError, count_packets as pcap_count, extract_hosts, merge_pcap_files
        from app.rolling import require_rolling

        job = self.get(capture_id)
        post_seconds = job.post_trigger_seconds or POST_TRIGGER_SECONDS
        pre_seconds = job.pre_trigger_seconds
        with self._lock:
            if job.status == "CANCELLED":
                return
            job.status = "RUNNING"
            triggered = datetime.now(timezone.utc)
            job.started_at = triggered
            job.triggered_at = triggered
            job.capture_window_start = triggered - timedelta(seconds=pre_seconds)
            job.capture_window_end = triggered + timedelta(seconds=post_seconds)
        work_dir = job.work_dir or (CAPTURE_DIR / f"snap-{job.id}")
        pre_dir = work_dir / "pre"
        overlap_dir = work_dir / "overlap"
        post_path = work_dir / "post.pcap"
        merged_path = work_dir / "merged.pcap"
        try:
            rolling = require_rolling(self.rolling)
            post_job = CaptureJob(
                id=f"{job.id}-post",
                status="RUNNING",
                interface=job.interface,
                source_ip=job.source_ip,
                destination_ip=job.destination_ip,
                duration_seconds=post_seconds,
                filter_expression=job.filter_expression,
                file_path=post_path,
            )
            argv = tcpdump_argv(post_job)
            process = subprocess.Popen(argv, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            with self._lock:
                job.process = process
            # Post-trigger is already listening. Copy the ring next so there is no
            # uncovered interval between pre-buffer and post-trigger.
            pre_files = rolling.copy_window(pre_dir)
            _stdout, stderr = process.communicate(timeout=post_seconds + 15)
            if job.status == "CANCELLED":
                self._remove_work_dir(job, keep_final=False)
                return
            if process.returncode in (126, 127):
                message = (stderr or b"").decode("utf-8", errors="replace").strip() or "tcpdump is not available"
                self._fail(job, "TCPDUMP_FAILURE", message[:500])
                self._remove_work_dir(job, keep_final=False)
                return
            overlap_files = rolling.copy_window(overlap_dir)
            inputs = list(pre_files)
            inputs.extend(overlap_files)
            if post_path.exists() and post_path.stat().st_size > 24:
                inputs.append(post_path)
            if not inputs:
                self._fail(job, "EMPTY_CAPTURE", "Rolling buffer and post-trigger capture were empty")
                self._remove_work_dir(job, keep_final=False)
                return
            merge_pcap_files(inputs, merged_path, dedupe=True)
            final_source = merged_path
            filtered_path = work_dir / "filtered.pcap"
            if job.source_ip or job.destination_ip:
                if extract_hosts(merged_path, filtered_path, job.source_ip, job.destination_ip):
                    final_source = filtered_path
            shutil.copy2(final_source, job.file_path)
            packet_count = pcap_count(job.file_path)
            if packet_count <= 0:
                self._fail(job, "EMPTY_CAPTURE", "Merged capture file is empty")
                self._remove_work_dir(job, keep_final=False)
                return
            with self._lock:
                job.status = "COMPLETED"
                job.completed_at = datetime.now(timezone.utc)
                job.packet_count = packet_count
                job.file_size_bytes = job.file_path.stat().st_size
            self._remove_work_dir(job, keep_final=True)
        except CaptureError as exc:
            self._fail(job, exc.code, exc.message)
            self._remove_work_dir(job, keep_final=False)
        except subprocess.TimeoutExpired:
            if job.process is not None:
                job.process.kill()
            self._fail(job, "TIMEOUT", "Post-trigger capture did not stop within the timeout")
            self._remove_work_dir(job, keep_final=False)
        except PcapFormatError as exc:
            self._fail(job, "TRANSFER_FAILURE", str(exc)[:500])
            self._remove_work_dir(job, keep_final=False)
        except Exception as exc:  # noqa: BLE001
            self._fail(job, "TCPDUMP_FAILURE", str(exc)[:500])
            self._remove_work_dir(job, keep_final=False)

    def _remove_work_dir(self, job: CaptureJob, keep_final: bool = False) -> None:
        work_dir = job.work_dir
        if work_dir is None or not work_dir.exists():
            return
        if keep_final and job.file_path.exists():
            for child in work_dir.iterdir():
                if child.resolve() == job.file_path.resolve():
                    continue
                if child.is_dir():
                    shutil.rmtree(child, ignore_errors=True)
                else:
                    child.unlink(missing_ok=True)
            return
        shutil.rmtree(work_dir, ignore_errors=True)

    def _fail(self, job: CaptureJob, code: str, message: str) -> None:
        with self._lock:
            if job.status == "CANCELLED":
                return
            job.status = "FAILED"
            job.error_code = code
            job.error_message = message
            job.completed_at = datetime.now(timezone.utc)
            if job.file_path.exists():
                job.file_size_bytes = job.file_path.stat().st_size


def clamp_duration(value: int | None) -> int:
    if value is None:
        value = 20
    if value < MIN_DURATION:
        return MIN_DURATION
    if value > MAX_DURATION:
        return MAX_DURATION
    return value


def tcpdump_argv(job: CaptureJob) -> list[str]:
    kill_after = str(job.duration_seconds + 2)
    argv = [
        TIMEOUT_BIN,
        "--kill-after=5s",
        kill_after,
        TCPDUMP_BIN,
        "-n",
        "-s",
        "0",
        "-i",
        job.interface,
        "-w",
        str(job.file_path),
        "-W",
        "1",
        "-C",
        str(MAX_FILE_MB),
        "-U",
    ]
    argv.extend(filter_argv(job.source_ip, job.destination_ip))
    return argv


def count_packets(path: Path) -> int:
    try:
        result = subprocess.run(
            [TCPDUMP_BIN, "-r", str(path), "-nn", "-q"],
            check=False,
            capture_output=True,
            timeout=15,
        )
        output = result.stdout.decode("utf-8", errors="replace")
        return len([line for line in output.splitlines() if line.strip()])
    except (OSError, subprocess.TimeoutExpired):
        return 0


def which_tcpdump() -> bool:
    return shutil.which(TCPDUMP_BIN) is not None
