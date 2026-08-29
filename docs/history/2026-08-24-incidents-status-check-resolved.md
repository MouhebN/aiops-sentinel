# 2026-08-24 — Align incidents_status_check with RESOLVED

| Field | Value |
| --- | --- |
| Date | 2026-08-24 |
| Module | Spring Boot / PostgreSQL schema (Flyway) |
| Type | Bugfix |
| Status | Done |

## Context

Incident lifecycle now persists `ACTIVE`, `ACKNOWLEDGED`, and `RESOLVED`. Hibernate `ddl-auto=update` created the incidents table earlier from the original enum.

## Problem

PostgreSQL still had Hibernate's original check:

```sql
CHECK (status IN ('ACTIVE', 'RECOVERED'))
```

Writing `status = 'RESOLVED'` (operator resolve, or recovery mapping) failed:

```text
ERROR: new row for relation "incidents" violates check constraint "incidents_status_check"
```

`ddl-auto=update` does not rewrite CHECK constraints. There were no existing Flyway migrations.

## Design

Introduce Flyway against the already-populated Postgres volume:

- `baseline-on-migrate=true`, `baseline-version=0` so `V1` **runs** on the current DB (baselining at `1` would skip `V1`)
- Do not edit a fake historical V1 that was never applied
- Drop the old check **before** rewriting `RECOVERED` → `RESOLVED` (the old check would reject `RESOLVED`)
- Recreate `incidents_status_check` as `ACTIVE | ACKNOWLEDGED | RESOLVED`
- Skip the body if `public.incidents` does not exist yet (empty DB: Hibernate still creates the table)
- Tests keep H2 `create-drop` and `spring.flyway.enabled=false`

No Java lifecycle change. `IncidentStatus.RECOVERED` remains only for reading unmigrated rows; persistence uses `RESOLVED`.

## Implementation (files)

| File | Role |
| --- | --- |
| `backend/pom.xml` | `flyway-core` + `flyway-database-postgresql` |
| `backend/src/main/resources/application.properties` | Flyway baseline 0 |
| `backend/src/main/resources/db/migration/V1__align_incidents_status_check.sql` | Drop / migrate / add check |
| `backend/src/test/resources/application.properties` | Flyway off on H2 |
| `backend/src/test/java/com/aiops/backend/incident/IncidentStatusCheckMigrationTests.java` | SQL order and allowed values |

## Tests

```bash
/usr/bin/mvn -f backend/pom.xml test -Dtest=IncidentStatusCheckMigrationTests,IncidentEpisodeCorrelationTests
```

## Result / example

Old: `status IN ('ACTIVE', 'RECOVERED')`.  
New: `status IN ('ACTIVE', 'ACKNOWLEDGED', 'RESOLVED')`.  
Live snapshot before migrate: 27 `ACTIVE` rows, 0 `RECOVERED` (UPDATE still runs for safety).

## Out of scope

Incident correlation window, UI, Hibernate enum mapping, other `*_check` constraints.

## Verify commands

```bash
docker compose up -d --build --force-recreate backend
docker compose exec -T postgres psql -U aiops -d aiops_sentinel -c "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'incidents_status_check';"
```

Expect `ACTIVE`, `ACKNOWLEDGED`, `RESOLVED`. Then resolve an incident in the UI.

## Rapport talking points

- Hibernate enum CHECK constraints are not evolved by `ddl-auto=update`
- Flyway on an existing volume must baseline below the first real script
- Cannot `UPDATE ... SET status = 'RESOLVED'` while the old check is still in place
