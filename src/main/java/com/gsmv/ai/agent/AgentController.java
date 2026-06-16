package com.gsmv.ai.agent;

import com.gsmv.ai.agent.dto.AgentDtos;
import com.gsmv.ai.rag.RagKnowledgeService;
import com.gsmv.common.ApiResponse;
import com.gsmv.common.PageResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/v1/agents/runs")
public class AgentController {

    private final AgentOrchestratorService orchestratorService;

    public AgentController(AgentOrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PageResponse<AgentDtos.AgentRunView>> listRuns(
            @RequestParam(required = false) String workflowType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String verificationStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(orchestratorService.listRuns(workflowType, status, verificationStatus, keyword, page, size));
    }

    @PostMapping("/knowledge-governance")
    @PreAuthorize("hasAuthority('RAG_READ')")
    public ApiResponse<AgentDtos.AgentRunView> runKnowledgeGovernance(
            @RequestBody(required = false) AgentDtos.KnowledgeGovernanceRequest request
    ) {
        String prompt = request == null ? null : request.prompt();
        Long documentId = request == null ? null : request.documentId();
        String normalizedPrompt = StringUtils.hasText(prompt) ? prompt.trim() : "检查知识库低质量、重复和冲突来源";
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("ragQuery", normalizedPrompt);
        if (documentId != null) {
            input.put("documentId", documentId);
        }
        AgentTask task = new AgentTask(
                AgentOrchestratorService.WORKFLOW_KNOWLEDGE_GOVERNANCE,
                documentId == null ? "RAG_KNOWLEDGE_BASE" : "RAG_DOCUMENT",
                documentId,
                normalizedPrompt,
                RagKnowledgeService.SCENARIO_ASSISTANT,
                input,
                null
        );
        return ApiResponse.success(orchestratorService.execute(task));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<AgentDtos.AgentRunView> getRun(@PathVariable Long id) {
        return ApiResponse.success(orchestratorService.getRun(id));
    }

    @GetMapping("/{id}/replay")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<AgentDtos.AgentRunReplayView> replayRun(@PathVariable Long id) {
        return ApiResponse.success(orchestratorService.getRunReplay(id));
    }
}
