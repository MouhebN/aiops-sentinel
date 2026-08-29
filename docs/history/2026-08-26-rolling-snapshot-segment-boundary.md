# 2026-08-26 — AUTO_ROLLING packet loss at rolling-segment boundary

| Field | Value |
| --- | --- |
| Date | 2026-08-26 |
| Module | Capture sensor rolling snapshot |
| Type | Bugfix / race |
| Status | Done |

## Context

AUTO_ROLLING snapshots the lab sensor ring (12×5 s ≈ 60 s pre-trigger), then captures 20 s after detection. A deterministic 7-SYN port scan (21, 22, 23, 80, 443, 3306, 5432 every 2 s) produced incident #49. Firewall Syslog had all 7 DENYs. Job 8 completed with **6 packets**; **port 443 was missing**.

Trigger: 16:52:05.458 (port 23). Job RUNNING: 16:52:05.639. tcpdump `-G 5` rotates on a 5 s clock (around `:00/:05/:10`). Port 443 at 16:52:09 sits inside the **active** post-trigger segment, near the next rotation at `:10`.

## Problem

Snapshot assembly was:

1. `copy_window` (`shutil.copy2` of live ring files, including the **open** `-G` file)
2. **then** start post-trigger tcpdump
3. merge pre copies + post file
4. never re-read the ring

That leaves a visibility hole around the trigger:

- Packets written to the active segment **after** the copy are not in the pre snapshot.
- Post-trigger is not listening yet during that copy.
- `shutil.copy2` of a growing pcap can tear the last record; mergecap may skip that whole segment.
- Janitor `prune` could unlink a file while it was being copied (no shared lock).
- After post-trigger ends, later rolling packets (including one dropped by post tcpdump at a `-G` rotation) are discarded.

Port 443 was in that hole: too late for the first copy, not guaranteed in post, never recovered from the ring.

Detection, correlation, and BPF host filters were not at fault.

## Design

Do **not** widen pre/post duration. Close the gap:

1. Start post-trigger tcpdump **immediately** at trigger (covers `triggeredAt → triggeredAt + 20s`).
2. Copy the ring with **complete packet records only** (`read_packets` stops at a truncated tail; do not `copy2` a live file).
3. Wait for post-trigger.
4. Copy the ring **again** (overlap). After 20 s the 60 s ring still holds the whole post window.
5. Merge pre + overlap + post with **dedupe** on `(ts, payload)`.
6. Hold the rolling lock during copy and prune so the janitor cannot delete a file mid-snapshot.
7. Record `triggeredAt`, `captureWindowStart = triggeredAt − pre`, `captureWindowEnd = triggeredAt + post` on the sensor job and on the Spring DTO (derived from `startedAt` + pre/post; no new DB column).

Rolling tcpdump is never stopped. No forced rotate (that would open another gap).

```text
rolling  ===============================================>
         trigger
            |
post tcpdump|----------- 20s -------------|
pre copy    ^
overlap copy                              ^
merge(pre ∪ post ∪ overlap)  — continuous
```

## Implementation (files)

| File | Role |
| --- | --- |
| `capture-sensor/app/pcaputil.py` | `copy_complete_pcap`, `write_pcap`, merge `dedupe` |
| `capture-sensor/app/rolling.py` | Locked complete-packet `copy_window`; locked `prune` |
| `capture-sensor/app/captures.py` | Post-first snapshot + overlap copy; window timestamps |
| `capture-sensor/app/main.py` | API `triggeredAt` / window fields |
| `backend/.../PacketCaptureJobResponse.java` | Derived window in job JSON |
| `capture-sensor/tests/test_snapshot_boundary.py` | 7-SYN / 5 s boundary regression |
| `backend/.../PacketCaptureWindowTests.java` | Window = trigger − 60s / + 20s |

## Tests

| Check | Result |
| --- | --- |
| Packets just before / at / after a 5 s rotation all copied | Pass |
| Torn active-segment tail dropped; complete records kept | Pass |
| Post omits 443; overlap copy restores all 7 unique SYNs | Pass |
| `Popen` (post) before first ring copy | Pass |
| Merge dedupes overlapping pre/post copies | Pass |
| Rolling process still running after snapshot | Pass |
| `PacketCaptureWindowTests` | Pass |
| `PacketCaptureRouteMappingTests` | Pass |

## Result / example

Same 7-SYN timeline must yield `capture-N.pcap` with ports 21, 22, 23, 80, **443**, 3306, 5432. Window is `startedAt − 60s` … `startedAt + 20s`, not “job started + a leftover interval”.

## Out of scope

Syslog/NetFlow detectors, incident correlation, BPF src/dst, changing 60/20/5 durations, forcing tcpdump `-G` rotate (would stop the ring).

## Verify commands

```bash
cd capture-sensor && PYTHONPATH=. python3 -m unittest discover -s tests -v
cd backend && ./mvnw -q -Dtest=PacketCaptureWindowTests,PacketCaptureRouteMappingTests,PacketCaptureJobServiceTests test
```

Rebuild `pfe-capture-sensor:local` so the lab runs the new snapshot path. The ring is empty until it refills (~60 s). Then one `port-scan.sh`; expect 7 SYNs in the AUTO_ROLLING pcap.

## Rapport talking points

- Pre-buffer and post-capture must **overlap** at the trigger; copy-then-start is a hole
- Live pcap files cannot be byte-copied; trailing records may be incomplete
- Bounded ring is enough to **re-read** the post window after 20 s (60 s retention)
