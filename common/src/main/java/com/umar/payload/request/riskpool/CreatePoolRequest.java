package com.umar.payload.request.riskpool;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
public class CreatePoolRequest {
    @NotBlank
    private String poolCode;
    @NotNull
    private Map<String, String> name;
    @NotBlank
    private String insurerRef;
    @NotBlank
    private String countryCode;
    @NotBlank
    private String currency;
    @NotEmpty
    private List<Map<String, Object>> mappings;
    @NotNull
    private Map<String, Object> accounting;
    @NotNull
    private Map<String, Object> limits;
    private Map<String, Object> capitalMemo;
    @NotNull
    private LocalDate effectiveFrom;
    private List<String> evidenceTokens;
}
