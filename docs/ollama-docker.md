# Ollama in Docker

Ollama runs as a first-class service on the AIOps Sentinel Docker network. FastAPI uses Ollama as the priority AI provider. The model is configured by environment variables, not by a manual command-line argument. The model is **not** pulled automatically on `docker compose up`.

FastAPI environment:

```text
AI_PROVIDER=ollama
OLLAMA_BASE_URL=http://ollama:11434
OLLAMA_MODEL=llama3.2:3b
OLLAMA_TIMEOUT_SECONDS=300
AI_RULES_FALLBACK_ENABLED=true
```

Rules fallback is used only when Ollama is unreachable, the model is missing, the request fails or times out, the response is invalid, or `AI_PROVIDER=rules`.

## Start Ollama

```bash
docker compose up -d ollama
```

## Pull model

```bash
docker compose exec ollama ollama pull llama3.2:3b
```

## Verify

```bash
docker compose exec ollama ollama list
```

## Restart FastAPI

```bash
docker compose up --build -d fastapi
```

## Test provider status

```bash
curl http://localhost:8001/api/ai/provider-status
```

Expected fields include `configuredProvider`, `configuredModel`, `ollamaReachable`, and `modelAvailable`.

## Start app services

```bash
docker compose up --build -d postgres ollama fastapi backend netflow-tools
```

## Fallback

If Ollama is down, the model is missing, or the request fails, FastAPI keeps serving incident analysis with the rule-based fallback. It does not crash. If Ollama is up and `llama3.2:3b` is available, FastAPI uses Ollama.
