from common import Device, DeviceSimulator, EventTemplate, random_metrics, run_simulator


simulator = DeviceSimulator(
    device=Device(
        device_id="atm-switch-app-01",
        name="ATM Transaction Switch",
        device_type="APPLICATION",
        location="Head Office Data Center",
    ),
    events=[
        EventTemplate(
            event_type="APPLICATION_HEALTH_OK",
            severity="INFO",
            message="ATM transaction switch is processing requests normally",
            details_factory=lambda: random_metrics(tps=(40, 220), latency_ms=(40, 180), error_rate_percent=(0, 1)),
            weight=65,
        ),
        EventTemplate(
            event_type="TRANSACTION_LATENCY_HIGH",
            severity="WARNING",
            message="ATM transaction latency is above normal threshold",
            details_factory=lambda: "service=atm-transaction-switch,channel=atm," + random_metrics(latency_ms=(900, 2500), queue_depth=(80, 260), error_rate_percent=(1, 4)),
            weight=18,
        ),
        EventTemplate(
            event_type="SERVICE_DOWN",
            severity="CRITICAL",
            message="ATM transaction authorization service is down",
            details_factory=lambda: "service=atm-authorization,port=9443,channel=atm,impact=atm_cash_withdrawals_and_balance_inquiry",
            weight=9,
        ),
        EventTemplate(
            event_type="FAILED_LOGIN_ATTEMPTS",
            severity="WARNING",
            message="Failed login attempts detected on banking operations portal",
            details_factory=lambda: "application=banking-ops-portal,source_ip=10.20.14.32,attempts=9,window_minutes=5,account=ops-supervisor",
            weight=8,
        ),
    ],
)


if __name__ == "__main__":
    run_simulator(simulator, "Simulate banking application health and incidents.")
