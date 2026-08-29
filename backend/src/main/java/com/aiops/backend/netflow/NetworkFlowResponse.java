package com.aiops.backend.netflow;

import java.time.Instant;

public record NetworkFlowResponse(
        Long id,
        Long sourceId,
        Instant startTime,
        Instant endTime,
        long durationMs,
        String sourceIp,
        String destinationIp,
        Integer sourcePort,
        Integer destinationPort,
        String protocol,
        long packets,
        long bytes,
        String exporterName,
        Integer inputInterface,
        Integer outputInterface,
        boolean suspicious,
        NetFlowAnomalyType anomalyType,
        String anomalyReason,
        Long incidentId,
        String rawRecord,
        Instant createdAt
) {
    public static NetworkFlowResponse from(NetworkFlow flow) {
        return new NetworkFlowResponse(
                flow.getId(),
                flow.getSourceId(),
                flow.getStartTime(),
                flow.getEndTime(),
                flow.getDurationMs(),
                flow.getSourceIp(),
                flow.getDestinationIp(),
                flow.getSourcePort(),
                flow.getDestinationPort(),
                flow.getProtocol(),
                flow.getPackets(),
                flow.getBytes(),
                flow.getExporterName(),
                flow.getInputInterface(),
                flow.getOutputInterface(),
                flow.isSuspicious(),
                flow.getAnomalyType(),
                flow.getAnomalyReason(),
                flow.getIncidentId(),
                flow.getRawRecord(),
                flow.getCreatedAt()
        );
    }
}
