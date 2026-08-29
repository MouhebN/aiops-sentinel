# 2026-08-19 — AI context compression and size limits

| Field | Value |
| --- | --- |
| Date | 2026-08-19 |
| Module | FastAPI AI analysis (`qwen3:8b` via Ollama) |
| Type | Prompt context compression / token efficiency |
| Status | Done |

## Context

Incidents such as `Possible port scan from 192.168.0.15 to 192.168.0.1` can include 35 nearly identical NetFlow events plus PCAP, NetFlow summaries, and previous incidents. The operator UI still needs the full list. The LLM does not.

## Problem

Sending every repeated event line:

- inflates prompt size and Ollama latency
- wastes CPU/RAM
- makes `qwen3:8b` focus on repeated noise
- can approach the model context window

The previous builder only sliced the latest N related events and, as a last resort, shortened the whole prompt. It did not group equivalents or protect critical evidence by priority.

## Design

Compression applies **only to the AI prompt**. Database, incident UI, PCAP viewer, and NetFlow evidence are unchanged.

```text
full incident data
        ↓
normalize + group repeated events
        ↓
omit NetFlow event rows already covered by a NetFlow summary
        ↓
limit lists by relevance (never drop the only supporting evidence)
        ↓
detect evidence types from the compressed view
        ↓
evidence-aware prompt sections
        ↓
Ollama
```

Grouping key: event type, event source, source IP, destination IP, protocol, severity, resulting status. Destination ports are collected into the group, not used as a split key.

Priority when a cap is hit: security/syslog/unique/critical groups first; previous similar incidents are dropped before PCAP/NetFlow/metrics.

## Default limits

Configurable via environment (also in `docker-compose.yml`):

| Env | Default |
| --- | --- |
| `AI_MAX_EVENT_GROUPS` | 10 |
| `AI_MAX_PREVIOUS_SIMILAR_INCIDENTS` | 3 |
| `AI_MAX_METRIC_SERIES` | 10 |
| `AI_MAX_PCAP_SUMMARIES` | 3 |
| `AI_MAX_NETFLOW_GROUPS` | 5 |
| `AI_MAX_MESSAGE_CHARS` | 1000 |

Raw logs/messages longer than the limit end with ` [truncated]` (never silent cuts).

Metrics: samples of the same name are aggregated (`current`, `min`, `max`, `average`, `samples`).

PCAP: keep packets/bytes, scan pair, service ports, extra findings (e.g. retransmissions). Do not dump large top-N IP lists into the prompt.

## Implementation

| File | Role |
| --- | --- |
| `fastapi/app/config.py` | `ContextLimits` + env defaults |
| `fastapi/app/services/context_compressor.py` | grouping, caps, PCAP/NetFlow/metric compression |
| `fastapi/app/services/prompt_builder.py` | format compressed context; evidence detection on compressed view |
| `fastapi/app/services/ollama_client.py` | log raw vs sent sizes |
| `fastapi/tests/test_context_compressor.py` | compression tests |
| `docker-compose.yml` | expose the new FastAPI env vars |

Unchanged: NetFlow/PCAP/Syslog detection, correlation, Ollama provider, response schema, UI data.

## Tests

`tests/test_context_compressor.py` covers grouping, non-merge of different types/pairs, security survival under caps, PCAP/NetFlow/Syslog preservation, metric bounds, raw-log truncation, configurable limits, and the lab 35-event scan.

## BEFORE vs AFTER (lab port-scan)

**Before (conceptual):** 35 `Netflow Port Scan` lines, plus a verbose PCAP top-N dump, plus the NetFlow summary.

**After (actual AI context excerpt):**

```text
Related event groups:
- omitted 35 repeated NetFlow/port-scan events because aggregated NetFlow evidence is included

Packet capture summaries:
- file=incident-portscan.pcap | packets=367 | bytes=133756 | protocols=TCP (360), UDP (7)
  scan confirmed: 192.168.0.15 -> 192.168.0.1
  ports=21, 22, 23, 80, 443, 3306, 5432
  additional findings=TCP retransmissions observed

NetFlow evidence:
- anomaly=PORT_SCAN | source=192.168.0.15 | destination=192.168.0.1
  | ports=21, 22, 23, 80, 443, 3306, 5432 | protocol=TCP
  | flowCount=35 | packets=525 | bytes=60375 | ...
```

NetFlow and PCAP prompt sections still activate.

## Example log line

```text
relatedEventsRaw=35 eventGroupsSent=0 pcapSummariesRaw=1 pcapSummariesSent=1
netflowGroupsRaw=1 netflowGroupsSent=1 promptCharacters=6000 contextCompressed=True
```

## Out of scope

Analysis caching, response validation, fallback changes, RAG, provider changes, frontend redesign.

## Verify

```bash
cd fastapi
PYTHONPATH=".:.venv/lib/python3.14/site-packages" python3 -m unittest tests.test_context_compressor tests.test_prompt_builder tests.test_pcap_analyzer -v
```

```bash
docker compose build fastapi
docker compose up -d --force-recreate fastapi
```

## Rapport talking points

- AI context is a compressed view, not a dump of the incident store.
- Repeated telemetry is represented as counts + first/last seen + ports.
- Evidence-aware instructions survive grouping (35 NetFlow rows still imply NetFlow).
