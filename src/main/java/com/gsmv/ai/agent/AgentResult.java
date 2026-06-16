package com.gsmv.ai.agent;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public record AgentResult(
        String agentName,
        String agentRole,
        String status,
        String summary,
        Map<String, Object> output,
        List<Map<String, Object>> evidence,
        Double confidence,
        String verificationStatus,
        String errorMessage
) {
    public AgentResult {
        output = output == null ? Map.of() : sanitize(output);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public static AgentResult success(
            String agentName,
            String agentRole,
            String summary,
            Map<String, Object> output,
            List<Map<String, Object>> evidence,
            Double confidence,
            String verificationStatus
    ) {
        return new AgentResult(agentName, agentRole, "SUCCESS", summary, output, evidence, confidence, verificationStatus, null);
    }

    public static AgentResult failure(String agentName, String agentRole, String errorMessage) {
        return new AgentResult(agentName, agentRole, "FAILED", "执行失败", Map.of(), List.of(), 0.0d, "NEEDS_REVIEW", errorMessage);
    }

    private static Map<String, Object> sanitize(Map<String, Object> source) {
        Map<String, Object> values = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null && value != null) {
                values.put(key, value);
            }
        });
        return Map.copyOf(values);
    }
}
