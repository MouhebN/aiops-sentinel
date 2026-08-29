package com.aiops.backend.device;

import java.time.Instant;

public record DeviceResponse(
        String id,
        String name,
        DeviceType type,
        String location,
        DeviceStatus status,
        Instant lastSeenAt
) {
    public static DeviceResponse from(Device device) {
        return new DeviceResponse(
                device.getId(),
                device.getName(),
                device.getType(),
                device.getLocation(),
                device.getStatus(),
                device.getLastSeenAt()
        );
    }
}
