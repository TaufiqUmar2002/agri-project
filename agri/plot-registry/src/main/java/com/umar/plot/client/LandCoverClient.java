package com.umar.plot.client;

public interface LandCoverClient {
    LandCoverMetrics evaluate(String plotId, double lat, double lon);

    record LandCoverMetrics(boolean available, double croplandPercent, double excludedPercent, boolean supportedArea) {}
}
