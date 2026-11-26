package com.umar.payload.response.farmer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewWalletResponse {
    private String walletId;
    private String status;
    private Boolean secondReviewPending;
    private String effectiveFrom;
    private String decidedBy;
    private String decidedAt;
}
