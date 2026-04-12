package com.gsmv.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UserCreateRequest(
        @NotBlank(message = "用户名不能为空") String username,
        @NotBlank(message = "密码不能为空") String password,
        @NotBlank(message = "显示名称不能为空") String displayName,
        String email,
        String phone,
        @NotNull(message = "状态不能为空") Integer status,
        @NotEmpty(message = "至少分配一个角色") List<Long> roleIds
) {
}
