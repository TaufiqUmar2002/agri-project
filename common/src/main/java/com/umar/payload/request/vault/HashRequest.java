package com.umar.payload.request.vault;

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
public class HashRequest {

    @NotBlank(message = "Data class is required")
    private String dataClass;

    @NotBlank(message = "Purpose is required")
    private String purpose;

    @NotBlank(message = "Context reference is required")
    private String contextRef;

    @NotEmpty(message = "At least one value is required")
    @Size(max = 100, message = "Maximum 100 values allowed")
    private List<String> values;
}
