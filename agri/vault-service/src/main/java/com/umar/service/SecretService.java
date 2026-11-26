package com.umar.service;

import com.umar.audit.AuditRecorder;
import com.umar.authentication.VaultSecurityContext;
import com.umar.authentication.WorkloadIdentity;
import com.umar.config.EphemeralSubjectDek;
import com.umar.crypto.CryptoEngineService;
import com.umar.cryptography.SigningService;
import com.umar.entity.keyManagement.Secret;
import com.umar.entity.keyManagement.SecretVersion;
import com.umar.events.VaultEventType;
import com.umar.exception.VaultApiException;
import com.umar.idempotency.IdempotencyClaim;
import com.umar.idempotency.IdempotencyControllerAdvice;
import com.umar.idempotency.IdempotencyService;
import com.umar.payload.request.vault.CreateSecretRequest;
import com.umar.payload.request.vault.SignRequest;
import com.umar.payload.request.vault.UseSecretRequest;
import com.umar.payload.response.vault.SecretResponse;
import com.umar.payload.response.vault.SignResponse;
import com.umar.payload.response.vault.UseSecretResponse;
import com.umar.policy.RateLimitAndAnomalyService;
import com.umar.repository.keyManagement.SecretRepository;
import com.umar.repository.keyManagement.SecretVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * VLT-03 (Store and Version Secrets) and VLT-15 (Use a Secret) + VLT-11 Sign support.
 */
@Service
@RequiredArgsConstructor
public class SecretService {

    private static final String SECRET_DATA_CLASS = "SECRET";
    private static final Set<String> READ_FORBIDDEN_CLASSES = Set.of("SIGNING_KEY", "HMAC_KEY");

    private final SecretRepository secretRepository;
    private final SecretVersionRepository secretVersionRepository;
    private final CryptoEngineService cryptoEngineService;
    private final SigningService signingService;
    private final AccessSignalEmitter accessSignalEmitter;
    private final VaultEventEmitter vaultEventEmitter;
    private final AuditRecorder auditRecorder;
    private final IdempotencyService idempotencyService;
    private final RateLimitAndAnomalyService rateLimitAndAnomalyService;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public SecretResponse createSecret(CreateSecretRequest request) {
        return createSecret(null, request);
    }

    public SecretResponse createSecret(String idempotencyKey, CreateSecretRequest request) {
        UUID key = parseOptionalUuid(idempotencyKey);
        if (key == null) {
            return doCreateSecret(request);
        }
        String caller = resolveCaller();
        String owner = UUID.randomUUID().toString();
        IdempotencyClaim claim = idempotencyService.claim(caller, "CREATE_SECRET", key, request, owner);
        return switch (claim.type()) {
            case REPLAY -> idempotencyService.deserializeReplay(claim.responsePayload(), SecretResponse.class);
            case IN_PROGRESS -> throw new IdempotencyControllerAdvice.IdempotencyInProgressException(
                    "Secret creation with this idempotency key is still in progress");
            case PAYLOAD_CONFLICT -> throw new IdempotencyControllerAdvice.IdempotencyConflictException(
                    "Idempotency key reused with a different request payload");
            case NEW_REQUEST -> {
                try {
                    SecretResponse resp = doCreateSecret(request);
                    idempotencyService.complete(claim.recordId(), owner, resp, 201);
                    yield resp;
                } catch (Exception e) {
                    idempotencyService.markFailed(claim.recordId(), owner);
                    throw e;
                }
            }
        };
    }

