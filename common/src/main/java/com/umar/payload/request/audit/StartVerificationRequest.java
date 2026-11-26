package com.umar.payload.request.audit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StartVerificationRequest {
    @NotBlank
    private String scope; // CHAIN | RANGE | CLAIM | ALL_CHAINS
    private String chainId;
    private Long fromSeq;
    private Long toSeq;
    private String claimId;
    @NotBlank
    private String mode; // QUICK | FULL
    private Boolean compareWriteOnce = true;
    @NotBlank
    private String reason;
    @NotBlank
    @Size(min = 10, max = 500)
    private String note;
}
