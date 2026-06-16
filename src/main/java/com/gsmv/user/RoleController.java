package com.gsmv.user;

import com.gsmv.common.ApiResponse;
import com.gsmv.user.dto.PermissionOption;
import com.gsmv.user.dto.RolePermissionView;
import com.gsmv.user.dto.RoleSaveRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/roles")
@PreAuthorize("hasAuthority('USER_ADMIN')")
public class RoleController {

    private final RoleManagementService roleManagementService;

    public RoleController(RoleManagementService roleManagementService) {
        this.roleManagementService = roleManagementService;
    }

    @GetMapping
    public ApiResponse<List<RolePermissionView>> listRoles() {
        return ApiResponse.success(roleManagementService.listRoles());
    }

    @GetMapping("/permissions")
    public ApiResponse<List<PermissionOption>> listPermissions() {
        return ApiResponse.success(roleManagementService.listPermissions());
    }

    @PostMapping
    public ApiResponse<RolePermissionView> createRole(@Valid @RequestBody RoleSaveRequest request) {
        return ApiResponse.success(roleManagementService.createRole(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<RolePermissionView> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody RoleSaveRequest request
    ) {
        return ApiResponse.success(roleManagementService.updateRole(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteRole(@PathVariable Long id) {
        roleManagementService.deleteRole(id);
        return ApiResponse.success();
    }
}
