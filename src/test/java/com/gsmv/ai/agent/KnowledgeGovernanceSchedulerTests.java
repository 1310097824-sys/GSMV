package com.gsmv.ai.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gsmv.ai.agent.dto.AgentDtos;
import com.gsmv.ai.rag.mapper.RagDocumentMapper;
import com.gsmv.ai.rag.model.RagDocument;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KnowledgeGovernanceSchedulerTests {

    private AgentGovernanceProperties properties;
    private RagDocumentMapper documentMapper;
    private AgentOrchestratorService orchestratorService;
    private KnowledgeGovernanceScheduler scheduler;

    @BeforeEach
    void setUp() {
        properties = new AgentGovernanceProperties();
        properties.setScanLimit(50);
        properties.setMaxIssueDocuments(10);
        documentMapper = mock(RagDocumentMapper.class);
        orchestratorService = mock(AgentOrchestratorService.class);
        scheduler = new KnowledgeGovernanceScheduler(properties, documentMapper, orchestratorService);
    }

    @Test
    void cleanSweepDoesNotCreateAgentRun() {
        when(documentMapper.findPage(null, null, null, 50, 0)).thenReturn(List.of(
                document(1L, "SPECIES", "中华白海豚", "READY", 3, null),
                document(2L, "OBSERVATION", "湛江观测记录", "READY", 2, null)
        ));

        AgentDtos.AgentRunView run = scheduler.runSweep("TEST");

        assertNull(run);
        verify(orchestratorService, never()).execute(any());
    }

    @Test
    void sweepCreatesKnowledgeGovernanceRunForQualityIssues() {
        when(documentMapper.findPage(null, null, null, 50, 0)).thenReturn(List.of(
                document(1L, "UPLOAD", "失败文档", "FAILED", 0, "PDF 解析失败"),
                document(2L, "UPLOAD", "空分块文档", "READY", 0, null),
                document(3L, "EXTERNAL_OBIS", "Duplicate Knowledge.pdf", "READY", 2, null),
                document(4L, "EXTERNAL_GBIF", "duplicate-knowledge", "READY", 2, null)
        ));
        AgentDtos.AgentRunView expectedRun = new AgentDtos.AgentRunView(
                9L,
                AgentOrchestratorService.WORKFLOW_KNOWLEDGE_GOVERNANCE,
                "SUCCESS",
                "RAG_GOVERNANCE_SWEEP",
                null,
                null,
                null,
                "governance",
                "done",
                AgentOrchestratorService.STATUS_NEEDS_REVIEW,
                0.7d,
                Map.of(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                List.of()
        );
        when(orchestratorService.execute(any())).thenReturn(expectedRun);

        AgentDtos.AgentRunView run = scheduler.runSweep("TEST");

        assertEquals(expectedRun, run);
        ArgumentCaptor<AgentTask> captor = ArgumentCaptor.forClass(AgentTask.class);
        verify(orchestratorService).execute(captor.capture());
        AgentTask task = captor.getValue();
        assertEquals(AgentOrchestratorService.WORKFLOW_KNOWLEDGE_GOVERNANCE, task.workflowType());
        assertEquals("RAG_GOVERNANCE_SWEEP", task.subjectType());
        assertTrue(task.prompt().contains("失败文档 1 个"));
        assertTrue(task.prompt().contains("空分块文档 1 个"));
        assertTrue(task.prompt().contains("疑似重复标题组 1 个"));
        assertNotNull(task.input().get("failedDocuments"));
        assertNotNull(task.input().get("emptyChunkDocuments"));
        assertNotNull(task.input().get("duplicateGroups"));
        assertTrue(task.input().get("conflictWarnings") instanceof List<?> warnings && warnings.size() == 3);
    }

    @Test
    void disabledSweepDoesNotReadDocuments() {
        properties.setEnabled(false);

        AgentDtos.AgentRunView run = scheduler.runSweep("TEST");

        assertNull(run);
        verify(documentMapper, never()).findPage(any(), any(), any(), eq(50), eq(0));
        verify(orchestratorService, never()).execute(any());
    }

    private RagDocument document(Long id, String sourceType, String title, String status, int chunkCount, String errorMessage) {
        RagDocument document = new RagDocument();
        document.setId(id);
        document.setSourceType(sourceType);
        document.setSourceId(id + 100);
        document.setTitle(title);
        document.setStatus(status);
        document.setChunkCount(chunkCount);
        document.setErrorMessage(errorMessage);
        document.setUpdatedAt(LocalDateTime.now());
        return document;
    }
}
