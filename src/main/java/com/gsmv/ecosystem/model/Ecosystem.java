package com.gsmv.ecosystem.model;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class Ecosystem {

    private Long id;
    private String name;
    private String type;
    private String description;
    private LocalDateTime createdAt;
}
