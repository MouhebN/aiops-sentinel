from app.schemas import IncidentAnalysisRequest, IncidentAnalysisResponse


DEFAULT_ACTIONS = [
    "Check the affected device status in the supervision dashboard.",
    "Review recent events from the same device and same location.",
    "Validate the incident manually before applying remediation.",
]

DEFAULT_COMMANDS = [
    "ping <device-ip>",
    "traceroute <device-ip>",
    "check device-specific logs",
]

DEFAULT_CAUSES = [
    "Temporary resource saturation or connectivity degradation.",
    "Recent configuration, load, or environmental change.",
    "Device or service failure that needs manual confirmation.",
]

SEVERITY_PRIORITY = {
    "CRITICAL": "P1",
    "WARNING": "P2",
    "INFO": "P3",
}


RULES = {
    "UPS_BATTERY_LOW": {
        "impact": "Protected infrastructure may lose backup power and shut down during an outage.",
        "causes": [
            "UPS battery is near end of life or not charging correctly.",
            "Input power is unstable or unavailable.",
            "Connected load is too high for the remaining runtime.",
        ],
        "risk": "The UPS may not keep protected devices online during a power failure. If the battery continues to drop, servers or network equipment can shut down unexpectedly.",
        "actions": [
            "Check the UPS battery percentage and estimated runtime.",
            "Verify input power and electrical source status.",
            "Reduce non-critical load connected to the UPS if runtime is too low.",
            "Plan battery replacement if the low level repeats frequently.",
        ],
        "commands": [
            "ping <ups-ip>",
            "snmpwalk -v2c -c <community> <ups-ip> 1.3.6.1.2.1.33",
            "check UPS web interface or management card",
        ],
    },
    "UPS_ON_BATTERY": {
        "impact": "The site is running on limited backup runtime instead of stable input power.",
        "causes": [
            "Main power outage or electrical panel issue.",
            "UPS input cable or circuit problem.",
            "Automatic transfer to battery after voltage instability.",
        ],
        "risk": "The UPS is no longer receiving normal input power. Runtime depends on battery level and connected load.",
        "actions": [
            "Verify whether there is a power outage or electrical issue.",
            "Check UPS runtime and connected equipment load.",
            "Notify operations if the site is running on backup power.",
        ],
        "commands": ["ping <ups-ip>", "check UPS input voltage", "check electrical panel status"],
    },
    "CPU_HIGH": {
        "impact": "Applications on the server may become slow, unstable, or unavailable.",
        "causes": [
            "One process is consuming abnormal CPU resources.",
            "Traffic or workload increased beyond current capacity.",
            "Recent deployment introduced a performance regression.",
        ],
        "risk": "High CPU usage can slow applications, cause request timeouts, and hide a process loop or overload condition.",
        "actions": [
            "Identify the top CPU-consuming process.",
            "Check whether the load is expected or abnormal.",
            "Review recent application logs and deployments.",
            "Scale or restart the affected service only after validation.",
        ],
        "commands": ["top", "htop", "ps aux --sort=-%cpu | head", "journalctl -u <service-name> -n 100"],
    },
    "DISK_FULL": {
        "impact": "Applications, databases, and logging pipelines may fail to write data.",
        "causes": [
            "Log files or temporary files grew unexpectedly.",
            "Database or backup files consumed available storage.",
            "Disk capacity is too small for the current workload.",
        ],
        "risk": "A full disk can stop logging, break databases, and prevent applications from writing required files.",
        "actions": [
            "Check disk usage by filesystem.",
            "Identify large log files or unexpected growth.",
            "Archive or rotate logs according to policy.",
            "Increase disk capacity if the growth is legitimate.",
        ],
        "commands": ["df -h", "du -xh /var/log | sort -h | tail", "journalctl --disk-usage"],
    },
    "SERVICE_DOWN": {
        "impact": "Users or dependent systems cannot use the affected application service.",
        "causes": [
            "Service crashed because of an application error.",
            "A dependency such as database, network, or configuration is unavailable.",
            "Recent deployment or restart failed.",
        ],
        "risk": "The monitored application service is unavailable, which can directly impact users or dependent systems.",
        "actions": [
            "Check service state and recent restart history.",
            "Inspect application logs around the incident time.",
            "Validate required ports and dependencies.",
            "Restart the service only after identifying the probable cause.",
        ],
        "commands": [
            "systemctl status <service-name>",
            "journalctl -u <service-name> -n 100",
            "ss -lntp | grep <port>",
        ],
    },
    "TRANSACTION_LATENCY_HIGH": {
        "impact": "ATM or banking transaction users may experience slow responses, timeouts, or repeated retries.",
        "causes": [
            "ATM transaction switch queue is saturated.",
            "Core banking dependency is slow or overloaded.",
            "Network latency between branch, ATM channel, and data center increased.",
        ],
        "risk": "High transaction latency can degrade customer-facing banking services and may create duplicate retries or failed operations if it continues.",
        "actions": [
            "Check transaction queue depth and response time trend.",
            "Correlate with core banking server CPU, database, and WAN events.",
            "Validate whether the issue affects one branch, one channel, or all ATM traffic.",
            "Escalate to banking application operations if latency keeps increasing.",
        ],
        "commands": [
            "curl -I https://<atm-switch-host>/health",
            "journalctl -u atm-transaction-switch -n 100",
            "mtr <core-banking-host>",
        ],
    },
    "CAMERA_OFFLINE": {
        "impact": "Operators lose visibility of the monitored physical area.",
        "causes": [
            "Camera lost power or PoE supply.",
            "Network switch port, VLAN, or cable issue.",
            "Camera firmware or hardware failure.",
        ],
        "risk": "Video visibility is lost for the monitored area, which can affect physical security supervision.",
        "actions": [
            "Check camera power and network connectivity.",
            "Verify switch port status and PoE if applicable.",
            "Confirm the camera is reachable from the monitoring network.",
        ],
        "commands": ["ping <camera-ip>", "arp -a | grep <camera-ip>", "check switch port status"],
    },
    "RTSP_STREAM_DOWN": {
        "impact": "Live video or recording may be unavailable even if the camera answers ping.",
        "causes": [
            "RTSP service is stopped or blocked.",
            "Stream URL or credentials are invalid.",
            "Network loss or bandwidth pressure affects video transport.",
        ],
        "risk": "The camera may still be reachable, but video streaming is unavailable for operators or recording systems.",
        "actions": [
            "Verify RTSP service availability.",
            "Check camera credentials and stream URL.",
            "Review bandwidth and packet loss on the camera network.",
        ],
        "commands": [
            "ping <camera-ip>",
            "ffprobe rtsp://<camera-ip>/<stream>",
            "nc -vz <camera-ip> 554",
        ],
    },
    "PORT_SCAN_DETECTED": {
        "impact": "An attacker or misconfigured scanner may be mapping exposed services.",
        "causes": [
            "External reconnaissance against the firewall or protected network.",
            "Internal vulnerability scanner running outside a planned window.",
            "Compromised host attempting lateral discovery.",
        ],
        "risk": "A host is probing multiple ports. This may indicate reconnaissance before an intrusion attempt.",
        "actions": [
            "Review source IP, targeted ports, and firewall rule action.",
            "Check whether the source IP is internal or external.",
            "Correlate with failed login attempts and blocked traffic.",
            "Escalate if repeated scans target sensitive services.",
        ],
        "commands": [
            "check firewall logs for <source-ip>",
            "whois <source-ip>",
            "grep <source-ip> firewall.log",
        ],
    },
    "SUSPICIOUS_IP_BLOCKED": {
        "impact": "Potentially malicious traffic was stopped, but repeated attempts may continue.",
        "causes": [
            "Known malicious source contacted a protected service.",
            "Firewall reputation or anomaly rule matched the source.",
            "A bot or automated scan targeted the environment.",
        ],
        "risk": "The firewall blocked suspicious traffic. The block reduced immediate risk, but repeated attempts may indicate an active threat.",
        "actions": [
            "Confirm the firewall rule that blocked the IP.",
            "Check if the same source targeted multiple services.",
            "Keep the IP blocked if confirmed malicious.",
            "Document the event for incident history.",
        ],
        "commands": ["check firewall rule hit count", "grep <source-ip> firewall.log", "whois <source-ip>"],
    },
    "FAILED_LOGIN_ATTEMPTS": {
        "impact": "Accounts may be locked, brute-forced, or targeted by credential attacks.",
        "causes": [
            "User or service account has stale credentials.",
            "Password guessing from one or more source IPs.",
            "Application integration is retrying with wrong secrets.",
        ],
        "risk": "Repeated authentication failures may indicate password guessing, misconfigured services, or compromised credentials.",
        "actions": [
            "Identify the target account and source IP.",
            "Check whether attempts are distributed or repeated from one source.",
            "Apply account lockout or IP blocking policy if needed.",
        ],
        "commands": ["grep 'Failed password' /var/log/auth.log", "lastb", "check IAM/audit logs"],
    },
    "PACKET_LOSS": {
        "impact": "Monitoring, applications, and video streams may show latency, errors, or interruptions.",
        "causes": [
            "Switch interface errors, bad cable, or duplex mismatch.",
            "Uplink congestion or routing instability.",
            "Wireless, WAN, or provider network degradation.",
        ],
        "risk": "Packet loss can degrade application performance, video streams, and monitoring reliability.",
        "actions": [
            "Check uplink utilization and interface errors.",
            "Verify cable, port, and switch health.",
            "Correlate with latency and device unreachable events.",
        ],
        "commands": ["ping -c 20 <gateway-ip>", "mtr <target-ip>", "show interface counters"],
    },
    "DEVICE_UNREACHABLE": {
        "impact": "The platform cannot supervise the component or confirm its operational state.",
        "causes": [
            "Device is powered off or disconnected.",
            "Routing, VLAN, or firewall path blocks monitoring traffic.",
            "Device operating system, agent, or network interface failed.",
        ],
        "risk": "The device cannot be contacted. It may be powered off, disconnected, blocked by network rules, or failing.",
        "actions": [
            "Check power and physical connectivity.",
            "Verify routing, VLAN, and firewall path.",
            "Check last known events from the same device.",
        ],
        "commands": ["ping <device-ip>", "traceroute <device-ip>", "arp -a | grep <device-ip>"],
    },
}


