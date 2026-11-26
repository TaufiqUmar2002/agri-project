package com.umar.payload.response.payout;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutStatementImportResponse {
    private String importId;
    private String connection;
    private String statementDate;
    private Map<String, Object> lines;
    private Map<String, Object> amounts;
    private Map<String, Object> breaks;
    private String importedAt;
}
