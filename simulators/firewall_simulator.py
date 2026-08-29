from common import Device, DeviceSimulator, EventTemplate, random_metrics, run_simulator


simulator = DeviceSimulator(
    device=Device(
        device_id="bank-perimeter-fw-01",
        name="Bank Perimeter Firewall 01",
        device_type="FIREWALL",
        location="Head Office Security Zone",
    ),
    events=[
        EventTemplate(
            event_type="FIREWALL_HEALTH_OK",
            severity="INFO",
            message="Bank perimeter firewall traffic and VPN services are normal",
            details_factory=lambda: random_metrics(cpu=(10, 45), sessions=(800, 2500), vpn_users=(20, 180), blocked=(5, 60)),
            weight=65,
        ),
        EventTemplate(
            event_type="FAILED_LOGIN_ATTEMPTS",
            severity="WARNING",
            message="Multiple failed VPN admin login attempts detected",
            details_factory=lambda: "source_ip=203.0.113.45,target=vpn-admin,attempts=12,window_minutes=5,account=network-ops",
            weight=12,
        ),
        EventTemplate(
            event_type="PORT_SCAN_DETECTED",
            severity="WARNING",
            message="Port scan detected against exposed banking network services",
            details_factory=lambda: "source_ip=198.51.100.22,target_zone=dmz,ports=22|80|443|8443|3389,action=monitored",
            weight=13,
        ),
        EventTemplate(
            event_type="SUSPICIOUS_IP_BLOCKED",
            severity="CRITICAL",
            message="Suspicious IP targeting banking DMZ was blocked by firewall policy",
            details_factory=lambda: "source_ip=192.0.2.99,target=internet-banking-gateway,action=blocked,rule=banking-threat-feed",
            weight=10,
        ),
    ],
)


if __name__ == "__main__":
    run_simulator(simulator, "Simulate firewall health and security events.")
