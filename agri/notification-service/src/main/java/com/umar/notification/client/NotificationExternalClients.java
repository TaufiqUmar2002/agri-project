package com.umar.notification.client;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Component
public class NotificationExternalClients {

    @Data
    public static class FarmerContact {
        private final String farmerId;
        private final String language;
        private final String msisdnMasked;
        private final String msisdnHash;
        private final String county;
        private final String subCounty;
        private final String ward;
        private final boolean whatsappCapable;
        private final String activePolicyId;
        private final String plotId;
    }

    private final Map<String, FarmerContact> farmers = new ConcurrentHashMap<>();
    private final Map<String, String> vaultSecrets = new ConcurrentHashMap<>();
    private final Map<String, List<String>> sentMessages = new ConcurrentHashMap<>();

    public NotificationExternalClients() {
        seedFarmer("frm_001", "sw", "+2547***001", sha256("+254712000001"), "Nakuru", "Njoro", "Mau Narok", true, "pol_001", "plt_001");
        seedFarmer("frm_002", "en", "+2547***002", sha256("+254712000002"), "Uasin Gishu", "Soy", "Kipsomba", false, "pol_002", "plt_002");
        seedFarmer("frm_003", "sw", "+2547***003", sha256("+254712000003"), "Nakuru", "Molo", "Elburgon", true, "pol_003", "plt_003");
    }

    public void seedFarmer(String farmerId, String lang, String masked, String msisdnHash,
                           String county, String subCounty, String ward, boolean whatsappCapable,
                           String policyId, String plotId) {
        farmers.put(farmerId, new FarmerContact(farmerId, lang, masked, msisdnHash, county, subCounty, ward, whatsappCapable, policyId, plotId));
    }

    public Optional<FarmerContact> findFarmerById(String farmerId) {
        return Optional.ofNullable(farmers.get(farmerId));
    }

    public Optional<FarmerContact> findFarmerByMsisdn(String msisdn) {
        String hash = sha256(msisdn);
        return farmers.values().stream().filter(f -> f.getMsisdnHash().equals(hash)).findFirst();
    }

    public Optional<FarmerContact> findFarmerByMsisdnHash(String hash) {
        return farmers.values().stream().filter(f -> f.getMsisdnHash().equals(hash)).findFirst();
    }

    public List<FarmerContact> resolveAudience(Map<String, Object> audience) {
        if (audience == null || audience.isEmpty()) {
            return List.copyOf(farmers.values());
        }
        String county = audience.get("county") != null ? String.valueOf(audience.get("county")) : null;
        return farmers.values().stream()
                .filter(f -> county == null || county.equalsIgnoreCase(f.getCounty()))
                .toList();
    }

    public String storeSecret(String secretValue) {
        String ref = "vsec_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        vaultSecrets.put(ref, secretValue);
        return ref;
    }

    public Optional<String> resolveSecret(String ref) {
        return Optional.ofNullable(vaultSecrets.get(ref));
    }

    public void shredSecret(String ref) {
        if (ref != null) {
            vaultSecrets.remove(ref);
        }
    }

    public boolean hasSecret(String ref) {
        return ref != null && vaultSecrets.containsKey(ref);
    }

    public String dispatchToAggregator(String aggregator, String channel, String notificationId, String renderedBody) {
        String providerMsgId = "prov_" + channel.toLowerCase() + "_" + UUID.randomUUID().toString().substring(0, 8);
        sentMessages.computeIfAbsent(notificationId, k -> new ArrayList<>()).add(channel + ":" + renderedBody);
        return providerMsgId;
    }

    public void confirmPlotViaUssd(String plotId, String farmerId) {
        // Simulates calling Plot Registry / Claims confirmation endpoint
    }

    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String hmacSha256(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : raw) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
