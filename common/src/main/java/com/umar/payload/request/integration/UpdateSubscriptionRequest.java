package com.umar.payload.request.integration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateSubscriptionRequest {
    @NotBlank
    private String action;
    private String status;
    @NotBlank @Size(min = 10, max = 300)
    private String reason;
}
