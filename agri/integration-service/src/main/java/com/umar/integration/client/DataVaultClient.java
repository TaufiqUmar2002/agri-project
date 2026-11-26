package com.umar.integration.client;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Abstraction for Data Vault (gRPC/mTLS in production, local in-memory vault in dev/test).
 * Secrets, signing keys, and artifacts never live in hub_db.
 */
public interface DataVaultClient {

    GeneratedKey generateWebhookSigningKey(String subscriptionId, String partnerId);

    String signPayload(String keyId, String timestamp, String rawBody);

    boolean verifySecretOwned(String secretRef, String ownerId, String dataClass, String purpose);

    Map<String, String> injectSecrets(List<String> secretRefs, String ownerId);

    String storeDocument(String dataClass, String purpose, String ownerId, String content, String contentType);

    Map<String, String> verifyDocuments(List<String> documentTokens, String purpose);

    void enableTokenization(String connectorId, List<String> piiFields);

    String tokenize(String dataClass, String value, String subjectRef, String purpose);

    String partnerScopedHash(String partnerId, String internalId);

    record GeneratedKey(String keyId, String revealOnceSecret, Instant createdAt) {}
}
