package com.umar.payload.request.plot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OverlapCheckRequest {
    private String plotId;
    private RegisterPlotRequest.GeoJsonPolygon geometry;
    private String farmerId;
    private Boolean includeOwn;
    private Boolean applyHold;
}
