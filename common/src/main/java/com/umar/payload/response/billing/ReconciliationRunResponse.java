package com.umar.payload.response.billing;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReconciliationRunResponse {
    private String runId;
    private String source;
    private String statementDate;
    private Map<String, Object> lines;
    private Map<String, Object> breaks;
    private Map<String, Object> applied;
    private Map<String, Object> held;
    private Map<String, Object> fees;
    private String completedAt;
}
