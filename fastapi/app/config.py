import logging
import os
from dataclasses import dataclass

LOGGER = logging.getLogger(__name__)


def _env(name: str, default: str = "") -> str:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip()


def _bool_env(name: str, default: str = "true") -> bool:
    return _env(name, default).lower() in {"1", "true", "yes", "on"}


def _int_env(name: str, default: int) -> int:
    raw = _env(name, str(default))
    try:
        return max(1, int(raw))
    except ValueError:
        return default


AI_PROVIDER = (_env("AI_PROVIDER", "ollama") or "ollama").lower()
OLLAMA_BASE_URL = (_env("OLLAMA_BASE_URL") or _env("OLLAMA_URL") or "http://localhost:11434").rstrip("/")
OLLAMA_MODEL = _env("OLLAMA_MODEL", "llama3.2:3b") or "llama3.2:3b"
OLLAMA_TIMEOUT_SECONDS = _int_env("OLLAMA_TIMEOUT_SECONDS", 300)
AI_RULES_FALLBACK_ENABLED = _bool_env("AI_RULES_FALLBACK_ENABLED", "true")
MAX_RELATED_EVENTS = _int_env("AI_MAX_EVENT_GROUPS", 10)
MAX_PROMPT_CHARS = _int_env("AI_MAX_PROMPT_CHARS", 12000)
AI_MAX_EVENT_GROUPS = MAX_RELATED_EVENTS
AI_MAX_PREVIOUS_SIMILAR_INCIDENTS = _int_env("AI_MAX_PREVIOUS_SIMILAR_INCIDENTS", 3)
AI_MAX_METRIC_SERIES = _int_env("AI_MAX_METRIC_SERIES", 10)
AI_MAX_PCAP_SUMMARIES = _int_env("AI_MAX_PCAP_SUMMARIES", 3)
AI_MAX_NETFLOW_GROUPS = _int_env("AI_MAX_NETFLOW_GROUPS", 5)
AI_MAX_MESSAGE_CHARS = _int_env("AI_MAX_MESSAGE_CHARS", 1000)
AI_MAX_PREVIOUS_REPORTS = _int_env("AI_MAX_PREVIOUS_REPORTS", 3)
AI_MAX_PCAP_TOP_N = _int_env("AI_MAX_PCAP_TOP_N", 4)
AI_MAX_PCAP_FINDINGS = _int_env("AI_MAX_PCAP_FINDINGS", 4)
AI_MAX_NETFLOW_PORTS = _int_env("AI_MAX_NETFLOW_PORTS", 15)
AI_ANALYSIS_VERSION = _env("AI_ANALYSIS_VERSION", "v1") or "v1"
AI_CACHE_ENABLED = _bool_env("AI_CACHE_ENABLED", "true")
AI_CACHE_PATH = _env("AI_CACHE_PATH", "data/ai_analysis_cache.sqlite") or "data/ai_analysis_cache.sqlite"


@dataclass(frozen=True)
class ContextLimits:
    max_event_groups: int = 10
    max_previous_similar_incidents: int = 3
    max_metric_series: int = 10
    max_pcap_summaries: int = 3
    max_netflow_groups: int = 5
    max_message_chars: int = 1000
    max_previous_reports: int = 3
    max_pcap_top_n: int = 4
    max_pcap_findings: int = 4
    max_netflow_ports: int = 15

    @classmethod
    def from_env(cls) -> "ContextLimits":
        return cls(
            max_event_groups=AI_MAX_EVENT_GROUPS,
            max_previous_similar_incidents=AI_MAX_PREVIOUS_SIMILAR_INCIDENTS,
            max_metric_series=AI_MAX_METRIC_SERIES,
            max_pcap_summaries=AI_MAX_PCAP_SUMMARIES,
            max_netflow_groups=AI_MAX_NETFLOW_GROUPS,
            max_message_chars=AI_MAX_MESSAGE_CHARS,
            max_previous_reports=AI_MAX_PREVIOUS_REPORTS,
            max_pcap_top_n=AI_MAX_PCAP_TOP_N,
            max_pcap_findings=AI_MAX_PCAP_FINDINGS,
            max_netflow_ports=AI_MAX_NETFLOW_PORTS,
        )


def should_try_ollama() -> bool:
    if AI_PROVIDER == "rules":
        return False
    if AI_PROVIDER == "auto":
        return bool(OLLAMA_BASE_URL and OLLAMA_MODEL)
    return True


def log_startup() -> None:
    LOGGER.info("AI provider configured: %s", AI_PROVIDER)
    LOGGER.info("Ollama base URL: %s", OLLAMA_BASE_URL)
    LOGGER.info("Ollama model: %s", OLLAMA_MODEL)
    LOGGER.info("Ollama timeout seconds: %s", OLLAMA_TIMEOUT_SECONDS)
    LOGGER.info("Rules fallback enabled: %s", str(AI_RULES_FALLBACK_ENABLED).lower())
    LOGGER.info(
        "AI context limits eventGroups=%s similarIncidents=%s metrics=%s pcap=%s netflow=%s messageChars=%s",
        AI_MAX_EVENT_GROUPS,
        AI_MAX_PREVIOUS_SIMILAR_INCIDENTS,
        AI_MAX_METRIC_SERIES,
        AI_MAX_PCAP_SUMMARIES,
        AI_MAX_NETFLOW_GROUPS,
        AI_MAX_MESSAGE_CHARS,
    )
    LOGGER.info(
        "AI analysis version=%s cacheEnabled=%s cachePath=%s",
        AI_ANALYSIS_VERSION,
        str(AI_CACHE_ENABLED).lower(),
        AI_CACHE_PATH,
    )
