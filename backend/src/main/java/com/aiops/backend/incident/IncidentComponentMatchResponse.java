package com.aiops.backend.incident;

import com.aiops.backend.component.ComponentStatus;
import com.aiops.backend.component.NetworkInterfaceRole;
import com.aiops.backend.device.DeviceType;

import java.time.Instant;

public record IncidentComponentMatchResponse(
        Long id,
        String name,
        DeviceType type,
        ComponentStatus lastStatus,
        boolean monitoringEnabled,
        String primaryIp,
        String matchedIp,
        String matchedInterfaceName,
        NetworkInterfaceRole matchedRole,
        boolean matchedPrimary,
        String relation,
        String location,
        Instant lastCheckedAt,
        Instant lastSeenAt,
        String lastError
) {
}
