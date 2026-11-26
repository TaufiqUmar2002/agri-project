package com.umar.payload.response.riskpool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BordereauxResponse {
    private String runId;
    private String poolId;
    private String type;
    private String period;
    private String status;
    private Map<String, Object> totals;
    private List<Map<String, Object>> rows;
    private String contentHash;
    private Map<String, Object> export;
    private String generatedAt;
    private int page;
    private int size;
    private long totalElements;
}
