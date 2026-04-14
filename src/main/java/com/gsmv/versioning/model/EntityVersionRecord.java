package com.gsmv.versioning.model;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class EntityVersionRecord {

    private Long id;
    private String entityType;
    private Long entityId;
    private Integer versionNo;
    private String action;
    private String snapshotJson;
    private String diffJson;
    private Long changedBy;
    private Long rollbackSourceVersionId;
    private LocalDateTime createdAt;
}
