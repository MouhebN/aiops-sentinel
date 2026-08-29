package com.aiops.backend.ai;

import com.aiops.backend.component.Criticality;
import com.aiops.backend.component.MonitoringMethod;
import com.aiops.backend.component.ComponentStatus;
import com.aiops.backend.device.DeviceType;
import com.aiops.backend.event.Severity;
import com.aiops.backend.incident.IncidentStatus;
import com.aiops.backend.netflow.NetFlowAnomalyType;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record AiIncidentContextResponse(
        Long incidentId,
        String correlationKey,
        String title,
        String category,
        Severity severity,
        IncidentStatus status,
        String location,
        String deviceId,
        String deviceName,
        DeviceType deviceType,
        Instant firstSeenAt,
        Instant lastSeenAt,
        Instant createdAt,
        Instant lastActivityAt,
        Instant resolvedAt,
        long durationMinutes,
        int eventCount,
        boolean acknowledged,
        Long previousSimilarIncidentId,
        ComponentSnapshot component,
        List<RelatedEventSnapshot> relatedEvents,
        List<MetricSnapshot> recentMetrics,
        List<PacketCaptureSummarySnapshot> packetCaptureSummaries,
        List<NetworkFlowSummarySnapshot> networkFlowSummaries,
        List<SimilarIncidentSnapshot> previousSimilarIncidents,
        List<PreviousReportSnapshot> previousReports
) {

    public record ComponentSnapshot(
            Long id,
            String name,
            DeviceType type,
            String ipAddress,
            String httpUrl,
            Integer tcpPort,
            String location,
            Criticality criticality,
            Set<MonitoringMethod> monitoringMethods,
            ComponentStatus lastStatus,
            String lastError,
            String lastCheckDetails,
            String matchedNetworkIp,
            String matchedInterfaceName
    ) {
    }

    public record RelatedEventSnapshot(
            Long id,
            String eventType,
            Severity severity,
            String message,
            String details,
            String rawLog,
            String sourceIp,
            String syslogSourceName,
            String parsingProfile,
            String eventSource,
            String protocol,
            String sourceAddress,
            Integer sourcePort,
            String destinationAddress,
            Integer destinationPort,
            String deviceId,
            String deviceName,
            DeviceType deviceType,
            String location,
            Instant occurredAt
    ) {
    }

    public record MetricSnapshot(
            String metricName,
            double metricValue,
            String unit,
            String source,
            Instant sampledAt
    ) {
    }

    public record PacketCaptureSummarySnapshot(
            Long id,
            String fileName,
            int totalPackets,
            long totalBytes,
            List<String> topSourceIps,
            List<String> topDestinationIps,
            List<String> topProtocols,
            List<String> topDestinationPorts,
            List<String> suspiciousFindings,
            String summary,
            Instant createdAt
    ) {
    }

    public record NetworkFlowSummarySnapshot(
            String sourceName,
            String sourceIp,
            String destinationIp,
            List<Integer> destinationPorts,
            List<String> protocols,
            long flowCount,
            long totalPackets,
            long totalBytes,
            NetFlowAnomalyType anomalyType,
            String anomalyReason,
            Instant firstSeenAt,
            Instant lastSeenAt
    ) {
    }

    public record SimilarIncidentSnapshot(
            Long id,
            String correlationKey,
            String title,
            String category,
            Severity severity,
            IncidentStatus status,
            String deviceId,
            String deviceName,
            Instant firstSeenAt,
            Instant lastSeenAt,
            Instant lastActivityAt,
            Instant resolvedAt,
            int eventCount
    ) {
    }

    public record PreviousReportSnapshot(
            Long id,
            Long eventId,
            String deviceId,
            String deviceName,
            String eventType,
            Severity severity,
            String summary,
            String provider,
            String model,
            Instant generatedAt
    ) {
    }
}
