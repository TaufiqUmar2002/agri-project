package com.umar.payload.response.iot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DeviceHealthResponse {
    private String deviceId;
    private String deviceType;
    private String model;
    private String status;
    private String connectivity;
    private String lastSeenAt;
    private String lastReadingAt;
    private List<Map<String, Object>> lastReadings;
    private Map<String, Object> battery;
    private Map<String, Object> signal;
    private Map<String, Object> quality;
    private Map<String, Object> clock;
    private List<String> alerts;
    private Map<String, Object> binding;
    private Integer calibrationVersion;
    private int version;
}
