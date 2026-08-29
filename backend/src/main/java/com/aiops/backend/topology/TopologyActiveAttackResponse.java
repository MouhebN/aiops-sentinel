package com.aiops.backend.topology;

import com.aiops.backend.event.Severity;

import java.util.List;

public record TopologyActiveAttackResponse(
        String source,
        String target,
        Long incidentId,
        String title,
        Severity severity,
        String type,
        String sourceIp,
        boolean blocked,
        List<String> evidence
) {
}
