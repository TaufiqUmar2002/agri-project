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
public class RefundResponse {
    private String refundId;
    private String invoiceId;
    private String policyId;
    private String status;
    private Long amountMinor;
    private String currency;
    private String reasonCode;
    private Map<String, Object> destination;
    private Map<String, Object> approvalRequired;
    private String createdAt;
}
