package com.aiops.backend.topology;

public record TopologyExternalEntityResponse(
        String id,
        String ip,
        String kind,
        String label
) {
}
