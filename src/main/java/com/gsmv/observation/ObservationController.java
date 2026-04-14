package com.gsmv.observation;

import com.gsmv.common.ApiResponse;
import com.gsmv.common.PageResponse;
import com.gsmv.observation.dto.ObservationDetailView;
import com.gsmv.observation.dto.ObservationSaveRequest;
import com.gsmv.observation.dto.ObservationView;
import com.gsmv.versioning.dto.EntityVersionView;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/observations")
public class ObservationController {

    private final ObservationService observationService;

    public ObservationController(ObservationService observationService) {
        this.observationService = observationService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('OBS_READ')")
    public ApiResponse<PageResponse<ObservationView>> list(
            @RequestParam(required = false) Long ecosystemId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime observedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime observedTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(observationService.list(ecosystemId, keyword, observedFrom, observedTo, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('OBS_READ')")
    public ApiResponse<ObservationDetailView> getDetail(@PathVariable Long id) {
        return ApiResponse.success(observationService.getDetail(id));
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize("hasAuthority('OBS_READ')")
    public ApiResponse<List<EntityVersionView>> listVersions(@PathVariable Long id) {
        return ApiResponse.success(observationService.listVersions(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('OBS_WRITE')")
    public ApiResponse<ObservationDetailView> create(@Valid @RequestBody ObservationSaveRequest request) {
        return ApiResponse.success(observationService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('OBS_WRITE')")
    public ApiResponse<ObservationDetailView> update(@PathVariable Long id, @Valid @RequestBody ObservationSaveRequest request) {
        return ApiResponse.success(observationService.update(id, request));
    }

    @PostMapping("/{id}/versions/{versionId}/rollback")
    @PreAuthorize("hasAuthority('OBS_WRITE')")
    public ApiResponse<ObservationDetailView> rollback(@PathVariable Long id, @PathVariable Long versionId) {
        return ApiResponse.success(observationService.rollback(id, versionId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('OBS_WRITE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        observationService.delete(id);
        return ApiResponse.success(null);
    }
}
