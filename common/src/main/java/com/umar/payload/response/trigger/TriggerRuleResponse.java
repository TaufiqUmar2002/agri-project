package com.umar.payload.response.trigger;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriggerRuleResponse {
    private String ruleId;
    private String ruleCode;
    private int version;
    private String productId;
    private int productVersion;
    private String status;
    private String definitionHash;
    private String contractualDefinitionHash;
    private List<Map<String, Object>> sandboxTest;
    private List<String> approvalsRequired;
    private List<Map<String, Object>> approvals;
    private String effectiveFrom;
    private String createdAt;
}
