package com.umar.payload.response.iot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DeviceBindingResponse {
    private String deviceId;
    private String status;
    private Map<String, Object> binding;
    private Map<String, Object> previousBinding;
    private String approvalStatus;
    private List<String> approvalsRequired;
    private String proposalHash;
    private List<String> warnings;
    private int version;
}
