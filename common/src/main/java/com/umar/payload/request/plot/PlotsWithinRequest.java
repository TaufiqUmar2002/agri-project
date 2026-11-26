package com.umar.payload.request.plot;

import com.umar.enums.plot.MatchMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlotsWithinRequest {

    @NotNull(message = "polygon is required")
    private RegisterPlotRequest.GeoJsonPolygon polygon;

    private Instant asOf;
    private MatchMode matchMode;
    private Double minOverlapPercent;
    private String coverageStatus; // ACTIVE | ANY
    private List<String> cropCodes;
    private List<String> regionCodes;
    private Boolean countOnly;

    @Min(1)
    @Max(5000)
    private Integer limit;
    private String cursor;
    private Double simplifyToleranceMeters;
}
