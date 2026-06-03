package com.gsmv.observation.dto;

public record ObservationSpeciesView(
        Long speciesId,
        String scientificName,
        String chineseName,
        Integer status,
        Integer countEstimated,
        String behavior,
        String comment
) {
}
