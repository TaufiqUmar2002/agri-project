package com.umar.payload.response.claims;

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
public class ClaimsReconciliationReportResponse {
    private Map<String, String> range;
    private Map<String, Object> summary;
    private List<Map<String, Object>> breaks;
    private Map<String, String> statementCoverage;
    private int page;
    private int size;
    private long total;
}
