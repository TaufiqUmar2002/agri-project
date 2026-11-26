package com.umar.payload.response.riskpool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PeriodCloseResponse {
    private String runId;
    private String poolId;
    private String period;
    private String status;
    private List<Map<String, Object>> checks;
    private List<String> blockers;
    private Map<String, Object> trialBalance;
    private Map<String, Object> earned;
    private String approvalId;
    private List<String> approvalsRequired;
    private String createdAt;
}
