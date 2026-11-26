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
public class PlotsWithinResponse {
    private List<Map<String, Object>> items;
    private String nextCursor;
    private String snapshotTime;
    private int matchedCount;
    private Map<String, Integer> excluded;
    private double totalAreaHa;
    private double simplifyToleranceMeters;
    private String ruleSetVersion;
}
