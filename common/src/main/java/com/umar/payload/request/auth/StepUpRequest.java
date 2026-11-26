package com.umar.payload.request.auth;

import com.umar.enums.auth.StepUpActionClass;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepUpRequest {
    @NotNull(message = "actionClass is required")
    private StepUpActionClass actionClass;
    private String deviceId;
}
