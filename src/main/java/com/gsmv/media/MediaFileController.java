package com.gsmv.media;

import com.gsmv.common.ApiResponse;
import com.gsmv.media.model.MediaFile;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/media")
public class MediaFileController {

    private final MediaFileService mediaFileService;

    public MediaFileController(MediaFileService mediaFileService) {
        this.mediaFileService = mediaFileService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('MEDIA_READ')")
    public ApiResponse<List<MediaFile>> list(
            @RequestParam String businessType,
            @RequestParam Long businessId
    ) {
        return ApiResponse.success(mediaFileService.list(businessType, businessId));
    }

    @PostMapping("/upload")
    @PreAuthorize("hasAuthority('MEDIA_WRITE')")
    public ApiResponse<MediaFile> upload(
            @RequestParam String businessType,
            @RequestParam Long businessId,
            @RequestParam("file") MultipartFile file
    ) {
        return ApiResponse.success(mediaFileService.upload(businessType, businessId, file));
    }
}
