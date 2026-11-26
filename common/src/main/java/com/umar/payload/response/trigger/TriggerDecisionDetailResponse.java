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
public class TriggerDecisionDetailResponse {
    private String decisionId;
    private String evaluationId;
    private String policyId;
    private String plotId;
    private String regionCode;
    private String ruleId;
    private int ruleVersion;
    private String ruleHash;
    private String outcome;
    private String status;
    private String tierId;
    private Double payoutPercent;
    private Long indicatedPayoutMinor;
    private String currency;
    private Map<String, Object> window;
    private Double computedIndexValue;
    private Double thresholdValue;
    private String dataQuality;
    private String evidenceHash;
    private String evidenceBundleToken;
    private String explanation;
    private List<Map<String, Object>> evidence;
    private List<Map<String, Object>> phases;
    private Map<String, Object> corroboration;
    private List<Map<String, Object>> history;
    private String decidedAt;
    private int version;
}
