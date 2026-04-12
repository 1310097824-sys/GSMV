package com.gsmv.auth;

import com.gsmv.audit.service.AuditService;
import com.gsmv.auth.dto.LoginRequest;
import com.gsmv.auth.dto.LoginResponse;
import com.gsmv.auth.dto.RegisterRequest;
import com.gsmv.common.ErrorCode;
import com.gsmv.common.exception.BusinessException;
import com.gsmv.security.JwtService;
import com.gsmv.user.UserAvatarUrls;
import com.gsmv.user.mapper.PermissionMapper;
import com.gsmv.user.mapper.RoleMapper;
import com.gsmv.user.mapper.UserMapper;
import com.gsmv.user.model.SysRole;
import com.gsmv.user.model.SysUser;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String APPROVAL_PENDING = "PENDING";
    private static final String APPROVAL_APPROVED = "APPROVED";
    private static final String APPROVAL_REJECTED = "REJECTED";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;

    public AuthService(
            UserMapper userMapper,
            RoleMapper roleMapper,
            PermissionMapper permissionMapper,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuditService auditService
    ) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    @Transactional
    public void register(RegisterRequest request) {
        if (userMapper.findByUsername(request.username()) != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已存在", HttpStatus.CONFLICT);
        }

        String requestedRoleCode = request.roleCode().trim().toUpperCase(Locale.ROOT);
        if (!List.of("STUDENT", "PUBLIC").contains(requestedRoleCode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持学生或公众角色申请注册", HttpStatus.BAD_REQUEST);
        }

        SysRole role = roleMapper.findByCode(requestedRoleCode);
        if (role == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "申请角色不存在", HttpStatus.NOT_FOUND);
        }

        SysUser user = new SysUser();
        user.setUsername(request.username().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName().trim());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setBio(null);
        user.setStatus(0);
        user.setApprovalStatus(APPROVAL_PENDING);
        user.setApprovalRemark("待管理员审核");
        userMapper.insert(user);
        roleMapper.insertUserRoles(user.getId(), List.of(role.getId()));
        auditService.record(user.getId(), "AUTH", "REGISTER", "SYS_USER", user.getId(), true,
                "{\"username\":\"" + user.getUsername() + "\",\"roleCode\":\"" + requestedRoleCode + "\"}");
    }

    public LoginResponse login(LoginRequest request) {
        SysUser user = userMapper.findByUsername(request.username());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditService.record(null, "AUTH", "LOGIN", "SYS_USER", null, false,
                    "{\"reason\":\"invalid-credentials\",\"username\":\"" + request.username() + "\"}");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误", HttpStatus.UNAUTHORIZED);
        }
        if (APPROVAL_PENDING.equalsIgnoreCase(user.getApprovalStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已提交申请，等待管理员审核", HttpStatus.FORBIDDEN);
        }
        if (APPROVAL_REJECTED.equalsIgnoreCase(user.getApprovalStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号申请未通过审核", HttpStatus.FORBIDDEN);
        }
        if (user.getStatus() != 1 || !APPROVAL_APPROVED.equalsIgnoreCase(user.getApprovalStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前用户已被禁用或未审核通过", HttpStatus.FORBIDDEN);
        }

        List<String> roles = roleMapper.findRoleCodesByUserId(user.getId());
        List<String> authorities = permissionMapper.findPermissionCodesByUserId(user.getId());
        String token = jwtService.generateToken(user, roles, authorities);
        userMapper.updateLastLoginAt(user.getId());
        auditService.record(user.getId(), "AUTH", "LOGIN", "SYS_USER", user.getId(), true,
                "{\"username\":\"" + user.getUsername() + "\"}");
        SysUser latest = userMapper.findById(user.getId());
        return new LoginResponse(
                token,
                jwtService.getTtlSeconds(),
                new LoginResponse.UserProfile(
                        latest.getId(),
                        latest.getUsername(),
                        latest.getDisplayName(),
                        UserAvatarUrls.resolve(latest),
                        roles,
                        authorities
                )
        );
    }
}
