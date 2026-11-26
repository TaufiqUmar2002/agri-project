package com.umar.payload.response.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ReportsListResponse {
    private List<Map<String, Object>> items;
    private List<Map<String, Object>> schedules;
    private Map<String, Object> report;
    private Map<String, Object> download;
    private String nextCursor;
}
