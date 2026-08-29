package com.aiops.backend.netflow;

import java.time.Instant;

public record ParsedNetFlowRecord(
        Instant startTime,
        Instant endTime,
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
        String rawRecord
) {
}
