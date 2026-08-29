from __future__ import annotations

import logging
import re
from dataclasses import dataclass, field

from pydantic import BaseModel, ValidationError

from app.schemas import IncidentAnalysisRequest
from app.services.evidence_index import EvidenceIndex, build_evidence_index, extract_ips
from app.services.fallback_reasons import (
    OLLAMA_INVALID_COMMANDS,
    OLLAMA_INVALID_JSON,
    OLLAMA_INVALID_PRIORITY,
    OLLAMA_LOW_QUALITY,
    OLLAMA_SCHEMA_VALIDATION_FAILED,
    OLLAMA_UNGROUNDED_IP,
    OLLAMA_UNSUPPORTED_CLAIMS,
)

LOGGER = logging.getLogger(__name__)

ALLOWED_PRIORITIES = {"P1", "P2", "P3", "P4"}
MAX_CAUSES = 5
MAX_ACTIONS = 7
MAX_COMMANDS = 6
MAX_SUMMARY_CHARS = 800
MAX_TEXT_CHARS = 600
MAX_LIST_ITEM_CHARS = 400

COMMAND_IPS_ALLOWED = {"127.0.0.1", "0.0.0.0", "::1", "8.8.8.8", "1.1.1.1"}
ALLOWED_TOOLS = {
    "tcpdump",
    "tshark",
    "ss",
    "ip",
    "ping",
    "curl",
    "grep",
    "egrep",
    "fgrep",
    "journalctl",
    "traceroute",
    "tracepath",
    "dig",
    "nslookup",
    "host",
    "systemctl",
    "cat",
    "less",
    "more",
    "head",
    "tail",
    "awk",
    "sed",
    "ps",
    "top",
    "htop",
    "netstat",
    "lsof",
    "nfdump",
    "openssl",
    "whois",
}
ADVISORY_PREFIXES = ("check ", "review ", "inspect ", "verify ", "consult ", "read ")
DESTRUCTIVE_PATTERNS = (
    re.compile(r"\brm\s+-[a-zA-Z]*f\b", re.I),
    re.compile(r"\bshutdown\b", re.I),
    re.compile(r"\breboot\b", re.I),
    re.compile(r"\bhalt\b", re.I),
    re.compile(r"\bpoweroff\b", re.I),
    re.compile(r"\biptables\s+-F\b", re.I),
    re.compile(r"\bnft\s+flush\b", re.I),
    re.compile(r"\bmkfs\b", re.I),
    re.compile(r"\bdd\s+if=", re.I),
    re.compile(r"\bsystemctl\s+(stop|restart|disable|mask)\b", re.I),
    re.compile(r"\buserdel\b", re.I),
    re.compile(r"\bkillall\b", re.I),
)
FILLER_SUMMARIES = {
    "investigate the issue",
    "investigate further",
    "monitor the system",
    "check the system",
    "n/a",
    "none",
    "todo",
}

CLAIM_RULES = (
    (
        "CLAIM_COMPROMISE",
        (
            r"successful compromise",
            r"compromised host",
            r"system (?:was |is )?compromised",
            r"host was compromised",
            r"service was breached",
            r"breached the firewall",
            r"attacker breached",
        ),
        "has_compromise",
    ),
    (
        "CLAIM_EXFILTRATION",
        (
            r"data exfiltration occurred",
            r"exfiltrated (?:sensitive |banking )?data",
            r"host exfiltrated",
            r"credentials stolen",
        ),
        "has_exfiltration",
    ),
    (
        "CLAIM_SUCCESSFUL_ACCESS",
        (
            r"unauthorized access occurred",
            r"attacker gained access",
            r"successful authentication",
            r"successful login",
            r"access was gained",
            r"successfully authenticated",
        ),
        "has_successful_access",
    ),
    (
        "CLAIM_LATERAL_MOVEMENT",
        (r"lateral movement occurred", r"moved laterally"),
        "has_compromise",
    ),
    (
        "CLAIM_MALWARE",
        (r"malware executed", r"malware ran", r"ransomware deployed"),
        "has_compromise",
    ),
    (
        "CLAIM_EXPLOITATION",
        (r"successful exploitation", r"successfully exploited", r"exploit(?:ation)? succeeded"),
        "has_compromise",
    ),
)

