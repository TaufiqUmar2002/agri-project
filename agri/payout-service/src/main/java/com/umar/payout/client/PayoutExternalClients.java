package com.umar.payout.client;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PayoutExternalClients {

    public record ClaimInstructionView(boolean payable, long amountMinor, String currency,
                                       String route, boolean useCurrentDestination) {}

    public record DestinationView(boolean verified, String network, String token, String mask, String holdReason) {}

    public record ProviderDispatchResult(boolean accepted, String status, String providerRef, String reasonCode, boolean retryable) {}

    public record StatementLine(String providerRef, long amountMinor, long feeMinor, String currency, String status) {}

    @Getter @Setter
    private volatile boolean claimsServiceAvailable = true;
    @Getter @Setter
    private volatile boolean vaultAvailable = true;
    @Getter @Setter
    private volatile boolean probePass = true;
    @Getter @Setter
    private volatile boolean forceOutcomeUncertain = false;

    private final Map<String, ClaimInstructionView> instructions = new ConcurrentHashMap<>();
    private final Map<String, DestinationView> destinations = new ConcurrentHashMap<>();
    private final Map<String, String> providerSecrets = new ConcurrentHashMap<>(Map.of(
            "mpesa", "mpesa-payout-secret",
            "mtn_momo", "mtn-payout-secret",
            "airtel_money", "airtel-payout-secret",
            "cashout", "cashout-payout-secret"
    ));
    private final Map<String, ProviderDispatchResult> nextDispatchResult = new ConcurrentHashMap<>();
    private final Map<String, List<StatementLine>> statementFiles = new ConcurrentHashMap<>();
    private final Map<String, String> queriedProviderOutcome = new ConcurrentHashMap<>();

    public void registerInstruction(String claimId, ClaimInstructionView view) {
        instructions.put(claimId, view);
    }

    public ClaimInstructionView fetchPaymentInstruction(String claimId) {
        return instructions.getOrDefault(claimId, new ClaimInstructionView(true, 60_000L, "KES", "SAME_WALLET", false));
    }

    public void registerDestination(String farmerId, DestinationView view) {
        destinations.put(farmerId, view);
    }

    public DestinationView resolveDestination(String farmerId, String route) {
        if ("CASH_OUT_AGENT".equals(route)) {
            return new DestinationView(true, "CASHOUT", "tok_cash_" + farmerId, "CASH-OUT AGENT", null);
        }
        return destinations.getOrDefault(farmerId,
                new DestinationView(true, "MPESA", "vt_wal_" + farmerId, "MPESA ****4821", null));
    }

    public void setNextDispatchResult(String connection, ProviderDispatchResult res) {
        nextDispatchResult.put(connection, res);
    }

    public ProviderDispatchResult dispatchToProvider(String connection, String originatorRef, long amountMinor, String destToken) {
        ProviderDispatchResult custom = nextDispatchResult.remove(connection);
        if (custom != null) return custom;
        String provRef = connection.toUpperCase().substring(0, 2) + "_" + originatorRef.substring(0, Math.min(8, originatorRef.length()));
        return new ProviderDispatchResult(true, "PENDING", provRef, null, false);
    }

    public void setProviderStatusQueryResult(String providerRef, String outcome) {
        queriedProviderOutcome.put(providerRef, outcome);
    }

    public String queryProviderStatus(String providerRef) {
        if (forceOutcomeUncertain) return "UNKNOWN";
        return queriedProviderOutcome.getOrDefault(providerRef, "FAILED");
    }

    public void putStatementFile(String fileToken, List<StatementLine> lines) {
        statementFiles.put(fileToken, lines);
    }

    public List<StatementLine> getStatementLines(String fileToken) {
        return statementFiles.get(fileToken);
    }

    public String getProviderSecret(String provider) {
        return providerSecrets.get(provider.toLowerCase());
    }

    public String signHmacSha256(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "";
        }
    }

    public boolean verifySignature(String provider, String rawBody, String signature) {
        String secret = getProviderSecret(provider);
        if (secret == null || signature == null || signature.isBlank()) return false;
        String expected = signHmacSha256(secret, rawBody);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    public String maskProviderRef(String ref) {
        if (ref == null || ref.length() <= 4) return "****";
        return ref.substring(0, 2) + "***" + ref.substring(ref.length() - 4);
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
