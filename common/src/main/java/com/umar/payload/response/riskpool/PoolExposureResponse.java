package com.umar.payload.response.riskpool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PoolExposureResponse {
    private String poolId;
    private String asOf;
    private String snapshotId;
    private String currency;
    private Map<String, Object> totals;
    private List<Map<String, Object>> groups;
    private Map<String, Object> concentration;
    private List<Map<String, Object>> accumulation;
    private List<Map<String, Object>> scenarios;
    private Map<String, Object> treaty;
    private String generatedAt;
}
