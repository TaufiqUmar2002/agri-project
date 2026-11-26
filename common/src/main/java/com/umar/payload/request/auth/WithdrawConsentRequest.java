package com.umar.payload.request.auth;

import com.umar.enums.auth.RegistrationChannel;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawConsentRequest {

    private String reason; // NO_LONGER_NEEDED | PRIVACY | TOO_COSTLY | OTHER

    @NotNull(message = "channel is required")
    private RegistrationChannel channel;
}
