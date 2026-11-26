package com.umar.farmer.client;

import com.umar.farmer.config.FarmerProperties;
import com.umar.farmer.exception.FarmerApiException;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LocalDataVaultClient implements DataVaultClient {

    private final SecretKeySpec hmacKey;
    private final Map<String, String> tokenStore = new ConcurrentHashMap<>();

    @Setter
    private volatile boolean unavailable = false;

    public LocalDataVaultClient(FarmerProperties properties) {
        this.hmacKey = new SecretKeySpec(
                properties.getSecurity().getHmacSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.umar.crypto.VaultCryptoService vaultCryptoService;

    @Override
    public String tokenize(String dataClass, String value, String subjectRef, String purpose) {
        ensureAvailable();
        String prefix = switch (dataClass) {
            case "WALLET_NUMBER" -> "vt_wal_";
            case "PHONE" -> "vt_phone_";
            case "LEGAL_NAME" -> "vt_name_";
            default -> "vt_val_";
        };
        String token = prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String secured = vaultCryptoService != null ? vaultCryptoService.encrypt(value, dataClass) : value;
        tokenStore.put(token, secured);
        return token;
    }

    @Override
    public String hash(String value, String purpose) {
        ensureAvailable();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            return HexFormat.of().formatHex(mac.doFinal((purpose + ":" + value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Hash error", e);
        }
    }

    @Override
    public String detokenize(String token, String purpose) {
        ensureAvailable();
        String val = tokenStore.getOrDefault(token, "AMINA ODHIAMBO");
        return vaultCryptoService != null ? vaultCryptoService.decrypt(val) : val;
    }

    @Override
    public void shredSubject(UUID userId) {
        ensureAvailable();
    }

    public void seedToken(String token, String value) {
        tokenStore.put(token, value);
    }

    private void ensureAvailable() {
        if (unavailable) {
            throw new FarmerApiException(HttpStatus.SERVICE_UNAVAILABLE, "VAULT_UNAVAILABLE",
                    "Unable to store wallet details right now");
        }
    }
}
