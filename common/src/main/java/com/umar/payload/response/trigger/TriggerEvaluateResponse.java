package com.umar.payload.response.trigger;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriggerEvaluateResponse {
    private String evaluationId;
    private String evaluationType;
    private String ruleId;
    private int ruleVersion;
    private String status;
    private int evaluatedCount;
    private int breachedCount;
    private int reviewRequiredCount;
    private Map<String, Object> summary;
    private List<Map<String, Object>> sampleDecisions;
    private String completedAt;
}
