package com.umar.payload.response.riskpool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PoolAlertsResponse {
    private String poolId;
    private int version;
    private List<Map<String, Object>> alerts;
    private String approvalId;
    private String updatedAt;
    private String updatedBy;
}
