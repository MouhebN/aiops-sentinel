# 2026-08-19 — Multi-evidence report attribution

| Field | Value |
| --- | --- |
| Date | 2026-08-19 |
| Module | FastAPI AI analysis (`qwen3:8b` via Ollama) |
| Type | Prompt quality / evidence-grounded reports |
| Status | Done |

## Context

Evidence-aware prompt sections were already added for Syslog, NetFlow, PCAP, metrics, and availability. The lab incident `Possible port scan from 192.168.0.15 to 192.168.0.1` already contained NetFlow (35 flows, 525 packets, ports 21/22/23/80/443/3306/5432), PCAP confirmation of the same scan, TCP retransmissions, and related security events.

## Problem

The prompt included the extra sections, but the generated report still collapsed to a single source, for example:

```text
NetFlow detected a port scan...
```

That hid PCAP corroboration, did not separate observed facts from inference, and still risked inventing vendor CLI such as `show firewall statistics` when the component is a generic FIREWALL / NetFlow collector.

## Design

Prompt-only refinement. Same pipeline, same JSON contract, same detection.

When two or more independent sources are present, the model must:

1. Name the sources in **summary or impact** (Syslog, NetFlow, PCAP, metrics, health checks).
2. Say what each source contributes.
3. State corroboration when they agree, or the gap when they do not.
4. Avoid “multiple evidence sources” with no names.
5. Keep observed vs inference vs not-established (no exploitation/compromise claim without evidence).

Diagnostic commands: match known platform; if vendor is unknown, prefer `tcpdump`, `ss`, `ip`, `journalctl`, `grep`, `ping`, `curl`; do not invent Cisco/vendor CLI.

## Implementation

| File | Change |
| --- | --- |
| `fastapi/app/services/prompt_templates.py` | Stronger `MULTI_EVIDENCE_INSTRUCTIONS`; diagnostic-command and summary-attribution rules in `BASE_INSTRUCTIONS` |
| `fastapi/tests/test_prompt_builder.py` | Attribution, contribution, corroboration, vendor-CLI, grounding tests; lab NetFlow 35/525 fixture |

Unchanged: NetFlow/PCAP/Syslog detection, Ollama provider, response schema, caching, context limits, fallback.

## Tests

1. NetFlow + PCAP prompt requires named source attribution.
2. Syslog + NetFlow + PCAP prompt requires each source’s contribution.
3. Multi-source prompt requires corroboration wording.
4. Unknown vendor does not encourage vendor-specific CLI.
5. Grounding rules remain present.

```bash
cd fastapi
PYTHONPATH=".:.venv/lib/python3.14/site-packages" python3 -m unittest tests.test_prompt_builder -v
```

## Result

Expected report shape for the lab scan (wording not fixed):

```text
Multiple independent evidence sources support a port-scan/reconnaissance event
from 192.168.0.15 to 192.168.0.1.

NetFlow detected repeated connections across seven destination ports, while
PCAP independently confirmed TCP scan behavior to the same service ports.
Together they corroborate reconnaissance, not successful compromise.
```

Observed: multi-port TCP attempts, same src/dst, NetFlow anomaly, PCAP confirmation.  
Inference: likely reconnaissance.  
Not established: successful access, exploitation, exfiltration.

## Out of scope

Context size limiting, caching, fallback changes, response validation.

## Verify after rebuild

```bash
docker compose build fastapi
docker compose up -d --force-recreate fastapi
```

Re-run AI analysis on the port-scan incident and check that summary/impact names NetFlow and PCAP.

## Rapport talking points

- Instruction presence is not enough; reports must **attribute** sources.
- Explicit observed / inference / not-established grounding for SOC wording.
- Diagnostic commands stay generic when vendor metadata is missing.
