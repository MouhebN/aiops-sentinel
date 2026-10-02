# 2026-10-02 — Docker Hub publish

| Field | Value |
| --- | --- |
| Date | 2026-10-02 |
| Module | GitHub Actions / Docker Hub |
| Type | CI publish |
| Status | Done |

## Context

The CI workflow already runs unit tests, the frontend build, a SonarQube quality gate, and a Trivy scan. Those jobs run on GitHub-hosted runners. When the run ends, the images built for Trivy are gone.

## Problem

A green pipeline does not leave an artifact another machine can start. Rebuilding the backend, FastAPI, and frontend images on a client PC requires the full toolchain. The registry push has to wait until every check is green, and the Docker Hub account must stay out of git so it can be swapped later.

## Design

A **Publish** job needs Backend, FastAPI, Capture sensor, Frontend, SonarQube, and Trivy. It runs only when the event is `push` and the branch is `main`. Pull requests still test and scan, and they do not log in.

Each job has its own runner, so Publish rebuilds with the same `docker build` contexts Trivy used (`./backend`, `./fastapi`, `./frontend`). Login uses `docker/login-action` and two Actions secrets: `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN`. Each image is pushed twice: the full git SHA and `latest`.

Image names:

- `<username>/aiops-sentinel-backend`
- `<username>/aiops-sentinel-fastapi`
- `<username>/aiops-sentinel-frontend`

Containerlab, Ollama, Postgres, and the capture sensor image are not published. Compose and `start-pfe.sh` still build from source.

## Implementation (files)

| File | Role |
| --- | --- |
| `.github/workflows/ci.yml` | Publish job after the existing jobs |
| `README.md` | Secret names, image names, and the main-only rule |

## Tests

| Check | Result |
| --- | --- |
| Workflow YAML parse | Pass |

A live push needs both secrets on the GitHub repository. Until they exist, the Publish job on `main` fails at Docker Hub login. Pull requests skip the job.

## Result / example

A push to `main` that passes tests, SonarQube, and Trivy uploads three images. A later machine pulls `latest` or a specific SHA without compiling the project. Replacing the two secrets points the same workflow at another Docker Hub account.

## Out of scope

- Netlify
- Pulling those images from Compose or `start-pfe.sh`
- Publishing the capture sensor, Postgres, Ollama, or Containerlab

## Verify commands

```bash
python3 -c 'import yaml; yaml.safe_load(open(".github/workflows/ci.yml"))'
```

On the repository, add `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN`, push `main`, and open the Publish job. The log should show six `docker push` lines.

## Rapport talking points

- Docker Hub is the warehouse for the three application images. It is not the server that runs the app.
- Publish runs only after tests, SonarQube, and Trivy are green, and only on `main`.
- The Docker Hub username is a secret. Moving the project to another GitHub account means creating the same two secrets there.
