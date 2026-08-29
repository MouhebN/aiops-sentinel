from common import Device, DeviceSimulator, EventTemplate, random_metrics, run_simulator


simulator = DeviceSimulator(
    device=Device(
        device_id="branch-entrance-camera-01",
        name="Branch Entrance Camera 01",
        device_type="IP_CAMERA",
        location="Downtown Branch - Main Entrance",
    ),
    events=[
        EventTemplate(
            event_type="CAMERA_HEALTH_OK",
            severity="INFO",
            message="Branch entrance camera is online and streaming",
            details_factory=lambda: "zone=customer_entrance," + random_metrics(latency_ms=(20, 80), bitrate_kbps=(1800, 4200)),
            weight=75,
        ),
        EventTemplate(
            event_type="RTSP_STREAM_DOWN",
            severity="WARNING",
            message="Branch entrance RTSP stream is unavailable",
            details_factory=lambda: "zone=customer_entrance,rtsp_port=554,stream=main,status=timeout,nvr_recording=degraded",
            weight=15,
        ),
        EventTemplate(
            event_type="CAMERA_OFFLINE",
            severity="CRITICAL",
            message="Branch entrance camera is offline",
            details_factory=lambda: "zone=customer_entrance,ping=failed,rtsp=failed,last_frame_age_seconds=300,physical_security_visibility=lost",
            weight=10,
        ),
    ],
)


if __name__ == "__main__":
    run_simulator(simulator, "Simulate IP camera availability and stream events.")
