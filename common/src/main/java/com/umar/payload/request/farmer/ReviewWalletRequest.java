package com.umar.payload.request.farmer;

import com.umar.enums.farmer.WalletReviewDecision;
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
public class ReviewWalletRequest {

    @NotNull(message = "decision is required")
    private WalletReviewDecision decision;

    private String reasonCode;

    @NotBlank(message = "note is required")
    @Size(max = 1000, message = "note cannot exceed 1000 characters")
    private String note;

    private List<String> evidence;
}
