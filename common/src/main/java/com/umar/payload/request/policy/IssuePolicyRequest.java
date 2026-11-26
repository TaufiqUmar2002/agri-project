package com.umar.payload.request.policy;

import com.umar.enums.policy.PayerType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class IssuePolicyRequest {
    @NotBlank
    private String quoteId;

    @NotNull
    @Valid
    private AcceptanceInfo acceptance;

    private PayerType payerType = PayerType.FARMER;
    private Map<String, Object> attributes;

    @Data
    public static class AcceptanceInfo {
        @NotBlank
        private String termsVersion;
        @NotBlank
        private String channel;
        @NotNull
        @Valid
        private EvidenceInfo evidence;
    }

    @Data
    public static class EvidenceInfo {
        @NotBlank
        private String type; // TAP_CONFIRM | USSD_CONFIRMATION | OTP_CONFIRMATION
        private String reference;
    }
}
