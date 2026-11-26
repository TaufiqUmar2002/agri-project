package com.umar.payload.request.vault;

import com.umar.enums.vault.ReturnMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class DeTokenizeRequest {

    @NotBlank(message = "Purpose is required")
    private String purpose;

    @NotBlank(message = "Context reference is required")
    private String contextRef;

    @NotEmpty(message = "Tokens array cannot be empty")
    @Size(max = 50, message = "Cannot process more than 50 tokens in a single request")
    private List<String> tokens;

    @Builder.Default
    private String returnMode = ReturnMode.VALUE.toString();


}
