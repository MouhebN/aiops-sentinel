package com.aiops.backend.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRequest(
        @NotBlank String fullName,
        @NotNull Role role,
        boolean enabled
) {
}
