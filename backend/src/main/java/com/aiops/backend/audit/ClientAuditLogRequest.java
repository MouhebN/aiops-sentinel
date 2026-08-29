package com.aiops.backend.audit;

import jakarta.validation.constraints.NotNull;

public record ClientAuditLogRequest(
        @NotNull AuditAction action,
        String targetType,
        String targetId,
        String details
) {
}
