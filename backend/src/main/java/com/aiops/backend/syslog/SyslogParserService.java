package com.aiops.backend.syslog;

import com.aiops.backend.event.Severity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RFC 3164 syslog timestamps such as {@code Aug 18 16:16:00} have no year and no timezone.
 * They are interpreted in {@code app.syslog.default-timezone} (the AIOps Sentinel deployment
 * timezone), then stored as UTC {@link Instant} values. JSON APIs emit ISO-8601 with a Z offset.
 * Local display conversion belongs in the frontend only.
 */
@Service
public class SyslogParserService {

    private static final Logger log = LoggerFactory.getLogger(SyslogParserService.class);
    private static final Pattern PRIORITY_PREFIX = Pattern.compile("^<\\d+>");
    private static final Pattern BSD_HEADER = Pattern.compile(
            "^(?<month>[A-Z][a-z]{2})\\s+(?<day>\\d{1,2})\\s+(?<time>\\d{2}:\\d{2}:\\d{2})\\s+(?<host>\\S+)\\s+(?<body>.*)$"
    );
    private static final Pattern SIMPLE_HOST_PREFIX = Pattern.compile("^(?<host>[A-Za-z0-9._-]+)\\s+(?<body>.+)$");
    private static final Pattern FIREWALL_FLOW = Pattern.compile(
            "(?i)\\b(?:deny|block|reject|drop|allow|accept)\\s+(?<protocol>TCP|UDP|ICMP)\\s+"
                    + "(?<sourceAddress>\\S+?):(?<sourcePort>\\d+)\\s*->\\s*"
                    + "(?<destinationAddress>\\S+?):(?<destinationPort>\\d+)"
    );

    private final ZoneId syslogDefaultZone;
    private final Clock clock;

    @Autowired
    public SyslogParserService(
            @Value("${app.syslog.default-timezone:UTC}") String defaultTimezone
    ) {
        this(defaultTimezone, Clock.systemUTC());
    }

    SyslogParserService(String defaultTimezone, Clock clock) {
        this.syslogDefaultZone = ZoneId.of(defaultTimezone.trim());
        this.clock = clock;
    }

    public ParsedSyslogMessage parse(String rawLog, SyslogParserProfile parserProfile) {
        String sanitized = rawLog == null ? "" : rawLog.trim();
        String withoutPriority = PRIORITY_PREFIX.matcher(sanitized).replaceFirst("").trim();

        SyslogEnvelope envelope = parseEnvelope(withoutPriority);
        String body = envelope.body() == null || envelope.body().isBlank() ? withoutPriority : envelope.body();
        String normalized = body.toLowerCase(Locale.ROOT);

        return switch (parserProfile) {
            case FIREWALL -> new ParsedSyslogMessage(
                    firewallEventType(normalized),
                    firewallSeverity(normalized),
                    body,
                    firewallDetails(body),
                    envelope.hostname(),
                    envelope.occurredAt(),
                    firewallProtocol(body),
                    firewallSourceAddress(body),
                    firewallSourcePort(body),
                    firewallDestinationAddress(body),
                    firewallDestinationPort(body)
            );
            case SWITCH -> new ParsedSyslogMessage(
                    switchEventType(normalized),
                    switchSeverity(normalized),
                    body,
                    body,
                    envelope.hostname(),
                    envelope.occurredAt(),
                    null,
                    null,
                    null,
                    null,
                    null
            );
            case LINUX_AUTH -> new ParsedSyslogMessage(
                    linuxAuthEventType(normalized),
                    linuxAuthSeverity(normalized),
                    body,
                    body,
                    envelope.hostname(),
                    envelope.occurredAt(),
                    null,
                    null,
                    null,
                    null,
                    null
            );
            case UPS -> new ParsedSyslogMessage(
                    upsEventType(normalized),
                    upsSeverity(normalized),
                    body,
                    body,
                    envelope.hostname(),
                    envelope.occurredAt(),
                    null,
                    null,
                    null,
                    null,
                    null
            );
            case CAMERA -> new ParsedSyslogMessage(
                    cameraEventType(normalized),
                    cameraSeverity(normalized),
                    body,
                    body,
                    envelope.hostname(),
                    envelope.occurredAt(),
                    null,
                    null,
                    null,
                    null,
                    null
            );
            case APPLICATION -> new ParsedSyslogMessage(
                    applicationEventType(normalized),
                    applicationSeverity(normalized),
                    body,
                    body,
                    envelope.hostname(),
                    envelope.occurredAt(),
                    null,
                    null,
                    null,
                    null,
                    null
            );
            case GENERIC -> new ParsedSyslogMessage(
                    "GENERIC_SYSLOG_EVENT",
                    genericSeverity(normalized),
                    body,
                    body,
                    envelope.hostname(),
                    envelope.occurredAt(),
                    null,
                    null,
                    null,
                    null,
                    null
            );
        };
    }

