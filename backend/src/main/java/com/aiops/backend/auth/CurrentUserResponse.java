package com.aiops.backend.auth;

import java.time.Instant;

public record CurrentUserResponse(
        Long id,
        String fullName,
        String email,
        Role role,
        boolean enabled,
        Instant createdAt
) {
    public static CurrentUserResponse from(AppUser user) {
        return new CurrentUserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt()
        );
    }
}
