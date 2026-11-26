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
public class OverlapCheckResponse {
    private String result; // CLEAR | OVERLAP | DUPLICATE
    private Double thresholdPercent;
    private String heldPlotId;
    private String message;
    private List<Map<String, Object>> matches;
    private String checkedAt;
    private String ruleSetVersion;
}
