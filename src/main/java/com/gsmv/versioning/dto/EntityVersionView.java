package com.gsmv.versioning.dto;

import java.time.LocalDateTime;
import java.util.List;

public record EntityVersionView(
        Long id,
        Integer versionNo,
        String action,
        Long changedBy,
        String changedByName,
        Long rollbackSourceVersionId,
        Integer rollbackSourceVersionNo,
        LocalDateTime createdAt,
        List<VersionFieldChangeView> changes
) {
}