SAFE_CLAIM_DOWNGRADES = (
    (re.compile(r"\bunauthorized access occurred\b", re.I), "risk of unauthorized access"),
    (re.compile(r"\baccess was gained\b", re.I), "access may have been attempted"),
    (re.compile(r"\bthe host was compromised\b", re.I), "the host may be at risk of compromise"),
    (re.compile(r"\bsystem was compromised\b", re.I), "system may be at risk of compromise"),
    (re.compile(r"\bdata exfiltration occurred\b", re.I), "data exfiltration is not established by current evidence"),
)

NEGATION_HINTS = (
    "no evidence",
    "not established",
    "does not indicate",
    "did not",
    "without evidence",
    "no sign",
    "not a successful",
    "no successful",
    "unproven",
)


class ModelOutput(BaseModel):
    summary: str
    priority: str
    impact: str
    risk: str
    probable_causes: list[str]
    suggested_actions: list[str]
    diagnostic_commands: list[str]


@dataclass
class ValidationResult:
    valid: bool
    repaired: bool = False
    payload: dict | None = None
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    removed_commands: list[str] = field(default_factory=list)
    unsupported_claims: list[str] = field(default_factory=list)
    reason_code: str | None = None


def validate_model_output(
    raw: dict,
    request: IncidentAnalysisRequest,
    index: EvidenceIndex | None = None,
) -> ValidationResult:
    index = index or build_evidence_index(request)
    first = _run_validation(raw, request, index, apply_repairs=True)
    if not first.valid or first.payload is None:
        return first
    second = _run_validation(first.payload, request, index, apply_repairs=False)
    second.repaired = first.repaired
    second.warnings = list(dict.fromkeys(first.warnings + second.warnings))
    second.removed_commands = list(dict.fromkeys(first.removed_commands + second.removed_commands))
    second.unsupported_claims = list(dict.fromkeys(first.unsupported_claims + second.unsupported_claims))
    if second.valid:
        second.payload = second.payload or first.payload
    return second


def _run_validation(
    raw: dict,
    request: IncidentAnalysisRequest,
    index: EvidenceIndex,
    apply_repairs: bool,
) -> ValidationResult:
    result = ValidationResult(valid=True, payload={})
    try:
        parsed = ModelOutput.model_validate(raw)
    except ValidationError as exception:
        result.valid = False
        result.reason_code = OLLAMA_SCHEMA_VALIDATION_FAILED
        result.errors.append(str(exception))
        return result

    working = {
        "summary": parsed.summary,
        "priority": parsed.priority,
        "impact": parsed.impact,
        "risk": parsed.risk,
        "probable_causes": list(parsed.probable_causes),
        "suggested_actions": list(parsed.suggested_actions),
        "diagnostic_commands": list(parsed.diagnostic_commands),
    }

    if not _normalize_schema(working, result, apply_repairs):
        return result
    if not _normalize_priority(working, result, apply_repairs):
        return result
    _trim_lengths(working, result, apply_repairs)
    _downgrade_or_reject_claims(working, index, result, apply_repairs)
    if not result.valid:
        return result
    _filter_commands(working, index, result, apply_repairs)
    if not result.valid:
        return result
    _reject_ungrounded_ips(working, index, result)
    if not result.valid:
        return result
    _reject_low_quality(working, request, result)
    if not result.valid:
        return result
    result.payload = working
    return result


