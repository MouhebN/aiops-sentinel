# 2026-08-24 — Multi-IP component identity for incident context

| Field | Value |
| --- | --- |
| Date | 2026-08-24 |
| Module | Spring Boot components / incidents / topology / AI context + React UI |
| Type | Feature / bugfix |
| Status | Done |

## Context

Security incidents carry data-plane addresses (`SRC=10.0.0.10`, `DST=10.10.10.20`). Monitored components are checked on management addresses (`BANK-SRV-01 = 172.30.30.20`). Incident Detail “Component Context” and “Latest Metrics” only followed `deviceId = component-{id}` (typical of availability checks), so firewall Syslog incidents never linked the real target.

## Problem

Correlation knew only `MonitoredComponent.ipAddress`. Sentinel could not resolve `10.10.10.20 → BANK-SRV-01`. Existing port-scan incidents already stored `DST=10.10.10.20` and stayed empty until identity could be resolved dynamically.

## Design

- New table `component_network_interfaces` (name, IPv4, role `MANAGEMENT|LAN|WAN|SERVICE|OTHER`, `is_primary`). Keep `MonitoredComponent.ipAddress` as the monitoring/primary address. Interface IPs are globally unique; a component may also list its own primary IP as an interface.
- Shared `ComponentIdentityResolver.resolveByIp`: (1) exact match on `monitored_components.ip_address`, (2) exact match on interface `ip_address`. No per-evidence duplicate matchers.
- `IncidentComponentBinder` harvests src/dst from Syslog events, NetFlow, correlation key, then PCAP, then attaches `TARGET` / `SOURCE` / `ASSOCIATED` on `IncidentResponse.relatedComponents`. No persisted `incident.componentId`; old incidents resolve on read.
- Metrics and AI load by resolved component id. Topology `endpointsOf` uses the same resolver so the attack edge targets BANK-SRV-01, not the management IP. `10.0.0.10` stays an external entity.

Lab mappings used in tests/docs only (not hardcoded in business logic):

| Component | Management | Extra interfaces |
| --- | --- | --- |
| BANK-FW-01 | 172.30.30.10 | WAN 10.0.0.1, internal 10.0.1.1 |
| CORE-RTR-01 | 172.30.30.11 | uplink 10.0.1.2, LAN 10.10.10.1 |
| BANK-SRV-01 | 172.30.30.20 | Service/LAN **10.10.10.20** |
| ATM-01 | 172.30.30.30 | LAN 10.10.10.30 |
| BANK-CAMERA-01 | 172.30.30.40 | LAN 10.10.10.40 |
| CORE-SW-01 | 172.30.30.12 | — |

## Implementation (files)

| File | Role |
| --- | --- |
| `backend/.../component/ComponentNetworkInterface.java` + role enum + repo + service + REST on `/api/components/{id}/interfaces` | Interface CRUD |
| `backend/.../component/ComponentIdentityResolver.java` | Shared IP → component |
| `backend/.../incident/IncidentComponentBinder.java` | Dynamic related components on incident APIs |
| `backend/src/main/resources/db/migration/V2__component_network_interfaces.sql` | Flyway table + unique IP index |
| Hibernate `ddl-auto=update` (runtime) / `create-drop` (tests) | Active schema creator; Flyway is additional |
| `backend/.../topology/TopologyService.java` | Resolver instead of primary-IP-only match |
| `backend/.../ai/AiContextBuilderService.java` + FastAPI `ComponentContext` | Target name, primary IP, matched network IP |
| `frontend/.../NetworkInterfacesSection.tsx` + component create/edit/detail | Enter aliases |
| `frontend/.../incidents/detail/index.tsx` | Context + metrics from `relatedComponents` |

## Tests

```bash
/usr/bin/mvn -f backend/pom.xml test -Dtest=ComponentIdentityResolutionTests,TopologyServiceTests,ComponentLifecycleTests
PYTHONPATH=".:.venv/lib/python3.14/site-packages" python3 -m unittest tests.test_prompt_builder.EvidenceAwarePromptTests.test_component_context_includes_primary_and_matched_network_ip -v
```

Covered: primary IP; secondary IP; unknown `10.0.0.10`; `10.10.10.20` → BANK-SRV-01; old incident without recreate; metrics by component id; topology target + external attacker; Syslog and NetFlow same resolver; delete interface keeps component/metrics; monitoring `ipAddress` still `172.30.30.20`.

## Result / example

Incident `DST=10.10.10.20` after adding Service LAN on BANK-SRV-01:

- Component Context: BANK-SRV-01, primary `172.30.30.20`, matched `10.10.10.20`
- Latest Metrics: samples for component id (availability/check metrics if present)
- Topology: `external:10.0.0.10` → `component:{BANK-SRV-01 id}`
- AI: Target component BANK-SRV-01, Primary IP 172.30.30.20, Matched network IP 10.10.10.20

## Out of scope

fprobe, nfcapd, Containerlab topology, Syslog transport, PCAP capture workflow, incident inactivity window, monitoring scheduler, AI provider/model.

## Verify commands

Same Maven/unittest commands. Then: edit BANK-SRV-01 → keep `172.30.30.20` → add interface Service LAN / SERVICE / `10.10.10.20` → open existing port-scan incident with DST=10.10.10.20.

Live Postgres after backend recreate: table `component_network_interfaces` exists (Hibernate `ddl-auto=update`). There is still **no** `flyway_schema_history` on this volume, so Flyway V2 did not apply. V2 remains in the repo for when Flyway actually runs. Unique IP constraint was created by Hibernate.

## Rapport talking points

- Management-plane monitoring IPs and data-plane evidence IPs are different identities of the same component
- One resolver for Syslog, NetFlow, PCAP, topology, and AI avoids per-source IP matching bugs
- Existing incidents do not need rebuild: identity is resolved when building `IncidentResponse`
