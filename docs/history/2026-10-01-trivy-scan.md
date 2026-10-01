# 2026-10-01 — Trivy dependency and image scan

| Field | Value |
| --- | --- |
| Date | 2026-10-01 |
| Module | GitHub Actions / Trivy |
| Type | CI security scan |
| Status | Done |

## Context

The CI workflow already runs unit tests, the frontend build, and a SonarQube quality gate on the Java backend. SonarQube reads source. It does not check known CVEs in libraries or packages inside the Docker images.

## Problem

A green test job and a green quality gate can still ship a dependency or a base image with a critical vulnerability, or a committed secret. The pipeline needs a supply-chain scan that fails the workflow on that class of finding.

## Design

One parallel job, **Trivy**, uses `aquasecurity/trivy-action` at `v0.36.0` (Trivy 0.70.0). The action installs Trivy on the runner and downloads the vulnerability database. Nothing is left running after the job.

Each target is printed as a table for `LOW` through `CRITICAL` with exit code 0, then scanned again at `CRITICAL` with exit code 1. HIGH and below stay in the log and do not fail the run.

- Filesystem: scanners `vuln` and `secret` on the repository root.
- Images: `docker build` of `backend/Dockerfile`, `fastapi/Dockerfile`, and `frontend/Dockerfile`, then an image scan of each. Containerlab images are not built.

A local secret scan at `CRITICAL` reported no findings. The lab passwords in Compose and the README were not classified as critical, so no `.trivyignore` was added. A Containerlab TLS key under `containerlab/clab-bank-lab/` is `HIGH` and is gitignored, so a clean checkout does not contain it.

## Implementation (files)

| File | Role |
| --- | --- |
| `.github/workflows/ci.yml` | Trivy job in parallel with the other jobs |
| `README.md` | CI bullet for the scan |

## Tests

| Check | Result |
| --- | --- |
| Workflow YAML parse | Pass |
| `trivy fs --scanners secret --severity CRITICAL` on the working tree | Exit 0, no critical secrets |
| Same scan at `HIGH,CRITICAL` | One HIGH private key in gitignored `containerlab/clab-bank-lab/.tls/ca/ca.key` |

The vulnerability database download was too slow on this network to finish a local CVE scan. The image and dependency CVE results appear on the first GitHub Actions run.

## Result / example

A push starts Trivy next to the test jobs. The Actions log shows a table per target. A critical CVE or a critical secret fails that job. Publishing images is still a later step.

## Out of scope

- Docker Hub push
- Netlify
- Ollama, Postgres, and Containerlab image scans
- Failing the job on HIGH findings

## Verify commands

```bash
docker run --rm -v "$PWD":/work -w /work aquasec/trivy:0.70.0 fs \
  --scanners secret --severity CRITICAL --skip-db-update --exit-code 1 .
```

Push the workflow and open the Trivy job in the Actions tab for the dependency and image tables.

## Rapport talking points

- Trivy is a scanner that runs inside the job. It is not a second server.
- SonarQube checks the Java source. Trivy checks libraries, secrets, and the three application images.
- Only a CRITICAL finding turns the job red. The full table stays in the log for the rapport.
