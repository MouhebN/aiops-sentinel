from __future__ import annotations

import json
import sqlite3
import threading
from datetime import datetime, timezone
from pathlib import Path

import app.config as config
from app.schemas import IncidentAnalysisResponse

_LOCKS_GUARD = threading.Lock()
_KEY_LOCKS: dict[str, threading.Lock] = {}
_STORES: dict[str, "AnalysisCacheStore"] = {}

CACHEABLE_FIELDS = (
    "summary",
    "priority",
    "impact",
    "risk",
    "probable_causes",
    "suggested_actions",
    "diagnostic_commands",
    "provider",
    "model",
    "requested_provider",
    "fallback_used",
    "fallback_reason_code",
    "fallback_reason",
)


class AnalysisCacheStore:
    def __init__(self, path: str) -> None:
        self.path = path
        Path(path).parent.mkdir(parents=True, exist_ok=True)
        self._connection = sqlite3.connect(path, check_same_thread=False, timeout=30)
        self._connection.execute(
            """
            CREATE TABLE IF NOT EXISTS ai_analysis_cache (
                cache_key TEXT PRIMARY KEY,
                context_fingerprint TEXT NOT NULL,
                provider TEXT NOT NULL,
                model TEXT,
                pipeline_version TEXT NOT NULL,
                incident_id INTEGER,
                report_json TEXT NOT NULL,
                created_at TEXT NOT NULL
            )
            """
        )
        self._connection.commit()

    def get(self, key: str) -> IncidentAnalysisResponse | None:
        row = self._connection.execute(
            "SELECT report_json FROM ai_analysis_cache WHERE cache_key = ?",
            (key,),
        ).fetchone()
        if row is None:
            return None
        payload = json.loads(row[0])
        return IncidentAnalysisResponse.model_validate(payload)

    def put(
        self,
        key: str,
        fingerprint: str,
        response: IncidentAnalysisResponse,
        incident_id: int | None,
    ) -> None:
        payload = response.model_dump(include=set(CACHEABLE_FIELDS))
        self._connection.execute(
            """
            INSERT OR REPLACE INTO ai_analysis_cache (
                cache_key, context_fingerprint, provider, model, pipeline_version,
                incident_id, report_json, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                key,
                fingerprint,
                response.provider,
                response.model,
                config.AI_ANALYSIS_VERSION,
                incident_id,
                json.dumps(payload, ensure_ascii=True),
                datetime.now(timezone.utc).isoformat(),
            ),
        )
        self._connection.commit()


def get_cache() -> AnalysisCacheStore:
    path = config.AI_CACHE_PATH
    with _LOCKS_GUARD:
        store = _STORES.get(path)
        if store is None:
            store = AnalysisCacheStore(path)
            _STORES[path] = store
        return store


def lock_for(key: str) -> threading.Lock:
    with _LOCKS_GUARD:
        lock = _KEY_LOCKS.get(key)
        if lock is None:
            lock = threading.Lock()
            _KEY_LOCKS[key] = lock
        return lock


def is_cacheable(response: IncidentAnalysisResponse) -> bool:
    return (
        response.provider == "ollama"
        and not response.fallback_used
        and bool(response.summary)
    )


def reset_cache_state() -> None:
    with _LOCKS_GUARD:
        for store in _STORES.values():
            try:
                store._connection.close()
            except sqlite3.Error:
                pass
        _STORES.clear()
        _KEY_LOCKS.clear()
