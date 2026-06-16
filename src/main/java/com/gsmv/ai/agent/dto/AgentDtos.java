package com.gsmv.ai.agent.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class AgentDtos {

    private AgentDtos() {
    }

    public record AgentStepView(
            Long id,
            Long runId,
            int stepOrder,
            String agentName,
            String agentRole,
            String status,
            String summary,
            Object input,
            Object output,
            Object evidence,
            String errorMessage,
            Double confidence,
            Long durationMs,
            LocalDateTime startedAt,
            LocalDateTime finishedAt
    ) {
    }

    public record AgentRunView(
            Long id,
            String workflowType,
            String status,
            String subjectType,
            Long subjectId,
            Long userId,
            String username,
            String prompt,
            String summary,
            String verificationStatus,
            Double confidence,
            Object finalOutput,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            LocalDateTime createdAt,
            List<AgentStepView> steps
    ) {
    }

    public record AgentRunReplayView(
            Long runId,
            String replayStatus,
            boolean reconstructable,
            AgentRunView run,
            int stepCount,
            long evidenceCount,
            List<String> agentSequence,
            Object reconstructedFinalOutput,
            Object verifierOutput,
            Object claimChecks,
            Object reviewFindings,
            List<String> consistencyIssues
    ) {
    }

    public record KnowledgeGovernanceRequest(
            String prompt,
            Long documentId
    ) {
    }
}
