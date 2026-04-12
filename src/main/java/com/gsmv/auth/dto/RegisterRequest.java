package com.gsmv.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
        @NotBlank(message = "用户名不能为空") String username,
        @NotBlank(message = "密码不能为空") String password,
        @NotBlank(message = "显示名称不能为空") String displayName,
        String email,
        String phone,
        @NotBlank(message = "申请角色不能为空") String roleCode
) {
}
