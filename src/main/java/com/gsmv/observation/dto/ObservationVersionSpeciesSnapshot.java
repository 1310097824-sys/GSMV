package com.gsmv.observation.dto;

public record ObservationVersionSpeciesSnapshot(
        Long speciesId,
        String scientificName,
        String chineseName,
        Integer countEstimated,
        String behavior,
        String comment
) {

    public static ObservationVersionSpeciesSnapshot fromDetailItem(ObservationSpeciesView item) {
        return new ObservationVersionSpeciesSnapshot(
                item.speciesId(),
                item.scientificName(),
                item.chineseName(),
                item.countEstimated(),
                item.behavior(),
                item.comment()
        );
    }

    public ObservationSpeciesInput toInput() {
        return new ObservationSpeciesInput(speciesId, countEstimated, behavior, comment);
    }
}
