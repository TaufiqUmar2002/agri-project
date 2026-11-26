package com.umar.payload.request.vault;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class BreakGlassUseRequest {

    @NotBlank(message = "Operation is required")
    private String operation; // DETOKENIZE | VIEW_DOCUMENT

    private List<String> tokens;

    private String documentToken;
}