    private SyslogEnvelope parseEnvelope(String line) {
        Matcher matcher = BSD_HEADER.matcher(line);
        if (matcher.matches()) {
            Instant occurredAt = parseTimestamp(
                    matcher.group("month"),
                    matcher.group("day"),
                    matcher.group("time")
            );
            return new SyslogEnvelope(matcher.group("host"), matcher.group("body"), occurredAt);
        }

        Matcher simpleMatcher = SIMPLE_HOST_PREFIX.matcher(line);
        if (simpleMatcher.matches()) {
            return new SyslogEnvelope(simpleMatcher.group("host"), simpleMatcher.group("body"), null);
        }

        return new SyslogEnvelope(null, line, null);
    }

    private Instant parseTimestamp(String monthText, String dayText, String timeText) {
        try {
            ZonedDateTime now = ZonedDateTime.now(clock.withZone(syslogDefaultZone));
            DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("MMM d HH:mm:ss")
                    .parseDefaulting(ChronoField.YEAR, now.getYear())
                    .toFormatter(Locale.ENGLISH);
            LocalDateTime parsed = LocalDateTime.parse(
                    monthText + " " + Integer.parseInt(dayText) + " " + timeText,
                    formatter
            );
            ZonedDateTime zoned = parsed.atZone(syslogDefaultZone);
            if (zoned.isAfter(now.plusDays(1))) {
                zoned = zoned.withYear(now.getYear() - 1);
            }
            Instant storedUtc = zoned.toInstant();
            log.debug(
                    "RFC3164 timestamp {}.{} {} interpreted in {} storedUtc={}",
                    monthText,
                    dayText,
                    timeText,
                    syslogDefaultZone,
                    storedUtc
            );
            return storedUtc;
        } catch (DateTimeParseException | IllegalArgumentException exception) {
            return null;
        }
    }

    private Severity genericSeverity(String normalized) {
        if (containsAny(normalized, "down", "unreachable", "error")) {
            return Severity.CRITICAL;
        }
        if (containsAny(normalized, "failed", "warning")) {
            return Severity.WARNING;
        }
        return Severity.INFO;
    }

    private String firewallEventType(String normalized) {
        if (containsAny(normalized, "port scan", "multiple ports", " scan ")) {
            return "PORT_SCAN";
        }
        if (containsAny(normalized, "deny", "block", "reject", "drop")) {
            return "FIREWALL_DENY";
        }
        if (containsAny(normalized, "allowed", "accept")) {
            return "FIREWALL_ALLOW";
        }
        return "FIREWALL_EVENT";
    }

    private Severity firewallSeverity(String normalized) {
        if (containsAny(normalized, "port scan", "multiple ports", " scan ")) {
            return Severity.CRITICAL;
        }
        if (containsAny(normalized, "deny", "block", "reject", "drop")) {
            return Severity.WARNING;
        }
        return Severity.INFO;
    }

    private String firewallDetails(String body) {
        Matcher matcher = FIREWALL_FLOW.matcher(body);
        if (!matcher.find()) {
            return body;
        }
        return "protocol=" + matcher.group("protocol").toUpperCase(Locale.ROOT)
                + "; sourceAddress=" + matcher.group("sourceAddress")
                + "; sourcePort=" + matcher.group("sourcePort")
                + "; destinationAddress=" + matcher.group("destinationAddress")
                + "; destinationPort=" + matcher.group("destinationPort");
    }

    private String firewallProtocol(String body) {
        Matcher matcher = FIREWALL_FLOW.matcher(body);
        return matcher.find() ? matcher.group("protocol").toUpperCase(Locale.ROOT) : null;
    }

