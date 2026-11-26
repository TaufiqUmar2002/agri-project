package com.umar.payload.response.plot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewPlotResponse {
    private String plotId;
    private String status;
    private boolean secondReviewPending;
    private String decidedBy;
    private String decidedAt;
}