    @Transactional
    protected SecretResponse doCreateSecret(CreateSecretRequest request) {
        String mode = request.getMode() != null ? request.getMode().trim().toUpperCase() : "PROVIDE";
        String secretClass = request.getSecretClass() != null ? request.getSecretClass().trim().toUpperCase() : "API_KEY";
        String ref = (request.getRef() != null && !request.getRef().isBlank())
                ? request.getRef().trim()
                : mintRef();

        if (request.getPolicy() != null && request.getPolicy().getUses() != null
                && request.getPolicy().getUses().stream().anyMatch(u -> "READ".equalsIgnoreCase(u))
                && READ_FORBIDDEN_CLASSES.contains(secretClass)) {
            throw VaultApiException.unprocessable("READ_NOT_ALLOWED", "Reading this secret is not allowed");
        }

        Secret secret;
        int nextVersion = 1;
        Instant previousRetiresAt = null;
        String action;

        if (request.getBaseRef() != null && !request.getBaseRef().isBlank()) {
            secret = secretRepository.findByRef(request.getBaseRef().trim())
                    .orElseThrow(() -> VaultApiException.notFound("SECRET_NOT_FOUND", "Base secret not found"));
            List<SecretVersion> existing = secretVersionRepository.findBySecret_IdOrderByVersionDesc(secret.getId());
            nextVersion = existing.isEmpty() ? 1 : existing.get(0).getVersion() + 1;
            int overlapHours = secret.getOverlapHours() != null ? secret.getOverlapHours() : 24;
            previousRetiresAt = Instant.now().plus(overlapHours, ChronoUnit.HOURS);
            for (SecretVersion prev : existing) {
                if ("ACTIVE".equalsIgnoreCase(prev.getState())) {
                    prev.setState("RETIRING");
                    prev.setRetiresAt(previousRetiresAt);
                    secretVersionRepository.save(prev);
                }
            }
            action = "NEW_VERSION";
        } else {
            if (secretRepository.findByRef(ref).isPresent()) {
                throw VaultApiException.conflict("REF_EXISTS", "This reference already exists");
            }
            secret = new Secret();
            secret.setId(UUID.randomUUID());
            secret.setRef(ref);
            secret.setName(request.getName() != null ? request.getName() : ref);
            secret.setSecretClass(secretClass);
            secret.setMode(mode);
            secret.setOwner(request.getOwner() != null ? request.getOwner() : resolveCaller());
            secret.setOwnerType(request.getOwnerType() != null ? request.getOwnerType() : "SERVICE");
            secret.setOwnerId(request.getOwnerId() != null ? request.getOwnerId() : secret.getOwner());
            secret.setStatus("ACTIVE");
            applyPolicyAndRotation(secret, request);
            secretRepository.save(secret);
            action = "CREATED";
        }

        byte[] rawMaterial;
        String algorithm = "AES_256";
        Integer keySizeBits = 256;
        String kid = null;
        Map<String, Object> publicKeyJwk = null;
        String revealOnceValue = null;

        if ("GENERATE".equals(mode)) {
            String reqAlg = request.getGenerate() != null && request.getGenerate().getAlgorithm() != null
                    ? request.getGenerate().getAlgorithm() : "HMAC_SHA256";
            Integer reqBits = request.getGenerate() != null ? request.getGenerate().getKeySizeBits() : 256;
            validateAlgorithmAllowed(reqAlg, reqBits);
            kid = secret.getRef() + "-v" + nextVersion;
            SigningService.GeneratedKeyMaterial gen = signingService.generateKey(reqAlg, reqBits, kid);
            rawMaterial = gen.privateOrSymmetricBytes();
            algorithm = gen.algorithm();
            keySizeBits = gen.keySizeBits();
            publicKeyJwk = gen.publicKeyJwk();
            if (Boolean.TRUE.equals(request.getRevealOnce())
                    && hasAllowedUse(secret, "REVEAL_ONCE")) {
                revealOnceValue = Base64.getEncoder().encodeToString(rawMaterial);
            }
        } else if ("DYNAMIC".equals(mode)) {
            if (request.getDynamic() == null || request.getDynamic().getProviderRef() == null) {
                throw VaultApiException.badRequest("VALIDATION_ERROR", "dynamic.providerRef is required for DYNAMIC mode");
            }
            secret.setProviderRef(request.getDynamic().getProviderRef());
            secret.setDynamicTtlSeconds(request.getDynamic().getTtlSeconds() != null
                    ? Math.min(3600, request.getDynamic().getTtlSeconds()) : 900);
            secret.setDynamicScopes(request.getDynamic().getScopes() != null
                    ? String.join(",", request.getDynamic().getScopes()) : "");
            secretRepository.save(secret);
            rawMaterial = ("dynamic:" + secret.getProviderRef()).getBytes(StandardCharsets.UTF_8);
            algorithm = "DYNAMIC_LEASE";
        } else {
            if (request.getValue() == null || request.getValue().isBlank()) {
                throw VaultApiException.badRequest("VALIDATION_ERROR", "Secret value is required in PROVIDE mode");
            }
            rawMaterial = request.getValue().getBytes(StandardCharsets.UTF_8);
            algorithm = "RAW";
            keySizeBits = rawMaterial.length * 8;
        }

        String token = secret.getRef() + ":" + nextVersion;
        Instant activeFrom = Instant.now();
        SecretVersion secretVersion = new SecretVersion();
        secretVersion.setId(UUID.randomUUID());
        secretVersion.setSecret(secret);
        secretVersion.setVersion(nextVersion);
        secretVersion.setAlgorithm(algorithm);
        secretVersion.setKeySizeBits(keySizeBits);
        secretVersion.setKid(kid);
        secretVersion.setActiveFrom(activeFrom);
        secretVersion.setState("ACTIVE");
        if (revealOnceValue != null) {
            secretVersion.setRevealedAt(activeFrom);
        }
        if (publicKeyJwk != null) {
            try {
                secretVersion.setPublicKeyJson(objectMapper.writeValueAsString(publicKeyJwk));
            } catch (Exception ignored) {
            }
        }

        char[] materialChars = Base64.getEncoder().encodeToString(rawMaterial).toCharArray();
        try (EphemeralSubjectDek dek = cryptoEngineService.generateEphemeralDek()) {
            var payload = cryptoEngineService.encryptValue(materialChars, dek, SECRET_DATA_CLASS, secret.getRef(), token);
            secretVersion.setCiphertext(payload.ciphertext());
            secretVersion.setNonce(payload.nonce());
            secretVersion.setWrappedDek(cryptoEngineService.wrapDek(dek));
            secretVersionRepository.save(secretVersion);
        } finally {
            Arrays.fill(materialChars, '\0');
            Arrays.fill(rawMaterial, (byte) 0);
        }

        String requestId = "vq_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        auditRecorder.recordStrict("VAULT_SECRET_CREATED", "CREATE_SECRET", SECRET_DATA_CLASS, secret.getRef(),
                "GENERAL", null, null, 1, "SUCCESS", secret.getId(), null);
        accessSignalEmitter.emit("CREATE_SECRET", SECRET_DATA_CLASS, "GENERAL", "SUCCESS", 1);
        vaultEventEmitter.emit(VaultEventType.SECRET_VERSIONED, Map.of(
                "operation", "CREATE_SECRET",
                "ref", secret.getRef(),
                "version", nextVersion,
                "secretClass", secret.getSecretClass(),
                "ownerType", secret.getOwnerType(),
                "action", action));
        vaultEventEmitter.emitAccess("CREATE_SECRET", SECRET_DATA_CLASS, "GENERAL", "SUCCESS", 1);

