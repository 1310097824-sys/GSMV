package com.gsmv.species.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record SpeciesSaveRequest(
        @NotBlank(message = "门不能为空") String phylumName,
        @NotBlank(message = "纲不能为空") String className,
        @NotBlank(message = "目不能为空") String orderName,
        @NotBlank(message = "科不能为空") String familyName,
        @NotBlank(message = "属不能为空") String genusName,
        @NotBlank(message = "学名不能为空") String scientificName,
        @NotBlank(message = "中文名不能为空") String chineseName,
        String protectionLevel,
        String iucnStatus,
        String description,
        String morphology,
        String habit,
        String habitat,
        String distribution,
        BigDecimal distributionLat,
        BigDecimal distributionLng,
        String geoRangeText,
        String videoUrl,
        String referenceText,
        @NotNull(message = "状态不能为空") Integer status
) {
}
