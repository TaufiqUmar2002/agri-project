package com.umar.payload.response.trigger;

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
public class SimulationResponse {
    private String simulationId;
    private String status;
    private String ruleId;
    private Integer ruleVersion;
    private String definitionHash;
    private int seasonsCovered;
    private List<String> regionCodes;
    private Double expectedLossRatio;
    private Double burningCostPercent;
    private Double dataCoveragePercent;
    private Map<String, Object> summary;
    private List<Map<String, Object>> perSeasonResults;
    private List<Map<String, Object>> comparison;
    private List<Map<String, Object>> sensitivityResults;
    private String reportToken;
    private String validUntil;
    private String createdAt;
}
