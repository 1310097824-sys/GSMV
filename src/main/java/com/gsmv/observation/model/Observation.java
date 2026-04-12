package com.gsmv.observation.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class Observation {

    private Long id;
    private Long ecosystemId;
    private Long observerUserId;
    private LocalDateTime observedAt;
    private BigDecimal locationLat;
    private BigDecimal locationLng;
    private String locationName;
    private String envJson;
    private String note;
    private LocalDateTime createdAt;
}
