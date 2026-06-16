package com.gsmv.ai.agent;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public record AgentTask(
        String workflowType,
        String subjectType,
        Long subjectId,
        String prompt,
        String scenario,
        Map<String, Object> input,
        List<String> requestedAgents,
        Map<String, Object> transientInput
) {
    public AgentTask(
            String workflowType,
            String subjectType,
            Long subjectId,
            String prompt,
            String scenario,
            Map<String, Object> input,
            List<String> requestedAgents
    ) {
        this(workflowType, subjectType, subjectId, prompt, scenario, input, requestedAgents, Map.of());
    }

    public AgentTask {
        input = input == null ? Map.of() : sanitize(input);
        requestedAgents = requestedAgents == null ? List.of() : List.copyOf(requestedAgents);
        transientInput = transientInput == null ? Map.of() : sanitize(transientInput);
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
