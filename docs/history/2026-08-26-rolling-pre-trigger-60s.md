# 2026-08-26 — Rolling pre-trigger window 60s

| Field | Value |
| --- | --- |
| Date | 2026-08-26 |
| Module | Capture sensor + Spring `app.pcap.rolling` |
| Type | Config / forensic buffer timing |
| Status | Done |

## Context

AUTO_ROLLING snapshots the sensor ring at first eligibility, then captures 20 s post-trigger. Manual **Capture traffic** is unchanged (still 20 s on-demand).

## Problem

Live validation after first-eligible auto-capture:

- one `port-scan.sh`
- NetFlow `PORT_SCAN` ~**27 s** later
- coordinator started `AUTO_ROLLING`; sensor `POST /snapshots` 201
- job ended **`EMPTY_CAPTURE`**
- ring pre-trigger was **20 s** (4×5 s), so the original SYNs had already rotated out

NetFlow is delayed by exporter flow expiry + nfcapd rotation + scheduled import (`NETFLOW_IMPORT_INTERVAL_MS=15000`). 20 s was shorter than observed detection latency.

**Not this change:** that same run had **no Syslog DENY** (firewall did not UDP-syslog). Leave that as a separate investigation.

## Design

Keep a bounded ring. Change defaults only:

| Setting | Old | New |
| --- | --- | --- |
| pre-trigger | 20 s | **60 s** |
| post-trigger | 20 s | 20 s |
| segment | 5 s | 5 s |
| expected files | 4 | **12** (`ceil(60/5)`) |
| max disk | 20 MB | 20 MB |

Janitor still deletes files older than `pre + segment` and enforces `maxBytes`. tcpdump still uses `-G 5` without `-W` (overwrite by deletion, not a fixed `-W` count). Trigger / idempotency / MANUAL path unchanged.

## Implementation (files)

| File | Role |
| --- | --- |
| `capture-sensor/app/rolling.py` | Default `ROLLING_PRE_TRIGGER_SECONDS=60` |
| `capture-sensor/Dockerfile` | Image env 60 |
| `backend/.../application.properties` | `app.pcap.rolling.pre-trigger-seconds=60` |
| `backend/.../PcapCaptureProperties.java` | Java default 60 |
| `frontend/.../PacketCapturePanel.tsx` | AUTO_ROLLING fallback 60s before |
| `docs/on-demand-pcap-capture.md` | Why 60 s; Syslog absence called out separately |

## Tests

| Check | Result |
| --- | --- |
| `test_rolling.RollingBufferTests.test_default_window_is_twelve_segments` | `expectedFiles=12`, max 20 MB |
| `test_rolling...test_prune_sixty_second_window_drops_rotated_out_segments` | Oldest gone; ring still bounded |
| `test_api...test_health_exposes_sixty_second_rolling_window` | `/health` `expectedFiles=12` |
| Existing 20 s prune test | Still valid as explicit override |
| `AutoCapturePolicyTests` / `AutoPacketCaptureServiceTests` | AUTO job `pre=60`, `post=20` |
| Manual `PacketCaptureJobServiceTests` | Unchanged 20 s duration |

## Result or example

```text
GET /health rolling:
  preTriggerSeconds=60
  segmentSeconds=5
  expectedFiles=12
  maxBytes=20971520
AUTO_ROLLING  60s before / 20s after detection
```

## Out of scope

Syslog DENY missing on the 2026-08-26 live scan; trigger/idempotency; tshark; MANUAL capture duration; raising the 20 MB cap.

## Verify commands

```bash
cd capture-sensor && PYTHONPATH=. python3 -m unittest discover -s tests -v
cd backend && ./mvnw -q -Dtest=AutoCapturePolicyTests,AutoPacketCaptureServiceTests,PacketCaptureJobServiceTests test
curl -sS http://172.30.30.10:8090/health
# expect rolling.expectedFiles=12, preTriggerSeconds=60, running=true
```

Recreate the capture sensor after image rebuild so the live ring uses 12 segments. Wait ≥60 s for a warm buffer before the next scan.

## Rapport talking points

- Buffer length must exceed NetFlow detection latency, not just packet RTT
- Boundedness is age prune + 20 MB cap, not unlimited history
- Syslog absence is independent of ring length
