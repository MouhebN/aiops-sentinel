import json
import logging
import os
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from fastapi import HTTPException

from app.config import (
    AI_PROVIDER,
    AI_RULES_FALLBACK_ENABLED,
    OLLAMA_BASE_URL,
    OLLAMA_MODEL,
    OLLAMA_TIMEOUT_SECONDS,
)
from app.schemas import IncidentAnalysisRequest, IncidentAnalysisResponse
from app.services.fallback_reasons import (
    OLLAMA_EMPTY_RESPONSE,
    OLLAMA_INTERNAL_ERROR,
    OLLAMA_INVALID_JSON,
    OLLAMA_MODEL_UNAVAILABLE,
    OLLAMA_SCHEMA_VALIDATION_FAILED,
    attach_ollama_success,
    attach_rules_fallback,
    classify_transport_error,
    employee_message,
    public_reason_code,
)
from app.services.prompt_builder import build_prompt
from app.services.response_validator import log_validation_result, validate_model_output
from app.services.rule_engine import analyze_with_rules


LOGGER = logging.getLogger(__name__)

AI_DEBUG_PROMPT = os.getenv("AI_DEBUG_PROMPT", "false").strip().lower() in {"1", "true", "yes", "on"}


def analyze_with_ollama(request: IncidentAnalysisRequest) -> IncidentAnalysisResponse:
    try:
        return _analyze_with_ollama(request)
    except HTTPException:
        raise
    except Exception as exception:
        LOGGER.exception("Ollama internal error")
        return fallback_to_rules(request, OLLAMA_INTERNAL_ERROR, technical=repr(exception))


