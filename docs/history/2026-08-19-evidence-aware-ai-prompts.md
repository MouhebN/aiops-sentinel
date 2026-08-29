# 2026-08-19 — Evidence-aware AI prompt templates

| Field | Value |
| --- | --- |
| Date | 2026-08-19 |
| Module | FastAPI AI analysis (`qwen3:8b` via Ollama) |
| Type | Prompt engineering / diagnostic quality |
| Status | Done |

## Context

AIOps Sentinel correlates several evidence sources on one incident: Syslog, NetFlow, PCAP, component health/availability, and metrics. The Spring Boot backend already sends this context to FastAPI. FastAPI still uses **one** analysis pipeline and **one** JSON response contract.

## Problem

The model received rich context, but the **reasoning instructions were generic**. Reports could be factually acceptable and still:

- ignore Syslog parser semantics (for example treating `FIREWALL_DENY` as successful access)
- treat NetFlow as if it contained packet payloads
- let unrelated PCAP background traffic dominate the assessment
- claim compromise, exploitation, or service impact without supporting evidence
- fail to say when Syslog, NetFlow, and PCAP independently confirm the same hypothesis

The goal was **not** a separate AI pipeline per incident type.

## Design

Keep one diagnostic flow. Build the prompt from:

1. **Base instructions** (always present)
2. **Optional evidence-specific sections** (included only when that evidence exists)
3. **Multi-evidence correlation** (included when at least two independent sources are present)
4. Existing incident header + context DTO formatting (unchanged sources)

```text
Base diagnostic instructions
+ Syslog instructions     if Syslog events exist
+ NetFlow instructions    if network_flow_summaries exist
+ PCAP instructions       if packet_capture_summaries exist
+ Metrics instructions    if recent_metrics exist
+ Availability instructions if PING/HTTP/TCP/RTSP/SNMP/health-check
+ Multi-evidence section  if two or more independent sources
+ Incident payload
```

Detection reuses the existing FastAPI DTOs (`IncidentAnalysisRequest`, `IncidentContext`). `FIREWALL_DENY` does not trigger availability instructions just because the log contains `TCP`.

## Base instructions (always)

The model must:

- act as an AIOps/SOC diagnostic assistant for an IT/SOC operator
- stay concise but technically useful
- distinguish observed evidence from AI inference
- never claim successful compromise, data exfiltration, authentication, malware execution, or service impact unless evidence supports it
- correlate agreeing sources and state contradictions
- give concrete remediation (not only “monitor” / “investigate further”)
- return the existing JSON schema: `summary`, `priority`, `impact`, `risk`, `probable_causes`, `suggested_actions`, `diagnostic_commands`

## Evidence-specific rules (summary)

| Evidence | When included | What the model is told |
| --- | --- | --- |
| Syslog | related event `eventSource=SYSLOG`, or raw log / parsing profile / syslog source name | Explain what the device reported. `FIREWALL_DENY` = blocked traffic, not successful access. |
| NetFlow | `networkFlowSummaries` present | Traffic-pattern metadata only. `PORT_SCAN` = reconnaissance / multi-port attempts, not exploitation. |
| PCAP | `packetCaptureSummaries` present | Packet-level confirmation or contradiction. SYN findings vs retransmissions. Ignore background noise. |
| Metrics | `recentMetrics` present | Spike vs sustained degradation vs capacity vs availability. Do not infer root cause from one metric alone. |
| Availability | event type / category / DOWN component with PING, HTTP, TCP, RTSP, SNMP | Propose checks matching the monitoring method. |
| Multi-evidence | two or more independent sources | State agreement explicitly; mention when one source does not confirm another. Do not claim successful compromise from a correlated scan. |

## Implementation

Prompt text lives in reusable constants. Assembly lives in a builder. Ollama calling code only asks for the built prompt.

| File | Role |
| --- | --- |
| `fastapi/app/services/prompt_templates.py` | Base + optional instruction constants and JSON schema |
| `fastapi/app/services/prompt_builder.py` | `detect_evidence()`, `build_instruction_sections()`, `build_prompt()` |
| `fastapi/app/services/ollama_client.py` | Unchanged provider/fallback; uses `build_prompt()` |
| `fastapi/tests/test_prompt_builder.py` | Section inclusion/exclusion tests |

Unchanged in this step: NetFlow detection, PCAP analysis, Syslog parsing, incident correlation, Ollama model selection, rules fallback, response schema.

## Tests

`fastapi/tests/test_prompt_builder.py` checks:

1. Syslog-only prompt includes Syslog instructions, not PCAP/NetFlow.
2. NetFlow-only prompt includes NetFlow instructions.
3. PCAP evidence includes PCAP instructions.
4. Metrics context includes metric instructions.
5. Mixed Syslog + NetFlow + PCAP includes all three plus correlation.
6. Missing evidence types do not add extra instruction sections.
7. Base grounding (SOC role, no unfounded compromise claim, JSON contract) is always present.

Lab scenario asserted in the mixed test:

```text
192.168.0.15 -> 192.168.0.1
NetFlow PORT_SCAN
ports 21, 22, 23, 80, 443, 3306, 5432
PCAP confirms the same scan
firewall DENY Syslog
```

The generated prompt must tell the model to correlate these sources and **not** claim successful compromise.

## Example prompt sections (Syslog + NetFlow + PCAP)

```text
You are an AIOps/SOC diagnostic assistant for an IT supervision platform.
...
Never claim successful compromise, data exfiltration, authentication, malware execution, or service impact unless evidence supports it.
When multiple evidence sources agree, correlate them. When they disagree, state the contradiction.
...
Analyze the incident and return only valid JSON using this schema: { ... }

Syslog evidence instructions:
Syslog evidence is present. Explain what the device explicitly reported.
... FIREWALL_DENY means traffic was blocked, not successful access. ...

NetFlow evidence instructions:
NetFlow evidence is present. Treat it as traffic-pattern metadata, not packet payload evidence.
... For PORT_SCAN, reason about reconnaissance ... Do not claim exploitation ...

PCAP evidence instructions:
PCAP evidence is present. Use it as packet-level confirmation or contradiction of other evidence.
... Do not let unrelated background packets dominate the incident assessment.

Multi-evidence correlation:
Multiple independent evidence types are present. If they support the same hypothesis, state that explicitly.
Example: Syslog reports repeated firewall DENY events; NetFlow independently shows multi-port traffic to the same destination; PCAP confirms TCP SYN attempts to the same service ports. Together these are high-confidence reconnaissance/port scanning, not successful compromise.
If one source does not confirm another, say so.
```

Metrics and availability sections are omitted when those sources are absent.

## Out of scope (later)

- Prompt context size limiting improvements
- Prompt caching
- Ollama fallback behaviour changes
- Extra response validation

## Verify

```bash
cd fastapi
PYTHONPATH=".:.venv/lib/python3.14/site-packages" python3 -m unittest tests.test_prompt_builder tests.test_pcap_analyzer -v
```

```bash
docker compose build fastapi
docker compose up -d --force-recreate fastapi
```

## Rapport talking points

- One LLM pipeline, evidence-conditional instructions (modular, extensible).
- Grounding: evidence vs inference; no over-claiming of compromise.
- Multi-source correlation is explicit when Syslog, NetFlow, and PCAP agree.
- Compatible with `qwen3:8b` (compact sections, same JSON contract).
