package com.umar.payload.request.claims;

import com.umar.enums.claims.ClaimDisputeReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateDisputeRequest {
    @NotNull
    private ClaimDisputeReason reasonCode;
    @NotBlank
    @Size(min = 20, max = 1500)
    private String explanation;
    private List<String> photoTokens;
    private String preferredLanguage;
}
