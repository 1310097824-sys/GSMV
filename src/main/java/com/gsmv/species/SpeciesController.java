package com.gsmv.species;

import com.gsmv.common.ApiResponse;
import com.gsmv.common.PageResponse;
import com.gsmv.media.MediaFileService;
import com.gsmv.media.model.MediaFile;
import com.gsmv.species.dto.SpeciesDetailView;
import com.gsmv.species.dto.SpeciesImageView;
import com.gsmv.species.dto.SpeciesSaveRequest;
import com.gsmv.species.dto.SpeciesView;
import com.gsmv.species.dto.TaxonOption;
import com.gsmv.versioning.dto.EntityVersionView;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/species")
public class SpeciesController {

    private final SpeciesService speciesService;
    private final MediaFileService mediaFileService;

    public SpeciesController(SpeciesService speciesService, MediaFileService mediaFileService) {
        this.speciesService = speciesService;
        this.mediaFileService = mediaFileService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SPECIES_READ')")
    public ApiResponse<PageResponse<SpeciesView>> listSpecies(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String protectionLevel,
            @RequestParam(required = false) String iucnStatus,
            @RequestParam(required = false) String distributionKeyword,
            @RequestParam(required = false) Long taxonId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(speciesService.listSpecies(
                keyword,
                status,
                protectionLevel,
                iucnStatus,
                distributionKeyword,
                taxonId,
                page,
                size
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SPECIES_READ')")
    public ApiResponse<SpeciesDetailView> getSpecies(@PathVariable Long id) {
        return ApiResponse.success(speciesService.getSpecies(id));
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize("hasAuthority('SPECIES_READ')")
    public ApiResponse<List<EntityVersionView>> listVersions(@PathVariable Long id) {
        return ApiResponse.success(speciesService.listVersions(id));
    }

    @GetMapping("/taxa")
    @PreAuthorize("hasAuthority('SPECIES_READ')")
    public ApiResponse<List<TaxonOption>> listTaxa() {
        return ApiResponse.success(speciesService.listTaxa());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SPECIES_WRITE')")
    public ApiResponse<SpeciesDetailView> createSpecies(@Valid @RequestBody SpeciesSaveRequest request) {
        return ApiResponse.success(speciesService.createSpecies(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SPECIES_WRITE')")
    public ApiResponse<SpeciesDetailView> updateSpecies(@PathVariable Long id, @Valid @RequestBody SpeciesSaveRequest request) {
        return ApiResponse.success(speciesService.updateSpecies(id, request));
    }

    @PostMapping("/{id}/versions/{versionId}/rollback")
    @PreAuthorize("hasAuthority('SPECIES_WRITE')")
    public ApiResponse<SpeciesDetailView> rollbackSpecies(@PathVariable Long id, @PathVariable Long versionId) {
        return ApiResponse.success(speciesService.rollbackSpecies(id, versionId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SPECIES_WRITE')")
    public ApiResponse<Void> deleteSpecies(@PathVariable Long id) {
        speciesService.deleteSpecies(id);
        return ApiResponse.success(null);
    }

    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('SPECIES_WRITE')")
    public ApiResponse<SpeciesImageView> uploadSpeciesImage(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(speciesService.uploadImage(id, file));
    }

    @GetMapping("/images/{mediaId}")
    public ResponseEntity<byte[]> image(@PathVariable Long mediaId) {
        MediaFile mediaFile = speciesService.getSpeciesImage(mediaId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mediaFile.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + mediaFile.getStoredFilename() + "\"")
                .cacheControl(CacheControl.noCache())
                .body(mediaFileService.readBytes(mediaFile));
    }
}
