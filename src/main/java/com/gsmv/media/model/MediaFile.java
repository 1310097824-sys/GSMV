package com.gsmv.media.model;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class MediaFile {

    private Long id;
    private String businessType;
    private Long businessId;
    private String originalFilename;
    private String storedFilename;
    private String contentType;
    private Long sizeBytes;
    private String storagePath;
    private String sha256;
    private Long uploadedBy;
    private LocalDateTime uploadedAt;
}
