# 2026-10-01 — Frontend image OpenSSL CVE-2026-31789

| Field | Value |
| --- | --- |
| Date | 2026-10-01 |
| Module | Frontend image / Trivy |
| Type | CI fix |
| Status | Done |

## Context

GitHub Actions run 36934556642 passed every job except Trivy. The failing step was “Fail on critical frontend image findings”.

## Problem

`aiops-frontend:ci` uses `nginx:1.27-alpine` (Alpine 3.21.3). That tag still contains OpenSSL 3.3.3-r0. Trivy reported CVE-2026-31789 as CRITICAL on both `libssl3` and `libcrypto3`, status `fixed`, fixed version `3.3.7-r0`. The React build and Nginx config were not the finding.

## Design

Keep Nginx 1.27. During the image build, upgrade only the two OpenSSL packages from the Alpine 3.21 repository, which already publishes `3.3.7-r2`.

## Implementation (files)

| File | Role |
| --- | --- |
| `frontend/Dockerfile` | `apk upgrade` of `libssl3` and `libcrypto3` in the Nginx stage |

## Tests

| Check | Result |
| --- | --- |
| `docker build -t aiops-frontend:ci ./frontend` | Image built |
| `libssl3` inside the image | 3.3.7-r2 |
| Trivy 0.70.0 image scan, severity CRITICAL, exit code 1 | Exit 0, no CRITICAL findings |

## Result / example

The same Nginx base is kept. The two libraries Trivy flagged are replaced with Alpine’s patched build before the image is tagged.

## Out of scope

- Changing the Nginx major version
- A Trivy web dashboard

## Verify commands

```bash
docker build -t aiops-frontend:ci ./frontend
docker run --rm --entrypoint apk aiops-frontend:ci info libssl3
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
  aquasec/trivy:0.70.0 image --severity CRITICAL --exit-code 1 aiops-frontend:ci
```

## Rapport talking points

- The frontend failure was OpenSSL inside the Nginx image, not the React application.
- Alpine had already published the fix. The official `nginx:1.27-alpine` tag had not picked it up yet.
- The Dockerfile upgrades `libssl3` and `libcrypto3` at build time so the scanned image contains 3.3.7-r2.
