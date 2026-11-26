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
public class QuoteResponse {
    private String quoteId;
    private String productId;
    private Integer productVersion;
    private Long sumInsuredMinor;
    private String currency;
    private Map<String, Object> premium;
    private List<Map<String, Object>> factors;
    private List<Map<String, Object>> payoutTiers;
    private Integer waitingPeriodDays;
    private Map<String, Object> coverageWindowEstimate;
    private String scoreSource;
    private Map<String, Object> subsidy;
    private String priceValidUntil;
    private String payBy;
    private List<String> warnings;
}
