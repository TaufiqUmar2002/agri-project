package com.umar.billing.client;

import com.umar.enums.auth.DelegationScope;
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
public class BillingExternalClients {

    public record ProviderInitResult(boolean accepted, boolean reachable, String providerRef, String status, String reasonCode, String prompt) {}
    public record StatementLine(String providerTxnId, long amountMinor, String currency, String date, String paymentRef, String type) {}

    @Getter @Setter
    private volatile boolean vaultAvailable = true;
    @Getter @Setter
    private volatile boolean providerReachable = true;
    @Getter @Setter
    private volatile boolean providerRejectNext = false;
    @Getter @Setter
    private volatile boolean authAvailable = true;
    @Getter @Setter
    private volatile boolean preRefundHold = false;

    private final Map<String, String> vaultTokens = new ConcurrentHashMap<>();
    private final Map<String, String> providerSecrets = new ConcurrentHashMap<>(Map.of(
            "mpesa", "mpesa-local-callback-secret",
            "mtn_momo", "mtn-local-callback-secret",
            "airtel_money", "airtel-local-callback-secret"
    ));
    private final Map<String, List<StatementLine>> statementFiles = new ConcurrentHashMap<>();
    private final List<Map<String, Object>> preInvoiceExtraLines = new ArrayList<>();

    public void putStatementFile(String fileToken, String rawContent, List<StatementLine> parsedLines) {
        vaultTokens.put(fileToken, rawContent);
        statementFiles.put(fileToken, parsedLines);
    }

    public void addPreInvoiceLine(Map<String, Object> line) {
        preInvoiceExtraLines.add(line);
    }

    public List<Map<String, Object>> preInvoiceHook(String invoiceId, long premiumMinor) {
        return List.copyOf(preInvoiceExtraLines);
    }

    public boolean validateDelegation(UUID delegationId, UUID agentId, UUID farmerId, DelegationScope scope) {
        return authAvailable && delegationId != null && agentId != null && farmerId != null && scope == DelegationScope.POLICY_ASSIST;
    }

    public String tokenizePayerPhone(String rawNumber) {
        if (!vaultAvailable) return null;
        String token = "vt_ph_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        vaultTokens.put(token, rawNumber);
        return token;
    }

    public String maskPhone(String raw) {
        if (raw == null || raw.length() < 6) return "****";
        return raw.substring(0, Math.min(5, raw.length() - 3)) + "*****" + raw.substring(raw.length() - 3);
    }

    public boolean verifyEvidenceTokens(List<String> tokens) {
        return vaultAvailable;
    }

    public String getProviderSecret(String provider) {
        if (!vaultAvailable) return null;
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

    public boolean verifyProviderSignature(String provider, String rawBody, String signatureHex) {
        String secret = getProviderSecret(provider);
        if (secret == null || signatureHex == null || signatureHex.isBlank()) return false;
        String expected = signHmacSha256(secret, rawBody);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signatureHex.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    public ProviderInitResult startProviderCollection(String provider, String paymentRef, long amountMinor, String currency) {
        if (!providerReachable) {
            return new ProviderInitResult(false, false, null, "FAILED", "PROVIDER_UNAVAILABLE",
                    "Paybill 123456 Account " + paymentRef);
        }
        if (providerRejectNext) {
            providerRejectNext = false;
            return new ProviderInitResult(false, true, null, "FAILED", "INVALID_NUMBER", null);
        }
        String provRef = "pr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String prompt = "Enter your " + provider + " PIN to pay " + currency + " " + (amountMinor / 100) + " to AgriShield. Reference " + paymentRef + ".";
        return new ProviderInitResult(true, true, provRef, "PENDING_PROVIDER", null, prompt);
    }

    public String sendRefundDisbursement(String provider, long amountMinor, String destinationToken) {
        return "disb_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    public List<StatementLine> parseStatement(String fileToken, String expectedChecksum) {
        String raw = vaultTokens.getOrDefault(fileToken, "");
        if (expectedChecksum != null && !expectedChecksum.isBlank() && !raw.isEmpty()) {
            String actual = sha256(raw);
            if (!actual.equals(expectedChecksum)) {
                throw new IllegalArgumentException("CHECKSUM_MISMATCH");
            }
        }
        return statementFiles.getOrDefault(fileToken, List.of());
    }

    public String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "hash_err";
        }
    }

    public void reset() {
        vaultAvailable = true;
        providerReachable = true;
        providerRejectNext = false;
        authAvailable = true;
        preRefundHold = false;
        preInvoiceExtraLines.clear();
    }
}