def _analyze_with_ollama(request: IncidentAnalysisRequest) -> IncidentAnalysisResponse:
    prompt_result = build_prompt(request)
    prompt = prompt_result["prompt"]
    if AI_DEBUG_PROMPT:
        LOGGER.info("Final Ollama prompt:\n%s", prompt)
    LOGGER.info(
        "Calling Ollama model=%s timeout=%ss relatedEventsRaw=%s eventGroupsSent=%s "
        "pcapSummariesRaw=%s pcapSummariesSent=%s netflowGroupsRaw=%s netflowGroupsSent=%s "
        "promptCharacters=%s contextCompressed=%s trimmed=%s",
        OLLAMA_MODEL,
        OLLAMA_TIMEOUT_SECONDS,
        prompt_result.get("related_events_raw"),
        prompt_result.get("event_groups_sent"),
        prompt_result.get("pcap_summaries_raw"),
        prompt_result.get("pcap_summaries_sent"),
        prompt_result.get("netflow_groups_raw"),
        prompt_result.get("netflow_groups_sent"),
        prompt_result.get("prompt_characters", len(prompt)),
        prompt_result.get("context_compressed"),
        prompt_result.get("trimmed"),
    )
    payload = {
        "model": OLLAMA_MODEL,
        "prompt": prompt,
        "stream": False,
        "format": "json",
        "options": {
            "temperature": 0.2,
            "num_predict": 750,
        },
    }

    http_request = Request(
        f"{OLLAMA_BASE_URL}/api/generate",
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    raw_response_text = None
    try:
        with urlopen(http_request, timeout=OLLAMA_TIMEOUT_SECONDS) as response:
            raw_response_text = response.read().decode("utf-8")
            raw_response = json.loads(raw_response_text)
    except (HTTPError, URLError, TimeoutError, OSError, json.JSONDecodeError) as exception:
        response_preview = preview_text(raw_response_text)
        if isinstance(exception, HTTPError):
            try:
                response_preview = preview_text(exception.read().decode("utf-8"))
            except Exception:
                pass
        code = classify_transport_error(exception, response_preview)
        return fallback_to_rules(
            request,
            code,
            technical=f"{type(exception).__name__}: {exception} | url={OLLAMA_BASE_URL} | model={OLLAMA_MODEL} | preview={response_preview}",
        )

    if isinstance(raw_response, dict) and raw_response.get("error") and not raw_response.get("response"):
        error_text = str(raw_response.get("error") or "")
        lowered = error_text.lower()
        code = (
            OLLAMA_MODEL_UNAVAILABLE
            if "not found" in lowered or "model" in lowered
            else OLLAMA_EMPTY_RESPONSE
        )
        return fallback_to_rules(request, code, technical=error_text)

    response_field = raw_response.get("response") if isinstance(raw_response, dict) else None
    if response_field is None or (isinstance(response_field, str) and not response_field.strip()):
        return fallback_to_rules(request, OLLAMA_EMPTY_RESPONSE, technical="empty response field")

    try:
        model_json = json.loads(response_field)
        LOGGER.info(
            "Ollama HTTP call succeeded. response_preview=%s",
            preview_text(raw_response_text) if AI_DEBUG_PROMPT else "<hidden>",
        )
    except (TypeError, json.JSONDecodeError) as exception:
        return fallback_to_rules(
            request,
            OLLAMA_INVALID_JSON,
            technical=f"{type(exception).__name__}: {exception} | preview={preview_text(raw_response_text)}",
        )

    if not isinstance(model_json, dict) or not model_json:
        return fallback_to_rules(
            request,
            OLLAMA_EMPTY_RESPONSE if not model_json else OLLAMA_SCHEMA_VALIDATION_FAILED,
            technical="response is not a JSON object",
        )

    validation = validate_model_output(model_json, request)
    log_validation_result(validation, OLLAMA_MODEL)
    if not validation.valid or validation.payload is None:
        if AI_DEBUG_PROMPT:
            LOGGER.info("Rejected Ollama payload preview=%s", preview_text(json.dumps(model_json)))
        return fallback_to_rules(
            request,
            validation.reason_code or OLLAMA_SCHEMA_VALIDATION_FAILED,
            technical="; ".join(validation.errors) or "invalid model output",
        )

    payload = validation.payload
    return attach_ollama_success(
        IncidentAnalysisResponse(
            summary=payload["summary"],
            priority=payload["priority"],
            impact=payload["impact"],
            risk=payload["risk"],
            probable_causes=payload["probable_causes"],
            suggested_actions=payload["suggested_actions"],
            diagnostic_commands=payload["diagnostic_commands"],
            provider="ollama",
            model=OLLAMA_MODEL,
        )
    )


def fallback_to_rules(request: IncidentAnalysisRequest, code: str, technical: str | None = None) -> IncidentAnalysisResponse:
    public = public_reason_code(code)
    if not AI_RULES_FALLBACK_ENABLED:
        LOGGER.error("Ollama request failed, rules fallback disabled. code=%s technical=%s", public, technical)
        raise HTTPException(
            status_code=503,
            detail=f"{public}: {employee_message(public)}",
        )
    LOGGER.warning("Ollama request failed, using rules fallback. code=%s technical=%s", public, technical)
    fallback = analyze_with_rules(request)
    return attach_rules_fallback(fallback, public, technical=technical)


def get_provider_status() -> dict:
    ollama_reachable = False
    available_models: list[str] = []
    message = None
    try:
        with urlopen(f"{OLLAMA_BASE_URL}/api/tags", timeout=min(10, OLLAMA_TIMEOUT_SECONDS)) as response:
            payload = json.loads(response.read().decode("utf-8"))
        ollama_reachable = True
        available_models = [
            str(item.get("name") or item.get("model") or "").strip()
            for item in payload.get("models", [])
            if item.get("name") or item.get("model")
        ]
    except (HTTPError, URLError, TimeoutError, OSError, json.JSONDecodeError) as exception:
        message = f"Ollama is unreachable at {OLLAMA_BASE_URL}: {type(exception).__name__}: {exception}"

    model_available = ollama_reachable and model_is_available(OLLAMA_MODEL, available_models)
    if ollama_reachable and not model_available:
        message = (
            f"Model {OLLAMA_MODEL} is not available. Pull it with: "
            f"docker compose exec ollama ollama pull {OLLAMA_MODEL}"
        )

    return {
        "configuredProvider": AI_PROVIDER,
        "ollamaBaseUrl": OLLAMA_BASE_URL,
        "configuredModel": OLLAMA_MODEL,
        "ollamaReachable": ollama_reachable,
        "modelAvailable": model_available,
        "availableModels": available_models,
        "rulesFallbackEnabled": AI_RULES_FALLBACK_ENABLED,
        "message": message,
    }


def model_is_available(configured_model: str, available_models: list[str]) -> bool:
    target = configured_model.strip().lower()
    for name in available_models:
        candidate = name.strip().lower()
        if candidate == target or candidate.startswith(target + "-") or candidate.startswith(target + "_"):
            return True
    return False


def preview_text(value: str | None, limit: int = 500) -> str:
    if not value:
        return "<none>"
    compact = value.replace("\n", "\\n")
    return compact[:limit]
