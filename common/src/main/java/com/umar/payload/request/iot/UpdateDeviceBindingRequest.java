package com.umar.payload.request.iot;

import com.umar.enums.iot.BindingAction;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
public class UpdateDeviceBindingRequest {
    private String approveProposal;
    private BindingAction action;
    private String plotId;
    private Map<String, Object> location;
    private Instant effectiveFrom;
    private String reasonCode;
    private String note;
    private Integer version;
}
