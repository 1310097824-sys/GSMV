package com.gsmv.audit.model;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AuditLog {

    private Long id;
    private Long userId;
    private String module;
    private String action;
    private String entityType;
    private Long entityId;
    private String requestId;
    private String ip;
    private String userAgent;
    private Integer success;
    private String detailJson;
    private LocalDateTime createdAt;
}
