package com.umar.payload.request.trigger;

import com.umar.enums.policy.PerilType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
public class CreateTriggerRuleRequest {
    @NotBlank
    private String ruleCode;
    @NotBlank
    private String productId;
    @NotNull
    private Integer productVersion;
    private String contractualDefinitionHash;
    private Integer baseRuleVersion;
    @NotNull
    private PerilType peril;
    @NotEmpty
    private List<String> regionCodes;
    private String indexType;
    private Map<String, Object> observationWindow;
    private List<Map<String, Object>> dataSources;
    private Map<String, Object> corroboration;
    private List<Map<String, Object>> phases;
    private List<Map<String, Object>> tiers;
    private Map<String, Object> qualityGates;
    private Map<String, Object> coolingPeriod;
    private Integer gracePeriodHours;
    private String backtestRef;
    @NotNull
    private LocalDate effectiveFrom;
    private boolean submitForApproval = true;
}
