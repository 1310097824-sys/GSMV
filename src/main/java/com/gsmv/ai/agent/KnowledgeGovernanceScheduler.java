package com.gsmv.ai.agent;

import com.gsmv.ai.agent.dto.AgentDtos;
import com.gsmv.ai.rag.RagKnowledgeService;
import com.gsmv.ai.rag.mapper.RagDocumentMapper;
import com.gsmv.ai.rag.model.RagDocument;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class KnowledgeGovernanceScheduler {

    private final AgentGovernanceProperties properties;
    private final RagDocumentMapper documentMapper;
    private final AgentOrchestratorService orchestratorService;

    public KnowledgeGovernanceScheduler(
            AgentGovernanceProperties properties,
            RagDocumentMapper documentMapper,
            AgentOrchestratorService orchestratorService
    ) {
        this.properties = properties;
        this.documentMapper = documentMapper;
        this.orchestratorService = orchestratorService;
    }

    @Scheduled(
            cron = "${gsmv.ai.agent.knowledge-governance.cron:0 30 2 * * *}",
            zone = "${gsmv.ai.agent.knowledge-governance.zone:Asia/Shanghai}"
    )
    public void runScheduledSweep() {
        runSweep("SCHEDULED");
    }

    public AgentDtos.AgentRunView runSweep(String trigger) {
        if (!properties.isEnabled()) {
            return null;
        }
        int scanLimit = Math.min(Math.max(properties.getScanLimit(), 1), 1000);
        int maxIssueDocuments = Math.min(Math.max(properties.getMaxIssueDocuments(), 1), 100);
        List<RagDocument> documents = safeList(documentMapper.findPage(null, null, null, scanLimit, 0));
        GovernanceScan scan = scanDocuments(documents, maxIssueDocuments);
        if (!scan.hasIssues()) {
            return null;
        }
        AgentTask task = new AgentTask(
                AgentOrchestratorService.WORKFLOW_KNOWLEDGE_GOVERNANCE,
                "RAG_GOVERNANCE_SWEEP",
                null,
                scan.prompt(),
                RagKnowledgeService.SCENARIO_ASSISTANT,
                scan.toInput(trigger, documents.size()),
                List.of()
        );
        return orchestratorService.execute(task);
    }

    private GovernanceScan scanDocuments(List<RagDocument> documents, int maxIssueDocuments) {
        List<Map<String, Object>> failedDocuments = new ArrayList<>();
        List<Map<String, Object>> emptyChunkDocuments = new ArrayList<>();
        Map<String, List<RagDocument>> duplicateBuckets = new LinkedHashMap<>();

        for (RagDocument document : documents) {
            if (failedDocuments.size() < maxIssueDocuments && isFailed(document)) {
                failedDocuments.add(toDocumentIssue(document, "FAILED_DOCUMENT", "知识文档索引失败或存在错误信息"));
            }
            if (emptyChunkDocuments.size() < maxIssueDocuments && isEmptyChunk(document)) {
                emptyChunkDocuments.add(toDocumentIssue(document, "EMPTY_CHUNKS", "知识文档没有可检索分块"));
            }
            String normalizedTitle = normalizeTitle(document.getTitle());
            if (StringUtils.hasText(normalizedTitle)) {
                duplicateBuckets.computeIfAbsent(normalizedTitle, ignored -> new ArrayList<>()).add(document);
            }
        }

        List<Map<String, Object>> duplicateGroups = duplicateBuckets.values().stream()
                .filter(group -> group.size() > 1)
                .limit(maxIssueDocuments)
                .map(this::toDuplicateGroup)
                .toList();
        return new GovernanceScan(failedDocuments, emptyChunkDocuments, duplicateGroups);
    }

    private boolean isFailed(RagDocument document) {
        return "FAILED".equalsIgnoreCase(document.getStatus()) || StringUtils.hasText(document.getErrorMessage());
    }

    private boolean isEmptyChunk(RagDocument document) {
        return "READY".equalsIgnoreCase(document.getStatus())
                && (document.getChunkCount() == null || document.getChunkCount() <= 0);
    }

    private Map<String, Object> toDocumentIssue(RagDocument document, String issueType, String reason) {
        return mapOf(
                "issueType", issueType,
                "documentId", document.getId(),
                "sourceType", document.getSourceType(),
                "sourceId", document.getSourceId(),
                "title", document.getTitle(),
                "status", document.getStatus(),
                "chunkCount", document.getChunkCount(),
                "errorMessage", document.getErrorMessage(),
                "reason", reason
        );
    }

    private Map<String, Object> toDuplicateGroup(List<RagDocument> group) {
        RagDocument first = group.get(0);
        return mapOf(
                "issueType", "DUPLICATE_TITLE",
                "title", first.getTitle(),
                "documentCount", group.size(),
                "documents", group.stream()
                        .map(document -> mapOf(
                                "documentId", document.getId(),
                                "sourceType", document.getSourceType(),
                                "sourceId", document.getSourceId(),
                                "status", document.getStatus(),
                                "chunkCount", document.getChunkCount()
                        ))
                        .toList(),
                "reason", "多个知识文档标题高度一致，建议检查是否重复入库"
        );
    }

    private String normalizeTitle(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("\\.(pdf|docx|txt|md)$", "")
                .replaceAll("[\\s_\\-—–]+", "")
                .trim();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private Map<String, Object> mapOf(Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            Object key = pairs[index];
            Object value = pairs[index + 1];
            if (key != null && value != null) {
                values.put(String.valueOf(key), value);
            }
        }
        return values;
    }

    private record GovernanceScan(
            List<Map<String, Object>> failedDocuments,
            List<Map<String, Object>> emptyChunkDocuments,
            List<Map<String, Object>> duplicateGroups
    ) {
        boolean hasIssues() {
            return !failedDocuments.isEmpty() || !emptyChunkDocuments.isEmpty() || !duplicateGroups.isEmpty();
        }

        String prompt() {
            return "定期知识库治理巡检：发现失败文档 " + failedDocuments.size()
                    + " 个、空分块文档 " + emptyChunkDocuments.size()
                    + " 个、疑似重复标题组 " + duplicateGroups.size()
                    + " 个，请复核低质量知识、重复知识和冲突来源。";
        }

        Map<String, Object> toInput(String trigger, int scannedCount) {
            List<String> warnings = new ArrayList<>();
            addWarning(warnings, failedDocuments, "存在索引失败或错误文档");
            addWarning(warnings, emptyChunkDocuments, "存在 READY 但无分块的低质量文档");
            addWarning(warnings, duplicateGroups, "存在疑似重复标题知识");
            return Map.of(
                    "trigger", StringUtils.hasText(trigger) ? trigger : "SCHEDULED",
                    "generatedAt", LocalDateTime.now().toString(),
                    "scannedDocumentCount", scannedCount,
                    "failedDocuments", failedDocuments,
                    "emptyChunkDocuments", emptyChunkDocuments,
                    "duplicateGroups", duplicateGroups,
                    "conflictWarnings", warnings,
                    "ragQuery", prompt()
            );
        }

        private void addWarning(List<String> warnings, Collection<?> values, String message) {
            if (!values.isEmpty()) {
                warnings.add(message + "：" + values.size());
            }
        }
    }
}
