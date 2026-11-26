package com.umar.payload.request.riskpool;

import com.umar.enums.riskpool.TreatyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
public class CreateTreatyRequest {
    @NotBlank
    private String treatyCode;
    private String baseTreatyId;
    @NotNull
    private TreatyType type;
    @NotEmpty
    private List<Map<String, Object>> placements;
    @NotNull
    private Map<String, Object> period;
    @NotNull
    private Map<String, Object> scope;
    @NotNull
    private Map<String, Object> terms;
    private Map<String, Object> premium;
    @NotNull
    private Map<String, Object> settlement;
    @NotBlank
    private String wordingToken;
    @NotNull
    private LocalDate effectiveFrom;
}
