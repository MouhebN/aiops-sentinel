# 2026-08-20 — Deterministic Ollama response validation

| Field | Value |
| --- | --- |
| Date | 2026-08-20 |
| Module | FastAPI AI analysis (`qwen3:8b` via Ollama) |
| Type | Post-generation validation / report trustworthiness |
| Status | Done |

## Context

The pipeline already compressed incident context and attached evidence-aware prompt sections. Ollama still returns probabilistic JSON. A fluent report could omit fields, invent IPs, over-claim compromise, or suggest `show firewall statistics` / `rm -rf`.

## Problem

Responses were accepted after JSON parse + required keys only. There was no deterministic check that the diagnosis stayed inside the incident evidence.

## Design

```text
Ollama JSON
    → schema / types / lengths
    → priority normalize (P1–P4)
    → claim checks vs evidence index
    → diagnostic command checks
    → IP grounding
    → low-quality heuristics
    → safe repairs
    → validate again
VALID → return report
INVALID → existing rules fallback (reason code kept in logs)
```

No second LLM call. Compression, prompts, detection, and provider selection are unchanged.

## Claims that need supporting evidence

| Claim | Allowed only if context contains |
| --- | --- |
| Successful access / login / authentication | `AUTH_SUCCESS` / `LOGIN_SUCCESS` (or equivalent) |
| Compromise / breached host | malware, EDR, compromise, C2-style event types |
| Exfiltration / stolen credentials | explicit exfil/DLP-style evidence |
| Lateral movement / malware executed | compromise-class evidence |

Port-scan + PCAP + firewall DENY supports reconnaissance wording, not successful exploitation.

Safe downgrade example: `Unauthorized access occurred` → `risk of unauthorized access`.

## Command checks

Allowed: `tcpdump`, `tshark`, `ss`, `ip`, `ping`, `curl`, `grep`, `journalctl`, `traceroute`, `dig`, `nslookup`, `systemctl status`, and similar read-only tools.

Removed when vendor is unknown: `show firewall statistics`, `show access-lists`.

Rejected: `rm -rf`, `shutdown`, `reboot`, `iptables -F`.

Allowed: `tcpdump ... -w /tmp/capture.pcap`.

## Safe repairs

Trim whitespace, `p1`→`P1`, drop blank/duplicate list items, cap list sizes, truncate overlong text with `[truncated]`, strip bad commands, downgrade a few exact over-claims.

Not repaired: missing required fields, `P5`/`critical`, unknown factual IPs, unrepairable compromise wording.

## Implementation

| File | Role |
| --- | --- |
| `fastapi/app/services/evidence_index.py` | Known IPs, ports, event types, vendor, claim-support flags |
| `fastapi/app/services/response_validator.py` | Validation, repair, second pass, reason codes |
| `fastapi/app/services/ollama_client.py` | Validate before return; fallback on failure |
| `fastapi/tests/test_response_validator.py` | 19 tests |

Reason codes (logged, not yet shown in UI): `OLLAMA_INVALID_JSON`, `OLLAMA_SCHEMA_VALIDATION_FAILED`, `OLLAMA_INVALID_PRIORITY`, `OLLAMA_UNSUPPORTED_CLAIMS`, `OLLAMA_UNGROUNDED_IP`, `OLLAMA_INVALID_COMMANDS`, `OLLAMA_LOW_QUALITY`.

## Tests

Valid unchanged; `p1`→`P1`; `P5` rejected; blank field rejected; blank/duplicate lists repaired; unknown IP rejected; port-scan forbids compromise/exfil/successful auth; `risk of unauthorized access` allowed; `AUTH_SUCCESS` allows login wording; Linux commands pass; Cisco `show` removed; `rm -rf`/`shutdown`/`iptables -F` removed; `tcpdump -w` kept; unrepairable hits rules fallback; repaired payload re-validated.

## Example — lab port-scan (accepted)

NetFlow `PORT_SCAN` `192.168.0.15 → 192.168.0.1` ports 21,22,23,80,443,3306,5432 + PCAP confirmation.

Accepted summary shape: reconnaissance, NetFlow + PCAP named, `risk of unauthorized access`, `ss`/`ping`/`tcpdump -w`/`journalctl`.

## Example — unsafe (rejected or repaired)

Rejected: `The attacker breached the firewall at 192.168.0.1.` → `OLLAMA_UNSUPPORTED_CLAIMS` → rules fallback.

Repaired: `Unauthorized access occurred from 192.168.0.15...` → `risk of unauthorized access`.

Repaired: `show firewall statistics` removed; `ss -lntp` kept.

## Observability

```text
aiResponseValid=true aiResponseRepaired=true validationWarnings=1 unsupportedClaims=0 invalidCommands=2 provider=ollama model=qwen3:8b
```

```text
aiResponseValid=false reason=OLLAMA_INVALID_PRIORITY
```

## Out of scope

Caching, RAG, fallback-reason UI, second LLM critique, provider/model change.

## Verify

```bash
cd fastapi
PYTHONPATH=".:.venv/lib/python3.14/site-packages" python3 -m unittest tests.test_response_validator tests.test_prompt_builder tests.test_context_compressor tests.test_pcap_analyzer -v
```

```bash
docker compose build fastapi
docker compose up -d --force-recreate fastapi
```

## Rapport talking points

- Generation stays probabilistic; acceptance is deterministic.
- Claims are gated by an evidence index built from the same compressed incident context.
- Fallback reason codes are stored in logs for a later UI task.
