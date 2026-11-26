package com.umar.payload.request.vault;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class SignRequest {

    @NotBlank(message = "Secret reference is required")
    private String secretRef;

    private String algorithm;

    @NotBlank(message = "Purpose is required")
    private String purpose;

    @NotBlank(message = "Context reference is required")
    private String contextRef;

    @NotBlank(message = "Payload is required")
    private String payload; // base64

    private Integer version;
}
