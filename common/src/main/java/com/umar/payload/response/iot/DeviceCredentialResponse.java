package com.umar.payload.response.iot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DeviceCredentialResponse {
    private String deviceId;
    private String status;
    private Map<String, Object> credential;
    private String enrolmentToken;
    private Map<String, Object> rotation;
    private String revokedAt;
    private int version;
}
