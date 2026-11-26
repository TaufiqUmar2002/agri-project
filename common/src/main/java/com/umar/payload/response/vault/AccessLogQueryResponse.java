package com.umar.payload.response.vault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AccessLogQueryResponse {
    private List<AccessRecordResponse> records;
    private Map<String, Object> summary;
    private Map<String, Object> subjectSummary;
    private Map<String, Object> integrity;
    private Map<String, Object> export;
    private int page;
    private int size;
    private long totalElements;
    private String requestId;
}
