package com.umar.payload.request.claims;

import com.umar.enums.claims.RetryRoute;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RetryClaimRequest {
    @NotNull
    private RetryRoute route;
    private Boolean useCurrentDestination;
    private String destinationId;
    private String farmerStepUpToken;
    private String agentAreaCode;
    @NotBlank
    @Size(min = 10, max = 500)
    private String reason;
}
