package com.umar.payload.request.audit;

import com.umar.enums.audit.FindingAction;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class FindingDispositionRequest {
    private String approveProposal;
    private FindingAction action;
    private String explanation;
    private List<String> evidenceRefs;
    private LocalDate acceptUntil;
    private Integer version;
}
