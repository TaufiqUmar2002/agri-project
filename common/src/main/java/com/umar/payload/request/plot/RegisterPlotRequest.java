package com.umar.payload.request.plot;

import com.umar.enums.plot.CaptureMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class RegisterPlotRequest {

    @NotBlank(message = "farmerId is required")
    private String farmerId;

    @Size(min = 1, max = 60, message = "name must be 1 to 60 characters")
    private String name;

    @NotNull(message = "captureMethod is required")
    private CaptureMethod captureMethod;

    private GeoJsonPolygon geometry;

    @Valid
    private List<GpsPoint> gpsTrack;

    private PinPoint pin;

    private Double areaHa;

    @Size(max = 5)
    private List<String> evidenceTokens;

    @NotNull(message = "capturedAt is required")
    private Instant capturedAt;

    @Size(max = 128)
    private String deviceId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeoJsonPolygon {
        private String type; // Polygon | MultiPolygon
        private List<List<List<Double>>> coordinates;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GpsPoint {
        private double lat;
        private double lon;
        private double accuracyMeters;
        private Instant timestamp;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PinPoint {
        private double lat;
        private double lon;
    }
}
