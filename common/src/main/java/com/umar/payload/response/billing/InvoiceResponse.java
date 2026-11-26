package com.umar.payload.response.billing;

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
public class InvoiceResponse {
    private String invoiceId;
    private String invoiceNumber;
    private String paymentReference;
    private String type;
    private String status;
    private String policyId;
    private String payerType;
    private String payerRef;
    private List<Map<String, Object>> lines;
    private Long totalMinor;
    private Long paidMinor;
    private Long outstandingMinor;
    private String currency;
    private Long subsidyMinor;
    private String dueDate;
    private String graceEndsAt;
    private Map<String, Object> nextDue;
    private Map<String, Object> activationThreshold;
    private List<Map<String, Object>> instalments;
    private List<Map<String, Object>> payments;
    private List<Map<String, Object>> attempts;
    private List<Map<String, Object>> refunds;
    private Long refundableMinor;
    private List<Map<String, Object>> history;
    private Long version;
    private String createdAt;
}
