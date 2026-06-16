package com.gsmv.ai.agent;

import java.util.List;
import java.util.Map;

public record AgentContext(
        Long runId,
        AgentTask task,
        Map<String, Object> memory,
        List<AgentResult> previousResults
) {
}
