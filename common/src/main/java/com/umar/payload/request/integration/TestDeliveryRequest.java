package com.umar.payload.request.integration;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TestDeliveryRequest {
    @NotBlank
    private String eventType;
    private String variant = "TYPICAL";
    private String simulateFailure = "NONE";
}
