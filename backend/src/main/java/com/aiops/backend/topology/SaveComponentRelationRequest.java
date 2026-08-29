package com.aiops.backend.topology;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SaveComponentRelationRequest(
        @NotNull Long sourceComponentId,
        @NotNull Long targetComponentId,
        @NotNull ComponentRelationType relationType,
        @Size(max = 200) String label
) {
}
