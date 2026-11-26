package com.umar.iot.client;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class IotExternalClients {

    public record PlotView(boolean active, String farmerId, String districtCode, double distanceMeters, boolean hasActivePolicy) {}

    @Getter @Setter private volatile boolean pkiAvailable = true;
    @Getter @Setter private volatile boolean vaultAvailable = true;

    private final Map<String, PlotView> plots = new ConcurrentHashMap<>();
    private final Set<String> validConsents = ConcurrentHashMap.newKeySet();
    private final Set<String> supportedModels = ConcurrentHashMap.newKeySet();
    {
        validConsents.addAll(List.of("cns_sensor_ok", "cns_sensor_2c07"));
        supportedModels.addAll(List.of("TB-250", "SM-100"));
    }

    public void registerPlot(String plotId, PlotView view) {
        plots.put(plotId, view);
    }

    public PlotView fetchPlot(String plotId) {
        return plots.get(plotId);
    }

    public void registerConsent(String consentRef) {
        validConsents.add(consentRef);
    }

    public boolean isConsentValid(String consentRef) {
        return validConsents.contains(consentRef);
    }

    public boolean isModelSupported(String model, String protocol) {
        return supportedModels.contains(model) && ("JSON_V1".equals(protocol) || "CBOR_V1".equals(protocol));
    }

    public String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
