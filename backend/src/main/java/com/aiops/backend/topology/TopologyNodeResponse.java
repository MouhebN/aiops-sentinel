package com.aiops.backend.topology;

import com.aiops.backend.component.ComponentStatus;
import com.aiops.backend.device.DeviceType;
import com.aiops.backend.event.Severity;

import java.time.Instant;
import java.util.List;

public record TopologyNodeResponse(
        String id,
        Long componentId,
        String name,
        DeviceType type,
        String ipAddress,
        String location,
        ComponentStatus operationalStatus,
        boolean monitoringEnabled,
        Instant lastCheckedAt,
        Instant lastSeenAt,
        int activeIncidentCount,
        Severity highestIncidentSeverity,
        TopologySecurityState securityState,
        List<Long> activeIncidentIds,
        List<String> evidenceTypes
) {
}
