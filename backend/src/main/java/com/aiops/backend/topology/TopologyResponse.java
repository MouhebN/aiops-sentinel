package com.aiops.backend.topology;

import java.util.List;

public record TopologyResponse(
        List<TopologyNodeResponse> nodes,
        List<TopologyLinkResponse> links,
        List<TopologyExternalEntityResponse> externalEntities,
        List<TopologyActiveAttackResponse> activeAttacks
) {
}
