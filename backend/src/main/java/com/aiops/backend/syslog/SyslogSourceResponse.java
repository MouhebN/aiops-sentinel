package com.aiops.backend.syslog;

import com.aiops.backend.device.DeviceType;

import java.time.Instant;

public record SyslogSourceResponse(
        Long id,
        String name,
        String expectedHost,
        String deviceId,
        String deviceName,
        DeviceType deviceType,
        String location,
        SyslogParserProfile parserProfile,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {

    public static SyslogSourceResponse from(SyslogSource source) {
        return new SyslogSourceResponse(
                source.getId(),
                source.getName(),
                source.getExpectedHost(),
                source.getDeviceId(),
                source.getDeviceName(),
                source.getDeviceType(),
                source.getLocation(),
                source.getParserProfile(),
                source.isEnabled(),
                source.getCreatedAt(),
                source.getUpdatedAt()
        );
    }
}
