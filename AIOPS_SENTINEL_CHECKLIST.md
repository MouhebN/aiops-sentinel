# AIOps Sentinel Checklist

## Project Goal

AIOps Sentinel is an intelligent IT supervision platform for a simulated bank infrastructure. The platform monitors components, centralizes events, shows alerts in a dashboard, and uses AI to explain incidents and suggest diagnostic actions.

## Completed MVP Foundation

- [x] Spring Boot backend created.
- [x] React/MUI frontend dashboard created from the template.
- [x] FastAPI AI service created.
- [x] Ollama integration added with rule-based fallback.
- [x] Bank-oriented incident context added to the AI prompt.
- [x] Incident-level AI analysis context added.
- [x] Python simulators added for bank infrastructure events.
- [x] Events can be ingested through the backend API.
- [x] Events, alerts, devices, reports, and AI analysis are visible in the frontend.
- [x] AI analysis can be saved as reports.
- [x] Incident page added to show backend-persisted incidents by component and problem category.

## Backend Monitoring Module

- [x] Added `MonitoredComponent` module.
- [x] Added component CRUD APIs.
- [x] Added enable/disable monitoring APIs.
- [x] Added manual `Check Now` API.
- [x] Added scheduler-based monitoring in Spring Boot.
- [x] Added component status tracking:
  - [x] `UNKNOWN`
  - [x] `UP`
  - [x] `WARNING`
  - [x] `DEGRADED`
  - [x] `DOWN`
- [x] Added counters:
  - [x] success count
  - [x] failure count
  - [x] last checked time
  - [x] last seen time
  - [x] last error
- [x] Added event generation when status changes.
- [x] Kept `Check Now` as a connection test only, without changing status or creating events.

## Current Monitoring Methods

- [x] `PING`
  - Checks if an IP device is reachable.
- [x] `HTTP_HEALTH`
  - Checks if an HTTP endpoint responds with a successful status code.
- [x] `TCP_PORT`
  - Checks if a service port is open.
- [x] `SNMP_BASIC`
  - Reads one SNMP OID, useful for device identity or a simple metric.
- [x] `SNMP_ROUTER_METRICS`
  - Reads router uptime, interface count, interface status, traffic counters, and interface errors when exposed by the device.

## Frontend Components Page

- [x] List monitored components.
- [x] Add component.
- [x] Edit component.
- [x] Start/stop monitoring.
- [x] Check connection manually.
- [x] Show status, target, methods, criticality, success/failure counters, and last error.
- [x] Component detail page.
- [x] Recent events by component.
- [x] Analyze component events with AI.

## Tested Components

### Home Wi-Fi Router

- [x] Type: `ROUTER`
- [x] IP: `192.168.1.1`
- [x] TCP port: `80`
- [x] SNMP enabled on router.
- [x] SNMP community: `public`
- [x] Methods tested:
  - [x] `PING`
  - [x] `TCP_PORT`
  - [x] `SNMP_BASIC`
  - [x] `SNMP_ROUTER_METRICS`
- [x] Observed SNMP identity:
  - `ADSL SoHo Router`
- [x] Observed SNMP metrics:
  - uptime
  - interface count
  - interface error counters
- [ ] Improve router metric display:
  - [ ] Convert SNMP timeticks to readable uptime.
  - [ ] Show SNMP metrics in a cleaner component detail section.
  - [ ] Detect recent router reboot.
  - [ ] Generate `ROUTER_RECENT_REBOOT` event.
  - [ ] Add thresholds for interface errors.
  - [ ] Add traffic rate calculation between checks.

### AIOps Sentinel Backend

- [x] Type: `APPLICATION`
- [x] HTTP URL: `http://localhost:8080/actuator/health`
- [x] TCP port: `8080`
- [x] Methods tested:
  - [x] `HTTP_HEALTH`
  - [x] `TCP_PORT`
- [ ] Add better application health details later:
  - [ ] database connection status
  - [ ] API latency
  - [ ] error rate

## Next Component To Test

### Workstation / Other PC

- [ ] Add component:
  - Name: `Workstation 01`
  - Type: `SERVER` or `OTHER`
  - Location: `Home Lab`
  - Criticality: `MEDIUM`
  - Method: `PING`
  - IP: other PC IP address
  - Interval: `15`
- [ ] Optional TCP test:
  - Start a service on the other PC:
    - `python -m http.server 8000`
  - Add method: `TCP_PORT`
  - TCP port: `8000`
- [ ] Optional HTTP test:
  - Add method: `HTTP_HEALTH`
  - HTTP URL: `http://OTHER_PC_IP:8000`
- [ ] Test incident scenarios:
  - [ ] Stop Python HTTP server.
  - [ ] Disconnect other PC from Wi-Fi.
  - [ ] Restart other PC.
  - [ ] Confirm events appear in dashboard.
  - [ ] Analyze generated incident with AI.

## Components To Add Later

### IP Camera / Video Surveillance

- [ ] Add as `IP_CAMERA`.
- [ ] Start with `PING`.
- [ ] Add `TCP_PORT` for camera web interface, usually `80` or `443`.
- [ ] Add RTSP check later:
  - default RTSP port: `554`
  - event examples:
    - `CAMERA_OFFLINE`
    - `RTSP_STREAM_DOWN`
    - `VIDEO_SURVEILLANCE_DEGRADED`
