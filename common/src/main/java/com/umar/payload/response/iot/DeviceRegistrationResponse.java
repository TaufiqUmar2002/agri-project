package com.umar.payload.response.iot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DeviceRegistrationResponse {
    private String deviceId;
    private String serialNumber;
    private String status;
    private Map<String, Object> binding;
    private Map<String, Object> mqtt;
    private String enrolmentToken;
    private String enrolmentExpiresAt;
    private Map<String, Object> calibration;
    private int version;
}
