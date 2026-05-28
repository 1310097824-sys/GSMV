package com.gsmv.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;

public final class AssistantAiDtos {

    private AssistantAiDtos() {
    }

    public record ConversationMessage(
            @NotBlank(message = "消息角色不能为空") String role,
            @NotBlank(message = "消息内容不能为空") String content
    ) {
    }

    public record ChatRequest(
            @NotBlank(message = "请输入提问内容") String message,
            @Valid List<ConversationMessage> history
    ) {
    }

    public record StructuredQuery(
            String intent,
            String locationKeyword,
            String ecosystemKeyword,
            String speciesKeyword,
            String protectionLevel,
            String iucnStatus,
            Integer yearsBack,
            Integer recentDays,
            boolean includeTrend,
            boolean riskOnly,
            Integer limit
    ) {
    }

    public record EvidenceItem(
            String type,
            String title,
            String description,
            Long sourceId,
            Double score,
            String sourcePath
    ) {
        public EvidenceItem(String type, String title, String description) {
            this(type, title, description, null, null, null);
        }
    }

    public record ChatResponse(
            String answer,
            StructuredQuery structuredQuery,
            List<String> highlights,
            List<EvidenceItem> evidence,
            boolean cacheHit
    ) {
    }

    public record ChatHistoryItem(
            Long id,
            String role,
            String content,
            LocalDateTime createdAt
    ) {
    }

    public record ChatHistoryResponse(
            List<ChatHistoryItem> messages,
            ChatResponse lastResponse
    ) {
    }

    public record ChatStreamEvent(
            String type,
            String content,
            ChatResponse response
    ) {
    }
}
