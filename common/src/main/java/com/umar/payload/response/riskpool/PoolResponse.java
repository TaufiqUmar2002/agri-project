package com.umar.payload.response.riskpool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PoolResponse {
    private String poolId;
    private String poolCode;
    private int version;
    private String status;
    private String configHash;
    private List<Map<String, Object>> mappings;
    private Map<String, Object> chartOfAccounts;
    private String approvalId;
    private List<String> approvalsRequired;
    private String effectiveFrom;
}
