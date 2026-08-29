# AIOps AI Service

This folder is reserved for the future FastAPI microservice that will explain incidents and suggest remediation actions.

Current role:

- expose a simple health endpoint
- expose the incident analysis API contract
- provide a rule-based analysis engine that works without an LLM
- use Ollama as the priority provider when configured
- keep AI logic separate from the Spring Boot supervision backend

Recommended architecture:

```text
simulators -> Spring Boot backend -> database -> dashboard
                                   -> FastAPI AI service later
```

The simulators stay in `../simulators` because they represent fake infrastructure devices, not AI logic.

Run later with:

```bash
cd fastapi
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8001
```

Default mode uses Ollama, with rules as fallback:

```bash
AI_PROVIDER=ollama OLLAMA_MODEL=llama3.2:3b OLLAMA_BASE_URL=http://localhost:11434 uvicorn app.main:app --reload --port 8001
```

Force rules only:

```bash
AI_PROVIDER=rules uvicorn app.main:app --reload --port 8001
```

When Ollama is ready locally, the same default env vars apply. The model is selected from `OLLAMA_MODEL`; it does not need to be passed on the command line beyond that environment variable.

In Docker, FastAPI uses `OLLAMA_BASE_URL=http://ollama:11434` and `OLLAMA_MODEL=llama3.2:3b`. Pull the model manually and see `docs/ollama-docker.md`. If Ollama is unavailable, analysis falls back to the rule engine.

Endpoints:

```text
GET  /health
GET  /api/ai/provider-status
POST /api/analyze-incident
```

Example request:

```json
{
  "eventType": "UPS_BATTERY_LOW",
  "severity": "CRITICAL",
  "message": "UPS battery below 15%",
  "details": "battery=12,runtime_minutes=4",
  "deviceType": "UPS",
  "deviceName": "UPS Main Rack"
}
```

Example response:

```json
{
  "summary": "UPS Main Rack reported UPS_BATTERY_LOW: UPS battery below 15%",
  "risk": "The UPS may not keep protected devices online during a power failure.",
  "suggested_actions": [
    "Check the UPS battery percentage and estimated runtime.",
    "Verify input power and electrical source status."
  ],
  "diagnostic_commands": [
    "ping <ups-ip>",
    "snmpwalk -v2c -c <community> <ups-ip> 1.3.6.1.2.1.33"
  ],
  "provider": "rules",
  "model": null
}
```
