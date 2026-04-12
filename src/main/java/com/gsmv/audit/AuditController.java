package com.gsmv.audit;

import com.gsmv.audit.dto.AuditLogView;
import com.gsmv.audit.service.AuditService;
import com.gsmv.common.ApiResponse;
import com.gsmv.common.PageResponse;
import com.gsmv.security.SecurityUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audits")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public ApiResponse<PageResponse<AuditLogView>> list(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) Integer success,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(auditService.list(module, success, page, size));
    }

    @GetMapping("/me")
    public ApiResponse<PageResponse<AuditLogView>> myActivities(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) Integer success,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Long userId = SecurityUtils.requireCurrentUser().userId();
        return ApiResponse.success(auditService.listByUser(userId, module, success, page, size));
    }
}
