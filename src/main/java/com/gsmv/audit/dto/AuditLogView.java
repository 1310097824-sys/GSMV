package com.gsmv.audit.dto;

import java.time.LocalDateTime;

public record AuditLogView(
        Long id,
        Long userId,
        String username,
        String displayName,
        String module,
        String action,
        String entityType,
        Long entityId,
        String requestId,
        Integer success,
        String detailJson,
        LocalDateTime createdAt
) {
}
