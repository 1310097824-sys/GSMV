package com.gsmv.user.model;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class SysPermission {

    private Long id;
    private String code;
    private String name;
    private String description;
    private LocalDateTime createdAt;
}
