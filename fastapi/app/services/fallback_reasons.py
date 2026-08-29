from __future__ import annotations

import json
import logging
from urllib.error import HTTPError, URLError

from app.config import OLLAMA_MODEL, OLLAMA_TIMEOUT_SECONDS
from app.schemas import IncidentAnalysisResponse

LOGGER = logging.getLogger(__name__)

OLLAMA_UNREACHABLE = "OLLAMA_UNREACHABLE"
OLLAMA_MODEL_UNAVAILABLE = "OLLAMA_MODEL_UNAVAILABLE"
OLLAMA_TIMEOUT = "OLLAMA_TIMEOUT"
OLLAMA_HTTP_ERROR = "OLLAMA_HTTP_ERROR"
OLLAMA_INVALID_JSON = "OLLAMA_INVALID_JSON"
OLLAMA_SCHEMA_VALIDATION_FAILED = "OLLAMA_SCHEMA_VALIDATION_FAILED"
OLLAMA_INVALID_PRIORITY = "OLLAMA_INVALID_PRIORITY"
OLLAMA_UNSUPPORTED_CLAIMS = "OLLAMA_UNSUPPORTED_CLAIMS"
OLLAMA_RESPONSE_VALIDATION_FAILED = "OLLAMA_RESPONSE_VALIDATION_FAILED"
OLLAMA_EMPTY_RESPONSE = "OLLAMA_EMPTY_RESPONSE"
OLLAMA_INTERNAL_ERROR = "OLLAMA_INTERNAL_ERROR"
OLLAMA_INVALID_COMMANDS = "OLLAMA_INVALID_COMMANDS"
OLLAMA_UNGROUNDED_IP = "OLLAMA_UNGROUNDED_IP"
OLLAMA_LOW_QUALITY = "OLLAMA_LOW_QUALITY"

PUBLIC_REASON_CODES = {
    OLLAMA_UNREACHABLE,
    OLLAMA_MODEL_UNAVAILABLE,
    OLLAMA_TIMEOUT,
    OLLAMA_HTTP_ERROR,
    OLLAMA_INVALID_JSON,
    OLLAMA_SCHEMA_VALIDATION_FAILED,
    OLLAMA_INVALID_PRIORITY,
    OLLAMA_UNSUPPORTED_CLAIMS,
    OLLAMA_RESPONSE_VALIDATION_FAILED,
    OLLAMA_EMPTY_RESPONSE,
    OLLAMA_INTERNAL_ERROR,
}

EMPLOYEE_MESSAGES = {
    OLLAMA_UNREACHABLE: "The local AI service could not be reached.",
    OLLAMA_MODEL_UNAVAILABLE: "The configured local AI model is not available.",
    OLLAMA_TIMEOUT: (
        f"The local AI model did not respond within the configured timeout ({OLLAMA_TIMEOUT_SECONDS} seconds)."
    ),
    OLLAMA_HTTP_ERROR: "The local AI service returned an unexpected HTTP error.",
    OLLAMA_INVALID_JSON: "The AI model returned a response that could not be interpreted safely.",
    OLLAMA_SCHEMA_VALIDATION_FAILED: "The AI response did not match the expected diagnostic report structure.",
    OLLAMA_INVALID_PRIORITY: "The AI response used a priority value that is not allowed.",
    OLLAMA_UNSUPPORTED_CLAIMS: (
        "The AI response contained conclusions that were not sufficiently supported by the incident evidence."
    ),
    OLLAMA_RESPONSE_VALIDATION_FAILED: "The AI response did not pass safety and quality checks.",
    OLLAMA_EMPTY_RESPONSE: "The AI model returned an empty response.",
    OLLAMA_INTERNAL_ERROR: "An unexpected error occurred while requesting the local AI model.",
}

_VALIDATOR_GENERIC_CODES = {
    OLLAMA_INVALID_COMMANDS,
    OLLAMA_UNGROUNDED_IP,
    OLLAMA_LOW_QUALITY,
}


def public_reason_code(code: str | None) -> str:
    if code in PUBLIC_REASON_CODES:
        return code
    if code in _VALIDATOR_GENERIC_CODES:
        return OLLAMA_RESPONSE_VALIDATION_FAILED
    return OLLAMA_RESPONSE_VALIDATION_FAILED if code else OLLAMA_INTERNAL_ERROR


def employee_message(code: str | None) -> str:
    public = public_reason_code(code)
    return EMPLOYEE_MESSAGES.get(public, EMPLOYEE_MESSAGES[OLLAMA_INTERNAL_ERROR])


def classify_transport_error(exception: BaseException, response_preview: str | None = None) -> str:
    preview = (response_preview or str(exception) or "").lower()
    if isinstance(exception, TimeoutError) or "timed out" in preview:
        return OLLAMA_TIMEOUT
    if isinstance(exception, HTTPError):
        body = preview
        if exception.code == 404 or ("model" in body and "not found" in body):
            return OLLAMA_MODEL_UNAVAILABLE
        return OLLAMA_HTTP_ERROR
    if isinstance(exception, URLError):
        reason = str(getattr(exception, "reason", exception)).lower()
        if "timed out" in reason:
            return OLLAMA_TIMEOUT
        return OLLAMA_UNREACHABLE
    if isinstance(exception, json.JSONDecodeError):
        return OLLAMA_INVALID_JSON
    if isinstance(exception, OSError):
        return OLLAMA_UNREACHABLE
    return OLLAMA_INTERNAL_ERROR


def attach_ollama_success(response: IncidentAnalysisResponse) -> IncidentAnalysisResponse:
    LOGGER.info(
        "requestedProvider=ollama actualProvider=ollama model=%s fallbackUsed=false",
        OLLAMA_MODEL,
    )
    return response.model_copy(
        update={
            "provider": "ollama",
            "requested_provider": "ollama",
            "model": OLLAMA_MODEL,
            "fallback_used": False,
            "fallback_reason_code": None,
            "fallback_reason": None,
        }
    )


def attach_rules_fallback(
    response: IncidentAnalysisResponse,
    code: str,
    technical: str | None = None,
) -> IncidentAnalysisResponse:
    public = public_reason_code(code)
    LOGGER.warning(
        "requestedProvider=ollama actualProvider=rules model=%s fallbackUsed=true fallbackReasonCode=%s technical=%s",
        OLLAMA_MODEL,
        public,
        technical or "<none>",
    )
    return response.model_copy(
        update={
            "provider": "rules",
            "requested_provider": "ollama",
            "model": OLLAMA_MODEL,
            "fallback_used": True,
            "fallback_reason_code": public,
            "fallback_reason": employee_message(public),
        }
    )


def attach_direct_rules(response: IncidentAnalysisResponse) -> IncidentAnalysisResponse:
    LOGGER.info("requestedProvider=rules actualProvider=rules fallbackUsed=false")
    return response.model_copy(
        update={
            "provider": "rules",
            "requested_provider": "rules",
            "model": None,
            "fallback_used": False,
            "fallback_reason_code": None,
            "fallback_reason": None,
        }
    )
