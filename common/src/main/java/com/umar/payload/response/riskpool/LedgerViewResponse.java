package com.umar.payload.response.riskpool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class LedgerViewResponse {
    private String poolId;
    private String view;
    private String asOf;
    private String period;
    private String currency;
    private List<Map<String, Object>> entries;
    private List<Map<String, Object>> balances;
    private Map<String, Object> trialBalance;
    private Map<String, Object> statement;
    private Map<String, Object> integrity;
    private Map<String, Object> export;
    private int page;
    private int size;
    private long totalElements;
}
