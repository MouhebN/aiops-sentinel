package com.aiops.backend.netflow;

import java.time.Instant;

public record NetFlowImportRunResponse(
        Long id,
        Long sourceId,
        Instant startedAt,
        Instant finishedAt,
        NetFlowImportStatus status,
        long recordsRead,
        long recordsImported,
        long suspiciousFlows,
        long incidentsCreated,
        String errorMessage
) {
    public static NetFlowImportRunResponse from(NetFlowImportRun run) {
        return new NetFlowImportRunResponse(
                run.getId(),
                run.getSourceId(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getStatus(),
                run.getRecordsRead(),
                run.getRecordsImported(),
                run.getSuspiciousFlows(),
                run.getIncidentsCreated(),
                run.getErrorMessage()
        );
    }
}
