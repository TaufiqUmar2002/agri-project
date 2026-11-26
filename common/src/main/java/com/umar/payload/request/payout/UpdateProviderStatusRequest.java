package com.umar.payload.request.payout;

import com.umar.enums.payout.ProviderStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProviderStatusRequest {
    @NotNull
    private ProviderStatus status;
    private Integer version;
    @NotBlank
    @Size(min = 10, max = 500)
    private String reason;
}
