# 2026-10-01 — Isolate Spring test databases

| Field | Value |
| --- | --- |
| Date | 2026-10-01 |
| Module | Backend tests / GitHub Actions |
| Type | Bugfix |
| Status | Done |

## Context

The new CI backend job runs `./mvnw -B test` on a GitHub-hosted runner. Locally that suite passed. On Actions, `TopologyServiceTests.topologyReturnsMonitoredComponentsAndRelationLinks` failed: `activeAttacks()` was expected to be empty and contained three `PORT_SCAN` incidents.

## Problem

Every `@SpringBootTest` used one JVM-wide database, `jdbc:h2:mem:aiops-test`, kept alive by `DB_CLOSE_DELAY=-1`. `IncidentCreatedAutoCaptureTests` commits through `TransactionTemplate` (three active incidents: `BANK-FW-01`, source `10.0.0.10`, evidence `SYSLOG` + `PCAP`). Surefire order on Linux is filesystem order, so that class can finish before `TopologyServiceTests`. The topology context was already cached, so Hibernate did not rebuild the schema, and the assertion saw the other class's rows.

## Design

Give each Spring test context its own in-memory database:

```properties
spring.datasource.url=jdbc:h2:mem:${random.uuid};DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false
```

`${random.uuid}` is resolved once when that context binds the datasource. A later context cannot read incidents committed by an earlier one. Tests inside one context still share a database; `@Transactional` rollback and the identity-resolution cleanup stay as they are.

## Implementation (files)

| File | Role |
| --- | --- |
| `backend/src/test/resources/application.properties` | Unique H2 name per test context |

## Tests

| Check | Result |
| --- | --- |
| `./mvnw -B -Dsurefire.runOrder=reversealphabetical test` | Pass, 171 tests, 0 failures |
| Hikari URLs in that run | Five distinct `jdbc:h2:mem:<uuid>` databases |

## Result / example

`topologyReturnsMonitoredComponentsAndRelationLinks` only sees incidents created inside `TopologyServiceTests`. The three auto-capture incidents stay in the capture-test context.

## Out of scope

- Changing auto-capture tests to roll back their `TransactionTemplate` commits
- SonarQube, Trivy, Docker Hub

## Verify commands

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
cd backend && ./mvnw -B -Dsurefire.runOrder=reversealphabetical test
```

## Rapport talking points

- The CI failure was test isolation, not a topology bug. GitHub's file order exposed a shared H2 database.
- Each Spring test context now gets its own in-memory database, so a committed port-scan incident cannot fail an unrelated assertion.
