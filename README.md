# AIOps Sentinel (PFE)

Bank-oriented AIOps platform: Spring Boot backend, React UI, FastAPI/Ollama analysis, and a Containerlab mini bank network.

## After a PC reboot

One command starts Compose (including the UI) and the bank lab (and **redeploys the lab** if containers are up but data-plane veths were lost on reboot):

```bash
./scripts/start-pfe.sh
```

```bash
./scripts/status-pfe.sh
./scripts/stop-pfe.sh
```

Open the UI at http://localhost:3000 (Nginx in Docker). Backend remains at http://localhost:8080.

Details: [docs/environment-lifecycle.md](docs/environment-lifecycle.md).  
What Syslog / NetFlow / PCAP are, how to add lab nodes, Windows/WSL notes, and the demo proof sequence: [docs/project-guide.md](docs/project-guide.md).

## First-time images

`start-pfe.sh` does **not** rebuild images.

```bash
docker compose build
./containerlab/scripts/build-lab-images.sh
```

## Frontend development

The Compose frontend is the default integrated UI. For hot reload while editing React:

```bash
cd frontend && npm run dev
```

Stop the Compose frontend first if port 3000 is already bound (`docker compose stop frontend`). Vite still talks to `http://localhost:8080` and `http://localhost:8001`.

## Layout

| Path | Role |
| --- | --- |
| `docker-compose.yml` | postgres, fastapi, ollama, backend, netflow-tools, **frontend** |
| `containerlab/bank-lab.clab.yml` | bank-lab topology |
| `docs/` | operational guides and [improvement history](docs/history/README.md) |
