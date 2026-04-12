package com.gsmv.observation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ObservationSaveRequest(
        @NotNull(message = "生态系统不能为空") Long ecosystemId,
        @NotNull(message = "观测时间不能为空") LocalDateTime observedAt,
        @NotNull(message = "纬度不能为空") BigDecimal locationLat,
        @NotNull(message = "经度不能为空") BigDecimal locationLng,
        String locationName,
        String envJson,
        String note,
        @Valid List<ObservationSpeciesInput> speciesItems
) {
}
