package com.gsmv.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RoleSaveRequest(
        @NotBlank(message = "角色编码不能为空")
        @Size(max = 64, message = "角色编码长度不能超过64个字符")
        String code,

        @NotBlank(message = "角色名称不能为空")
        @Size(max = 64, message = "角色名称长度不能超过64个字符")
        String name,

        @Size(max = 255, message = "角色说明长度不能超过255个字符")
        String description,

        @NotEmpty(message = "请至少选择一个权限")
        List<@NotBlank(message = "权限编码不能为空") String> permissionCodes
) {
}
