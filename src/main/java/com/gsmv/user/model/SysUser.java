package com.gsmv.user.model;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class SysUser {

    private Long id;
    private String username;
    private String passwordHash;
    private String displayName;
    private String email;
    private String phone;
    private String bio;
    private Integer status;
    private String approvalStatus;
    private String approvalRemark;
    private Long reviewedBy;
    private Long avatarMediaId;
    private LocalDateTime reviewedAt;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
