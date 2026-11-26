package com.umar.payload.response.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class IntegrityStatusResponse {
    private String overall;
    private String asOf;
    private List<Map<String, Object>> chains;
    private Map<String, Object> intake;
    private Map<String, Object> keys;
    private List<Map<String, Object>> alerts;
    private Map<String, Object> run;
}
