from common import Device, DeviceSimulator, EventTemplate, random_metrics, run_simulator


simulator = DeviceSimulator(
    device=Device(
        device_id="core-banking-server-01",
        name="Core Banking App Server 01",
        device_type="SERVER",
        location="Head Office Data Center",
    ),
    events=[
        EventTemplate(
            event_type="SERVER_HEALTH_OK",
            severity="INFO",
            message="Core banking server health is normal",
            details_factory=lambda: random_metrics(cpu=(10, 55), ram=(20, 65), disk=(25, 70), active_sessions=(120, 850)),
            weight=70,
        ),
        EventTemplate(
            event_type="CPU_HIGH",
            severity="WARNING",
            message="Core banking application server CPU usage is above 90%",
            details_factory=lambda: "application=core-banking-api," + random_metrics(cpu=(90, 99), ram=(55, 85), disk=(40, 80), active_sessions=(900, 1800)),
            weight=15,
        ),
        EventTemplate(
            event_type="DISK_FULL",
            severity="CRITICAL",
            message="Core banking database log disk usage is critically high",
            details_factory=lambda: "volume=/var/lib/core-banking/audit-logs," + random_metrics(cpu=(30, 70), ram=(40, 85), disk=(95, 100)),
            weight=8,
        ),
        EventTemplate(
            event_type="SERVICE_DOWN",
            severity="CRITICAL",
            message="Core banking API service is down",
            details_factory=lambda: "service=core-banking-api,port=8443,process=stopped,impact=branch_tellers_and_atm_authorization",
            weight=7,
        ),
    ],
)


if __name__ == "__main__":
    run_simulator(simulator, "Simulate server health and incident events.")
