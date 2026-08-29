# Improvement history (rapport notes)

This folder records **what changed, why, and how to verify it**.

Use it when writing the PFE rapport: each file is one improvement, written so the problem, design, files, tests, and result can be copied into the report.

Operational how-to guides stay in `docs/` (Ollama, NetFlow, Syslog, PCAP testing). This folder is the **chronological history of product/AI improvements**.

A Cursor project rule (`.cursor/rules/pfe-history.mdc`) tells the agent to add an entry here after each completed feature, without being asked.

## How to add an entry

1. Create a file named `YYYY-MM-DD-short-title.md` in this folder.
2. Add a row to the table below (newest first).
3. Follow the template in the latest entry: context, problem, design, files, tests, result, out of scope.

## Index

| Date | Title | Module | File |
| --- | --- | --- | --- |
| 2026-08-29 | Dockerize frontend into start-pfe | Docker Compose / Nginx | [2026-08-29-dockerize-frontend.md](./2026-08-29-dockerize-frontend.md) |
| 2026-08-27 | Operator demo scenario suite | scripts/demo + Containerlab | [2026-08-27-demo-scenario-suite.md](./2026-08-27-demo-scenario-suite.md) |
| 2026-08-27 | BANK-SRV-01 stale PID recovery | Containerlab bank-srv-01 HTTP | [2026-08-27-bank-srv-stale-pid.md](./2026-08-27-bank-srv-stale-pid.md) |
| 2026-08-26 | AUTO_ROLLING packet loss at rolling-segment boundary | Capture sensor rolling snapshot | [2026-08-26-rolling-snapshot-segment-boundary.md](./2026-08-26-rolling-snapshot-segment-boundary.md) |
| 2026-08-26 | Backend permanently on banklab-mgmt | Docker Compose / start-pfe / Syslog | [2026-08-26-backend-banklab-mgmt.md](./2026-08-26-backend-banklab-mgmt.md) |
| 2026-08-26 | PCAP file size vs captured packet bytes | Incident Detail PCAP UI + analysis DTO | [2026-08-26-pcap-size-metric-labels.md](./2026-08-26-pcap-size-metric-labels.md) |
| 2026-08-26 | Rolling pre-trigger window 60s | Capture sensor + Spring `app.pcap.rolling` | [2026-08-26-rolling-pre-trigger-60s.md](./2026-08-26-rolling-pre-trigger-60s.md) |
| 2026-08-26 | First-eligible AUTO_ROLLING PCAP | Spring Boot incidents / NetFlow / PCAP jobs | [2026-08-26-first-eligible-auto-pcap.md](./2026-08-26-first-eligible-auto-pcap.md) |
| 2026-08-25 | Rolling pre-trigger PCAP buffer and auto-capture | Capture sensor + Spring PCAP jobs | [2026-08-25-rolling-pcap-buffer.md](./2026-08-25-rolling-pcap-buffer.md) |
| 2026-08-25 | Containerlab data-plane recovery after host reboot | start-pfe.sh / bank-lab / capture sensor | [2026-08-25-dataplane-recovery.md](./2026-08-25-dataplane-recovery.md) |
| 2026-08-25 | Packet capture REST route collisions | Spring Boot PCAP REST | [2026-08-25-pcap-route-collisions.md](./2026-08-25-pcap-route-collisions.md) |
| 2026-08-25 | Automated on-demand PCAP capture | Spring Boot PCAP jobs + lab capture sensor + React | [2026-08-25-on-demand-pcap-capture.md](./2026-08-25-on-demand-pcap-capture.md) |
| 2026-08-24 | Multi-IP component identity for incident context | Spring Boot components / incidents / topology / AI + React | [2026-08-24-component-multi-ip-identity.md](./2026-08-24-component-multi-ip-identity.md) |
| 2026-08-24 | ACKNOWLEDGED lastActivityAt on new NetFlow evidence | Spring Boot NetFlow / incidents | [2026-08-24-acknowledged-last-activity-netflow.md](./2026-08-24-acknowledged-last-activity-netflow.md) |
| 2026-08-24 | Align incidents_status_check with RESOLVED | Spring Boot / PostgreSQL Flyway | [2026-08-24-incidents-status-check-resolved.md](./2026-08-24-incidents-status-check-resolved.md) |
| 2026-08-24 | Incident episode lifecycle and inactivity correlation | Spring Boot incidents + NetFlow + topology + UI | [2026-08-24-incident-episode-lifecycle.md](./2026-08-24-incident-episode-lifecycle.md) |
| 2026-08-24 | ICMP nfdump CSV type.code parsing | Spring Boot NetFlow import | [2026-08-24-icmp-netflow-csv-ports.md](./2026-08-24-icmp-netflow-csv-ports.md) |
| 2026-08-24 | Continuous interface-level NetFlow (fprobe on eth1) | Containerlab + nfcapd + import | [2026-08-24-softflowd-netflow.md](./2026-08-24-softflowd-netflow.md) |
| 2026-08-24 | Camera MediaMTX/ffmpeg restart after zombies | Containerlab camera-01 | [2026-08-24-camera-rtsp-restart.md](./2026-08-24-camera-rtsp-restart.md) |
| 2026-08-24 | Recover lab daemons after Docker restart | start-pfe.sh / bank-lab | [2026-08-24-lab-daemon-recovery.md](./2026-08-24-lab-daemon-recovery.md) |
| 2026-08-24 | Single-command PFE environment lifecycle | Docker Compose + Containerlab | [2026-08-24-environment-lifecycle.md](./2026-08-24-environment-lifecycle.md) |
| 2026-08-23 | Component operational-status aggregation | Spring Boot checks + topology | [2026-08-23-component-status-aggregation.md](./2026-08-23-component-status-aggregation.md) |
| 2026-08-23 | Stale SNMP community on FW/RTR | Spring Boot SNMP checks | [2026-08-23-stale-snmp-community-fw-rtr.md](./2026-08-23-stale-snmp-community-fw-rtr.md) |
| 2026-08-23 | SNMPv2c community on the wire | Spring Boot SNMP checks | [2026-08-23-snmp-v2c-community-pdu.md](./2026-08-23-snmp-v2c-community-pdu.md) |
| 2026-08-23 | SNMP RO community from mgmt subnet | Containerlab snmpd | [2026-08-23-snmpd-mgmt-rocommunity.md](./2026-08-23-snmpd-mgmt-rocommunity.md) |
| 2026-08-23 | Camera RTSP image (offline runtime) | Containerlab camera-01 | [2026-08-23-camera-offline-rtsp-image.md](./2026-08-23-camera-offline-rtsp-image.md) |
| 2026-08-23 | ATM TCP 9090 without runtime apk | Containerlab ATM | [2026-08-23-atm-tcp-busybox-nc.md](./2026-08-23-atm-tcp-busybox-nc.md) |
| 2026-08-23 | Living lab protocols (SNMP, RTSP, TCP) | Containerlab bank-lab | [2026-08-23-living-lab-snmp-rtsp-tcp.md](./2026-08-23-living-lab-snmp-rtsp-tcp.md) |
| 2026-08-23 | Persistent Stop Monitoring and Component delete | Spring Boot + React Components / topology | [2026-08-23-component-lifecycle-stop-delete.md](./2026-08-23-component-lifecycle-stop-delete.md) |
| 2026-08-23 | Topology details panel opacity | React Infrastructure map | [2026-08-23-topology-details-drawer-opacity.md](./2026-08-23-topology-details-drawer-opacity.md) |
| 2026-08-23 | Infrastructure / network topology map | Spring Boot + React | [2026-08-23-infrastructure-topology-map.md](./2026-08-23-infrastructure-topology-map.md) |
| 2026-08-23 | Lab nfcapd slot flush after NetFlow export | Containerlab NetFlow exporter | [2026-08-23-lab-nfcapd-slot-flush.md](./2026-08-23-lab-nfcapd-slot-flush.md) |
| 2026-08-23 | Syslog / NetFlow UTC timeline alignment | Syslog + NetFlow + lab sender | [2026-08-23-syslog-netflow-utc-timeline.md](./2026-08-23-syslog-netflow-utc-timeline.md) |
| 2026-08-22 | Idempotent NetFlow import / correlation_key reuse | Spring Boot NetFlow | [2026-08-22-netflow-import-idempotency.md](./2026-08-22-netflow-import-idempotency.md) |
| 2026-08-20 | Safe AI analysis result caching | FastAPI / Spring Boot / UI | [2026-08-20-ai-analysis-result-caching.md](./2026-08-20-ai-analysis-result-caching.md) |
| 2026-08-20 | Ollama fallback reason transparency | FastAPI / Spring Boot / UI | [2026-08-20-ai-fallback-reason-transparency.md](./2026-08-20-ai-fallback-reason-transparency.md) |
| 2026-08-20 | Deterministic Ollama response validation | FastAPI / Ollama | [2026-08-20-ollama-response-validation.md](./2026-08-20-ollama-response-validation.md) |
| 2026-08-19 | AI context compression and size limits | FastAPI / Ollama | [2026-08-19-ai-context-compression.md](./2026-08-19-ai-context-compression.md) |
| 2026-08-19 | Multi-evidence report attribution | FastAPI / Ollama | [2026-08-19-multi-evidence-report-attribution.md](./2026-08-19-multi-evidence-report-attribution.md) |
| 2026-08-19 | Evidence-aware AI prompt templates | FastAPI / Ollama | [2026-08-19-evidence-aware-ai-prompts.md](./2026-08-19-evidence-aware-ai-prompts.md) |
