package com.umar.payload.response.policy;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PolicyResponse {
    private String policyId;
    private String policyNumber;
    private String status;
    private String plotId;
    private String farmerId;
    private String productId;
    private Integer productVersion;
    private Map<String, Object> product;
    private Map<String, Object> terms;
    private Map<String, Object> premium;
    private Map<String, Object> payout;
    private String payBy;
    private Map<String, Object> summary;
    private Map<String, Object> paymentInstructions;
    private Integer waitingPeriodDays;
    private Map<String, Object> coverageWindowEstimate;
    private String coverageStart;
    private String coverageEnd;
    private List<String> holdReasons;
    private String nextAction;
    private List<Map<String, Object>> history;
    private Map<String, Object> attributes;
    private String documentUrl;
    private Long version;
    private String createdAt;
}
