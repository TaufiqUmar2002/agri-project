package com.umar.payload.response.iot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DeviceCalibrationResponse {
    private String deviceId;
    private int calibrationVersion;
    private String status;
    private List<Map<String, Object>> channels;
    private String effectiveFrom;
    private List<String> approvalsRequired;
    private String proposalHash;
    private Map<String, Object> reprocess;
    private int version;
}
