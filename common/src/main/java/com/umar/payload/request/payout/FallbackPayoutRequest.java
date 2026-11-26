package com.umar.payload.request.payout;

import com.umar.enums.payout.FallbackAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FallbackPayoutRequest {
    @NotNull
    private FallbackAction action;
    private String targetConnection;
    private String agentId;
    private String agentAreaCode;
    @NotBlank
    @Size(min = 10, max = 500)
    private String reason;
}
