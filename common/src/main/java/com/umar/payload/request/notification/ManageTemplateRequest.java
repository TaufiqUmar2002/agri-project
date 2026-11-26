package com.umar.payload.request.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ManageTemplateRequest {
    private String action; // SAVE | SUBMIT | APPROVE | REJECT | RETIRE | PREVIEW
    private String category;
    private boolean mandatory;
    private boolean acceptsSecret;
    private Map<String, String> variants;
    private List<Map<String, Object>> variables;
    @NotNull
    private Integer version;
    @NotBlank
    @Size(min = 10, max = 500)
    private String note;
}
