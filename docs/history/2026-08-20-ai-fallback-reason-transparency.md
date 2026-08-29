# 2026-08-20 — Ollama fallback reason transparency

| Field | Value |
| --- | --- |
| Date | 2026-08-20 |
| Module | FastAPI AI analysis, Spring Boot reports, React UI |
| Type | Operator transparency / degraded-mode explainability |
| Status | Done |

## Context

The AI pipeline already selects a provider, calls Ollama `qwen3:8b`, validates the JSON, and falls back to the deterministic rules engine. Evidence-aware prompts, context compression, and response validation were already in place. Validation already produced internal codes such as `OLLAMA_INVALID_PRIORITY` and `OLLAMA_UNSUPPORTED_CLAIMS`.

## Problem

When Ollama could not be used, the operator mostly saw `provider=rules` (or historically `rules-fallback`). The report did not explain **why** Ollama was skipped. Intentional `AI_PROVIDER=rules` looked the same as an automatic fallback after a timeout or invalid model output.

## Design

Keep the existing try-Ollama-then-rules path. Attach structured fallback metadata to every analysis response:

```text
requestedProvider
actual provider
model
fallbackUsed
fallbackReasonCode
fallbackReason   (employee-facing, no stack traces)
```

Two situations stay distinct:

| Situation | provider | requestedProvider | fallbackUsed |
| --- | --- | --- | --- |
| Ollama succeeded | `ollama` | `ollama` | `false` |
| Ollama requested, then failed | `rules` | `ollama` | `true` + code/reason |
| Rules selected on purpose (`AI_PROVIDER=rules`) | `rules` | `rules` | `false`, no Ollama-failure reason |

Public codes are centralized. Validator-only codes (`OLLAMA_UNGROUNDED_IP`, `OLLAMA_LOW_QUALITY`, `OLLAMA_INVALID_COMMANDS`) map to `OLLAMA_RESPONSE_VALIDATION_FAILED` for the operator API.

## Failure mapping

| Failure | Code | Employee-facing meaning |
| --- | --- | --- |
| Connection/DNS/`URLError` | `OLLAMA_UNREACHABLE` | Local AI service could not be reached |
| HTTP 404 / model not found | `OLLAMA_MODEL_UNAVAILABLE` | Configured model is not available |
| HTTP/read timeout | `OLLAMA_TIMEOUT` | Model did not respond in time |
| Other non-success HTTP | `OLLAMA_HTTP_ERROR` | Unexpected HTTP error |
| Malformed JSON | `OLLAMA_INVALID_JSON` | Response could not be interpreted safely |
| Missing/invalid schema fields | `OLLAMA_SCHEMA_VALIDATION_FAILED` | Response did not match the report structure |
| Priority outside P1–P4 | `OLLAMA_INVALID_PRIORITY` | Priority value is not allowed |
| Compromise/exfil without evidence | `OLLAMA_UNSUPPORTED_CLAIMS` | Conclusions not supported by evidence |
| Other unrepaired validator failure | `OLLAMA_RESPONSE_VALIDATION_FAILED` | Safety/quality checks failed |
| Empty model output | `OLLAMA_EMPTY_RESPONSE` | Empty response |
| Unexpected exception | `OLLAMA_INTERNAL_ERROR` | Unexpected error requesting the model |

Technical exception text stays in server logs (`technical=...`). It is not copied into `fallbackReason`.

## Implementation

| File | Role |
| --- | --- |
| `fastapi/app/services/fallback_reasons.py` | Codes, employee messages, transport classification, attach helpers |
| `fastapi/app/schemas.py` | Optional `requestedProvider` / `fallbackUsed` / `fallbackReasonCode` / `fallbackReason` |
| `fastapi/app/services/ollama_client.py` | Map transport/parse/validation failures to codes; fallback provider is `rules` |
| `fastapi/app/services/analyzer.py` | Direct rules path uses `attach_direct_rules()` |
| `fastapi/app/services/response_validator.py` | Reuses the same reason constants |
| `backend/.../DiagnosticReport.java` | Persist fallback metadata |
| `backend/.../SaveDiagnosticReportRequest.java` | Optional save fields; old 21-arg constructor kept |
| `backend/.../DiagnosticReportResponse.java` | API DTO |
| `backend/.../DiagnosticReportService.java` | Save/load + `REPORT_CREATED` audit enrichment |
| `frontend/src/helpers/aiFallback.ts` | Normalize camelCase/snake_case; notice copy |
| `frontend/src/components/aiops/AiFallbackNotice.tsx` | Warning block for automatic fallback only |
| `frontend/src/pages/ai-analysis/index.tsx` | Live analysis notice + post-analysis audit details |
| `frontend/src/pages/reports/index.tsx` | Saved-report notice and provider column |
| `frontend/src/api/aiopsApi.ts` | Persist metadata when saving a report |

## Tests

FastAPI: success (no reason), unreachable, timeout, model unavailable, HTTP error, invalid JSON, schema, invalid priority, unsupported claims, generic validation, empty response, internal error (exception text hidden), direct rules (not an Ollama failure), JSON aliases, UI copy contract.

Spring Boot: persisted fallback report keeps `OLLAMA_TIMEOUT` metadata; `REPORT_CREATED` audit includes `fallbackUsed` and `fallbackReasonCode`; successful Ollama save is not stored as fallback.

## Example — successful Ollama

```json
{
  "provider": "ollama",
  "requestedProvider": "ollama",
  "model": "qwen3:8b",
  "fallbackUsed": false,
  "fallbackReasonCode": null,
  "fallbackReason": null
}
```

## Example — timeout fallback

```json
{
  "provider": "rules",
  "requestedProvider": "ollama",
  "model": "qwen3:8b",
  "fallbackUsed": true,
  "fallbackReasonCode": "OLLAMA_TIMEOUT",
  "fallbackReason": "The local AI model did not respond within the configured timeout (300 seconds)."
}
```

## Example — response-validation fallback

```json
{
  "provider": "rules",
  "requestedProvider": "ollama",
  "model": "qwen3:8b",
  "fallbackUsed": true,
  "fallbackReasonCode": "OLLAMA_UNSUPPORTED_CLAIMS",
  "fallbackReason": "The AI response contained conclusions that were not sufficiently supported by the incident evidence."
}
```

## Out of scope

Caching, RAG, model tuning, new providers, provider-status dashboard, prompt/compression/validation-rule changes, Syslog/NetFlow/PCAP detection, incident correlation.

## Verify

```bash
cd fastapi
PYTHONPATH=".:.venv/lib/python3.14/site-packages" python3 -m unittest tests.test_fallback_reasons tests.test_frontend_fallback_notice tests.test_response_validator tests.test_prompt_builder tests.test_context_compressor tests.test_pcap_analyzer -v
```

```bash
cd backend && ./mvnw test
```

```bash
docker compose build fastapi backend
docker compose up -d --force-recreate fastapi backend
```

Frontend is Vite (`npm run dev`); rebuild the SPA if serving a production bundle.

## Rapport talking points

- Fallback is intentional resilience, not an application crash.
- Operators see a stable code plus a safe sentence; engineers still have the exception in logs.
- Direct `AI_PROVIDER=rules` is not labelled as an Ollama failure.
- Saved reports keep the same distinction, so history cannot later look like an Ollama success.
