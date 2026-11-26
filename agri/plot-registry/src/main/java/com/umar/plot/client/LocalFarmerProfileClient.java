package com.umar.plot.client;

import com.umar.plot.exception.PlotApiException;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LocalFarmerProfileClient implements FarmerProfileClient {

    @Setter private volatile boolean unavailable = false;
    private final Map<UUID, FarmerStanding> farmers = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.umar.integration.IntegrationPeerClient integrationPeerClient;

    @Override
    public FarmerStanding getFarmerStanding(UUID farmerId) {
        if (unavailable) {
            throw new PlotApiException(HttpStatus.SERVICE_UNAVAILABLE, "FARMER_SERVICE_UNAVAILABLE",
                    "Please try again in a moment");
        }
        if (integrationPeerClient != null) {
            integrationPeerClient.getFarmer(farmerId.toString());
        }
        return farmers.getOrDefault(farmerId, new FarmerStanding(true, "ACTIVE", farmerId.toString()));
    }

    public void registerFarmer(UUID farmerId, String status, String userId) {
        farmers.put(farmerId, new FarmerStanding(true, status, userId));
    }

    public void registerMissing(UUID farmerId) {
        farmers.put(farmerId, new FarmerStanding(false, null, null));
    }
}
