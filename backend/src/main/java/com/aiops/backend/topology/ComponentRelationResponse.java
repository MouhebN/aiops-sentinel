package com.aiops.backend.topology;

import java.time.Instant;

public record ComponentRelationResponse(
        Long id,
        Long sourceComponentId,
        String sourceName,
        Long targetComponentId,
        String targetName,
        ComponentRelationType relationType,
        String label,
        Instant createdAt
) {

    public static ComponentRelationResponse from(ComponentRelation relation) {
        return new ComponentRelationResponse(
                relation.getId(),
                relation.getSource().getId(),
                relation.getSource().getName(),
                relation.getTarget().getId(),
                relation.getTarget().getName(),
                relation.getRelationType(),
                relation.getLabel(),
                relation.getCreatedAt()
        );
    }
}