def _normalize_schema(working: dict, result: ValidationResult, apply_repairs: bool) -> bool:
    for field in ("summary", "impact", "risk", "priority"):
        value = working[field]
        if not isinstance(value, str):
            result.valid = False
            result.reason_code = OLLAMA_SCHEMA_VALIDATION_FAILED
            result.errors.append(f"{field} must be a string")
            return False
        cleaned = value.strip()
        if cleaned != value:
            result.repaired = True
        working[field] = cleaned
        if not cleaned:
            result.valid = False
            result.reason_code = OLLAMA_SCHEMA_VALIDATION_FAILED
            result.errors.append(f"{field} is blank")
            return False

    for field, minimum, maximum in (
        ("probable_causes", 1, MAX_CAUSES),
        ("suggested_actions", 1, MAX_ACTIONS),
        ("diagnostic_commands", 0, MAX_COMMANDS),
    ):
        raw_items = working[field]
        if not isinstance(raw_items, list):
            result.valid = False
            result.reason_code = OLLAMA_SCHEMA_VALIDATION_FAILED
            result.errors.append(f"{field} must be a list")
            return False
        cleaned = []
        seen: set[str] = set()
        for item in raw_items:
            text = " ".join(str(item).split()).strip()
            if not text:
                result.repaired = True
                result.warnings.append(f"Removed blank {field} entry")
                continue
            key = text.lower()
            if key in seen:
                result.repaired = True
                result.warnings.append(f"Removed duplicate {field} entry")
                continue
            seen.add(key)
            cleaned.append(text)
        if len(cleaned) > maximum:
            if not apply_repairs:
                result.valid = False
                result.reason_code = OLLAMA_SCHEMA_VALIDATION_FAILED
                result.errors.append(f"{field} exceeds {maximum} entries")
                return False
            cleaned = cleaned[:maximum]
            result.repaired = True
            result.warnings.append(f"Trimmed {field} to {maximum} entries")
        if len(cleaned) < minimum:
            result.valid = False
            result.reason_code = OLLAMA_SCHEMA_VALIDATION_FAILED
            result.errors.append(f"{field} needs at least {minimum} entries")
            return False
        working[field] = cleaned
    return True


def _normalize_priority(working: dict, result: ValidationResult, apply_repairs: bool) -> bool:
    original = working["priority"]
    normalized = original.strip().upper()
    if normalized != original:
        if not apply_repairs:
            result.valid = False
            result.reason_code = OLLAMA_INVALID_PRIORITY
            result.errors.append(f"Unnormalized priority {original}")
            return False
        result.repaired = True
        result.warnings.append(f"Normalized priority {original} -> {normalized}")
    if normalized not in ALLOWED_PRIORITIES:
        result.valid = False
        result.reason_code = OLLAMA_INVALID_PRIORITY
        result.errors.append(f"Invalid priority {original}")
        return False
    working["priority"] = normalized
    return True


def _trim_lengths(working: dict, result: ValidationResult, apply_repairs: bool) -> None:
    limits = {
        "summary": MAX_SUMMARY_CHARS,
        "impact": MAX_TEXT_CHARS,
        "risk": MAX_TEXT_CHARS,
    }
    for field, limit in limits.items():
        if len(working[field]) > limit:
            if apply_repairs:
                working[field] = working[field][:limit].rstrip() + " [truncated]"
                result.repaired = True
                result.warnings.append(f"Truncated {field}")
            else:
                result.valid = False
                result.reason_code = OLLAMA_SCHEMA_VALIDATION_FAILED
                result.errors.append(f"{field} exceeds {limit} characters")
    for field in ("probable_causes", "suggested_actions", "diagnostic_commands"):
        trimmed = []
        for item in working[field]:
            if len(item) > MAX_LIST_ITEM_CHARS:
                if apply_repairs:
                    item = item[:MAX_LIST_ITEM_CHARS].rstrip() + " [truncated]"
                    result.repaired = True
                else:
                    result.valid = False
                    result.reason_code = OLLAMA_SCHEMA_VALIDATION_FAILED
                    result.errors.append(f"{field} item exceeds {MAX_LIST_ITEM_CHARS} characters")
                    return
            trimmed.append(item)
        working[field] = trimmed


def _downgrade_or_reject_claims(
    working: dict,
    index: EvidenceIndex,
    result: ValidationResult,
    apply_repairs: bool,
) -> None:
    working["summary"] = _scan_text(working["summary"], index, result, apply_repairs)
    working["impact"] = _scan_text(working["impact"], index, result, apply_repairs)
    working["risk"] = _scan_text(working["risk"], index, result, apply_repairs)
    working["probable_causes"] = [
        _scan_text(item, index, result, apply_repairs) for item in working["probable_causes"]
    ]
    working["suggested_actions"] = [
        _scan_text(item, index, result, apply_repairs) for item in working["suggested_actions"]
    ]


def _scan_text(text: str, index: EvidenceIndex, result: ValidationResult, apply_repairs: bool) -> str:
    updated = text
    if apply_repairs:
        for pattern, replacement in SAFE_CLAIM_DOWNGRADES:
            if pattern.search(updated) and not _is_negated(updated, pattern.search(updated)):
                updated = pattern.sub(replacement, updated)
                result.repaired = True
                result.warnings.append("Unsupported successful-access wording downgraded")
    for claim, patterns, evidence_attr in CLAIM_RULES:
        if getattr(index, evidence_attr):
            continue
        for raw in patterns:
            match = re.search(raw, updated, flags=re.I)
            if not match or _is_negated(updated, match):
                continue
            result.unsupported_claims.append(claim)
            result.valid = False
            result.reason_code = OLLAMA_UNSUPPORTED_CLAIMS
            result.errors.append(f"{claim} is not supported by incident evidence")
            return updated
    return updated


