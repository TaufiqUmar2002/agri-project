package com.umar.auth.client;

import com.umar.auth.exception.AuthApiException;
import com.umar.auth.security.CryptoHashService;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class LocalDataVaultClient implements DataVaultClient {

    private final CryptoHashService cryptoHashService;
    private final Map<String, byte[]> storedDocs = new ConcurrentHashMap<>();
    private final Set<UUID> shreddedSubjects = ConcurrentHashMap.newKeySet();
    private final Map<UUID, List<String>> legalHolds = new ConcurrentHashMap<>();

    @Setter
    private volatile boolean unavailable = false;

    @Override
    public String tokenize(String dataClass, String value, String subjectRef, String purpose) {
        ensureAvailable();
        String prefix = switch (dataClass) {
            case "PHONE" -> "vt_phone_";
            case "ID_NUMBER" -> "vt_id_";
            default -> "vt_val_";
        };
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    @Override
    public String computeKeyedHash(String value, String purpose) {
        ensureAvailable();
        return cryptoHashService.hmacSha256(purpose + ":" + value);
    }

    @Override
    public String storeDocument(String dataClass, String subjectRef, String purpose, String filename, byte[] bytes) {
        ensureAvailable();
        String token = "vt_doc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        storedDocs.put(token, bytes);
        return token;
    }

    @Override
    public boolean verifyDocumentExists(String documentToken) {
        ensureAvailable();
        if (documentToken == null) return false;
        return storedDocs.containsKey(documentToken) || documentToken.startsWith("vt_doc_");
    }

    @Override
    public void shredSubject(UUID userId, String reason) {
        ensureAvailable();
        shreddedSubjects.add(userId);
    }

    @Override
    public List<String> listActiveLegalHolds(UUID userId) {
        ensureAvailable();
        return legalHolds.getOrDefault(userId, List.of());
    }

    public void addLegalHold(UUID userId, String holdId) {
        legalHolds.computeIfAbsent(userId, k -> new ArrayList<>()).add(holdId);
    }

    public void clearLegalHolds(UUID userId) {
        legalHolds.remove(userId);
    }

    public boolean isShredded(UUID userId) {
        return shreddedSubjects.contains(userId);
    }

    private void ensureAvailable() {
        if (unavailable) {
            throw new AuthApiException(HttpStatus.SERVICE_UNAVAILABLE, "VAULT_UNAVAILABLE", "Data Vault unavailable");
        }
    }
}
