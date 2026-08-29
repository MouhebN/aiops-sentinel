package com.aiops.backend.component;

import com.aiops.backend.device.DeviceType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record SaveMonitoredComponentRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull DeviceType type,
        @Size(max = 255) String ipAddress,
        @Size(max = 500) String httpUrl,
        @Positive Integer tcpPort,
        @Positive Integer snmpPort,
        @Size(max = 100) String snmpCommunity,
        @Size(max = 255) String snmpOid,
        @NotBlank @Size(max = 255) String location,
        Criticality criticality,
        @NotEmpty Set<@NotNull MonitoringMethod> monitoringMethods,
        @Min(5) Integer checkIntervalSeconds,
        Boolean enabled
) {

    @AssertTrue(message = "ipAddress is required when PING monitoring is selected")
    public boolean isPingConfigurationValid() {
        return monitoringMethods == null
                || !monitoringMethods.contains(MonitoringMethod.PING)
                || (ipAddress != null && !ipAddress.isBlank());
    }

    @AssertTrue(message = "httpUrl is required when HTTP_HEALTH monitoring is selected")
    public boolean isHttpConfigurationValid() {
        return monitoringMethods == null
                || (!monitoringMethods.contains(MonitoringMethod.HTTP_HEALTH)
                && !monitoringMethods.contains(MonitoringMethod.UPS_HTTP_METRICS))
                || (httpUrl != null && !httpUrl.isBlank());
    }

    @AssertTrue(message = "ipAddress and tcpPort are required when TCP_PORT monitoring is selected")
    public boolean isTcpConfigurationValid() {
        return monitoringMethods == null
                || (!monitoringMethods.contains(MonitoringMethod.TCP_PORT)
                && !monitoringMethods.contains(MonitoringMethod.RTSP_HEALTH))
                || ((ipAddress != null && !ipAddress.isBlank()) && tcpPort != null);
    }

    @AssertTrue(message = "ipAddress and snmpCommunity are required when SNMP monitoring is selected")
    public boolean isSnmpConfigurationValid() {
        return monitoringMethods == null
                || (!monitoringMethods.contains(MonitoringMethod.SNMP_BASIC)
                && !monitoringMethods.contains(MonitoringMethod.SNMP_ROUTER_METRICS)
                && !monitoringMethods.contains(MonitoringMethod.SNMP_SERVER_METRICS)
                && !monitoringMethods.contains(MonitoringMethod.UPS_SNMP_METRICS))
                || (ipAddress != null && !ipAddress.isBlank()
                && snmpCommunity != null && !snmpCommunity.isBlank());
    }
}
