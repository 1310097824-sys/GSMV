package com.gsmv.user;

import com.gsmv.common.ApiResponse;
import com.gsmv.common.PageResponse;
import com.gsmv.media.MediaFileService;
import com.gsmv.media.model.MediaFile;
import com.gsmv.user.dto.RoleOption;
import com.gsmv.user.dto.UserApprovalRequest;
import com.gsmv.user.dto.UserCreateRequest;
import com.gsmv.user.dto.UserProfileUpdateRequest;
import com.gsmv.user.dto.UserProfileView;
import com.gsmv.user.dto.UserUpdateRequest;
import com.gsmv.user.dto.UserView;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final MediaFileService mediaFileService;

    public UserController(UserService userService, MediaFileService mediaFileService) {
        this.userService = userService;
        this.mediaFileService = mediaFileService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_ADMIN')")
    public ApiResponse<PageResponse<UserView>> listUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String approvalStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(userService.listUsers(keyword, status, approvalStatus, page, size));
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('USER_ADMIN')")
    public ApiResponse<List<RoleOption>> listRoles() {
        return ApiResponse.success(userService.listRoles());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_ADMIN')")
    public ApiResponse<UserView> createUser(@Valid @RequestBody UserCreateRequest request) {
        return ApiResponse.success(userService.createUser(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_ADMIN')")
    public ApiResponse<UserView> updateUser(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        return ApiResponse.success(userService.updateUser(id, request));
    }

    @PostMapping("/{id}/approval")
    @PreAuthorize("hasAuthority('USER_ADMIN')")
    public ApiResponse<UserView> reviewUser(@PathVariable Long id, @Valid @RequestBody UserApprovalRequest request) {
        return ApiResponse.success(userService.reviewUser(id, request));
    }

    @GetMapping("/profile")
    public ApiResponse<UserProfileView> currentProfile() {
        return ApiResponse.success(userService.getCurrentProfile());
    }

    @PutMapping("/profile")
    public ApiResponse<UserProfileView> updateCurrentProfile(@Valid @RequestBody UserProfileUpdateRequest request) {
        return ApiResponse.success(userService.updateCurrentProfile(request));
    }

    @PostMapping(value = "/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UserProfileView> uploadAvatar(@RequestPart("file") MultipartFile file) {
        return ApiResponse.success(userService.uploadCurrentAvatar(file));
    }

    @GetMapping("/avatar/{id}")
    public ResponseEntity<byte[]> avatar(@PathVariable Long id) {
        MediaFile mediaFile = userService.getAvatarMedia(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mediaFile.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + mediaFile.getStoredFilename() + "\"")
                .cacheControl(CacheControl.noCache())
                .body(mediaFileService.readBytes(mediaFile));
    }
}
