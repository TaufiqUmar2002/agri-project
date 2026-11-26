package com.umar.payload.response.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RegulatoryReportResponse {
    private String reportId;
    private String reportType;
    private String format;
    private String status;
    private Map<String, Object> period;
    private List<String> checkpoints;
    private Map<String, Object> integrity;
    private Map<String, Object> rowCounts;
    private Map<String, Object> totals;
    private String reportHash;
    private String scheduleId;
    private String nextRunAt;
    private List<Map<String, Object>> approvals;
    private boolean downloadAvailable;
}
