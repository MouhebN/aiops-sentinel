package com.aiops.backend.topology;

public record TopologyLinkResponse(
        String id,
        String source,
        String target,
        ComponentRelationType relationType,
        String label
) {
}
