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
public class SponsorBatchResponse {
    private String batchId;
    private String batchReference;
    private String status;
    private String consolidatedInvoiceId;
    private Long totalMinor;
    private String currency;
    private Integer accepted;
    private Integer rejected;
    private List<Map<String, Object>> results;
    private String dueDate;
    private Map<String, Object> settlementInstructions;
    private Map<String, Object> creditExposure;
}
