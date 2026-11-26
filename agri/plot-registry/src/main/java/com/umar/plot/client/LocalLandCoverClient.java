package com.umar.plot.client;

import lombok.Setter;
import org.springframework.stereotype.Component;

@Component
public class LocalLandCoverClient implements LandCoverClient {

    @Setter private volatile boolean available = true;
    @Setter private volatile double defaultCroplandPercent = 94.0;
    @Setter private volatile double defaultExcludedPercent = 0.0;
    @Setter private volatile boolean defaultSupportedArea = true;

    @Override
    public LandCoverMetrics evaluate(String plotId, double lat, double lon) {
        if (!available) {
            return new LandCoverMetrics(false, 0.0, 0.0, true);
        }
        return new LandCoverMetrics(true, defaultCroplandPercent, defaultExcludedPercent, defaultSupportedArea);
    }
}
