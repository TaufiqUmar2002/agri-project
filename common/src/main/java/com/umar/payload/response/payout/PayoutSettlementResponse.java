package com.umar.payload.response.payout;

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
public class PayoutSettlementResponse {
    private String from;
    private String to;
    private String status;
    private List<Map<String, Object>> batches;
    private Map<String, Object> totals;
    private Map<String, Object> matchSummary;
    private String checksum;
    private String exportToken;
}
