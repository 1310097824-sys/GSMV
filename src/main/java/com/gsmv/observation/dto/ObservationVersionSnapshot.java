package com.gsmv.observation.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ObservationVersionSnapshot(
        Long ecosystemId,
        String ecosystemName,
        Long observerUserId,
        String observerName,
        LocalDateTime observedAt,
        BigDecimal locationLat,
        BigDecimal locationLng,
        String locationName,
        String envJson,
        String note,
        List<ObservationVersionSpeciesSnapshot> speciesItems
) {

    public static ObservationVersionSnapshot fromDetail(ObservationDetailView detail) {
        return new ObservationVersionSnapshot(
                detail.ecosystemId(),
                detail.ecosystemName(),
                detail.observerUserId(),
                detail.observerName(),
                detail.observedAt(),
                detail.locationLat(),
                detail.locationLng(),
                detail.locationName(),
                detail.envJson(),
                detail.note(),
                detail.speciesItems().stream()
                        .map(ObservationVersionSpeciesSnapshot::fromDetailItem)
                        .toList()
        );
    }

    public ObservationSaveRequest toSaveRequest() {
        return new ObservationSaveRequest(
                ecosystemId,
                observedAt,
                locationLat,
                locationLng,
                locationName,
                envJson,
                note,
                speciesItems == null ? List.of() : speciesItems.stream()
                        .map(ObservationVersionSpeciesSnapshot::toInput)
                        .toList()
        );
    }
}
