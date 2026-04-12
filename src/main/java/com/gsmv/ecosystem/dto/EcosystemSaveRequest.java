package com.gsmv.ecosystem.dto;

import jakarta.validation.constraints.NotBlank;

public record EcosystemSaveRequest(
        @NotBlank(message = "生态系统名称不能为空") String name,
        String type,
        String description
) {
}
