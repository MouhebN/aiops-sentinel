package com.aiops.backend.component;

import java.time.Instant;

public record ComponentNetworkInterfaceResponse(
        Long id,
        Long monitoredComponentId,
        String name,
        String ipAddress,
        NetworkInterfaceRole role,
        boolean primary,
        Instant createdAt
) {

    public static ComponentNetworkInterfaceResponse from(ComponentNetworkInterface networkInterface) {
        return new ComponentNetworkInterfaceResponse(
                networkInterface.getId(),
                networkInterface.getMonitoredComponent().getId(),
                networkInterface.getName(),
                networkInterface.getIpAddress(),
                networkInterface.getRole(),
                networkInterface.isPrimary(),
                networkInterface.getCreatedAt()
        );
    }
}
