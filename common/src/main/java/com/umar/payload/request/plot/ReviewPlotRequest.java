package com.umar.payload.request.plot;

import com.umar.enums.plot.PlotFlag;
import com.umar.enums.plot.PlotReviewDecision;
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
public class ReviewPlotRequest {

    @NotNull(message = "decision is required")
    private PlotReviewDecision decision;

    private List<PlotFlag> resolvedFlags;
    private List<String> reasonCodes;
    private List<String> evidence;

    @NotBlank(message = "note is required")
    @Size(max = 1000)
    private String note;
}
