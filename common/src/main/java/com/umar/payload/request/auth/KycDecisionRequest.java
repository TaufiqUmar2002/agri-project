package com.umar.payload.request.auth;

import com.umar.enums.auth.KycDecisionType;
import com.umar.enums.auth.KycTier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycDecisionRequest {

    @NotNull(message = "decision is required")
    private KycDecisionType decision;

    private KycTier approvedTier;

    private List<String> reasonCodes;

    private ReviewChecklist checklist;

    @NotBlank(message = "note is required")
    @Size(max = 1000, message = "note cannot exceed 1000 characters")
    private String note;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewChecklist {
        private Boolean nameMatch;
        private Boolean photoMatch;
        private Boolean documentAuthentic;
        private Boolean duplicateResolved;
    }
}
