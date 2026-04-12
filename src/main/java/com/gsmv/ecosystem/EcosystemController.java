package com.gsmv.ecosystem;

import com.gsmv.common.ApiResponse;
import com.gsmv.common.PageResponse;
import com.gsmv.ecosystem.dto.EcosystemSaveRequest;
import com.gsmv.ecosystem.model.Ecosystem;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/v1/ecosystems")
public class EcosystemController {

    private final EcosystemService ecosystemService;

    public EcosystemController(EcosystemService ecosystemService) {
        this.ecosystemService = ecosystemService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ECOSYSTEM_READ')")
    public ApiResponse<PageResponse<Ecosystem>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(ecosystemService.list(keyword, type, page, size));
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('ECOSYSTEM_READ')")
    public ApiResponse<List<Ecosystem>> listAll() {
        return ApiResponse.success(ecosystemService.listAll());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ECOSYSTEM_WRITE')")
    public ApiResponse<Ecosystem> create(@Valid @RequestBody EcosystemSaveRequest request) {
        return ApiResponse.success(ecosystemService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ECOSYSTEM_WRITE')")
    public ApiResponse<Ecosystem> update(@PathVariable Long id, @Valid @RequestBody EcosystemSaveRequest request) {
        return ApiResponse.success(ecosystemService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ECOSYSTEM_WRITE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        ecosystemService.delete(id);
        return ApiResponse.success(null);
    }
}