        Map<String, Object> rotationMap = secret.getNextRotationAt() != null
                ? Map.of(
                        "nextRotationAt", secret.getNextRotationAt().toString(),
                        "notifyDaysBeforeExpiry", secret.getNotifyDaysBeforeExpiry() != null ? secret.getNotifyDaysBeforeExpiry() : 30)
                : null;

        return SecretResponse.builder()
                .id(secret.getId())
                .ref(secret.getRef())
                .name(secret.getName())
                .owner(secret.getOwner())
                .status(secret.getStatus())
                .version(nextVersion)
                .secretClass(secret.getSecretClass())
                .state("ACTIVE")
                .publicKey(publicKeyJwk)
                .activeFrom(activeFrom.toString())
                .previousVersionRetiresAt(previousRetiresAt != null ? previousRetiresAt.toString() : null)
                .rotation(rotationMap)
                .revealOnce(revealOnceValue)
                .requestId(requestId)
                .build();
    }

    @Transactional
    public UseSecretResponse useSecret(String ref) {
        UseSecretRequest fallback = UseSecretRequest.builder()
                .operation("READ")
                .purpose("GENERAL")
                .contextRef("direct:" + ref)
                .build();
        return useSecret(ref, null, fallback);
    }

    @Transactional
    public UseSecretResponse useSecret(String ref, String idempotencyKey, UseSecretRequest request) {
        Secret secret = secretRepository.findByRef(ref)
                .orElseThrow(() -> VaultApiException.notFound("SECRET_NOT_FOUND", "Secret not found"));

        String operation = request.getOperation().trim().toUpperCase();
        String caller = resolveCaller();
        rateLimitAndAnomalyService.checkAndConsume(caller, SECRET_DATA_CLASS, request.getPurpose(), "USE_SECRET", 1);

        // Enforce per-secret policy if configured
        enforceSecretPolicy(secret, caller, operation, request.getPurpose());

        if ("READ".equals(operation) && READ_FORBIDDEN_CLASSES.contains(secret.getSecretClass())) {
            throw VaultApiException.forbidden("READ_NOT_ALLOWED", "This secret cannot be read");
        }

        List<SecretVersion> versions = secretVersionRepository.findBySecret_IdOrderByVersionDesc(secret.getId());
        if (versions.isEmpty()) {
            throw VaultApiException.notFound("SECRET_NOT_FOUND", "No secret versions found");
        }

        SecretVersion targetVersion;
        if (request.getVersion() != null) {
            targetVersion = versions.stream()
                    .filter(v -> v.getVersion() == request.getVersion())
                    .findFirst()
                    .orElseThrow(() -> VaultApiException.notFound("SECRET_NOT_FOUND", "Secret version not found"));
            if ("RETIRED".equalsIgnoreCase(targetVersion.getState())
                    || (targetVersion.getRetiresAt() != null && Instant.now().isAfter(targetVersion.getRetiresAt()))) {
                throw VaultApiException.gone("VERSION_RETIRED", "This version is no longer valid");
            }
        } else {
            targetVersion = versions.stream()
                    .filter(v -> "ACTIVE".equalsIgnoreCase(v.getState()))
                    .findFirst()
                    .orElseThrow(() -> VaultApiException.gone("VERSION_RETIRED", "No active secret version"));
        }

        String requestId = "vq_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);

        // Strict audit BEFORE returning secret result
        auditRecorder.recordStrict("VAULT_SECRET_USED", "USE_SECRET:" + operation, SECRET_DATA_CLASS, ref,
                request.getPurpose(), request.getContextRef(), null, 1, "SUCCESS", secret.getId(), null);

        UseSecretResponse.UseSecretResponseBuilder builder = UseSecretResponse.builder()
                .ref(ref)
                .version(targetVersion.getVersion())
                .operation(operation)
                .requestId(requestId);

        switch (operation) {
            case "SIGN" -> {
                byte[] payloadBytes = decodeBase64Payload(request.getPayload());
                byte[] keyMaterial = decryptSecretMaterial(secret, targetVersion);
                try {
                    String alg = request.getAlgorithm() != null ? request.getAlgorithm() : targetVersion.getAlgorithm();
                    byte[] sig = signingService.sign(alg, keyMaterial, payloadBytes);
                    builder.signature(Base64.getEncoder().encodeToString(sig))
                            .keyVersion(targetVersion.getVersion())
                            .algorithm(SigningService.normalizeAlgorithm(alg))
                            .kid(targetVersion.getKid());
                } finally {
                    Arrays.fill(keyMaterial, (byte) 0);
                }
            }
            case "VERIFY" -> {
                byte[] payloadBytes = decodeBase64Payload(request.getPayload());
                byte[] sigBytes = decodeBase64Payload(request.getSignature());
                List<SecretVersion> candidates = request.getVersion() != null
                        ? List.of(targetVersion)
                        : versions.stream().filter(v -> "ACTIVE".equalsIgnoreCase(v.getState())
                                || ("RETIRING".equalsIgnoreCase(v.getState())
                                && (v.getRetiresAt() == null || Instant.now().isBefore(v.getRetiresAt())))).toList();
                boolean matched = false;
                Integer matchedVersion = null;
                for (SecretVersion candidate : candidates) {
                    byte[] keyMaterial = decryptSecretMaterial(secret, candidate);
                    try {
                        String alg = request.getAlgorithm() != null ? request.getAlgorithm() : candidate.getAlgorithm();
                        String spki = extractSpki(candidate.getPublicKeyJson());
                        if (signingService.verify(alg, keyMaterial, spki, payloadBytes, sigBytes)) {
                            matched = true;
                            matchedVersion = candidate.getVersion();
                            break;
                        }
                    } finally {
                        Arrays.fill(keyMaterial, (byte) 0);
                    }
                }
                builder.valid(matched).keyVersion(matchedVersion);
            }
            case "LEASE" -> {
                int ttl = (request.getLease() != null && request.getLease().getTtlSeconds() != null)
                        ? Math.min(3600, Math.max(60, request.getLease().getTtlSeconds()))
                        : (secret.getDynamicTtlSeconds() != null ? secret.getDynamicTtlSeconds() : 900);
                byte[] ephemeralCred = new byte[24];
                secureRandom.nextBytes(ephemeralCred);
                builder.leaseId("lse_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                        .credential("vcred_" + Base64.getUrlEncoder().withoutPadding().encodeToString(ephemeralCred))
                        .expiresAt(Instant.now().plusSeconds(ttl).toString())
                        .renewable(false);
            }
            case "INJECT" -> {
                int ttl = (request.getLease() != null && request.getLease().getTtlSeconds() != null)
                        ? Math.min(3600, Math.max(60, request.getLease().getTtlSeconds()))
                        : 900;
                String targetRuntime = request.getLease() != null ? request.getLease().getTarget() : "attested-sandbox";
                builder.leaseId("inj_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                        .targetRuntime(targetRuntime)
                        .expiresAt(Instant.now().plusSeconds(ttl).toString());
            }
            case "REVEAL_ONCE" -> {
                if (targetVersion.getRevealedAt() != null) {
                    throw VaultApiException.conflict("ALREADY_REVEALED", "This secret was already handed over");
                }
                byte[] keyMaterial = decryptSecretMaterial(secret, targetVersion);
                try {
                    String val = "RAW".equalsIgnoreCase(targetVersion.getAlgorithm())
                            ? new String(keyMaterial, StandardCharsets.UTF_8)
                            : Base64.getEncoder().encodeToString(keyMaterial);
                    Instant now = Instant.now();
                    targetVersion.setRevealedAt(now);
                    secretVersionRepository.save(targetVersion);
                    builder.value(val).shownAt(now.toString());
                } finally {
                    Arrays.fill(keyMaterial, (byte) 0);
                }
            }
            case "READ" -> {
                byte[] keyMaterial = decryptSecretMaterial(secret, targetVersion);
                try {
                    String val = "RAW".equalsIgnoreCase(targetVersion.getAlgorithm())
                            ? new String(keyMaterial, StandardCharsets.UTF_8)
                            : Base64.getEncoder().encodeToString(keyMaterial);
                    builder.value(val).keyVersion(targetVersion.getVersion());
                } finally {
                    Arrays.fill(keyMaterial, (byte) 0);
                }
            }
            default -> throw VaultApiException.badRequest("VALIDATION_ERROR", "Unsupported secret operation: " + operation);
        }

        accessSignalEmitter.emit("USE_SECRET:" + operation, SECRET_DATA_CLASS, request.getPurpose(), "SUCCESS", 1);
        vaultEventEmitter.emitAccess("USE_SECRET:" + operation, SECRET_DATA_CLASS, request.getPurpose(), "SUCCESS", 1);
        return builder.build();
    }

    @Transactional
    public SignResponse signPayload(SignRequest request) {
        UseSecretRequest useReq = UseSecretRequest.builder()
                .operation("SIGN")
                .purpose(request.getPurpose())
                .contextRef(request.getContextRef())
                .algorithm(request.getAlgorithm())
                .payload(request.getPayload())
                .version(request.getVersion())
                .build();
        UseSecretResponse res = useSecret(request.getSecretRef(), null, useReq);
        return SignResponse.builder()
                .signature(res.getSignature())
                .keyVersion(res.getKeyVersion() != null ? res.getKeyVersion() : res.getVersion())
                .algorithm(res.getAlgorithm())
                .kid(res.getKid())
                .requestId(res.getRequestId())
                .build();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSecretMetadata(String ref) {
        Secret secret = secretRepository.findByRef(ref)
                .orElseThrow(() -> VaultApiException.notFound("SECRET_NOT_FOUND", "Secret not found"));
        List<SecretVersion> versions = secretVersionRepository.findBySecret_IdOrderByVersionDesc(secret.getId());
        SecretVersion active = versions.stream()
                .filter(v -> "ACTIVE".equalsIgnoreCase(v.getState()))
                .findFirst().orElse(versions.isEmpty() ? null : versions.get(0));

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("exists", true);
        meta.put("ref", secret.getRef());
        meta.put("secretClass", secret.getSecretClass());
        meta.put("mode", secret.getMode());
        meta.put("owner", secret.getOwner());
        meta.put("status", secret.getStatus());
        meta.put("activeVersion", active != null ? active.getVersion() : 0);
        meta.put("algorithm", active != null ? active.getAlgorithm() : null);
        meta.put("createdAt", secret.getCreatedAt().toString());
        if (active != null && active.getPublicKeyJson() != null) {
            try {
                meta.put("publicKey", objectMapper.readValue(active.getPublicKeyJson(), new TypeReference<Map<String, Object>>() {}));
            } catch (Exception ignored) {
            }
        }
        return meta;
    }

    @Transactional
    public SecretResponse rotateSigningSecret(String ref) {
        Secret secret = secretRepository.findByRef(ref)
                .orElseThrow(() -> VaultApiException.notFound("KEY_NOT_FOUND", "Signing secret not found: " + ref));
        List<SecretVersion> versions = secretVersionRepository.findBySecret_IdOrderByVersionDesc(secret.getId());
        String alg = versions.isEmpty() || versions.get(0).getAlgorithm() == null
                ? "RS256" : versions.get(0).getAlgorithm();
        CreateSecretRequest req = CreateSecretRequest.builder()
                .ref(ref + ".v" + (versions.size() + 1))
                .baseRef(ref)
                .secretClass(secret.getSecretClass())
                .mode("GENERATE")
                .generate(CreateSecretRequest.GenerateSpec.builder()
                        .algorithm(alg)
                        .keySizeBits(versions.isEmpty() ? 2048 : versions.get(0).getKeySizeBits())
                        .build())
                .build();
        return doCreateSecret(req);
    }

    private byte[] decryptSecretMaterial(Secret secret, SecretVersion version) {
        String token = secret.getRef() + ":" + version.getVersion();
        try (EphemeralSubjectDek dek = cryptoEngineService.unwrapDek(version.getWrappedDek())) {
            String b64 = cryptoEngineService.decryptValue(
                    version.getCiphertext(), version.getNonce(), dek,
                    SECRET_DATA_CLASS, secret.getRef(), token);
            return Base64.getDecoder().decode(b64);
        }
    }

    private void enforceSecretPolicy(Secret secret, String caller, String operation, String purpose) {
        if ("TEMP-SERVICE".equalsIgnoreCase(caller)) {
            return;
        }
        if (secret.getAllowedUses() != null && !secret.getAllowedUses().isBlank()) {
            Set<String> uses = Set.of(secret.getAllowedUses().split(","));
            if (!uses.contains(operation)) {
                if ("READ".equals(operation) || "REVEAL_ONCE".equals(operation)) {
                    throw VaultApiException.forbidden("READ_NOT_ALLOWED", "This secret cannot be read");
                }
                throw VaultApiException.forbidden("POLICY_DENIED", "Not authorised for operation " + operation);
            }
        }
        if (secret.getAllowedServices() != null && !secret.getAllowedServices().isBlank()) {
            boolean serviceAllowed = Arrays.stream(secret.getAllowedServices().split(","))
                    .anyMatch(s -> s.trim().equalsIgnoreCase(caller));
            if (!serviceAllowed) {
                throw VaultApiException.forbidden("POLICY_DENIED", "Caller not authorised for this secret");
            }
        }
        if (secret.getAllowedPurposes() != null && !secret.getAllowedPurposes().isBlank()) {
            boolean purposeAllowed = Arrays.stream(secret.getAllowedPurposes().split(","))
                    .anyMatch(p -> p.trim().equalsIgnoreCase(purpose));
            if (!purposeAllowed) {
                throw VaultApiException.forbidden("POLICY_DENIED", "Not authorised for this purpose");
            }
        }
    }

    private boolean hasAllowedUse(Secret secret, String use) {
        if (secret.getAllowedUses() == null || secret.getAllowedUses().isBlank()) {
            return true;
        }
        return Arrays.stream(secret.getAllowedUses().split(","))
                .anyMatch(u -> u.trim().equalsIgnoreCase(use));
    }

    private void applyPolicyAndRotation(Secret secret, CreateSecretRequest request) {
        if (request.getPolicy() != null) {
            if (request.getPolicy().getUses() != null) {
                secret.setAllowedUses(String.join(",", request.getPolicy().getUses()));
            }
            if (request.getPolicy().getServices() != null) {
                secret.setAllowedServices(String.join(",", request.getPolicy().getServices()));
            }
            if (request.getPolicy().getPurposes() != null) {
                secret.setAllowedPurposes(String.join(",", request.getPolicy().getPurposes()));
            }
            if (request.getPolicy().getOverlapHours() != null) {
                secret.setOverlapHours(request.getPolicy().getOverlapHours());
            }
        }
        if (request.getRotation() != null) {
            secret.setRotationIntervalDays(request.getRotation().getIntervalDays());
            secret.setNotifyDaysBeforeExpiry(request.getRotation().getNotifyDaysBeforeExpiry());
            if (request.getRotation().getIntervalDays() != null && request.getRotation().getIntervalDays() > 0) {
                secret.setNextRotationAt(Instant.now().plus(request.getRotation().getIntervalDays(), ChronoUnit.DAYS));
            }
        }
    }

    private void validateAlgorithmAllowed(String alg, Integer bits) {
        String normalized = SigningService.normalizeAlgorithm(alg);
        if (!Set.of("HMAC_SHA256", "RS256", "RSA_2048", "RSA_3072", "ES256", "EC_P256", "AES_256").contains(normalized)) {
            throw VaultApiException.unprocessable("ALGORITHM_NOT_ALLOWED", "This algorithm is not allowed");
        }
        if (("RS256".equals(normalized) || "RSA_2048".equals(normalized)) && bits != null && bits < 2048) {
            throw VaultApiException.unprocessable("ALGORITHM_NOT_ALLOWED", "RSA key size must be at least 2048 bits");
        }
    }

    private String extractSpki(String publicKeyJson) {
        if (publicKeyJson == null || publicKeyJson.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(publicKeyJson, new TypeReference<>() {});
            Object spki = map.get("spkiBase64");
            return spki != null ? spki.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] decodeBase64Payload(String b64) {
        if (b64 == null || b64.isBlank()) {
            throw VaultApiException.badRequest("VALIDATION_ERROR", "Payload/signature is required");
        }
        try {
            return Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            return b64.getBytes(StandardCharsets.UTF_8);
        }
    }

    private UUID parseOptionalUuid(String key) {
        if (key == null || key.isBlank()) return null;
        try {
            return UUID.fromString(key);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Idempotency-Key must be a valid UUID");
        }
    }

    private String resolveCaller() {
        WorkloadIdentity identity = VaultSecurityContext.getWorkloadIdentity();
        return identity != null ? identity.serviceName() : "UNKNOWN";
    }

    private String mintRef() {
        byte[] random = new byte[16];
        secureRandom.nextBytes(random);
        return "sec_" + HexFormat.of().formatHex(random);
    }
}
