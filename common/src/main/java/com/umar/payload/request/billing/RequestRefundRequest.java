package com.umar.payload.request.billing;

import com.umar.enums.billing.RefundReasonCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

@Data
public class RequestRefundRequest {
    @NotBlank
    private String invoiceId;
    private List<String> paymentIds;
    @NotNull
    @Positive
    private Long amountMinor;
    @NotNull
    private RefundReasonCode reasonCode;
    private String sourceEventId;
    private String note;
}
