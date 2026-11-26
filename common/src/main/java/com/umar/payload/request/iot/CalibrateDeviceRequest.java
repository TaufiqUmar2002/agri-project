package com.umar.payload.request.iot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
public class CalibrateDeviceRequest {
    private String approveProposal;
    private List<Map<String, Object>> channels;
    private Instant effectiveFrom;
    private boolean reprocess;
    private String reason;
    private Map<String, Object> reference;
    private String note;
    private Integer version;
}
