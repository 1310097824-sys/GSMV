package com.gsmv.species.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class Species {

    private Long id;
    private Long taxonId;
    private String protectionLevel;
    private String iucnStatus;
    private String description;
    private String morphology;
    private String habit;
    private String habitat;
    private String distribution;
    private BigDecimal distributionLat;
    private BigDecimal distributionLng;
    private String geoRangeText;
    private String videoUrl;
    private String referenceText;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
