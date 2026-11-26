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
public class PlotResponse {
    private String plotId;
    private String farmerId;
    private String name;
    private String status;
    private Double areaHa;
    private String accuracyClass;
    private String captureMethod;
    private Map<String, Double> centroid;
    private Map<String, String> region;
    private Object geometry;
    private Map<String, Object> season;
    private Map<String, Object> coverage;
    private String monitoringStatus;
    private Map<String, Object> validation;
    private List<String> flags;
    private Map<String, Object> attributes;
    private String holdReason;
    private String nextAction;
    private Integer geometryVersion;
    private Integer version;
    private String createdAt;
}
