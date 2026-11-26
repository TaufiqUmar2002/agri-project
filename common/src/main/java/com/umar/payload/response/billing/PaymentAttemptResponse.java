package com.umar.payload.response.billing;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentAttemptResponse {
    private String attemptId;
    private String invoiceId;
    private String status;
    private Long amountMinor;
    private String currency;
    private String maskedPayer;
    private String expiresAt;
    private Map<String, Object> instructions;
    private Map<String, Object> instalment;
    private Integer pollAfterSeconds;
}
