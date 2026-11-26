package com.umar.payload.response.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ClaimTraceResponse {
    private String claimId;
    private String policyId;
    private String plotId;
    private Map<String, Object> claim;
    private Map<String, Object> decision;
    private Map<String, Object> evidence;
    private Map<String, Object> policy;
    private List<Map<String, Object>> payouts;
    private List<Map<String, Object>> notifications;
    private Map<String, Object> integrity;
    private Map<String, Object> completeness;
    private List<Map<String, Object>> findings;
    private String packToken;
}