- [ ] Add ONVIF discovery/status later if needed.

### UPS / Onduleur

- [x] Add as `UPS`.
- [x] Start with `PING`.
- [x] Add `HTTP_HEALTH` for mock UPS endpoint.
- [x] Add `UPS_HTTP_METRICS` for battery/load/runtime/power-source monitoring.
- [x] Add `UPS_SNMP_METRICS` for real UPS devices exposing standard UPS-MIB.
- [x] Add mock UPS script:
  - [x] `simulators/mock_ups_device.py`
- [x] Mock UPS scenarios:
  - [x] mains power normal
  - [x] on battery
  - [x] low battery
  - [x] high load
  - [x] UPS down
- [x] Add `SNMP_BASIC` if a real UPS supports SNMP.
- [x] Add UPS SNMP profile:
  - [x] manufacturer/model
  - [x] battery status
  - [x] battery charge
  - [x] runtime remaining
  - [x] input voltage
  - [x] output voltage
  - [x] output load
  - [x] power source status
- [ ] Event examples:
  - `UPS_BATTERY_LOW`
  - `UPS_ON_BATTERY`
  - `UPS_LOAD_HIGH`
  - `POWER_RISK_DETECTED`

### Switch

- [ ] Add as `SWITCH`.
- [ ] Start with `PING`.
- [ ] Add `TCP_PORT` if management UI or SSH exists.
- [ ] Add SNMP metrics if supported:
  - interface status
  - interface errors
  - traffic counters
  - uptime
- [ ] Event examples:
  - `SWITCH_PORT_DOWN`
  - `NETWORK_INTERFACE_ERRORS_HIGH`
  - `BANDWIDTH_USAGE_HIGH`

### Firewall

- [ ] Add as `FIREWALL`.
- [ ] Start with `PING`.
- [ ] Add `TCP_PORT` for management port if available.
- [ ] Later add log ingestion through Syslog or API.
- [ ] Event examples:
  - `SUSPICIOUS_IP_BLOCKED`
  - `PORT_SCAN_DETECTED`
  - `FAILED_LOGIN_ATTEMPTS`
  - `VPN_TUNNEL_DOWN`

### Bank Applications

- [ ] Add as `APPLICATION`.
- [ ] Use `HTTP_HEALTH`.
- [ ] Use `TCP_PORT`.
- [ ] Later add application metrics:
  - response time
  - error rate
  - transaction latency
  - failed transactions
- [ ] Event examples:
  - `APPLICATION_DOWN`
  - `TRANSACTION_LATENCY_HIGH`
  - `API_ERROR_RATE_HIGH`

## Metric History And Charts

- [x] Add generic `metric_samples` table.
- [x] Store metric samples from scheduled component checks.
- [x] Extract availability metrics:
  - [x] ping available
  - [x] HTTP available
  - [x] TCP available
  - [x] RTSP available
- [x] Extract infrastructure metrics:
  - [x] CPU usage
  - [x] memory usage
  - [x] storage max usage
  - [x] UPS battery
  - [x] UPS runtime
  - [x] UPS load
  - [x] UPS voltage
  - [x] router interface count/errors
- [x] Add component metrics API.
- [x] Add metric charts to component detail page.
- [x] Add metric retention cleanup.
- [x] Add metric aggregation for long periods.
- [x] Add threshold configuration per metric.
- [x] Generate events when metrics cross warning/critical thresholds.
- [x] Generate recovery events when threshold state returns to normal.
- [x] Show warning/critical threshold lines on component metric charts.

## Missing Real-World Features

- [x] Dedicated global metric dashboard charts.
- [x] Backend incident persistence with active/recovered lifecycle and related events.
- [x] Incident rebuild/backfill endpoint for existing event history.
- [x] Incident Center page with active/recovered incidents, related events, AI analysis action, and persisted acknowledgement.
- [x] Incident detail page with lifecycle, component context, metrics snapshot, related events, and AI action.
- [x] AI analysis can use incident title, lifecycle, related events, component configuration, and recent metrics.
- [ ] Better SNMP profiles by component type.
- [x] Syslog collector for firewall, switch, server, UPS, camera, and application logs.
- [x] Backend syslog source configuration.
- [x] Frontend Syslog Sources page.
- [x] Syslog collector can fetch enabled source mappings from backend.
- [ ] RTSP check for cameras.
- [ ] Local agent for servers/workstations.
- [x] Notification center with active incident badge, dropdown, sound toggle, and quick acknowledgement.
- [ ] User authentication.
- [ ] Role-based access control.
- [ ] PostgreSQL migration for production-like deployment.
- [ ] Docker Compose for backend, frontend, FastAPI, Ollama, and database.
- [ ] PDF report export.
- [ ] Incident timeline view.
- [ ] Correlation between multiple events.

## Important Notes For Report

- `PING`, `HTTP_HEALTH`, and `TCP_PORT` are active checks.
- `SNMP` provides structured metrics and status values, not classic application logs.
- Real logs should later come from Syslog, API ingestion, or a local agent.
- The platform uses a normalized event model: all raw checks and logs are converted into standard events consumed by the backend, dashboard, and AI analysis module.
- The AI module suggests diagnostic actions only. It does not execute commands automatically.
