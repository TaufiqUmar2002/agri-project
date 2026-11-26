package com.umar.payload.request.riskpool;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ClosePeriodRequest {
    @NotBlank
    private String action; // CLOSE | REOPEN
    private List<String> acceptedBreaks;
    private String reasonCode;
    @NotBlank
    @Size(min = 20, max = 1000)
    private String note;
}
