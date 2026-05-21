package com.gsmv.ai.review.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AiReviewTicket {

    private Long id;
    private String sourceType;
    private String status;
    private String resolutionCode;
    private Long submittedBy;
    private String submittedByName;
    private Long reviewerUserId;
    private String reviewerName;
    private Long imageMediaId;
    private String likelyChineseName;
    private String likelyScientificName;
    private BigDecimal confidence;
    private Integer needsHumanReview;
    private String reasoning;
    private String candidateJson;
    private String relatedSpeciesJson;
    private String initialRecognitionJson;
    private String ragEvidenceJson;
    private String reviewEvidenceJson;
    private String submitNote;
    private Long finalSpeciesId;
    private String finalChineseName;
    private String finalScientificName;
    private String reviewNote;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getResolutionCode() { return resolutionCode; }
    public void setResolutionCode(String resolutionCode) { this.resolutionCode = resolutionCode; }
    public Long getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(Long submittedBy) { this.submittedBy = submittedBy; }
    public String getSubmittedByName() { return submittedByName; }
    public void setSubmittedByName(String submittedByName) { this.submittedByName = submittedByName; }
    public Long getReviewerUserId() { return reviewerUserId; }
    public void setReviewerUserId(Long reviewerUserId) { this.reviewerUserId = reviewerUserId; }
    public String getReviewerName() { return reviewerName; }
    public void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }
    public Long getImageMediaId() { return imageMediaId; }
    public void setImageMediaId(Long imageMediaId) { this.imageMediaId = imageMediaId; }
    public String getLikelyChineseName() { return likelyChineseName; }
    public void setLikelyChineseName(String likelyChineseName) { this.likelyChineseName = likelyChineseName; }
    public String getLikelyScientificName() { return likelyScientificName; }
    public void setLikelyScientificName(String likelyScientificName) { this.likelyScientificName = likelyScientificName; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public Integer getNeedsHumanReview() { return needsHumanReview; }
    public void setNeedsHumanReview(Integer needsHumanReview) { this.needsHumanReview = needsHumanReview; }
    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }
    public String getCandidateJson() { return candidateJson; }
    public void setCandidateJson(String candidateJson) { this.candidateJson = candidateJson; }
    public String getRelatedSpeciesJson() { return relatedSpeciesJson; }
    public void setRelatedSpeciesJson(String relatedSpeciesJson) { this.relatedSpeciesJson = relatedSpeciesJson; }
    public String getInitialRecognitionJson() { return initialRecognitionJson; }
    public void setInitialRecognitionJson(String initialRecognitionJson) { this.initialRecognitionJson = initialRecognitionJson; }
    public String getRagEvidenceJson() { return ragEvidenceJson; }
    public void setRagEvidenceJson(String ragEvidenceJson) { this.ragEvidenceJson = ragEvidenceJson; }
    public String getReviewEvidenceJson() { return reviewEvidenceJson; }
    public void setReviewEvidenceJson(String reviewEvidenceJson) { this.reviewEvidenceJson = reviewEvidenceJson; }
    public String getSubmitNote() { return submitNote; }
    public void setSubmitNote(String submitNote) { this.submitNote = submitNote; }
    public Long getFinalSpeciesId() { return finalSpeciesId; }
    public void setFinalSpeciesId(Long finalSpeciesId) { this.finalSpeciesId = finalSpeciesId; }
    public String getFinalChineseName() { return finalChineseName; }
    public void setFinalChineseName(String finalChineseName) { this.finalChineseName = finalChineseName; }
    public String getFinalScientificName() { return finalScientificName; }
    public void setFinalScientificName(String finalScientificName) { this.finalScientificName = finalScientificName; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
