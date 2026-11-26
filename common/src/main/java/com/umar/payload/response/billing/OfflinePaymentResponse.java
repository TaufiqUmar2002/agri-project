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
public class OfflinePaymentResponse {
    private String paymentId;
    private String invoiceId;
    private String status;
    private String method;
    private Long amountMinor;
    private String currency;
    private Map<String, Object> invoice;
    private Map<String, Object> receipt;
    private Long floatBalanceMinor;
    private String approvalId;
}
