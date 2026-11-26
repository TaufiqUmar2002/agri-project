package com.umar.plot.client;

import java.util.UUID;

public interface FarmerProfileClient {
    FarmerStanding getFarmerStanding(UUID farmerId);

    record FarmerStanding(boolean exists, String status, String userId) {}
}
