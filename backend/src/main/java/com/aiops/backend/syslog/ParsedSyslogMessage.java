package com.aiops.backend.syslog;

import com.aiops.backend.event.Severity;

import java.time.Instant;

public record ParsedSyslogMessage(
        String eventType,
        Severity severity,
        String message,
        String details,
        String parsedHostname,
        Instant occurredAt,
        String protocol,
        String sourceAddress,
        Integer sourcePort,
        String destinationAddress,
        Integer destinationPort
) {
}
