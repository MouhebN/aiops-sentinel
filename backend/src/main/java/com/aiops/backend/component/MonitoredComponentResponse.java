package com.aiops.backend.component;

import com.aiops.backend.device.DeviceType;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record MonitoredComponentResponse(
        Long id,
        String name,
        DeviceType type,
        String ipAddress,
        String httpUrl,
        Integer tcpPort,
        Integer snmpPort,
        String snmpCommunity,
        String snmpOid,
        String location,
        Criticality criticality,
        Set<MonitoringMethod> monitoringMethods,
        int checkIntervalSeconds,
        boolean enabled,
        ComponentStatus lastStatus,
        Instant lastSeenAt,
        Instant lastCheckedAt,
        String lastError,
        String lastCheckDetails,
        int failureCount,
        int successCount,
        Instant createdAt,
        Instant updatedAt,
        List<ComponentNetworkInterfaceResponse> networkInterfaces
) {

    public static MonitoredComponentResponse from(MonitoredComponent component) {
        return from(component, List.of());
    }

    public static MonitoredComponentResponse from(
            MonitoredComponent component,
            List<ComponentNetworkInterfaceResponse> networkInterfaces
    ) {
        return new MonitoredComponentResponse(
                component.getId(),
                component.getName(),
                component.getType(),
                component.getIpAddress(),
                component.getHttpUrl(),
                component.getTcpPort(),
                component.getSnmpPort(),
                component.getSnmpCommunity(),
                component.getSnmpOid(),
                component.getLocation(),
                component.getCriticality(),
                component.getMonitoringMethods(),
                component.getCheckIntervalSeconds(),
                component.isEnabled(),
                component.getLastStatus(),
                component.getLastSeenAt(),
                component.getLastCheckedAt(),
                component.getLastError(),
                component.getLastCheckDetails(),
                component.getFailureCount(),
                component.getSuccessCount(),
                component.getCreatedAt(),
                component.getUpdatedAt(),
                networkInterfaces == null ? List.of() : networkInterfaces
        );
    }
}
