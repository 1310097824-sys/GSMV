package com.gsmv.user;

import com.gsmv.audit.service.AuditService;
import com.gsmv.common.ErrorCode;
import com.gsmv.common.exception.BusinessException;
import com.gsmv.common.exception.NotFoundException;
import com.gsmv.security.SecurityUtils;
import com.gsmv.user.dto.PermissionOption;
import com.gsmv.user.dto.RolePermissionView;
import com.gsmv.user.dto.RoleSaveRequest;
import com.gsmv.user.mapper.PermissionMapper;
import com.gsmv.user.mapper.RoleMapper;
import com.gsmv.user.model.SysPermission;
import com.gsmv.user.model.SysRole;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleManagementService {

    private static final Pattern ROLE_CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");
    private static final String ADMIN_ROLE_CODE = "ADMIN";
    private static final String USER_ADMIN_PERMISSION = "USER_ADMIN";
    private static final Set<String> BUILT_IN_ROLE_CODES = Set.of(
            ADMIN_ROLE_CODE,
            "RESEARCHER",
            "VIEWER",
            "STUDENT",
            "PUBLIC"
    );

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final AuditService auditService;

    public RoleManagementService(RoleMapper roleMapper, PermissionMapper permissionMapper, AuditService auditService) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.auditService = auditService;
    }

    public List<PermissionOption> listPermissions() {
        return permissionMapper.findAll().stream()
                .map(this::toPermissionOption)
                .toList();
    }

    public List<RolePermissionView> listRoles() {
        return roleMapper.findAll().stream()
                .map(this::toRolePermissionView)
                .toList();
    }

    @Transactional
    public RolePermissionView createRole(RoleSaveRequest request) {
        String code = normalizeRoleCode(request.code());
        validateRoleCode(code);
        if (roleMapper.findByCode(code) != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "角色编码已存在", HttpStatus.CONFLICT);
        }

        SysRole role = new SysRole();
        role.setCode(code);
        role.setName(request.name().trim());
        role.setDescription(normalizeOptionalText(request.description()));
        roleMapper.insertRole(role);
        replacePermissions(role.getId(), code, request.permissionCodes());

        auditService.record(SecurityUtils.requireCurrentUser().userId(), "ROLE", "CREATE", "SYS_ROLE", role.getId(), true,
                "{\"code\":\"" + code + "\"}");
        return toRolePermissionView(roleMapper.findById(role.getId()));
    }

    @Transactional
    public RolePermissionView updateRole(Long id, RoleSaveRequest request) {
        SysRole existing = requireRole(id);
        String code = normalizeRoleCode(request.code());
        validateRoleCode(code);

        if (isBuiltInRole(existing.getCode()) && !existing.getCode().equals(code)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "内置角色编码不能修改", HttpStatus.BAD_REQUEST);
        }
        if (roleMapper.countByCodeExceptId(code, id) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "角色编码已存在", HttpStatus.CONFLICT);
        }

        existing.setCode(code);
        existing.setName(request.name().trim());
        existing.setDescription(normalizeOptionalText(request.description()));
        roleMapper.updateRole(existing);
        replacePermissions(id, code, request.permissionCodes());

        auditService.record(SecurityUtils.requireCurrentUser().userId(), "ROLE", "UPDATE", "SYS_ROLE", id, true,
                "{\"code\":\"" + code + "\"}");
        return toRolePermissionView(roleMapper.findById(id));
    }

    @Transactional
    public void deleteRole(Long id) {
        SysRole existing = requireRole(id);
        if (isBuiltInRole(existing.getCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "内置角色不能删除", HttpStatus.BAD_REQUEST);
        }
        if (roleMapper.countUsersByRoleId(id) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该角色仍有用户使用，不能删除", HttpStatus.CONFLICT);
        }

        roleMapper.deleteRolePermissions(id);
        roleMapper.deleteRole(id);
        auditService.record(SecurityUtils.requireCurrentUser().userId(), "ROLE", "DELETE", "SYS_ROLE", id, true,
                "{\"code\":\"" + existing.getCode() + "\"}");
    }

    private void replacePermissions(Long roleId, String roleCode, List<String> permissionCodes) {
        List<String> normalizedCodes = normalizePermissionCodes(permissionCodes);
        if (normalizedCodes.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请至少选择一个权限", HttpStatus.BAD_REQUEST);
        }
        if (ADMIN_ROLE_CODE.equals(roleCode) && !normalizedCodes.contains(USER_ADMIN_PERMISSION)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "ADMIN 角色必须保留用户权限管理", HttpStatus.BAD_REQUEST);
        }
        if (permissionMapper.countByCodes(normalizedCodes) != normalizedCodes.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "存在无效权限编码", HttpStatus.BAD_REQUEST);
        }

        roleMapper.deleteRolePermissions(roleId);
        roleMapper.insertRolePermissionsByCodes(roleId, normalizedCodes);
    }

    private RolePermissionView toRolePermissionView(SysRole role) {
        return new RolePermissionView(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                permissionMapper.findPermissionCodesByRoleId(role.getId()),
                roleMapper.countUsersByRoleId(role.getId()),
                isBuiltInRole(role.getCode())
        );
    }

    private PermissionOption toPermissionOption(SysPermission permission) {
        return new PermissionOption(
                permission.getId(),
                permission.getCode(),
                permission.getName(),
                permission.getDescription()
        );
    }

    private SysRole requireRole(Long id) {
        SysRole role = roleMapper.findById(id);
        if (role == null) {
            throw new NotFoundException("角色不存在");
        }
        return role;
    }

    private boolean isBuiltInRole(String code) {
        return BUILT_IN_ROLE_CODES.contains(code);
    }

    private String normalizeRoleCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private void validateRoleCode(String code) {
        if (!ROLE_CODE_PATTERN.matcher(code).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "角色编码需以大写字母开头，仅包含大写字母、数字或下划线", HttpStatus.BAD_REQUEST);
        }
    }

    private List<String> normalizePermissionCodes(List<String> permissionCodes) {
        if (permissionCodes == null) {
            return List.of();
        }
        return permissionCodes.stream()
                .map(code -> code == null ? "" : code.trim().toUpperCase(Locale.ROOT))
                .filter(code -> !code.isBlank())
                .distinct()
                .toList();
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
