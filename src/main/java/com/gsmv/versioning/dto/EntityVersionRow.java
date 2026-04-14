package com.gsmv.versioning.dto;

import java.time.LocalDateTime;

public record EntityVersionRow(
        Long id,
        String entityType,
        Long entityId,
        Integer versionNo,
        String action,
        String snapshotJson,
        String diffJson,
        Long changedBy,
        String changedByName,
        Long rollbackSourceVersionId,
        Integer rollbackSourceVersionNo,
        LocalDateTime createdAt
) {
}
