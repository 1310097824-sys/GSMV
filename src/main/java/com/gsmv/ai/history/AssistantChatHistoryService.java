package com.gsmv.ai.history;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsmv.ai.dto.AssistantAiDtos;
import com.gsmv.ai.history.mapper.AssistantChatHistoryMapper;
import com.gsmv.common.ErrorCode;
import com.gsmv.common.exception.BusinessException;
import com.gsmv.security.CurrentUser;
import com.gsmv.security.SecurityUtils;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AssistantChatHistoryService {

    private static final int DEFAULT_HISTORY_LIMIT = 120;
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<AssistantAiDtos.EvidenceItem>> EVIDENCE_LIST_TYPE = new TypeReference<>() {
    };

    private final AssistantChatHistoryMapper historyMapper;
    private final ObjectMapper objectMapper;

    public AssistantChatHistoryService(
            AssistantChatHistoryMapper historyMapper,
            ObjectMapper objectMapper
    ) {
        this.historyMapper = historyMapper;
        this.objectMapper = objectMapper;
    }

    public AssistantAiDtos.ChatHistoryResponse listCurrentUserHistory() {
        CurrentUser currentUser = SecurityUtils.requireCurrentUser();
        List<AssistantChatMessage> records = historyMapper.listRecentByUserId(currentUser.userId(), DEFAULT_HISTORY_LIMIT);
        AssistantAiDtos.ChatResponse lastResponse = null;
        List<AssistantAiDtos.ChatHistoryItem> messages = new ArrayList<>();

        for (AssistantChatMessage record : records) {
            messages.add(new AssistantAiDtos.ChatHistoryItem(
                    record.getId(),
                    record.getRole(),
                    record.getContent(),
                    record.getCreatedAt()
            ));
            if ("assistant".equals(record.getRole()) && StringUtils.hasText(record.getStructuredQueryJson())) {
                lastResponse = toChatResponse(record);
            }
        }

        return new AssistantAiDtos.ChatHistoryResponse(messages, lastResponse);
    }

    public void recordExchange(
            AssistantAiDtos.ChatRequest request,
            AssistantAiDtos.ChatResponse response
    ) {
        CurrentUser currentUser = SecurityUtils.requireCurrentUser();
        String userMessage = request == null ? "" : request.message();
        if (StringUtils.hasText(userMessage)) {
            AssistantChatMessage message = new AssistantChatMessage();
            message.setUserId(currentUser.userId());
            message.setRole("user");
            message.setContent(userMessage.trim());
            historyMapper.insert(message);
        }

        if (response != null && StringUtils.hasText(response.answer())) {
            AssistantChatMessage message = new AssistantChatMessage();
            message.setUserId(currentUser.userId());
            message.setRole("assistant");
            message.setContent(response.answer());
            message.setStructuredQueryJson(writeJson(response.structuredQuery()));
            message.setHighlightsJson(writeJson(response.highlights()));
            message.setEvidenceJson(writeJson(response.evidence()));
            message.setCacheHit(response.cacheHit());
            historyMapper.insert(message);
        }
    }

    public void clearCurrentUserHistory() {
        CurrentUser currentUser = SecurityUtils.requireCurrentUser();
        historyMapper.deleteByUserId(currentUser.userId());
    }

    private AssistantAiDtos.ChatResponse toChatResponse(AssistantChatMessage record) {
        AssistantAiDtos.StructuredQuery structuredQuery = readJson(
                record.getStructuredQueryJson(),
                AssistantAiDtos.StructuredQuery.class
        );
        List<String> highlights = readJson(record.getHighlightsJson(), STRING_LIST_TYPE);
        List<AssistantAiDtos.EvidenceItem> evidence = readJson(record.getEvidenceJson(), EVIDENCE_LIST_TYPE);
        return new AssistantAiDtos.ChatResponse(
                record.getContent(),
                structuredQuery,
                highlights == null ? List.of() : highlights,
                evidence == null ? List.of() : evidence,
                Boolean.TRUE.equals(record.getCacheHit())
        );
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 对话历史序列化失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private <T> T readJson(String value, Class<T> type) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 对话历史解析失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private <T> T readJson(String value, TypeReference<T> type) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 对话历史解析失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
