from common import Device, DeviceSimulator, EventTemplate, random_metrics, run_simulator


simulator = DeviceSimulator(
    device=Device(
        device_id="branch-core-switch-01",
        name="Branch Core Switch 01",
        device_type="SWITCH",
        location="Downtown Branch - Network Closet",
    ),
    events=[
        EventTemplate(
            event_type="SWITCH_HEALTH_OK",
            severity="INFO",
            message="Branch LAN switch ports and uplinks are healthy",
            details_factory=lambda: "vlans=atm|teller|cctv|guest," + random_metrics(cpu=(5, 35), temperature=(32, 48), active_ports=(18, 24)),
            weight=72,
        ),
        EventTemplate(
            event_type="PACKET_LOSS",
            severity="WARNING",
            message="Packet loss detected on branch WAN uplink",
            details_factory=lambda: "affected_services=atm_authorization|teller_workstations," + random_metrics(packet_loss_percent=(3, 12), latency_ms=(80, 180)),
            weight=18,
        ),
        EventTemplate(
            event_type="DEVICE_UNREACHABLE",
            severity="CRITICAL",
            message="Branch core switch is unreachable",
            details_factory=lambda: "ping=failed,snmp=timeout,uplink=unknown,affected_vlans=atm|teller|cctv",
            weight=10,
        ),
    ],
)


if __name__ == "__main__":
    run_simulator(simulator, "Simulate switch health and connectivity events.")
