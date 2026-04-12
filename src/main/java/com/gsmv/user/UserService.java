package com.gsmv.user;

import com.gsmv.audit.service.AuditService;
import com.gsmv.common.ErrorCode;
import com.gsmv.common.PageResponse;
import com.gsmv.common.exception.BusinessException;
import com.gsmv.common.exception.NotFoundException;
import com.gsmv.media.MediaFileService;
import com.gsmv.media.model.MediaFile;
import com.gsmv.security.CurrentUser;
import com.gsmv.security.SecurityUtils;
import com.gsmv.user.dto.RoleOption;
import com.gsmv.user.dto.UserApprovalRequest;
import com.gsmv.user.dto.UserCreateRequest;
import com.gsmv.user.dto.UserProfileUpdateRequest;
import com.gsmv.user.dto.UserProfileView;
import com.gsmv.user.dto.UserUpdateRequest;
import com.gsmv.user.dto.UserView;
import com.gsmv.user.mapper.RoleMapper;
import com.gsmv.user.mapper.UserMapper;
import com.gsmv.user.model.SysRole;
import com.gsmv.user.model.SysUser;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserService {

    private static final String APPROVAL_PENDING = "PENDING";
    private static final String APPROVAL_APPROVED = "APPROVED";
    private static final String APPROVAL_REJECTED = "REJECTED";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final MediaFileService mediaFileService;

    public UserService(
            UserMapper userMapper,
            RoleMapper roleMapper,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            MediaFileService mediaFileService
    ) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.mediaFileService = mediaFileService;
    }

    public PageResponse<UserView> listUsers(String keyword, Integer status, String approvalStatus, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = (safePage - 1) * safeSize;
        String normalizedApprovalStatus = approvalStatus == null ? null : approvalStatus.trim().toUpperCase(Locale.ROOT);
        List<UserView> items = userMapper.findPage(keyword, status, normalizedApprovalStatus, safeSize, offset).stream()
                .map(this::toView)
                .toList();
        long total = userMapper.count(keyword, status, normalizedApprovalStatus);
        return new PageResponse<>(items, total, safePage, safeSize);
    }

    public List<RoleOption> listRoles() {
        return roleMapper.findAll().stream()
                .map(this::toRoleOption)
                .toList();
    }

    @Transactional
    public UserView createUser(UserCreateRequest request) {
        CurrentUser currentUser = SecurityUtils.requireCurrentUser();
        if (userMapper.findByUsername(request.username()) != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已存在", HttpStatus.CONFLICT);
        }

        SysUser user = new SysUser();
        user.setUsername(request.username().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName().trim());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setBio(null);
        user.setStatus(request.status());
        user.setApprovalStatus(APPROVAL_APPROVED);
        user.setApprovalRemark("管理员创建");
        user.setReviewedBy(currentUser.userId());
        user.setReviewedAt(LocalDateTime.now());
        userMapper.insert(user);
        roleMapper.insertUserRoles(user.getId(), request.roleIds());
        auditService.record(currentUser.userId(), "USER", "CREATE", "SYS_USER", user.getId(), true,
                "{\"username\":\"" + request.username() + "\"}");
        return toView(userMapper.findById(user.getId()));
    }

    @Transactional
    public UserView updateUser(Long id, UserUpdateRequest request) {
        SysUser existing = requireUser(id);
        existing.setDisplayName(request.displayName());
        existing.setEmail(request.email());
        existing.setPhone(request.phone());
        existing.setStatus(request.status());
        existing.setBio(existing.getBio());
        userMapper.updateByAdmin(existing);
        if (request.password() != null && !request.password().isBlank()) {
            userMapper.updatePassword(id, passwordEncoder.encode(request.password()));
        }
        roleMapper.deleteUserRoles(id);
        roleMapper.insertUserRoles(id, request.roleIds());
        auditService.record(SecurityUtils.requireCurrentUser().userId(), "USER", "UPDATE", "SYS_USER", id, true,
                "{\"username\":\"" + existing.getUsername() + "\"}");
        return toView(userMapper.findById(id));
    }

    @Transactional
    public UserView reviewUser(Long id, UserApprovalRequest request) {
        SysUser existing = requireUser(id);
        String approvalStatus = request.approvalStatus().trim().toUpperCase(Locale.ROOT);
        if (!List.of(APPROVAL_APPROVED, APPROVAL_REJECTED).contains(approvalStatus)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "审核结果只能为 APPROVED 或 REJECTED", HttpStatus.BAD_REQUEST);
        }

        int nextStatus = APPROVAL_APPROVED.equals(approvalStatus) ? 1 : 0;
        CurrentUser currentUser = SecurityUtils.requireCurrentUser();
        userMapper.updateApproval(id, approvalStatus, request.approvalRemark(), currentUser.userId(), nextStatus);
        auditService.record(currentUser.userId(), "USER", "REVIEW", "SYS_USER", id, true,
                "{\"approvalStatus\":\"" + approvalStatus + "\"}");
        return toView(userMapper.findById(id));
    }

    public UserProfileView getCurrentProfile() {
        return toProfileView(requireCurrentSysUser());
    }

    @Transactional
    public UserProfileView updateCurrentProfile(UserProfileUpdateRequest request) {
        CurrentUser currentUser = SecurityUtils.requireCurrentUser();
        SysUser existing = requireUser(currentUser.userId());
        existing.setDisplayName(request.displayName().trim());
        existing.setEmail(request.email());
        existing.setPhone(request.phone());
        existing.setBio(request.bio());
        userMapper.updateProfile(existing);
        auditService.record(currentUser.userId(), "USER", "PROFILE_UPDATE", "SYS_USER", existing.getId(), true,
                "{\"username\":\"" + existing.getUsername() + "\"}");
        return toProfileView(userMapper.findById(existing.getId()));
    }

    @Transactional
    public UserProfileView uploadCurrentAvatar(MultipartFile file) {
        CurrentUser currentUser = SecurityUtils.requireCurrentUser();
        if (file.isEmpty() || file.getContentType() == null || !file.getContentType().startsWith("image/")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请上传图片格式的头像文件", HttpStatus.BAD_REQUEST);
        }
        MediaFile mediaFile = mediaFileService.store("USER_AVATAR", currentUser.userId(), file, currentUser.userId());
        userMapper.updateAvatarMediaId(currentUser.userId(), mediaFile.getId());
        auditService.record(currentUser.userId(), "USER", "AVATAR_UPLOAD", "SYS_USER", currentUser.userId(), true,
                "{\"mediaId\":" + mediaFile.getId() + "}");
        return toProfileView(userMapper.findById(currentUser.userId()));
    }

    public MediaFile getAvatarMedia(Long userId) {
        SysUser user = requireUser(userId);
        if (user.getAvatarMediaId() == null) {
            throw new NotFoundException("用户头像不存在");
        }
        return mediaFileService.getRequired(user.getAvatarMediaId());
    }

    private SysUser requireCurrentSysUser() {
        return requireUser(SecurityUtils.requireCurrentUser().userId());
    }

    private SysUser requireUser(Long id) {
        SysUser user = userMapper.findById(id);
        if (user == null) {
            throw new NotFoundException("用户不存在");
        }
        return user;
    }

    private RoleOption toRoleOption(SysRole role) {
        return new RoleOption(role.getId(), role.getCode(), role.getName(), role.getDescription());
    }

    private List<RoleOption> findRoleOptions(Long userId) {
        return roleMapper.findRolesByUserId(userId).stream()
                .map(this::toRoleOption)
                .toList();
    }

    private UserView toView(SysUser user) {
        List<RoleOption> roles = findRoleOptions(user.getId());
        return new UserView(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getPhone(),
                user.getBio(),
                UserAvatarUrls.resolve(user),
                user.getStatus(),
                user.getApprovalStatus(),
                user.getApprovalRemark(),
                user.getReviewedAt(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                roles
        );
    }

    private UserProfileView toProfileView(SysUser user) {
        return new UserProfileView(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getPhone(),
                user.getBio(),
                UserAvatarUrls.resolve(user),
                user.getStatus(),
                user.getApprovalStatus(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                findRoleOptions(user.getId())
        );
    }
}
