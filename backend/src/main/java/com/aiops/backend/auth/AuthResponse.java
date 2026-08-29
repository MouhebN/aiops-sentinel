package com.aiops.backend.auth;

public record AuthResponse(
        String token,
        CurrentUserResponse user
) {
}
