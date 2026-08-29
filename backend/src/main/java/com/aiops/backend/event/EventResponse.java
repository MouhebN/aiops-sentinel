package com.aiops.backend.event;

import com.aiops.backend.device.DeviceStatus;
import com.aiops.backend.device.DeviceType;

import java.time.Instant;

public record EventResponse(
        Long id,
        String deviceId,
        String deviceName,
        DeviceType deviceType,
        String location,
        String eventType,
        Severity severity,
        DeviceStatus resultingStatus,
        String message,
        String details,
        String rawLog,
        String sourceIp,
        String syslogSourceName,
        String parsingProfile,
        String eventSource,
        Instant occurredAt
) {
    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(),
                event.getDeviceId(),
                event.getDeviceName(),
                event.getDeviceType(),
                event.getLocation(),
                event.getEventType(),
                event.getSeverity(),
                SeverityClassifier.toDeviceStatus(event.getSeverity()),
                event.getMessage(),
                event.getDetails(),
                event.getRawLog(),
                event.getSourceIp(),
                event.getSyslogSourceName(),
                event.getParsingProfile(),
                event.getEventSource(),
                event.getOccurredAt()
        );
    }
}
