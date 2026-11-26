package com.umar.payload.response.plot;

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
public class ValidatePlotResponse {
    private String plotId;
    private String outcome;
    private List<String> reasonCodes;
    private Map<String, Object> metrics;
    private String ruleSetVersion;
    private boolean dryRun;
    private String validatedAt;
}
