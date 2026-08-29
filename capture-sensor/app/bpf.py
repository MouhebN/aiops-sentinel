"""Safe BPF construction. Never interpolates unvalidated strings into a shell."""

from __future__ import annotations

import ipaddress
import re

INTERFACE_RE = re.compile(r"^[A-Za-z][A-Za-z0-9._-]{0,15}$")


def validate_interface(name: str, allowed: set[str]) -> str:
    if not name or not INTERFACE_RE.fullmatch(name):
        raise ValueError("interface is not allowed")
    if name not in allowed:
        raise ValueError("interface is not allowed")
    return name


def validate_ip(value: str | None) -> str | None:
    if value is None:
        return None
    trimmed = value.strip()
    if not trimmed:
        return None
    try:
        return str(ipaddress.ip_address(trimmed))
    except ValueError as exc:
        raise ValueError("IP address is invalid") from exc


def build_host_filter(source_ip: str | None, destination_ip: str | None) -> str:
    """Build a tcpdump host filter from already-validated IP addresses."""
    terms: list[str] = []
    if source_ip:
        terms.append(f"host {source_ip}")
    if destination_ip:
        terms.append(f"host {destination_ip}")
    if not terms:
        return "ip or ip6"
    return " and ".join(terms)


def filter_argv(source_ip: str | None, destination_ip: str | None) -> list[str]:
    """tcpdump expression as argv tokens (never a single shell string)."""
    expression = build_host_filter(source_ip, destination_ip)
    return expression.split()
