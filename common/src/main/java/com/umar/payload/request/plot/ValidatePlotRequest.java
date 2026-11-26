package com.umar.payload.request.plot;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidatePlotRequest {
    private String ruleSetVersion;
    private Boolean dryRun;
    @Size(max = 200)
    private String reason;
}
