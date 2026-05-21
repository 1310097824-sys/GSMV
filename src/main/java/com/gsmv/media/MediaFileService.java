package com.gsmv.media;

import com.gsmv.audit.service.AuditService;
import com.gsmv.common.exception.NotFoundException;
import com.gsmv.config.StorageProperties;
import com.gsmv.media.mapper.MediaFileMapper;
import com.gsmv.media.model.MediaFile;
import com.gsmv.security.CurrentUser;
import com.gsmv.security.SecurityUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaFileService {

    private final MediaFileMapper mediaFileMapper;
    private final StorageProperties storageProperties;
    private final AuditService auditService;

    public MediaFileService(MediaFileMapper mediaFileMapper, StorageProperties storageProperties, AuditService auditService) {
        this.mediaFileMapper = mediaFileMapper;
        this.storageProperties = storageProperties;
        this.auditService = auditService;
    }

    @Transactional
    public MediaFile upload(String businessType, Long businessId, MultipartFile file) {
        CurrentUser currentUser = SecurityUtils.requireCurrentUser();
        MediaFile mediaFile = store(businessType, businessId, file, currentUser.userId());
        auditService.record(currentUser.userId(), "MEDIA", "UPLOAD", businessType, businessId, true,
                "{\"file\":\"" + mediaFile.getOriginalFilename() + "\"}");
        return mediaFile;
    }

    @Transactional
    public MediaFile store(String businessType, Long businessId, MultipartFile file, Long uploadedBy) {
        try {
            byte[] bytes = file.getBytes();
            String originalFilename = file.getOriginalFilename() == null ? "unknown.bin" : file.getOriginalFilename();
            String extension = "";
            int lastDot = originalFilename.lastIndexOf('.');
            if (lastDot >= 0) {
                extension = originalFilename.substring(lastDot);
            }
            String folder = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
            Path root = Paths.get(storageProperties.uploadDir());
            Path targetDir = root.resolve(businessType.toLowerCase()).resolve(folder);
            if (businessId != null) {
                targetDir = targetDir.resolve(String.valueOf(businessId));
            }
            Files.createDirectories(targetDir);

            String storedFilename = UUID.randomUUID().toString().replace("-", "") + extension;
            Path targetPath = targetDir.resolve(storedFilename);
            Files.write(targetPath, bytes);

            MediaFile mediaFile = new MediaFile();
            mediaFile.setBusinessType(businessType);
            mediaFile.setBusinessId(businessId);
            mediaFile.setOriginalFilename(originalFilename);
            mediaFile.setStoredFilename(storedFilename);
            mediaFile.setContentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
            mediaFile.setSizeBytes(file.getSize());
            mediaFile.setStoragePath(targetPath.toString());
            mediaFile.setSha256(sha256(bytes));
            mediaFile.setUploadedBy(uploadedBy);
            mediaFileMapper.insert(mediaFile);
            return mediaFile;
        } catch (IOException ex) {
            throw new IllegalStateException("附件保存失败", ex);
        }
    }

    @Transactional
    public MediaFile storeBytes(
            String businessType,
            Long businessId,
            String originalFilename,
            String contentType,
            byte[] bytes,
            Long uploadedBy
    ) {
        try {
            byte[] safeBytes = bytes == null ? new byte[0] : bytes;
            String safeFilename = originalFilename == null ? "unknown.bin" : originalFilename;
            String extension = "";
            int lastDot = safeFilename.lastIndexOf('.');
            if (lastDot >= 0) {
                extension = safeFilename.substring(lastDot);
            }
            String folder = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
            Path root = Paths.get(storageProperties.uploadDir());
            Path targetDir = root.resolve(businessType.toLowerCase()).resolve(folder);
            if (businessId != null) {
                targetDir = targetDir.resolve(String.valueOf(businessId));
            }
            Files.createDirectories(targetDir);

            String storedFilename = UUID.randomUUID().toString().replace("-", "") + extension;
            Path targetPath = targetDir.resolve(storedFilename);
            Files.write(targetPath, safeBytes);

            MediaFile mediaFile = new MediaFile();
            mediaFile.setBusinessType(businessType);
            mediaFile.setBusinessId(businessId);
            mediaFile.setOriginalFilename(safeFilename);
            mediaFile.setStoredFilename(storedFilename);
            mediaFile.setContentType(contentType == null ? "application/octet-stream" : contentType);
            mediaFile.setSizeBytes((long) safeBytes.length);
            mediaFile.setStoragePath(targetPath.toString());
            mediaFile.setSha256(sha256(safeBytes));
            mediaFile.setUploadedBy(uploadedBy);
            mediaFileMapper.insert(mediaFile);
            return mediaFile;
        } catch (IOException ex) {
            throw new IllegalStateException("File save failed", ex);
        }
    }

    public MediaFile getRequired(Long id) {
        MediaFile mediaFile = mediaFileMapper.findById(id);
        if (mediaFile == null) {
            throw new NotFoundException("附件不存在");
        }
        return mediaFile;
    }

    public byte[] readBytes(MediaFile mediaFile) {
        try {
            return Files.readAllBytes(Path.of(mediaFile.getStoragePath()));
        } catch (IOException ex) {
            throw new IllegalStateException("附件读取失败", ex);
        }
    }

    public List<MediaFile> list(String businessType, Long businessId) {
        return mediaFileMapper.findByBusiness(businessType, businessId);
    }

    @Transactional
    public void deleteByBusiness(String businessType, Long businessId) {
        List<MediaFile> files = mediaFileMapper.findByBusiness(businessType, businessId);
        for (MediaFile mediaFile : files) {
            try {
                Files.deleteIfExists(Path.of(mediaFile.getStoragePath()));
            } catch (IOException ignored) {
                // Keep metadata cleanup resilient even if the physical file is already missing.
            }
        }
        mediaFileMapper.deleteByBusiness(businessType, businessId);
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }
}