def priority_for(severity: str) -> str:
    return SEVERITY_PRIORITY.get(severity.upper(), "P3")


def default_impact(request: IncidentAnalysisRequest) -> str:
    return (
        f"{request.device_name} may affect supervision reliability or service availability "
        f"until the {request.device_type.lower()} condition is reviewed."
    )


def analyze_with_rules(request: IncidentAnalysisRequest) -> IncidentAnalysisResponse:
    event_type = request.event_type.upper()
    rule = RULES.get(event_type)
    summary = summary_for(request, event_type)
    context_actions = context_actions_for(request)

    if rule is None:
        return IncidentAnalysisResponse(
            summary=summary,
            priority=priority_for(request.severity),
            impact=default_impact(request),
            risk=risk_for(request, "The event indicates an abnormal condition that should be reviewed with device context and recent logs."),
            probable_causes=DEFAULT_CAUSES,
            suggested_actions=context_actions + DEFAULT_ACTIONS,
            diagnostic_commands=DEFAULT_COMMANDS,
            provider="rules",
        )

    return IncidentAnalysisResponse(
        summary=summary,
        priority=priority_for(request.severity),
        impact=rule.get("impact", default_impact(request)),
        risk=risk_for(request, rule["risk"]),
        probable_causes=rule.get("causes", DEFAULT_CAUSES),
        suggested_actions=context_actions + rule["actions"],
        diagnostic_commands=rule["commands"],
        provider="rules",
    )


def summary_for(request: IncidentAnalysisRequest, event_type: str) -> str:
    if request.incident is None:
        return f"{request.device_name} reported {event_type}: {request.message}"

    return (
        f"{request.incident.title}: {request.incident.event_count or 1} related event(s) "
        f"were correlated for {request.device_name}. Latest signal: {event_type} - {request.message}"
    )


def risk_for(request: IncidentAnalysisRequest, base_risk: str) -> str:
    if request.incident is None:
        return base_risk

    return (
        f"{base_risk} Incident status is {request.incident.status}, "
        f"duration is {request.incident.duration_minutes or 0} minute(s), "
        f"and {request.incident.event_count or 1} related event(s) were observed."
    )


def context_actions_for(request: IncidentAnalysisRequest) -> list[str]:
    if request.incident is None:
        return []

    actions = [
        "Review the related events together instead of treating the latest event as isolated.",
    ]
    component = request.incident.component
    if component and component.monitoring_methods:
        actions.append(
            "Compare the failed checks with successful checks from the same component to isolate device, service, or protocol failure."
        )
    if request.incident.recent_metrics:
        actions.append("Check recent metric history around the incident start time for degradation trends.")
    return actions
