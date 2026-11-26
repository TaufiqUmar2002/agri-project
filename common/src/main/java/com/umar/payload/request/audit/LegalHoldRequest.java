package com.umar.payload.request.audit;

import com.umar.enums.audit.HoldAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class LegalHoldRequest {
    @NotNull
    private HoldAction action;
    private Map<String, Object> scope;
    private String caseRef;
    private String reasonCode;
    @NotBlank
    @Size(min = 10, max = 1000)
    private String note;
    private String holdId;
    private String approveProposal;
}
