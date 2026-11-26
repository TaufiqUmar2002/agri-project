package com.umar.payload.request.riskpool;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ConfigurePoolAlertsRequest {
    @NotEmpty
    private List<Map<String, Object>> alerts;
    private Map<String, Object> evaluationCadence;
    @NotBlank
    @Size(min = 20, max = 500)
    private String reason;
}
