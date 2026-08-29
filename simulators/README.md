# AIOps Device Simulators

These scripts simulate a bank IT infrastructure and send events to the Spring Boot backend.

The current simulated environment includes:

- Head office core banking servers
- ATM transaction application services
- Bank perimeter firewall and VPN access
- Branch LAN switching and WAN uplinks
- Branch UPS power protection
- Branch IP camera supervision

Default backend URL:

```bash
http://localhost:8080/api/events
```

Run one simulator:

```bash
python3 simulators/server_simulator.py
python3 simulators/camera_simulator.py
python3 simulators/ups_simulator.py
python3 simulators/firewall_simulator.py
python3 simulators/switch_simulator.py
python3 simulators/application_simulator.py
```

Run a mock IP camera as a real network component:

```bash
python3 simulators/mock_camera_device.py
```

Then add it in AIOps Sentinel:

- Type: `IP_CAMERA`
- IP address: `127.0.0.1`
- Methods: `PING`, `RTSP_HEALTH`, `HTTP_HEALTH`
- TCP/RTSP port: `8554`
- HTTP URL: `http://localhost:8085/health`

Control the mock camera:

```bash
curl -X POST http://localhost:8085/control/rtsp/down
curl -X POST http://localhost:8085/control/rtsp/up
curl -X POST http://localhost:8085/control/camera/down
curl -X POST http://localhost:8085/control/camera/up
curl -X POST http://localhost:8085/control/reset
```

Run a mock UPS/onduleur as a real monitored component:

```bash
python3 simulators/mock_ups_device.py
```

Then add it in AIOps Sentinel:

- Type: `UPS`
- IP address: `127.0.0.1`
- Methods: `PING`, `HTTP_HEALTH`, `UPS_HTTP_METRICS`
- HTTP URL: `http://localhost:8095/health`

For a real UPS/onduleur with SNMP enabled, use:

- Type: `UPS`
- Methods: `PING`, `SNMP_BASIC`, `UPS_SNMP_METRICS`
- SNMP port: `161`
- SNMP community: the read-only community configured on the UPS
- SNMP OID for basic test: `1.3.6.1.2.1.33.1.1.1.0`

Control the mock UPS:

```bash
curl -X POST http://localhost:8095/control/on-battery
curl -X POST http://localhost:8095/control/low-battery
curl -X POST http://localhost:8095/control/high-load
curl -X POST http://localhost:8095/control/down
curl -X POST http://localhost:8095/control/reset
```

Run all simulators:

```bash
python3 simulators/run_all.py
```

Useful options:

```bash
python3 simulators/server_simulator.py --backend-url http://localhost:8080 --interval 3
python3 simulators/ups_simulator.py --once
python3 simulators/firewall_simulator.py --once --dry-run
```

Payload sent to the backend:

```json
{
  "deviceId": "core-banking-server-01",
  "deviceName": "Core Banking App Server 01",
  "deviceType": "SERVER",
  "location": "Head Office Data Center",
  "eventType": "CPU_HIGH",
  "severity": "WARNING",
  "message": "Core banking application server CPU usage is above 90%",
  "details": "application=core-banking-api,cpu=93,disk=72,ram=81,active_sessions=1320"
}
```

Behavior rule:

- `INFO`: normal health event
- `WARNING`: abnormal condition that needs attention
- `CRITICAL`: incident that can make the device `DOWN`
