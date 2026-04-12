package com.gsmv.auth.dto;

import java.util.List;

public record LoginResponse(
        String accessToken,
        long expiresInSeconds,
        UserProfile user
) {
    public record UserProfile(
            Long id,
            String username,
            String displayName,
            String avatarUrl,
            List<String> roles,
            List<String> authorities
    ) {
    }
}
