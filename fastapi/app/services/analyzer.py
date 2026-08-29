from __future__ import annotations

import logging
import time

import app.config as config
from app.schemas import IncidentAnalysisRequest, IncidentAnalysisResponse
from app.services.analysis_cache import get_cache, is_cacheable, lock_for
from app.services.context_fingerprint import cache_key, context_fingerprint, short_fingerprint
from app.services.fallback_reasons import attach_direct_rules
from app.services.ollama_client import analyze_with_ollama
from app.services.rule_engine import analyze_with_rules

LOGGER = logging.getLogger(__name__)


def analyze(request: IncidentAnalysisRequest) -> IncidentAnalysisResponse:
    started = time.perf_counter()
    fingerprint = context_fingerprint(request)
    requested_provider = "ollama" if config.should_try_ollama() else "rules"
    model = config.OLLAMA_MODEL if requested_provider == "ollama" else None
    key = cache_key(fingerprint, requested_provider, model)
    incident_id = request.incident.id if request.incident is not None else None

    if requested_provider == "rules":
        LOGGER.info(
            "aiCacheHit=false incidentId=%s provider=rules reason=direct-rules contextFingerprint=%s",
            incident_id,
            short_fingerprint(fingerprint),
        )
        response = attach_direct_rules(analyze_with_rules(request))
        return attach_cache_metadata(response, False, elapsed_ms(started), fingerprint)

    if not config.AI_CACHE_ENABLED:
        LOGGER.info(
            "aiCacheHit=false incidentId=%s provider=ollama model=%s contextFingerprint=%s reason=disabled",
            incident_id,
            model,
            short_fingerprint(fingerprint),
        )
        response = analyze_with_ollama(request)
        return attach_cache_metadata(response, False, elapsed_ms(started), fingerprint)

    with lock_for(key):
        cached = get_cache().get(key)
        if cached is not None:
            duration = elapsed_ms(started)
            LOGGER.info(
                "aiCacheHit=true incidentId=%s provider=ollama model=%s contextFingerprint=%s analysisDurationMs=%s",
                incident_id,
                model,
                short_fingerprint(fingerprint),
                duration,
            )
            return attach_cache_metadata(cached, True, duration, fingerprint)

        LOGGER.info(
            "aiCacheHit=false incidentId=%s provider=ollama model=%s contextFingerprint=%s",
            incident_id,
            model,
            short_fingerprint(fingerprint),
        )
        response = analyze_with_ollama(request)
        if is_cacheable(response):
            get_cache().put(key, fingerprint, response, incident_id)
            LOGGER.info(
                "aiCacheStored=true incidentId=%s provider=ollama model=%s contextFingerprint=%s analysisDurationMs=%s",
                incident_id,
                model,
                short_fingerprint(fingerprint),
                elapsed_ms(started),
            )
        else:
            LOGGER.info(
                "aiCacheStored=false incidentId=%s provider=%s fallbackUsed=%s",
                incident_id,
                response.provider,
                response.fallback_used,
            )
        return attach_cache_metadata(response, False, elapsed_ms(started), fingerprint)


def attach_cache_metadata(
    response: IncidentAnalysisResponse,
    cache_hit: bool,
    duration_ms: int,
    fingerprint: str,
) -> IncidentAnalysisResponse:
    return response.model_copy(
        update={
            "cache_hit": cache_hit,
            "analysis_duration_ms": duration_ms,
            "context_fingerprint": fingerprint,
        }
    )


def elapsed_ms(started: float) -> int:
    return max(0, int((time.perf_counter() - started) * 1000))
