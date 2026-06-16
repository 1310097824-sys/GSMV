package com.gsmv.user.dto;

import java.util.List;

public record RolePermissionView(
        Long id,
        String code,
        String name,
        String description,
        List<String> permissionCodes,
        long userCount,
        boolean builtIn
) {
}
