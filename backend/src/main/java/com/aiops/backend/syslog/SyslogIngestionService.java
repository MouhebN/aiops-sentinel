package com.aiops.backend.syslog;

import com.aiops.backend.device.DeviceType;
import com.aiops.backend.event.Event;
import com.aiops.backend.event.EventIngestionRequest;
import com.aiops.backend.event.EventRepository;
import com.aiops.backend.event.Severity;
import com.aiops.backend.event.EventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class SyslogIngestionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyslogIngestionService.class);

    private final SyslogSourceRepository syslogSourceRepository;
    private final SyslogParserService syslogParserService;
    private final EventService eventService;
    private final EventRepository eventRepository;

    public SyslogIngestionService(
            SyslogSourceRepository syslogSourceRepository,
            SyslogParserService syslogParserService,
            EventService eventService,
            EventRepository eventRepository
    ) {
        this.syslogSourceRepository = syslogSourceRepository;
        this.syslogParserService = syslogParserService;
        this.eventService = eventService;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public void ingest(String sourceIp, String rawLog) {
        String normalizedSourceIp = sourceIp == null ? null : sourceIp.trim();
        String normalizedRawLog = rawLog == null ? "" : rawLog.trim();
        if (normalizedRawLog.isBlank()) {
            return;
        }

        ParsedSyslogMessage parsed = syslogParserService.parse(normalizedRawLog, SyslogParserProfile.GENERIC);
        String parsedHostname = parsed.parsedHostname();

        Optional<SyslogSource> matchedSource = findMatchingSource(normalizedSourceIp, parsedHostname);
        SyslogParserProfile finalProfile = matchedSource.map(SyslogSource::getParserProfile).orElse(SyslogParserProfile.GENERIC);
        if (finalProfile != SyslogParserProfile.GENERIC) {
            parsed = syslogParserService.parse(normalizedRawLog, finalProfile);
            parsedHostname = parsed.parsedHostname();
        }
        parsed = maybeEscalateFirewallActivity(parsed, normalizedRawLog, occurredAtFrom(parsed));

        LOGGER.info(
                "Syslog normalized: sourceIp={}, matchedSource={}, parserProfile={}, eventType={}, severity={}, message={}, rawLog={}",
                normalizedSourceIp,
                matchedSource.map(SyslogSource::getName).orElse("none"),
                finalProfile,
                parsed.eventType(),
                parsed.severity(),
                parsed.message(),
                normalizedRawLog
        );

        SyslogSource source = matchedSource.orElse(null);
        String hostname = parsedHostname == null || parsedHostname.isBlank()
                ? normalizedSourceIp
                : parsedHostname;

        String deviceName = source != null ? source.getDeviceName() : (hostname == null || hostname.isBlank() ? "Unknown syslog sender" : hostname);
        String deviceId = source != null ? source.getDeviceId() : "syslog-" + slugify(hostname == null ? "unknown" : hostname);
        DeviceType deviceType = source != null ? source.getDeviceType() : DeviceType.OTHER;
        String location = source != null ? source.getLocation() : "Unknown";
        Instant occurredAt = occurredAtFrom(parsed);

        Event savedEvent = eventService.ingest(new EventIngestionRequest(
                deviceId,
                deviceName,
                deviceType,
                location,
                parsed.eventType(),
                parsed.severity(),
                parsed.message(),
                parsed.details(),
                normalizedRawLog,
                normalizedSourceIp,
                source == null ? null : source.getName(),
                finalProfile.name(),
                "SYSLOG",
                occurredAt
        ));
        LOGGER.info(
                "Syslog event saved: eventId={}, deviceId={}, deviceName={}, eventType={}, severity={}",
                savedEvent.getId(),
                savedEvent.getDeviceId(),
                savedEvent.getDeviceName(),
                savedEvent.getEventType(),
                savedEvent.getSeverity()
        );
    }

    private Instant occurredAtFrom(ParsedSyslogMessage parsed) {
        return parsed.occurredAt() == null ? Instant.now() : parsed.occurredAt();
    }

    private ParsedSyslogMessage maybeEscalateFirewallActivity(
            ParsedSyslogMessage parsed,
            String rawLog,
            Instant occurredAt
    ) {
        if (!"FIREWALL_DENY".equals(parsed.eventType())
                || parsed.sourceAddress() == null
                || parsed.destinationAddress() == null
                || parsed.destinationPort() == null) {
            return parsed;
        }

        Instant from = occurredAt.minus(Duration.ofMinutes(5));
        Set<Integer> destinationPorts = new HashSet<>();
        destinationPorts.add(parsed.destinationPort());

        for (Event candidate : eventRepository.findTop200ByOccurredAtGreaterThanEqualAndEventSourceOrderByOccurredAtDesc(from, "SYSLOG")) {
            FirewallFlowDetails details = extractFirewallFlow(candidate.getDetails(), candidate.getRawLog());
            if (details == null) {
                continue;
            }
            if (parsed.sourceAddress().equalsIgnoreCase(details.sourceAddress())
                    && parsed.destinationAddress().equalsIgnoreCase(details.destinationAddress())) {
                destinationPorts.add(details.destinationPort());
            }
        }

        if (destinationPorts.size() < 3) {
            return parsed;
        }

        return new ParsedSyslogMessage(
                "POSSIBLE_PORT_SCAN",
                Severity.CRITICAL,
                parsed.message(),
                appendPortScanHint(parsed.details(), parsed.sourceAddress(), parsed.destinationAddress(), destinationPorts),
                parsed.parsedHostname(),
                parsed.occurredAt(),
                parsed.protocol(),
                parsed.sourceAddress(),
                parsed.sourcePort(),
                parsed.destinationAddress(),
                parsed.destinationPort()
        );
    }

    private String appendPortScanHint(String details, String sourceAddress, String destinationAddress, Set<Integer> destinationPorts) {
        return (details == null || details.isBlank() ? "" : details + "; ")
                + "portScanHint=true; sourceAddress=" + sourceAddress
                + "; destinationAddress=" + destinationAddress
                + "; distinctDestinationPorts=" + destinationPorts.stream().sorted().toList();
    }

    private FirewallFlowDetails extractFirewallFlow(String details, String rawLog) {
        String candidate = details != null && details.contains("sourceAddress=") ? details : rawLog;
        if (candidate == null || candidate.isBlank()) {
            return null;
        }

        String sourceAddress = extractValue(candidate, "sourceAddress");
        String destinationAddress = extractValue(candidate, "destinationAddress");
        Integer destinationPort = extractInteger(candidate, "destinationPort");

        if (sourceAddress == null || destinationAddress == null || destinationPort == null) {
            ParsedSyslogMessage reparsed = syslogParserService.parse(candidate, SyslogParserProfile.FIREWALL);
            if (reparsed.sourceAddress() == null || reparsed.destinationAddress() == null || reparsed.destinationPort() == null) {
                return null;
            }
            return new FirewallFlowDetails(reparsed.sourceAddress(), reparsed.destinationAddress(), reparsed.destinationPort());
        }

        return new FirewallFlowDetails(sourceAddress, destinationAddress, destinationPort);
    }

    private String extractValue(String text, String key) {
        String marker = key + "=";
        int start = text.indexOf(marker);
        if (start < 0) {
            return null;
        }
        int valueStart = start + marker.length();
        int end = text.indexOf(';', valueStart);
        return (end < 0 ? text.substring(valueStart) : text.substring(valueStart, end)).trim();
    }

    private Integer extractInteger(String text, String key) {
        String value = extractValue(text, key);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private record FirewallFlowDetails(String sourceAddress, String destinationAddress, Integer destinationPort) {
    }

    private Optional<SyslogSource> findMatchingSource(String sourceIp, String parsedHostname) {
        if (parsedHostname != null && !parsedHostname.isBlank()) {
            if (sourceIp != null && !sourceIp.isBlank()) {
                Optional<SyslogSource> byHostAndName = syslogSourceRepository
                        .findFirstByEnabledTrueAndExpectedHostIgnoreCaseAndNameIgnoreCase(sourceIp, parsedHostname);
                if (byHostAndName.isPresent()) {
                    return byHostAndName;
                }

                Optional<SyslogSource> byHostAndDeviceName = syslogSourceRepository
                        .findFirstByEnabledTrueAndExpectedHostIgnoreCaseAndDeviceNameIgnoreCase(sourceIp, parsedHostname);
                if (byHostAndDeviceName.isPresent()) {
                    return byHostAndDeviceName;
                }
            }

            Optional<SyslogSource> byName = syslogSourceRepository.findFirstByEnabledTrueAndNameIgnoreCase(parsedHostname);
            if (byName.isPresent()) {
                return byName;
            }

            Optional<SyslogSource> byDeviceName = syslogSourceRepository.findFirstByEnabledTrueAndDeviceNameIgnoreCase(parsedHostname);
            if (byDeviceName.isPresent()) {
                return byDeviceName;
            }
        }

        if (sourceIp == null || sourceIp.isBlank()) {
            return Optional.empty();
        }
        return syslogSourceRepository.findFirstByEnabledTrueAndExpectedHostIgnoreCase(sourceIp);
    }

    private String slugify(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }
}
