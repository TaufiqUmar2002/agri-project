package com.umar.payload.response.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FindingsListResponse {
    private List<Map<String, Object>> items;
    private Map<String, Object> finding;
    private Map<String, Object> counts;
    private String nextCursor;
}
