package com.gsmv.audit.service;

import com.gsmv.audit.dto.AuditLogView;
import com.gsmv.audit.mapper.AuditMapper;
import com.gsmv.audit.model.AuditLog;
import com.gsmv.common.PageResponse;
import com.gsmv.common.TraceIdContext;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditMapper auditMapper;

    public AuditService(AuditMapper auditMapper) {
        this.auditMapper = auditMapper;
    }

    public void record(Long userId, String module, String action, String entityType, Long entityId, boolean success, String detailJson) {
        AuditLog auditLog = new AuditLog();
        auditLog.setUserId(userId);
        auditLog.setModule(module);
        auditLog.setAction(action);
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId);
        auditLog.setRequestId(TraceIdContext.getTraceId());
        auditLog.setSuccess(success ? 1 : 0);
        auditLog.setDetailJson(detailJson);
        auditMapper.insert(auditLog);
    }

    public PageResponse<AuditLogView> list(String module, Integer success, int page, int size) {
        return listByUser(null, module, success, page, size);
    }

    public PageResponse<AuditLogView> listByUser(Long userId, String module, Integer success, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = (safePage - 1) * safeSize;
        List<AuditLogView> items = auditMapper.findPage(userId, module, success, safeSize, offset);
        long total = auditMapper.count(userId, module, success);
        return new PageResponse<>(items, total, safePage, safeSize);
    }
}
