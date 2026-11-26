package com.umar.payload.response.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TemplateResponse {
    private String code;
    private String language;
    private int version;
    private String status;
    private String category;
    private boolean mandatory;
    private boolean acceptsSecret;
    private List<Map<String, Object>> checks;
    private Map<String, Object> preview;
    private List<Map<String, Object>> approvals;
    private String providerStatus;
    private boolean current;
}
