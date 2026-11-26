package com.umar.payload.request.integration;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class InboundDataRequest {
    @NotEmpty
    private List<Map<String, Object>> records;
}
