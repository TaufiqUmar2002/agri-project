package com.umar.payload.request.policy;

import com.umar.enums.policy.CoverageLevel;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GenerateQuoteRequest {
    @NotBlank
    private String plotId;
    private String productId;
    private CoverageLevel coverageLevel;
    private Long sumInsuredMinor;
    private String renewsPolicyId;
    private String subsidyCode;
    private String language;
    @NotBlank
    private String channel; // APP | USSD | WHATSAPP | AGENT
    private String farmerId;
}
