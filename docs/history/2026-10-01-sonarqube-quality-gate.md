# 2026-10-01 — SonarQube quality gate

| Field | Value |
| --- | --- |
| Date | 2026-10-01 |
| Module | GitHub Actions / JaCoCo / SonarQube |
| Type | CI quality gate |
| Status | Done |

## Context

The CI workflow already builds and tests the backend, FastAPI, capture sensor, and frontend in parallel. The next gate is code quality on the Java backend: coverage plus blocker bugs and vulnerabilities, visible both in Actions and on a local dashboard.

## Problem

A green test job does not say whether the backend has blocker defects. JaCoCo only measures which lines tests executed. SonarQube is the server that reads that report and applies a quality gate. The default Sonar way gate treats a first analysis as new code and requires about 80% coverage, which would fail this codebase before a coverage target is chosen.

## Design

JaCoCo runs during `mvn test` and writes `backend/target/site/jacoco/jacoco.xml`.

A fifth GitHub Actions job, **SonarQube**, starts after **Backend**. It raises `vm.max_map_count`, starts `sonarqube:community`, waits until `/api/system/status` is `UP`, replaces `admin` / `admin`, and installs a project quality gate named `AIOps Sentinel`:

- blocker issues greater than 0 (`software_quality_blocker_issues`, or `blocker_violations` on older servers)
- vulnerabilities greater than 0 (`vulnerabilities` or `software_quality_security_issues`; rating worse than A if those counts are absent)

That gate is the server default before the scan, so the project does not inherit Sonar way. `./mvnw verify sonar:sonar` sends the JaCoCo XML. `sonar.qualitygate.wait=true` polls the quality-gate API, and a following step reads `/api/qualitygates/project_status` and fails the job unless the status is `OK`.

The same image is a Compose service `sonarqube` on profile `quality`, port 9000. `./scripts/start-pfe.sh` does not pass that profile.

## Implementation (files)

| File | Role |
| --- | --- |
| `backend/pom.xml` | JaCoCo 0.8.13, XML report path, project key `aiops-sentinel-backend` |
| `.github/workflows/ci.yml` | SonarQube job after Backend |
| `.github/scripts/prepare-sonarqube.sh` | Password, quality gate, token |
| `docker-compose.yml` | Profile `quality`, port 9000 |
| `README.md` | CI job and local dashboard commands |

## Tests

| Check | Result |
| --- | --- |
| `./mvnw -B test` on Java 21 | Pass, 171 tests |
| `backend/target/site/jacoco/jacoco.xml` | Present after the test run |
| `docker compose config --services` | Does not list `sonarqube` |
| `docker compose --profile quality config --services` | Lists `sonarqube` |
| `bash -n .github/scripts/prepare-sonarqube.sh` | Pass |

## Result / example

On a push, Actions runs the four existing jobs and, once Backend is green, a SonarQube job. A blocker issue or a vulnerability fails that job. Coverage is uploaded with the analysis and is not a pass/fail percentage.

## Out of scope

- Trivy, Docker Hub, Netlify
- Scanning FastAPI, the capture sensor, or the frontend
- An 80% coverage threshold
- Starting SonarQube from `start-pfe.sh`

## Verify commands

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
cd backend && ./mvnw -B test
test -s target/site/jacoco/jacoco.xml
cd ..
docker compose config --services
docker compose --profile quality config --services
sudo sysctl -w vm.max_map_count=262144
docker compose --profile quality up -d sonarqube
```

## Rapport talking points

- JaCoCo measures coverage. SonarQube decides if the build is allowed to stay green.
- The gate fails on blocker issues and vulnerabilities. It does not demand 80% coverage on the first scan.
- CI starts a fresh SonarQube container so the gate does not depend on a laptop being online. The Compose profile on port 9000 is the dashboard to open in front of the jury.
