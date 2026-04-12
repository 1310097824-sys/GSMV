package com.gsmv.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UserApprovalRequest(
        @NotBlank(message = "审核结果不能为空") String approvalStatus,
        String approvalRemark
) {
}
