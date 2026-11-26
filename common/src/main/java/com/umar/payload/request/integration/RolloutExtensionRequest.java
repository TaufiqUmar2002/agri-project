package com.umar.payload.request.integration;

import com.umar.enums.integration.RolloutStage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class RolloutExtensionRequest {
    private String version;
    @NotNull
    private RolloutStage stage;
    private EnableExtensionRequest.ScopeSpec scope;
    private CanarySpec canary;
    @Min(1) @Max(336)
    private Integer durationHours;
    private CriteriaSpec criteria;
    @NotBlank @Size(min = 20, max = 500)
    private String reason;

    @Data
    public static class CanarySpec {
        @Min(1) @Max(50)
        private Integer percent;
        private List<String> regionCodes;
        private List<String> productIds;
    }

    @Data
    public static class CriteriaSpec {
        private Long minInvocations = 100L;
        private Double maxErrorRatePercent = 1.0;
        private Double maxTimeoutRatePercent = 1.0;
        private Long maxP95Ms = 1000L;
        private Long maxUnexpectedHolds = 50L;
    }
}
