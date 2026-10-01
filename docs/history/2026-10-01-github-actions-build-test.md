# 2026-10-01 — GitHub Actions build and test

| Field | Value |
| --- | --- |
| Date | 2026-10-01 |
| Module | GitHub Actions / backend, FastAPI, capture sensor, frontend |
| Type | CI |
| Status | Done |

## Context

AIOps Sentinel already has Maven tests (H2), Python `unittest` suites, and a production frontend build (`tsc` + Vite). Nothing ran those checks automatically when code was pushed. Later stages (SonarQube, Trivy, Docker Hub, Netlify) need a green build-and-test job first.

## Problem

A jury demo of DevSecOps needs a workflow that fails when tests or the UI build fail. The full `./mvnw test` suite was also red: `ComponentIdentityResolutionTests` saw `BANK-SRV-01` rows committed by earlier `@SpringBootTest` classes in the shared H2 database `aiops-test`, and the component scheduler could mutate those rows on a background thread.

## Design

One workflow, `.github/workflows/ci.yml`, named `CI`.

- Triggers: `push` and `pull_request`.
- Four parallel jobs on `ubuntu-latest`. The workflow is red if any job fails.
- `permissions: contents: read`. No secrets.
- In-progress runs on the same ref are cancelled when a new push arrives.

```text
push / pull_request
  ├─ Backend          Java 21, ./mvnw -B test
  ├─ FastAPI          Python 3.12, unittest
  ├─ Capture sensor   Python 3.12, unittest
  └─ Frontend         Node 20, npm ci && npm run build
```

Test profile changes so that suite is stable on a clean runner:

- `spring.task.scheduling.enabled=false` in `backend/src/test/resources/application.properties` (component checks and other `@Scheduled` tasks do not run during tests).
- `ComponentIdentityResolutionTests` deletes components left by other classes before each test. The class is `@Transactional`, so the delete rolls back and does not wipe data for later tests.

## Implementation (files)

| File | Role |
| --- | --- |
| `.github/workflows/ci.yml` | Parallel build and test jobs |
| `backend/src/test/resources/application.properties` | Disable scheduling in tests |
| `backend/src/test/java/com/aiops/backend/component/ComponentIdentityResolutionTests.java` | Isolate from components committed by other tests |
| `README.md` | Pointer to the Actions workflow |

## Tests

| Check | Result |
| --- | --- |
| `JAVA_HOME=java-21 ./mvnw -B test` | Pass, 171 tests, 0 failures |
| FastAPI `python -m unittest discover -s tests -t . -v` | Pass, 88 tests (tshark cases run locally because tshark is installed; CI skips them) |
| Capture sensor unittest on `python:3.12-slim` | Pass, 33 tests |
| `npm ci && npm run build` in `frontend/` | Pass |

## Result / example

GitHub Actions runs four checks on every push. Backend uses the in-memory H2 URL already set for tests, so the job does not start PostgreSQL. The frontend job is `npm run build` because the UI has no unit-test script; that is the same command as the Docker image build.

## Out of scope

- SonarQube, JaCoCo upload, Trivy, Docker Hub, Netlify
- Containerlab, `scripts/tests/*`, Ollama, and Compose
- Frontend `npm run lint`

## Verify commands

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
cd backend && ./mvnw -B test
cd fastapi && python -m unittest discover -s tests -t . -v
cd capture-sensor && python -m unittest discover -s tests -t . -v
cd frontend && npm ci && npm run build
```

Push the workflow, then open the Actions tab. A following push or pull request should show Backend, FastAPI, Capture sensor, and Frontend.

## Rapport talking points

- The first CI stage is build and test only: Java 21, Python 3.12, Node 20, matching the Dockerfiles.
- Tests do not need the bank lab. Backend uses H2; FastAPI skips tshark when it is absent; the capture sensor mocks tcpdump.
- The quality gate is the workflow result: a failing test or a broken frontend build blocks the green check before any later security or publish stage.
