package com.umar.payload.request.policy;

import com.umar.enums.policy.ProductDecisionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class ProductDecisionRequest {
    @NotNull
    private ProductDecisionType decision;

    @NotBlank
    @Size(min = 20, max = 2000)
    private String note;

    private Map<String, Boolean> checklist;
}