def _is_negated(text: str, match: re.Match | None) -> bool:
    if match is None:
        return False
    prefix = text[max(0, match.start() - 48) : match.start()].lower()
    return any(hint in prefix for hint in NEGATION_HINTS)


def _filter_commands(
    working: dict,
    index: EvidenceIndex,
    result: ValidationResult,
    apply_repairs: bool,
) -> None:
    kept: list[str] = []
    for command in working["diagnostic_commands"]:
        reason = _command_issue(command, index)
        if reason is None:
            kept.append(command)
            continue
        result.removed_commands.append(command)
        result.warnings.append(reason)
        result.repaired = True
        if not apply_repairs:
            result.valid = False
            result.reason_code = OLLAMA_INVALID_COMMANDS
            result.errors.append(reason)
            return
    working["diagnostic_commands"] = kept[:MAX_COMMANDS]


def _command_issue(command: str, index: EvidenceIndex) -> str | None:
    lowered = command.strip()
    if any(pattern.search(lowered) for pattern in DESTRUCTIVE_PATTERNS):
        return "Rejected destructive command"
    head = _command_head(lowered)
    if head == "show" and not index.vendor_known:
        return "Rejected vendor-specific CLI without known vendor"
    if head == "systemctl" and re.search(r"\bsystemctl\s+(stop|restart|disable|mask)\b", lowered, re.I):
        return "Rejected destructive command"
    if head in ALLOWED_TOOLS:
        return None
    if lowered.lower().startswith(ADVISORY_PREFIXES):
        return None
    if head == "show" and index.vendor_known:
        return None
    return "Rejected unknown or unsupported diagnostic command"


def _command_head(command: str) -> str:
    parts = command.split()
    if parts and parts[0].lower() == "sudo":
        parts = parts[1:]
    return parts[0].lower() if parts else ""


def _reject_ungrounded_ips(working: dict, index: EvidenceIndex, result: ValidationResult) -> None:
    narrative = " ".join(
        [working["summary"], working["impact"], working["risk"], *working["probable_causes"], *working["suggested_actions"]]
    )
    for ip in extract_ips(narrative):
        if ip not in index.ips:
            result.valid = False
            result.reason_code = OLLAMA_UNGROUNDED_IP
            result.errors.append(f"Unknown IP {ip} is not present in incident evidence")
            return
    for command in working["diagnostic_commands"]:
        for ip in extract_ips(command):
            if ip in index.ips or ip in COMMAND_IPS_ALLOWED:
                continue
            result.warnings.append(f"Diagnostic command uses IP {ip} not present in the incident")


def _reject_low_quality(working: dict, request: IncidentAnalysisRequest, result: ValidationResult) -> None:
    summary = working["summary"].strip().lower().rstrip(".")
    if summary in FILLER_SUMMARIES:
        result.valid = False
        result.reason_code = OLLAMA_LOW_QUALITY
        result.errors.append("Summary is generic filler")
        return
    if summary == (request.message or "").strip().lower().rstrip("."):
        result.valid = False
        result.reason_code = OLLAMA_LOW_QUALITY
        result.errors.append("Summary copies the input without analysis")


def log_validation_result(result: ValidationResult, model: str) -> None:
    if result.valid:
        LOGGER.info(
            "aiResponseValid=true aiResponseRepaired=%s validationWarnings=%s unsupportedClaims=%s "
            "invalidCommands=%s provider=ollama model=%s",
            str(result.repaired).lower(),
            len(result.warnings),
            len(result.unsupported_claims),
            len(result.removed_commands),
            model,
        )
        return
    LOGGER.warning(
        "aiResponseValid=false reason=%s repaired=%s validationWarnings=%s unsupportedClaims=%s invalidCommands=%s model=%s",
        result.reason_code or "unknown",
        str(result.repaired).lower(),
        len(result.warnings),
        len(result.unsupported_claims),
        len(result.removed_commands),
        model,
    )
