package com.gsmv.observation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ObservationSpeciesInput(
        @NotNull(message = "物种不能为空") Long speciesId,
        @Min(value = 1, message = "估算数量必须大于 0") Integer countEstimated,
        String behavior,
        String comment
) {
}
