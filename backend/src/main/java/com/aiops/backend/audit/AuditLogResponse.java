package com.aiops.backend.audit;

import com.aiops.backend.auth.Role;

import java.time.Instant;

public record AuditLogResponse(
        Long id,
        String username,
        Role userRole,
        AuditAction action,
        String targetType,
        String targetId,
        String details,
        String ipAddress,
        Instant createdAt
) {
    public static AuditLogResponse from(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getUsername(),
                auditLog.getUserRole(),
                auditLog.getAction(),
                auditLog.getTargetType(),
                auditLog.getTargetId(),
                auditLog.getDetails(),
                auditLog.getIpAddress(),
                auditLog.getCreatedAt()
        );
    }
}
