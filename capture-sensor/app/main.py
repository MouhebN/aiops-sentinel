from __future__ import annotations

from contextlib import asynccontextmanager
from datetime import datetime, timezone

from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse, Response
from pydantic import BaseModel, ConfigDict, Field

from app.captures import ALLOWED_INTERFACES, CaptureError, CaptureStore, which_tcpdump
from app.rolling import RollingBuffer

rolling = RollingBuffer()
store = CaptureStore(rolling=rolling)


@asynccontextmanager
async def lifespan(_app: FastAPI):
    rolling.start()
    yield
    rolling.stop()


app = FastAPI(title="AIOps lab packet capture sensor", version="1.1.0", lifespan=lifespan)


class StartCaptureBody(BaseModel):
    model_config = ConfigDict(extra="forbid")

    duration_seconds: int = Field(default=20, alias="durationSeconds")
    interface: str
    source_ip: str | None = Field(default=None, alias="sourceIp")
    destination_ip: str | None = Field(default=None, alias="destinationIp")


class StartSnapshotBody(BaseModel):
    model_config = ConfigDict(extra="forbid")

    interface: str
    source_ip: str | None = Field(default=None, alias="sourceIp")
    destination_ip: str | None = Field(default=None, alias="destinationIp")
    pre_trigger_seconds: int | None = Field(default=None, alias="preTriggerSeconds")
    post_trigger_seconds: int | None = Field(default=None, alias="postTriggerSeconds")


class CaptureResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    id: str
    status: str
    interface: str
    source_ip: str | None = Field(serialization_alias="sourceIp")
    destination_ip: str | None = Field(serialization_alias="destinationIp")
    duration_seconds: int = Field(serialization_alias="durationSeconds")
    filter_expression: str = Field(serialization_alias="filterExpression")
    started_at: datetime | None = Field(default=None, serialization_alias="startedAt")
    completed_at: datetime | None = Field(default=None, serialization_alias="completedAt")
    packet_count: int | None = Field(default=None, serialization_alias="packetCount")
    file_size_bytes: int | None = Field(default=None, serialization_alias="fileSizeBytes")
    error_code: str | None = Field(default=None, serialization_alias="errorCode")
    error_message: str | None = Field(default=None, serialization_alias="errorMessage")
    mode: str = "on_demand"
    pre_trigger_seconds: int = Field(default=0, serialization_alias="preTriggerSeconds")
    post_trigger_seconds: int = Field(default=0, serialization_alias="postTriggerSeconds")
    triggered_at: datetime | None = Field(default=None, serialization_alias="triggeredAt")
    capture_window_start: datetime | None = Field(default=None, serialization_alias="captureWindowStart")
    capture_window_end: datetime | None = Field(default=None, serialization_alias="captureWindowEnd")


def to_response(job) -> CaptureResponse:
    return CaptureResponse(
        id=job.id,
        status=job.status,
        interface=job.interface,
        source_ip=job.source_ip,
        destination_ip=job.destination_ip,
        duration_seconds=job.duration_seconds,
        filter_expression=job.filter_expression,
        started_at=job.started_at,
        completed_at=job.completed_at,
        packet_count=job.packet_count,
        file_size_bytes=job.file_size_bytes,
        error_code=job.error_code,
        error_message=job.error_message,
        mode=job.mode,
        pre_trigger_seconds=job.pre_trigger_seconds,
        post_trigger_seconds=job.post_trigger_seconds,
        triggered_at=job.triggered_at,
        capture_window_start=job.capture_window_start,
        capture_window_end=job.capture_window_end,
    )


@app.get("/health")
def health() -> dict:
    tcpdump_ok = which_tcpdump()
    rolling_status = rolling.status()
    status = "UP" if tcpdump_ok else "DEGRADED"
    if rolling.enabled and not rolling.running:
        status = "DEGRADED"
    return {
        "status": status,
        "tcpdump": tcpdump_ok,
        "allowedInterfaces": sorted(ALLOWED_INTERFACES),
        "activeCaptures": store.active_count(),
        "rolling": rolling_status,
        "checkedAt": datetime.now(timezone.utc).isoformat(),
    }


@app.post("/captures", status_code=201)
def start_capture(body: StartCaptureBody) -> CaptureResponse:
    try:
        job = store.start(
            interface=body.interface,
            source_ip=body.source_ip,
            destination_ip=body.destination_ip,
            duration_seconds=body.duration_seconds,
        )
        return to_response(job)
    except CaptureError as exc:
        raise HTTPException(status_code=exc.status_code, detail={"code": exc.code, "message": exc.message}) from exc
    except ValueError as exc:
        raise HTTPException(status_code=400, detail={"code": "INVALID_REQUEST", "message": str(exc)}) from exc


@app.post("/snapshots", status_code=201)
def start_snapshot(body: StartSnapshotBody) -> CaptureResponse:
    try:
        job = store.start_snapshot(
            interface=body.interface,
            source_ip=body.source_ip,
            destination_ip=body.destination_ip,
            pre_trigger_seconds=body.pre_trigger_seconds,
            post_trigger_seconds=body.post_trigger_seconds,
        )
        return to_response(job)
    except CaptureError as exc:
        raise HTTPException(status_code=exc.status_code, detail={"code": exc.code, "message": exc.message}) from exc
    except ValueError as exc:
        raise HTTPException(status_code=400, detail={"code": "INVALID_REQUEST", "message": str(exc)}) from exc


@app.get("/captures/{capture_id}")
def get_capture(capture_id: str) -> CaptureResponse:
    try:
        return to_response(store.get(capture_id))
    except CaptureError as exc:
        raise HTTPException(status_code=exc.status_code, detail={"code": exc.code, "message": exc.message}) from exc


@app.get("/captures/{capture_id}/file")
def get_capture_file(capture_id: str) -> FileResponse:
    try:
        job = store.get(capture_id)
    except CaptureError as exc:
        raise HTTPException(status_code=exc.status_code, detail={"code": exc.code, "message": exc.message}) from exc
    if job.status != "COMPLETED" or not job.file_path.exists():
        raise HTTPException(
            status_code=409,
            detail={"code": "NOT_READY", "message": "Capture file is not ready"},
        )
    return FileResponse(
        path=job.file_path,
        media_type="application/vnd.tcpdump.pcap",
        filename=f"{job.id}.pcap",
    )


@app.delete("/captures/{capture_id}")
def delete_capture(capture_id: str) -> Response:
    try:
        store.delete(capture_id)
    except CaptureError as exc:
        raise HTTPException(status_code=exc.status_code, detail={"code": exc.code, "message": exc.message}) from exc
    return Response(status_code=204)
