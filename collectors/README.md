# AIOps Sentinel Collectors

The generic collector reads enabled monitored components from the backend and performs active checks.

Current methods:

- `PING`: ICMP reachability check for IP addresses.
- `HTTP_HEALTH`: HTTP GET check for health endpoints or web interfaces.

Run once:

```bash
python3 collectors/generic_collector.py --once
```

Run continuously:

```bash
python3 collectors/generic_collector.py --backend-url http://localhost:8080 --poll-interval 5
```

Dry run:

```bash
python3 collectors/generic_collector.py --once --dry-run
```

The collector updates component status through:

```text
PATCH /api/components/{id}/status
```

It sends normalized events through the existing endpoint:

```text
POST /api/events
```

Events are emitted when a check fails or when a component recovers from `DOWN`.

## Syslog Collector

The syslog collector listens for UDP syslog messages and forwards normalized events to the backend.
It is useful for firewall, switch, Linux server, router, and application logs.

Default port is `5514` instead of `514` because `514` usually requires administrator/root privileges.

Run:

```bash
python3 collectors/syslog_collector.py --backend-url http://localhost:8080
```

Run with backend-configured syslog sources from the frontend:

```bash
python3 collectors/syslog_collector.py --backend-url http://localhost:8080
```

The collector fetches enabled mappings from:

```text
GET /api/syslog-sources/enabled
```

Run with an optional local device map fallback:

```bash
python3 collectors/syslog_collector.py \
  --backend-url http://localhost:8080 \
  --device-map collectors/syslog_device_map.example.json
```

Dry run without sending to backend:

```bash
python3 collectors/syslog_collector.py --dry-run
```

Send test syslog messages:

```bash
python3 simulators/send_syslog.py --count 5
```

The collector maps raw syslog into the existing event model:

```text
syslog message
→ eventType / severity / message / details
→ POST /api/events
→ backend incident correlation
→ dashboard, alerts, incidents, AI analysis
```

Recognized examples:

- port scans → `PORT_SCAN_DETECTED`
- suspicious blocked IPs → `SUSPICIOUS_IP_BLOCKED`
- failed logins → `FAILED_LOGIN_ATTEMPTS`
- VPN tunnel down → `VPN_TUNNEL_DOWN`
- switch interface down → `SWITCH_PORT_DOWN`
- UPS battery low → `UPS_BATTERY_LOW`
- RTSP stream down → `RTSP_STREAM_DOWN`
