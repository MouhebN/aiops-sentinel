from common import Device, DeviceSimulator, EventTemplate, random_metrics, run_simulator


simulator = DeviceSimulator(
    device=Device(
        device_id="branch-ups-01",
        name="Branch Server Room UPS 01",
        device_type="UPS",
        location="Downtown Branch - Server Room",
    ),
    events=[
        EventTemplate(
            event_type="UPS_HEALTH_OK",
            severity="INFO",
            message="Branch UPS battery and power input are normal",
            details_factory=lambda: "protected_assets=atm_switch|branch_router|camera_nvr," + random_metrics(battery=(65, 100), load=(20, 70), input_voltage=(218, 230)),
            weight=70,
        ),
        EventTemplate(
            event_type="UPS_ON_BATTERY",
            severity="WARNING",
            message="Branch UPS is running on battery power",
            details_factory=lambda: "protected_assets=atm_switch|branch_router|camera_nvr," + random_metrics(battery=(30, 80), load=(35, 85), input_voltage=(0, 0)),
            weight=18,
        ),
        EventTemplate(
            event_type="UPS_BATTERY_LOW",
            severity="CRITICAL",
            message="Branch UPS battery below 15%",
            details_factory=lambda: "protected_assets=atm_switch|branch_router|camera_nvr," + random_metrics(battery=(5, 14), load=(40, 90), runtime_minutes=(2, 8)),
            weight=12,
        ),
    ],
)


if __name__ == "__main__":
    run_simulator(simulator, "Simulate UPS power and battery events.")
