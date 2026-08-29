package com.aiops.backend.component;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UpdateComponentStatusRequest(
        @NotNull ComponentStatus status,
        Instant checkedAt,
        Instant seenAt,
        @Size(max = 2000) String error
) {
}
