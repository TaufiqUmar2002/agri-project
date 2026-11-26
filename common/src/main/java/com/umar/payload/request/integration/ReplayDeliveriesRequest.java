package com.umar.payload.request.integration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ReplayDeliveriesRequest {
    private String subscriptionId;
    private String from;
    private String to;
    private OffsetSpec fromOffset;
    private OffsetSpec toOffset;
    private List<String> eventTypes;
    private boolean includeDelivered = false;
    @NotBlank @Size(min = 10, max = 300)
    private String reason;

    @Data
    public static class OffsetSpec {
        private Integer partition;
        private Long offset;
    }
}
