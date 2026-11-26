package com.umar.payload.request.claims;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConfirmPayoutRequest {
    @NotBlank
    private String stepUpToken;
    @NotNull
    private Long acknowledgedAmountMinor;
    @NotBlank
    private String channel; // APP | USSD | WEB
}
