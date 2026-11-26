package com.umar.payload.request.claims;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResolveDisputeRequest {
    @NotBlank
    private String outcome; // UPHELD | REJECTED
    @NotBlank
    @Size(min = 20, max = 1500)
    private String explanation;
    private boolean evidenceReviewed;
    private boolean supplementary;
    private Long supplementaryAmountMinor;
    private String proposalHash;
}
