# 2026-08-26 — PCAP file size vs captured packet bytes

| Field | Value |
| --- | --- |
| Date | 2026-08-26 |
| Module | Incident Detail PCAP UI + analysis DTO |
| Type | UX / metric labeling |
| Status | Done |

## Context

Incident Detail shows both the capture **job** and the tshark **analysis**. Operators (and a jury) saw two byte numbers that looked interchangeable.

## Problem

Example:

```text
7 relevant packets · 654 B     ← job
Packets: 7
Bytes: 518                     ← analysis
```

Both were unlabeled “bytes”. They are **not** the same quantity: 654 B is the `.pcap` on disk (headers included); 518 is the sum of tshark `frame_len` values.

## Design

Keep stored fields. Relabel in the UI. Expose a clearer JSON alias without dropping `totalBytes`.

| Metric | Source | Meaning |
| --- | --- | --- |
| PCAP file size | `job.fileSizeBytes` / `analysis.fileSize` | Length of the stored/transferred `.pcap` |
| Captured packet bytes | tshark `totalBytes` (`frame_len` sum) | Traffic bytes represented in the analysis |

They can differ. No DB migration.

## Implementation (files)

| File | Role |
| --- | --- |
| `frontend/.../PcapSizeMetric.tsx` | Labels + info tooltip |
| `frontend/.../PacketCapturePanel.tsx` | Job: Relevant packets / PCAP file size |
| `frontend/.../incidents/detail/index.tsx` | Analysis: Captured packet bytes + PCAP file size |
| `frontend/src/api/aiopsApi.ts` | Optional `capturedPacketBytes` |
| `backend/.../PacketCaptureAnalysisResponse.java` | `capturedPacketBytes` = `totalBytes` (keeps `totalBytes`) |

## Tests

| Check | Result |
| --- | --- |
| `PacketCaptureSizeSemanticsTests` | JSON has `fileSize=654`, `totalBytes=518`, `capturedPacketBytes=518` |
| `PacketCaptureJobServiceTests.pcapFileSizeAndTsharkCapturedBytesCanDiffer` | Ingest stores 654 vs 518 |
| `liveCaptureStoresFileSizeFromRetrievedPcapNotTsharkBytes` | Job file size from retrieved PCAP, analysis from tshark |
| Manual upload still works | `fileSize` ≠ `totalBytes` when tshark reports 1800 on an 8-byte stub file |
| `PacketCaptureRouteMappingTests` | REST still maps |

## Result or example

```text
Relevant packets: 7
PCAP file size: 654 B          ⓘ Size of the saved capture file, including PCAP metadata.

Captured packet bytes: 518 B   ⓘ Total size of the packets represented in the analysis.
PCAP file size: 654 B
```

## Out of scope

Changing tshark, NetFlow “Bytes” on flow rows, AI prompt wording, DB schema.

## Verify commands

```bash
cd backend && ./mvnw -q -Dtest=PacketCaptureSizeSemanticsTests,PacketCaptureJobServiceTests,PacketCaptureRouteMappingTests test
```

Incident Detail → completed AUTO_ROLLING or MANUAL job + Packet capture evidence.

## Rapport talking points

- File size includes libpcap headers; analysis bytes are packet/frame lengths
- The two numbers disagreeing is expected, not a bug
