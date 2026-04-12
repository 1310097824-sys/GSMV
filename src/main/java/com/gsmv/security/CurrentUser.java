package com.gsmv.security;

import java.util.Set;

public record CurrentUser(
        Long userId,
        String username,
        String displayName,
        Set<String> authorities
) {
}
