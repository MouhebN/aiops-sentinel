package com.aiops.backend.netflow;

import java.time.Instant;

public record NetFlowSourceResponse(
        Long id,
        String name,
        NetFlowProviderType providerType,
        String dataDirectory,
        int collectorPort,
        boolean enabled,
        Instant lastImportAt,
        NetFlowImportStatus lastImportStatus,
        String lastImportMessage,
        Instant createdAt,
        Instant updatedAt
) {
    public static NetFlowSourceResponse from(NetFlowSource source) {
        return new NetFlowSourceResponse(
                source.getId(),
                source.getName(),
                source.getProviderType(),
                source.getDataDirectory(),
                source.getCollectorPort(),
                source.isEnabled(),
                source.getLastImportAt(),
                source.getLastImportStatus(),
                source.getLastImportMessage(),
                source.getCreatedAt(),
                source.getUpdatedAt()
        );
    }
}
