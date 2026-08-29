# 2026-08-20 — Safe AI analysis result caching

| Field | Value |
| --- | --- |
| Date | 2026-08-20 |
| Module | FastAPI AI analysis, Spring Boot reports, React UI |
| Type | Performance / result reuse |
| Status | Done |

## Context

Ollama `qwen3:8b` analysis already uses evidence-aware prompts, context compression, deterministic validation, and rules fallback with an operator-facing reason. A real call can take several minutes. Operators often re-analyze the same unchanged incident.

## Problem

Every Analyze click called Ollama again even when the effective diagnostic context was identical. That wasted inference time, CPU/RAM, operator time, and GPU/Ollama capacity.

## Design

```text
compressed AI context
    → SHA-256 fingerprint
    → cache key = pipelineVersion + requestedProvider + model + fingerprint

hit  → return last validated Ollama report
miss → Ollama → validate → store only if accepted → return
```

Cache identity is SQLite in the FastAPI service (no Redis). Saved Spring Boot reports also store `contextFingerprint`, `cacheHit`, and `analysisDurationMs` so a persisted report keeps the same provenance.

In-process per-key lock: two identical concurrent requests share one Ollama call.

## What is fingerprinted

Compressed/normalized diagnostic state:

- incident id, correlation key, title, category, status, acknowledged, firstSeen
- primary event type/severity/message/details/device
- component identity and `lastStatus` (UP → DOWN misses)
- compressed event groups (type, endpoints, ports, count, representative message)
- NetFlow aggregates (`flowCount`, packets, bytes, ports, anomaly)
- PCAP summaries (file, packets, findings, ports)
- aggregated metrics (current/min/max/average/sampleCount)
- previous similar incidents (id/status/severity/eventCount)

Pipeline version constant: `AI_ANALYSIS_VERSION=v1`.

## What is excluded, and why

| Excluded | Why |
| --- | --- |
| `durationMinutes`, incident `lastSeenAt` | Change with clock/UI refresh |
| Previous diagnostic reports | Saving a report would otherwise bust the cache |
| PCAP `createdAt`, metric `sampledAt`, event group first/last seen | Retrieval timestamps, not evidence meaning |
| Component `lastCheckDetails` | Often includes the last poll clock |
| Request/report generation timestamps | Would make every request unique |

Duplicate identical NetFlow rows that compress to the same aggregate keep the same fingerprint. `flowCount` 35 → 200 misses.

## Fallback treatment

Automatic rules fallback (timeout, invalid JSON, validation failure, etc.) is **not** stored. A later Analyze retries Ollama. Intentional `AI_PROVIDER=rules` never uses the Ollama cache.

## Persistence

| Store | Role |
| --- | --- |
| FastAPI SQLite `ai_analysis_cache` | Skip Ollama on repeated Analyze |
| `diagnostic_reports.context_fingerprint` | Saved report keeps the context id |

Analyze cache hits do **not** create `REPORT_CREATED`. That audit still happens only when the operator saves a report.

## Implementation

| File | Role |
| --- | --- |
| `fastapi/app/services/context_fingerprint.py` | Canonical SHA-256 of compressed context |
| `fastapi/app/services/analysis_cache.py` | SQLite store + per-key lock |
| `fastapi/app/services/analyzer.py` | Lookup/store, duration, logs |
| `fastapi/app/schemas.py` | `cacheHit`, `analysisDurationMs`, `contextFingerprint` |
| `fastapi/app/config.py` | `AI_ANALYSIS_VERSION`, `AI_CACHE_ENABLED`, `AI_CACHE_PATH` |
| Spring `DiagnosticReport*` | Persist cache metadata |
| `frontend/src/helpers/aiFallback.ts` | Cached Yes/No, human duration |
| `frontend/src/pages/ai-analysis/index.tsx` | Header + audit `cacheHit` |
| `docker-compose.yml` | Cache volume + env |

## Tests

Lab PORT_SCAN 192.168.0.15 → 192.168.0.1, NetFlow 35 flows, PCAP, ports 21/22/23/80/443/3306/5432: first miss (`Ollama called=1`), second hit (`called still=1`). Also syslog/NetFlow/PCAP/metric/status misses; timestamp-only stable; model/provider/version misses; validation failure and timeout not cached; concurrent lock; JSON aliases; saved-report fingerprint.

## Example — first vs cache hit

```json
{
  "provider": "ollama",
  "model": "qwen3:8b",
  "cacheHit": false,
  "analysisDurationMs": 287400
}
```

```json
{
  "provider": "ollama",
  "model": "qwen3:8b",
  "cacheHit": true,
  "analysisDurationMs": 120
}
```

Expected operator UI: `Cached: No · Analysis time: 4m 47s` then `Cached: Yes · Analysis time: 120 ms`.

## Latency benefit

Cache miss ≈ Ollama runtime (often minutes). Cache hit ≈ SQLite read (typically tens to hundreds of milliseconds). New evidence changes the fingerprint and Ollama runs again.

## Out of scope

RAG, model replacement, Ollama tuning, Redis, prompt/compression/validation/fallback-rule changes, Syslog/NetFlow/PCAP detection, incident correlation.

## Verify

```bash
cd fastapi
PYTHONPATH=".:.venv/lib/python3.14/site-packages" python3 -m unittest tests.test_ai_analysis_cache tests.test_fallback_reasons tests.test_frontend_fallback_notice tests.test_response_validator tests.test_prompt_builder tests.test_context_compressor tests.test_pcap_analyzer -v
```

```bash
cd backend && bash ./mvnw test
```

```bash
docker compose build fastapi backend
docker compose up -d --force-recreate fastapi backend
```

## Rapport talking points

- Cache key is evidence + provider + model + explicit pipeline version, not wall-clock time.
- Only validated Ollama JSON is reusable; a timeout cannot become a permanent “success”.
- SQLite is enough for this PFE; the operator still gets an audit row on every Analyze, including cache hits.
