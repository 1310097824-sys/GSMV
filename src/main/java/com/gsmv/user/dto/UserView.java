package com.gsmv.user.dto;

import java.time.LocalDateTime;
import java.util.List;

public record UserView(
        Long id,
        String username,
        String displayName,
        String email,
        String phone,
        String bio,
        String avatarUrl,
        Integer status,
        String approvalStatus,
        String approvalRemark,
        LocalDateTime reviewedAt,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        List<RoleOption> roles
) {
}