    private String firewallSourceAddress(String body) {
        Matcher matcher = FIREWALL_FLOW.matcher(body);
        return matcher.find() ? matcher.group("sourceAddress") : null;
    }

    private Integer firewallSourcePort(String body) {
        Matcher matcher = FIREWALL_FLOW.matcher(body);
        return matcher.find() ? Integer.valueOf(matcher.group("sourcePort")) : null;
    }

    private String firewallDestinationAddress(String body) {
        Matcher matcher = FIREWALL_FLOW.matcher(body);
        return matcher.find() ? matcher.group("destinationAddress") : null;
    }

    private Integer firewallDestinationPort(String body) {
        Matcher matcher = FIREWALL_FLOW.matcher(body);
        return matcher.find() ? Integer.valueOf(matcher.group("destinationPort")) : null;
    }

    private String switchEventType(String normalized) {
        if (containsAny(normalized, "link down", "interface down", "port down")) {
            return "INTERFACE_DOWN";
        }
        if (containsAny(normalized, "link up", "interface up", "port up")) {
            return "INTERFACE_UP";
        }
        if (containsAny(normalized, "packet error", " errors", "error ")) {
            return "INTERFACE_ERRORS";
        }
        return "SWITCH_EVENT";
    }

    private Severity switchSeverity(String normalized) {
        if (containsAny(normalized, "link down", "interface down", "port down")) {
            return Severity.CRITICAL;
        }
        if (containsAny(normalized, "packet error", " errors", "error ")) {
            return Severity.WARNING;
        }
        return Severity.INFO;
    }

    private String linuxAuthEventType(String normalized) {
        if (containsAny(normalized, "failed password", "authentication failure", "invalid user")) {
            return "AUTH_FAILURE";
        }
        if (containsAny(normalized, "accepted password", "login success")) {
            return "AUTH_SUCCESS";
        }
        if (containsAny(normalized, "sudo", "session opened")) {
            return "AUTH_ACTIVITY";
        }
        return "LINUX_AUTH_EVENT";
    }

    private Severity linuxAuthSeverity(String normalized) {
        if (containsAny(normalized, "failed password", "authentication failure", "invalid user")) {
            return Severity.CRITICAL;
        }
        return Severity.INFO;
    }

    private String upsEventType(String normalized) {
        if (containsAny(normalized, "battery low", "low battery")) {
            return "UPS_BATTERY_LOW";
        }
        if (containsAny(normalized, "on battery", "power failure")) {
            return "UPS_ON_BATTERY";
        }
        if (containsAny(normalized, "power restored", "utility restored")) {
            return "UPS_POWER_RESTORED";
        }
        return "UPS_EVENT";
    }

    private Severity upsSeverity(String normalized) {
        if (containsAny(normalized, "battery low", "low battery", "on battery", "power failure")) {
            return Severity.CRITICAL;
        }
        return Severity.INFO;
    }

    private String cameraEventType(String normalized) {
        if (containsAny(normalized, "video loss", "stream lost", "rtsp failed", "camera offline")) {
            return "CAMERA_STREAM_LOST";
        }
        if (containsAny(normalized, "stream restored", "camera online")) {
            return "CAMERA_STREAM_RESTORED";
        }
        return "CAMERA_EVENT";
    }

    private Severity cameraSeverity(String normalized) {
        if (containsAny(normalized, "video loss", "stream lost", "rtsp failed", "camera offline")) {
            return Severity.CRITICAL;
        }
        return Severity.INFO;
    }

    private String applicationEventType(String normalized) {
        if (containsAny(normalized, "service stopped", "service down")) {
            return "SERVICE_DOWN";
        }
        if (containsAny(normalized, "exception", "error")) {
            return "APPLICATION_ERROR";
        }
        if (containsAny(normalized, "started", "healthy")) {
            return "SERVICE_RECOVERED";
        }
        return "APPLICATION_EVENT";
    }

    private Severity applicationSeverity(String normalized) {
        if (containsAny(normalized, "service stopped", "service down")) {
            return Severity.CRITICAL;
        }
        if (containsAny(normalized, "exception", "error")) {
            return Severity.WARNING;
        }
        return Severity.INFO;
    }

    private boolean containsAny(String normalized, String... patterns) {
        for (String pattern : patterns) {
            if (normalized.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private record SyslogEnvelope(String hostname, String body, Instant occurredAt) {
    }
}
