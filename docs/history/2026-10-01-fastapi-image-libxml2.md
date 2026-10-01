# 2026-10-01 — FastAPI image libxml2 CVE-2026-6653

| Field | Value |
| --- | --- |
| Date | 2026-10-01 |
| Module | FastAPI image / Trivy |
| Type | CI fix |
| Status | Done |

## Context

GitHub Actions run 36910073100 passed SonarQube and the four test jobs. The Trivy job failed on the FastAPI image only.

## Problem

`aiops-fastapi:ci` is Debian 13 (`python:3.12-slim`). Trivy reported one CRITICAL finding:

| Library | CVE | Installed | Fixed version | Status |
| --- | --- | --- | --- | --- |
| libxml2 | CVE-2026-6653 | 2.12.7+dfsg+really2.9.14-2.1+deb13u3 | none | affected |

That package is a 2.9.14 revert pulled in by `tshark`. Debian trixie marks the issue `no-dsa` and has no updated package. `apt-get upgrade` on that base cannot clear it. Ubuntu 24.04 ships the patch as `libxml2` 2.9.14+dfsg-1.3ubuntu3.8 (USN-8456-1).

## Design

Keep `tshark` in the image. Change the base from Debian 13 to Ubuntu 24.04, which is the release that published the patch, and install Python 3.12 there. `apt-get upgrade` applies the security pocket before the lists are removed. Application dependencies stay in a virtualenv so Ubuntu's externally managed Python does not block `pip`.

## Implementation (files)

| File | Role |
| --- | --- |
| `fastapi/Dockerfile` | Ubuntu 24.04 base, Python 3.12 venv, tshark, security upgrades |

## Tests

| Check | Result |
| --- | --- |
| `docker build -t aiops-fastapi:ci ./fastapi` | Image built |
| `libxml2` inside the image | 2.9.14+dfsg-1.3ubuntu3.9 (newer than the Ubuntu fix 3.8) |
| `tshark -v` | Wireshark 4.2.2 |
| `import fastapi` | 0.142.2 |
| Trivy 0.70.0 image scan, severity CRITICAL, exit code 1 | Exit 0, no CRITICAL findings |

## Result / example

The CRITICAL finding was the Debian package with no fix, not a Python dependency. The image now carries Ubuntu's patched `libxml2`.

## Out of scope

- Frontend and backend image findings below CRITICAL
- Removing tshark
- A `.trivyignore` entry for this CVE

## Verify commands

```bash
docker build -t aiops-fastapi:ci ./fastapi
docker run --rm --entrypoint dpkg aiops-fastapi:ci -l libxml2
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
  aquasec/trivy:0.70.0 image --severity CRITICAL --exit-code 1 aiops-fastapi:ci
```

## Rapport talking points

- Trivy failed on `libxml2` inside the FastAPI image because `tshark` depends on it.
- Debian 13 has no fix. Ubuntu 24.04 does, so the image base moved there.
- The Python application is unchanged. Only the operating-system package that ships the vulnerable library changed.
