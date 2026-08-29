package com.aiops.backend.event;

import com.aiops.backend.device.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record EventIngestionRequest(
        @NotBlank String deviceId,
        @NotBlank String deviceName,
        @NotNull DeviceType deviceType,
        @NotBlank String location,
        @NotBlank String eventType,
        Severity severity,
        @NotBlank @Size(max = 1000) String message,
        @Size(max = 4000) String details,
        String rawLog,
        @Size(max = 120) String sourceIp,
        @Size(max = 120) String syslogSourceName,
        @Size(max = 60) String parsingProfile,
        @Size(max = 40) String eventSource,
        Instant occurredAt
) {

    public EventIngestionRequest(
            String deviceId,
            String deviceName,
            DeviceType deviceType,
            String location,
            String eventType,
            Severity severity,
            String message,
            String details,
            Instant occurredAt
    ) {
        this(deviceId, deviceName, deviceType, location, eventType, severity, message, details, null, null, null, null, null, occurredAt);
    }
}
