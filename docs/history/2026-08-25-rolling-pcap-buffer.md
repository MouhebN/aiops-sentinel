# 2026-08-25 — Rolling pre-trigger PCAP buffer and auto-capture

| Field | Value |
| --- | --- |
| Date | 2026-08-25 |
| Module | Capture sensor + Spring PCAP jobs + Incident Detail |
| Type | Feature / forensic buffering |
| Status | Done |

## Context

On-demand PCAP already worked (operator clicks Capture traffic → LAB_SENSOR → tshark). A post-trigger-only auto-capture would start **after** incident persistence, so the original scan SYNs could already be gone.

## Problem

Sentinel detects PORT_SCAN from Syslog/NetFlow after packets have crossed BANK-FW-01 `eth1`. Without a short rolling buffer, automatic capture cannot include pre-detection traffic.

## Design

Bounded ring on the lab sensor: tcpdump `-G 5` into `buffer-%Y%m%d%H%M%S.pcap`, janitor deletes files older than the pre-trigger window and enforces `max-file-size-mb`. Snapshot copies ring files **without stopping** the rolling process, runs a post-trigger capture, merges with mergecap or a libpcap merge (not byte concatenation), optional host filter, ingest via existing `PacketCaptureAnalysisService`.

```text
IncidentCreatedEvent (after commit)
  → AutoCapturePolicy
  → AUTO_ROLLING job
  → POST /snapshots
  → pre + post PCAP → tshark
```

Only **new** open SECURITY episodes matching configured tokens (`PORT_SCAN`, `SUSPICIOUS`, `RECON`). Extra Syslog/NetFlow on the same episode does not snapshot again. Manual capture stays `MANUAL`.

## Implementation (files)

| File | Role |
| --- | --- |
| `capture-sensor/app/rolling.py` | Ring buffer + prune |
| `capture-sensor/app/pcaputil.py` | Merge / count |
| `capture-sensor/app/captures.py` | `start_snapshot` |
| `backend/.../AutoCapturePolicy.java` | Eligibility |
| `backend/.../IncidentCreatedEvent.java` | After-commit trigger |
| `backend/.../AutoPacketCaptureListener.java` | Async listener |
| `backend/.../PacketCaptureJob.java` | `CaptureTrigger`, pre/post seconds |
| `frontend/.../PacketCapturePanel.tsx` | AUTO_ROLLING status |
| `scripts/lib/pfe-env.sh` / `pfe-dataplane.sh` | Rebuild sensor on recreate; health requires rolling |
| `docs/on-demand-pcap-capture.md` | Operator + production mapping |

## Tests

| Check | Result |
| --- | --- |
| Sensor (23 tests: pcaputil, rolling, API, store, bpf) | Pass |
| `AutoCapturePolicyTests` | PORT_SCAN eligible; availability/resolved/FIREWALL_DENY not |
| `AutoPacketCaptureServiceTests` | Auto snapshot, idempotent, provider fail, tshark, AI, manual distinguishable |
| `IncidentCreatedAutoCaptureTests` | After-commit listener; same episode no second job; new episode new snapshot |
| `PacketCaptureJobServiceTests` / `PacketCaptureRouteMappingTests` | Manual capture and REST routes still work |

## Result or example

```text
Automatic forensic capture
AUTO_ROLLING  COMPLETED
20s before / 20s after detection
7 relevant packets · 518 B · Analysis available
```

## Out of scope

Unlimited full-packet capture, NDR vendors, auto-rerun AI when PCAP arrives, reopening RESOLVED incidents.

## Verify commands

```bash
cd capture-sensor && PYTHONPATH=. python3 -m unittest discover -s tests -v
cd backend && ./mvnw -q -Dtest=AutoCapturePolicyTests,AutoPacketCaptureServiceTests,IncidentCreatedAutoCaptureTests,PacketCaptureJobServiceTests,PacketCaptureRouteMappingTests test
```

Live: start Sentinel, confirm rolling on `/health`, run `port-scan.sh` **once**, do not click Capture. Expect AUTO_ROLLING PCAP with the original SYNs.

## Rapport talking points

- Bounded forensic buffer, not continuous full capture
- Pre-trigger packets require the ring to already be running
- Incident creation is independent of tcpdump
