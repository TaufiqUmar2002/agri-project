package com.umar.payload.request.integration;

import com.umar.enums.integration.FailMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class EnableExtensionRequest {
    private String version;
    @NotBlank
    private String action;
    private ScopeSpec scope;
    @Min(1) @Max(1000)
    private Integer priority;
    private FailMode failMode;
    private Integer timeoutMs;
    private AutoDisableSpec autoDisable;
    @NotBlank @Size(min = 20, max = 500)
    private String reason;

    @Data
    public static class ScopeSpec {
        private List<String> tenantIds;
        private List<String> productIds;
        private List<String> regionCodes;
    }

    @Data
    public static class AutoDisableSpec {
        private Integer errorRatePercent = 20;
        private Integer windowMinutes = 5;
        private Integer p95LatencyMs;
        private Integer consecutiveFailures = 20;
    }
}
