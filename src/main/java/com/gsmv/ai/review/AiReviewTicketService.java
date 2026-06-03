package com.gsmv.ai.review;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsmv.ai.dto.SpeciesAiDtos;
import com.gsmv.ai.rag.RagKnowledgeService;
import com.gsmv.ai.rag.dto.RagDtos;
import com.gsmv.ai.review.dto.AiReviewTicketDtos;
import com.gsmv.ai.review.mapper.AiReviewTicketMapper;
import com.gsmv.ai.review.model.AiReviewTicket;
import com.gsmv.audit.service.AuditService;
import com.gsmv.common.ErrorCode;
import com.gsmv.common.PageResponse;
import com.gsmv.common.exception.BusinessException;
import com.gsmv.common.exception.NotFoundException;
import com.gsmv.media.MediaFileService;
import com.gsmv.media.model.MediaFile;
import com.gsmv.security.CurrentUser;
import com.gsmv.security.SecurityUtils;
import com.gsmv.species.SpeciesService;
import com.gsmv.species.dto.SpeciesDetailView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AiReviewTicketService {

    private static final String TICKET_STATUS_PENDING = "PENDING";
    private static final String TICKET_STATUS_RESOLVED = "RESOLVED";
    private static final String TICKET_STATUS_REJECTED = "REJECTED";
    private static final String AI_REVIEW_IMAGE_BUSINESS_TYPE = "AI_REVIEW_IMAGE";

    private final AiReviewTicketMapper ticketMapper;
    private final MediaFileService mediaFileService;
    private final SpeciesService speciesService;
    private final RagKnowledgeService ragKnowledgeService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public AiReviewTicketService(
            AiReviewTicketMapper ticketMapper,
            MediaFileService mediaFileService,
            SpeciesService speciesService,
            RagKnowledgeService ragKnowledgeService,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.ticketMapper = ticketMapper;
        this.mediaFileService = mediaFileService;
        this.speciesService = speciesService;
        this.ragKnowledgeService = ragKnowledgeService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AiReviewTicketDtos.ReviewTicketDetailView createTicket(
            AiReviewTicketDtos.CreateReviewTicketRequest request,
            MultipartFile file
    ) {
        CurrentUser currentUser = SecurityUtils.requireCurrentUser();
        validateImage(file);

        AiReviewTicket ticket = new AiReviewTicket();
        ticket.setSourceType("SPECIES_IDENTIFY");
        ticket.setStatus(TICKET_STATUS_PENDING);
        ticket.setSubmittedBy(currentUser.userId());
        ticket.setLikelyChineseName(normalizeNullable(request.likelyChineseName()));
        ticket.setLikelyScientificName(normalizeNullable(request.likelyScientificName()));
        ticket.setConfidence(BigDecimal.valueOf(boundedConfidence(request.confidence())).setScale(4, RoundingMode.HALF_UP));
        ticket.setNeedsHumanReview(request.needsHumanReview() ? 1 : 0);
        ticket.setReasoning(normalizeNullable(request.reasoning()));
        ticket.setCandidateJson(writeJson(defaultList(request.candidates())));
        ticket.setRelatedSpeciesJson(writeJson(defaultList(request.relatedSpeciesRecords())));
        ticket.setInitialRecognitionJson(writeJson(request));
        ticket.setRagEvidenceJson(writeJson(defaultList(request.ragEvidence())));
        ticket.setReviewEvidenceJson(writeJson(List.of()));
        ticket.setSubmitNote(normalizeNullable(request.submitNote()));
        ticketMapper.insert(ticket);

        MediaFile image = mediaFileService.store(AI_REVIEW_IMAGE_BUSINESS_TYPE, ticket.getId(), file, currentUser.userId());
        ticketMapper.updateImageMediaId(ticket.getId(), image.getId());
        ragKnowledgeService.syncAiReviewTicket(ticket.getId());

        auditService.record(
                currentUser.userId(),
                "AI",
                "CREATE_REVIEW_TICKET",
                "AI_REVIEW_TICKET",
                ticket.getId(),
                true,
                "{\"confidence\":" + ticket.getConfidence() + "}"
        );
        return getTicket(ticket.getId());
    }

    public PageResponse<AiReviewTicketDtos.ReviewTicketView> listTickets(String keyword, String status, int page, int size) {
        CurrentUser currentUser = SecurityUtils.requireCurrentUser();
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = (safePage - 1) * safeSize;
        Long submittedBy = canManageTickets(currentUser) ? null : currentUser.userId();
        String normalizedStatus = normalizeNullable(status == null ? null : status.toUpperCase(Locale.ROOT));

        List<AiReviewTicketDtos.ReviewTicketView> items = ticketMapper.findPage(
                        normalizeNullable(keyword),
                        normalizedStatus,
                        submittedBy,
                        safeSize,
                        offset
                ).stream()
                .map(this::toView)
                .toList();
        long total = ticketMapper.count(normalizeNullable(keyword), normalizedStatus, submittedBy);
        return new PageResponse<>(items, total, safePage, safeSize);
    }

    public AiReviewTicketDtos.ReviewTicketDetailView getTicket(Long id) {
        CurrentUser currentUser = SecurityUtils.requireCurrentUser();
        AiReviewTicket ticket = requireTicket(id);
        if (!canManageTickets(currentUser) && !Objects.equals(ticket.getSubmittedBy(), currentUser.userId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "你只能查看自己提交的复核工单", HttpStatus.FORBIDDEN);
        }
        return toDetail(ticket);
    }

    @Transactional
    public AiReviewTicketDtos.ReviewTicketDetailView startReview(Long id) {
        CurrentUser currentUser = SecurityUtils.requireCurrentUser();
        requireWriteAuthority(currentUser);
        AiReviewTicket ticket = requireTicket(id);
        if (TICKET_STATUS_RESOLVED.equals(ticket.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该工单已经完成复核", HttpStatus.CONFLICT);
        }
        ticketMapper.markInReview(id, currentUser.userId());
        ragKnowledgeService.syncAiReviewTicket(id);
        auditService.record(currentUser.userId(), "AI", "START_REVIEW_TICKET", "AI_REVIEW_TICKET", id, true, "{}");
        return getTicket(id);
    }

    @Transactional
    public AiReviewTicketDtos.ReviewTicketDetailView resolveTicket(
            Long id,
            AiReviewTicketDtos.ResolveReviewTicketRequest request
    ) {
        CurrentUser currentUser = SecurityUtils.requireCurrentUser();
        requireWriteAuthority(currentUser);
        AiReviewTicket ticket = requireTicket(id);
        if (TICKET_STATUS_RESOLVED.equals(ticket.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该工单已经完成复核", HttpStatus.CONFLICT);
        }

        String resolutionCode = normalizeRequired(request.resolutionCode()).toUpperCase(Locale.ROOT);
        if (!List.of("CONFIRMED", "NOT_MATCH", "UNABLE_TO_CONFIRM").contains(resolutionCode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "复核结论只支持 CONFIRMED、NOT_MATCH、UNABLE_TO_CONFIRM", HttpStatus.BAD_REQUEST);
        }

        Long finalSpeciesId = request.finalSpeciesId();
        String finalChineseName = normalizeNullable(request.finalChineseName());
        String finalScientificName = normalizeNullable(request.finalScientificName());
        if (finalSpeciesId != null) {
            SpeciesDetailView species = speciesService.getSpecies(finalSpeciesId);
            if (!Integer.valueOf(1).equals(species.status())) {
                throw new BusinessException(
                        ErrorCode.BAD_REQUEST,
                        "归档物种不能作为复核结论关联物种，请先启用该物种或选择其他可用物种",
                        HttpStatus.BAD_REQUEST
                );
            }
            finalChineseName = firstNonBlank(species.chineseName(), finalChineseName);
            finalScientificName = firstNonBlank(species.scientificName(), finalScientificName);
        }
        if ("CONFIRMED".equals(resolutionCode) && !StringUtils.hasText(finalChineseName) && !StringUtils.hasText(finalScientificName)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "确认物种时请至少填写最终物种名称或选择已有物种档案", HttpStatus.BAD_REQUEST);
        }

        ticketMapper.resolve(
                id,
                resolutionCode,
                currentUser.userId(),
                finalSpeciesId,
                finalChineseName,
                finalScientificName,
                normalizeRequired(request.reviewNote()),
                LocalDateTime.now()
        );
        ragKnowledgeService.syncAiReviewTicket(id);

        auditService.record(
                currentUser.userId(),
                "AI",
                "RESOLVE_REVIEW_TICKET",
                "AI_REVIEW_TICKET",
                id,
                true,
                "{\"resolutionCode\":\"" + escapeJson(resolutionCode) + "\"}"
        );
        return getTicket(id);
    }

    @Transactional
    public AiReviewTicketDtos.ReviewTicketDetailView rejectTicket(
            Long id,
            AiReviewTicketDtos.RejectReviewTicketRequest request
    ) {
        CurrentUser currentUser = SecurityUtils.requireCurrentUser();
        requireWriteAuthority(currentUser);
        AiReviewTicket ticket = requireTicket(id);
        if (TICKET_STATUS_RESOLVED.equals(ticket.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "已完成的复核工单不能驳回", HttpStatus.CONFLICT);
        }
        ticketMapper.reject(id, currentUser.userId(), normalizeRequired(request.reviewNote()), LocalDateTime.now());
        ragKnowledgeService.syncAiReviewTicket(id);
        auditService.record(currentUser.userId(), "AI", "REJECT_REVIEW_TICKET", "AI_REVIEW_TICKET", id, true, "{}");
        return getTicket(id);
    }

    @Transactional
    public AiReviewTicketDtos.ReviewTicketDetailView resubmitTicket(
            Long id,
            AiReviewTicketDtos.ResubmitReviewTicketRequest request
    ) {
        CurrentUser currentUser = SecurityUtils.requireCurrentUser();
        AiReviewTicket ticket = requireTicket(id);
        if (!canManageTickets(currentUser) && !Objects.equals(ticket.getSubmittedBy(), currentUser.userId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能重新提交自己创建的复核工单", HttpStatus.FORBIDDEN);
        }
        if (!TICKET_STATUS_REJECTED.equals(ticket.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有已驳回的复核工单可以重新提交", HttpStatus.CONFLICT);
        }
        ticketMapper.resubmit(id, normalizeNullable(request.submitNote()));
        ragKnowledgeService.syncAiReviewTicket(id);
        auditService.record(currentUser.userId(), "AI", "RESUBMIT_REVIEW_TICKET", "AI_REVIEW_TICKET", id, true, "{}");
        return getTicket(id);
    }

    @Transactional
    public AiReviewTicketDtos.ReviewTicketDetailView linkSpecies(
            Long id,
            AiReviewTicketDtos.LinkSpeciesRequest request
    ) {
        if (request.finalSpeciesId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择要关联的已有物种档案", HttpStatus.BAD_REQUEST);
        }
        SpeciesDetailView species = speciesService.getSpecies(request.finalSpeciesId());
        return resolveTicket(id, new AiReviewTicketDtos.ResolveReviewTicketRequest(
                "CONFIRMED",
                request.finalSpeciesId(),
                species.chineseName(),
                species.scientificName(),
                request.reviewNote()
        ));
    }

    public MediaFile getReviewImage(Long mediaId) {
        MediaFile mediaFile = mediaFileService.getRequired(mediaId);
        if (!AI_REVIEW_IMAGE_BUSINESS_TYPE.equalsIgnoreCase(mediaFile.getBusinessType())) {
            throw new NotFoundException("复核工单图片不存在");
        }
        return mediaFile;
    }

    private AiReviewTicket requireTicket(Long id) {
        AiReviewTicket ticket = ticketMapper.findById(id);
        if (ticket == null) {
            throw new NotFoundException("复核工单不存在");
        }
        return ticket;
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请上传需要人工复核的图片", HttpStatus.BAD_REQUEST);
        }
        if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "人工复核工单仅支持图片附件", HttpStatus.BAD_REQUEST);
        }
    }

    private void requireWriteAuthority(CurrentUser currentUser) {
        if (!currentUser.authorities().contains("AI_REVIEW_WRITE")) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前账号没有处理AI复核工单的权限", HttpStatus.FORBIDDEN);
        }
    }

    private boolean canManageTickets(CurrentUser currentUser) {
        return currentUser.authorities().contains("AI_REVIEW_READ");
    }

    private AiReviewTicketDtos.ReviewTicketView toView(AiReviewTicket ticket) {
        return new AiReviewTicketDtos.ReviewTicketView(
                ticket.getId(),
                ticket.getSourceType(),
                ticket.getStatus(),
                ticket.getResolutionCode(),
                ticket.getSubmittedBy(),
                ticket.getSubmittedByName(),
                ticket.getReviewerUserId(),
                ticket.getReviewerName(),
                ticket.getLikelyChineseName(),
                ticket.getLikelyScientificName(),
                ticket.getConfidence() == null ? 0.0d : ticket.getConfidence().doubleValue(),
                ticket.getNeedsHumanReview() != null && ticket.getNeedsHumanReview() == 1,
                ticket.getImageMediaId(),
                ticket.getImageMediaId() == null ? null : "/api/v1/ai/review-tickets/images/" + ticket.getImageMediaId(),
                ticket.getReviewedAt(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt()
        );
    }

    private AiReviewTicketDtos.ReviewTicketDetailView toDetail(AiReviewTicket ticket) {
        return new AiReviewTicketDtos.ReviewTicketDetailView(
                ticket.getId(),
                ticket.getSourceType(),
                ticket.getStatus(),
                ticket.getResolutionCode(),
                ticket.getSubmittedBy(),
                ticket.getSubmittedByName(),
                ticket.getReviewerUserId(),
                ticket.getReviewerName(),
                ticket.getImageMediaId(),
                ticket.getImageMediaId() == null ? null : "/api/v1/ai/review-tickets/images/" + ticket.getImageMediaId(),
                ticket.getLikelyChineseName(),
                ticket.getLikelyScientificName(),
                ticket.getConfidence() == null ? 0.0d : ticket.getConfidence().doubleValue(),
                ticket.getNeedsHumanReview() != null && ticket.getNeedsHumanReview() == 1,
                ticket.getReasoning(),
                readCandidates(ticket.getCandidateJson()),
                readRelatedSpecies(ticket.getRelatedSpeciesJson()),
                readRagEvidence(ticket.getRagEvidenceJson()),
                ticket.getInitialRecognitionJson(),
                ticket.getReviewEvidenceJson(),
                ticket.getSubmitNote(),
                ticket.getFinalSpeciesId(),
                ticket.getFinalChineseName(),
                ticket.getFinalScientificName(),
                ticket.getReviewNote(),
                ticket.getReviewedAt(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt()
        );
    }

    private List<SpeciesAiDtos.IdentificationCandidate> readCandidates(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<SpeciesAiDtos.IdentificationCandidate>>() { });
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private List<SpeciesAiDtos.RelatedSpeciesRecord> readRelatedSpecies(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<SpeciesAiDtos.RelatedSpeciesRecord>>() { });
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private List<RagDtos.RagEvidenceItem> readRagEvidence(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<RagDtos.RagEvidenceItem>>() { });
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "复核工单数据保存失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private double boundedConfidence(double value) {
        if (Double.isNaN(value)) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private <T> List<T> defaultList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String normalizeRequired(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim();
    }

    private String normalizeNullable(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
