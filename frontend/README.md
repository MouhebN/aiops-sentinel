# AIOps Sentinel Frontend

React + Vite dashboard for the AIOps Sentinel supervision platform.

## Default: Docker / Nginx

`./scripts/start-pfe.sh` starts the production UI as a Compose service. Open http://localhost:3000.

The browser calls same-origin `/api/*`. Nginx proxies `/api/analyze-incident` to FastAPI and all other `/api/` paths to Spring Boot.

## Local development

```bash
npm install
npm run dev
```

Vite serves on http://localhost:3000 and talks directly to:

```bash
VITE_API_BASE_URL=http://localhost:8080
VITE_AI_SERVICE_URL=http://localhost:8001
```

If Compose already bound :3000, stop that container first: `docker compose stop frontend`.
