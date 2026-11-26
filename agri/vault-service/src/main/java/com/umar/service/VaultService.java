package com.umar.service;

import com.umar.audit.AuditRecorder;
import com.umar.authentication.VaultSecurityContext;
import com.umar.authentication.WorkloadIdentity;
import com.umar.blindindex.BlindIndexMatch;
import com.umar.blindindex.BlindIndexService;
import com.umar.config.EphemeralSubjectDek;
import com.umar.crypto.CryptoEngineService;
import com.umar.crypto.ValidationRegistryService;
import com.umar.cryptography.FpeService;
import com.umar.entity.dataProtection.DataClass;
import com.umar.entity.dataProtection.VaultToken;
import com.umar.enums.vault.DetokenizeMode;
import com.umar.enums.vault.TokenFormat;
import com.umar.events.VaultEventType;
import com.umar.exception.VaultApiException;
import com.umar.idempotency.IdempotencyClaim;
import com.umar.idempotency.IdempotencyControllerAdvice;
import com.umar.idempotency.IdempotencyService;
import com.umar.payload.request.vault.DeTokenizeRequest;
import com.umar.payload.request.vault.TokenizeRequest;
import com.umar.payload.response.vault.DetokenizeResponse;
import com.umar.payload.response.vault.ItemFailure;
import com.umar.payload.response.vault.ResolvedValue;
import com.umar.payload.response.vault.TokenFailure;
import com.umar.payload.response.vault.TokenSuccess;
import com.umar.payload.response.vault.TokenizeResponse;
import com.umar.policy.PolicyResource;
import com.umar.policy.RateLimitAndAnomalyService;
import com.umar.policy.VaultPolicyService;
import com.umar.repository.dataProtection.DataClassRepository;
import com.umar.repository.dataProtection.VaultTokenRepository;
import com.umar.repository.keyManagement.DataKeyMaterial;
import com.umar.repository.keyManagement.SubjectKeyServiceImpl;
import com.umar.util.TokenGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class VaultService implements IVaultService {

    private final VaultTokenRepository tokenRepository;
    private final DataClassRepository dataClassRepository;
    private final TokenGenerator tokenGenerator;
    private final CryptoEngineService cryptoEngineService;
    private final ValidationRegistryService validatorService;
    private final FpeService fpeService;
    private final VaultPolicyService vaultPolicyService;
    private final RateLimitAndAnomalyService rateLimitAndAnomalyService;
    private final SubjectKeyServiceImpl subjectKeyService;
    private final AccessSignalEmitter accessSignalEmitter;
    private final VaultEventEmitter vaultEventEmitter;
    private final IdempotencyService idempotencyService;
    private final AuditRecorder auditRecorder;
    private final BlindIndexService blindIndexService;

    @Override
    public TokenizeResponse processTokenization(String idempotencyKey, TokenizeRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw VaultApiException.badRequest("VALIDATION_ERROR", "At least one item is required");
        }
        if (request.getItems().size() > 100) {
            throw VaultApiException.payloadTooLarge("BATCH_TOO_LARGE", "The batch is too large", Map.of("maxItems", 100));
        }

        UUID key = parseIdempotencyKey(idempotencyKey);
        if (key == null) {
            return doTokenize(request);
        }

        String caller = resolveCaller();
        String owner = UUID.randomUUID().toString();
        IdempotencyClaim claim = idempotencyService.claim(
                caller, IdempotencyService.TOKENIZE_OPERATION, key, request, owner);

        return switch (claim.type()) {
            case REPLAY -> idempotencyService.deserializeReplay(claim.responsePayload(), TokenizeResponse.class);
            case IN_PROGRESS -> throw new IdempotencyControllerAdvice.IdempotencyInProgressException(
                    "Request with this idempotency key is still being processed");
            case PAYLOAD_CONFLICT -> throw new IdempotencyControllerAdvice.IdempotencyConflictException(
                    "Idempotency key reused with a different request payload");
            case NEW_REQUEST -> {
                try {
                    TokenizeResponse response = doTokenize(request);
                    idempotencyService.complete(claim.recordId(), owner, response, 201);
                    yield response;
                } catch (Exception e) {
                    idempotencyService.markFailed(claim.recordId(), owner);
                    throw e;
                }
            }
        };
    }

    @Transactional
    protected TokenizeResponse doTokenize(TokenizeRequest request) {
        String caller = resolveCaller();
        String operationRequestId = "vq_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        List<TokenSuccess> successList = new ArrayList<>();
        List<ItemFailure> failureList = new ArrayList<>();

        // Enforce rate limits per distinct class in batch
        request.getItems().stream()
                .map(i -> i.getDataClass().trim().toUpperCase())
                .distinct()
                .forEach(dc -> rateLimitAndAnomalyService.checkAndConsume(
                        caller, dc, request.getPurpose(), "TOKENIZE", request.getItems().size()));

        for (int index = 0; index < request.getItems().size(); index++) {
            TokenizeRequest.Items item = request.getItems().get(index);
            String dataClass = item.getDataClass().trim().toUpperCase();

            if (!validatorService.isValidForClass(item.getValue(), dataClass)) {
                failureList.add(new ItemFailure(index, "VALUE_INVALID",
                        "Value fails the structural verification constraints of class " + dataClass));
                continue;
            }

            boolean requestedFpe = item.getFormat() == TokenFormat.FPE;
            if (requestedFpe && !validatorService.isFpePermitted(dataClass)) {
                failureList.add(new ItemFailure(index, "FPE_NOT_ALLOWED",
                        "Format preserving option unavailable for data class " + dataClass));
                continue;
            }

            DataClass dcEntity = dataClassRepository.findByNameAndStatus(dataClass, "ACTIVE").orElse(null);
            String retentionGroup = (dcEntity != null && dcEntity.getRetentionGroup() != null)
                    ? dcEntity.getRetentionGroup().name()
                    : "ERASABLE";
            boolean allowsBlindIndex = dcEntity == null
                    || !Boolean.FALSE.equals(dcEntity.getHasLookupHash());

            String rawStr = new String(item.getValue());
            String canonicalValue = validatorService.canonicalize(rawStr, dataClass);

            boolean reuse = item.getReuseExisting() == null || item.getReuseExisting();
            if (reuse && allowsBlindIndex) {
                Optional<BlindIndexMatch> match = blindIndexService.findExistingForSubject(
                        dataClass, item.getSubjectRef(), canonicalValue);
                if (match.isPresent()) {
                    Optional<VaultToken> existingToken = tokenRepository.findById(match.get().tokenId())
                            .or(() -> tokenRepository.findExistingMatch(item.getSubjectRef(), dataClass));
                    if (existingToken.isPresent()
                            && !subjectKeyService.isSubjectGroupShredded(item.getSubjectRef(), retentionGroup)) {
                        VaultToken t = existingToken.get();
                        successList.add(new TokenSuccess(index, t.getToken(), t.getDataClass(),
                                t.getMaskedForm(), true, t.getFormat(), t.getCreatedAt()));
                        continue;
                    }
                }
            }

            DataKeyMaterial subjectKeyMaterial = subjectKeyService.getOrCreateActiveKey(
                    item.getSubjectRef(), dataClass, retentionGroup);

            try (EphemeralSubjectDek activeDek = cryptoEngineService.generateEphemeralDek()) {
                String formatStr = requestedFpe ? "FPE" : "OPAQUE";
                String mintedTokenStr = requestedFpe
                        ? fpeService.generateFormatPreservingToken(dataClass, item.getSubjectRef(), canonicalValue)
                        : tokenGenerator.mintTokenString(dataClass, false);
                String hint = tokenGenerator.createValueMaskedHint(
                        canonicalValue, dcEntity != null ? dcEntity.getMaskingRules() : null);

                char[] canonicalChars = canonicalValue.toCharArray();
                CryptoEngineService.CryptoPayload cryptoPayload;
                try {
                    cryptoPayload = cryptoEngineService.encryptValue(
                            canonicalChars, activeDek, dataClass, item.getSubjectRef(), mintedTokenStr);
                } finally {
                    Arrays.fill(canonicalChars, '\0');
                }

                byte[] wrappedItemDek = cryptoEngineService.wrapDekWithSubjectKey(
                        activeDek, subjectKeyMaterial.key(), subjectKeyMaterial.keyVersionId());

                VaultToken newToken = VaultToken.builder()
                        .id(UUID.randomUUID())
                        .token(mintedTokenStr)
                        .dataClass(dataClass)
                        .subjectRef(item.getSubjectRef())
                        .subjectKeyId(subjectKeyMaterial.keyVersionId())
                        .retentionGroup(retentionGroup)
                        .ciphertext(cryptoPayload.ciphertext())
                        .nonce(cryptoPayload.nonce())
                        .keyVersion(cryptoPayload.keyVersion())
                        .wrappedDek(wrappedItemDek)
                        .maskedForm(hint)
                        .format(formatStr)
                        .createdAt(Instant.now())
                        .build();

                tokenRepository.save(newToken);
                if (allowsBlindIndex) {
                    blindIndexService.saveActiveIndex(dataClass, item.getSubjectRef(), newToken.getId(), canonicalValue);
                }
                successList.add(new TokenSuccess(index, mintedTokenStr, dataClass, hint,
                        false, formatStr, newToken.getCreatedAt()));
            }
        }

        String primaryClass = request.getItems().get(0).getDataClass().trim().toUpperCase();
        String outcome = failureList.isEmpty() ? "SUCCESS" : (successList.isEmpty() ? "FAILED" : "PARTIAL");

        auditRecorder.recordStrict("VAULT_TOKENIZED", "TOKENIZE", primaryClass, null,
                request.getPurpose(), request.getContextRef(), null, successList.size(),
                failureList.isEmpty() ? "SUCCESS" : "FAILED", null,
                failureList.isEmpty() ? null : failureList.get(0).code());

        accessSignalEmitter.emit("TOKENIZE", primaryClass, request.getPurpose(), outcome, successList.size());
        vaultEventEmitter.emit(VaultEventType.TOKENIZED, Map.of(
                "operation", "TOKENIZE",
                "dataClass", primaryClass,
                "callerService", caller,
                "purpose", request.getPurpose(),
                "count", successList.size(),
                "failureCount", failureList.size()));
        vaultEventEmitter.emitAccess("TOKENIZE", primaryClass, request.getPurpose(), outcome, successList.size());

        return new TokenizeResponse(successList, failureList, operationRequestId);
    }

    @Override
    @Transactional(readOnly = true)
    public DetokenizeResponse processDeTokenization(String requestId, DeTokenizeRequest request) {
        if (request == null || request.getTokens() == null || request.getTokens().isEmpty()) {
            throw VaultApiException.badRequest("VALIDATION_ERROR", "Tokens array cannot be empty");
        }
        if (request.getTokens().size() > 50) {
            throw VaultApiException.badRequest("VALIDATION_ERROR", "Cannot process more than 50 tokens in a single request");
        }

        String caller = resolveCaller();
        String effectiveRequestId = (requestId != null && !requestId.isBlank())
                ? requestId
                : "vq_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        boolean maskedRequested = "MASKED".equalsIgnoreCase(request.getReturnMode());

        List<ResolvedValue> values = new ArrayList<>();
        List<TokenFailure> failures = new ArrayList<>();

        List<VaultToken> records = new ArrayList<>();
        for (String token : request.getTokens()) {
            Optional<VaultToken> tokenRecord = tokenRepository.findByToken(token);
            if (tokenRecord.isEmpty()) {
                failures.add(TokenFailure.builder().token(token).code("TOKEN_NOT_FOUND").build());
                continue;
            }
            records.add(tokenRecord.get());
        }

        List<String> distinctClasses = records.stream().map(VaultToken::getDataClass).distinct().toList();
        if (distinctClasses.size() > 1) {
            throw VaultApiException.badRequest("VALIDATION_ERROR", "All tokens in a detokenize batch must share the same data class");
        }

        List<PolicyResource> resources = distinctClasses.stream()
                .map(dc -> new PolicyResource(dc, request.getPurpose()))
                .toList();
        if (!resources.isEmpty()) {
            vaultPolicyService.authorize("DETOKENIZE", resources);
            for (String dc : distinctClasses) {
                rateLimitAndAnomalyService.checkAndConsume(
                        caller, dc, request.getPurpose(), "DETOKENIZE", records.size());
            }
        }

        String primaryClass = distinctClasses.isEmpty() ? null : distinctClasses.get(0);
        if (primaryClass != null && !maskedRequested) {
            Optional<DataClass> dcOpt = dataClassRepository.findByNameAndStatus(primaryClass, "ACTIVE");
            if (dcOpt.isPresent()) {
                DetokenizeMode mode = dcOpt.get().getDetokenizable();
                if (mode == DetokenizeMode.NO || mode == DetokenizeMode.VIEW_REVIEWERS_ONLY) {
                    throw VaultApiException.forbidden("CLASS_NOT_DETOKENIZABLE",
                            "This data cannot be retrieved this way");
                }
            }
        }

        // Write access record BEFORE returning sensitive values (section 2.10, 4.2)
        auditRecorder.recordStrict("VAULT_DETOKENIZED", "DETOKENIZE", primaryClass, null,
                request.getPurpose(), request.getContextRef(), null, records.size(),
                "SUCCESS", null, null);

        for (VaultToken record : records) {
            String token = record.getToken();
            if (subjectKeyService.isSubjectGroupShredded(record.getSubjectRef(), record.getRetentionGroup())) {
                failures.add(TokenFailure.builder().token(token).code("SUBJECT_ERASED").build());
                continue;
            }

            Optional<DataClass> dcOpt = dataClassRepository.findByNameAndStatus(record.getDataClass(), "ACTIVE");
            boolean forceMasked = maskedRequested
                    || (dcOpt.isPresent() && dcOpt.get().getDetokenizable() == DetokenizeMode.MASKED_ONLY);

            if (forceMasked) {
                values.add(ResolvedValue.builder()
                        .token(token)
                        .dataClass(record.getDataClass())
                        .value(record.getMaskedForm())
                        .build());
                continue;
            }

            if (record.getWrappedDek() == null) {
                failures.add(TokenFailure.builder().token(token).code("KEY_UNAVAILABLE").build());
                continue;
            }

            try (EphemeralSubjectDek dek = unwrapTokenDek(record)) {
                String value = cryptoEngineService.decryptValue(
                        record.getCiphertext(), record.getNonce(), dek,
                        record.getDataClass(), record.getSubjectRef(), record.getToken());
                values.add(ResolvedValue.builder()
                        .token(token)
                        .dataClass(record.getDataClass())
                        .value(value)
                        .build());
            } catch (VaultApiException ex) {
                if ("SUBJECT_ERASED".equals(ex.getCode())) {
                    failures.add(TokenFailure.builder().token(token).code("SUBJECT_ERASED").build());
                } else {
                    failures.add(TokenFailure.builder().token(token).code(ex.getCode()).build());
                }
            } catch (Exception e) {
                log.warn("Detokenization failed for token {}: {}", token, e.getMessage());
                failures.add(TokenFailure.builder().token(token).code("DECRYPTION_FAILED").build());
            }
        }

        String outcome = failures.isEmpty() ? "SUCCESS" : "PARTIAL";
        accessSignalEmitter.emit("DETOKENIZE", primaryClass, request.getPurpose(), outcome, values.size());
        vaultEventEmitter.emitAccess("DETOKENIZE", primaryClass, request.getPurpose(), outcome, values.size());

        return DetokenizeResponse.builder()
                .values(values)
                .failures(failures)
                .requestId(effectiveRequestId)
                .build();
    }

    private EphemeralSubjectDek unwrapTokenDek(VaultToken record) {
        if (record.getSubjectKeyId() != null) {
            DataKeyMaterial subjectKeyMaterial = subjectKeyService.getKeyByVersion(record.getSubjectKeyId());
            return cryptoEngineService.unwrapDekWithSubjectKey(
                    record.getWrappedDek(), subjectKeyMaterial.key(), subjectKeyMaterial.keyVersionId());
        }
        return cryptoEngineService.unwrapDek(record.getWrappedDek());
    }

    private UUID parseIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(key);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Idempotency-Key must be a valid UUID");
        }
    }

    private String resolveCaller() {
        WorkloadIdentity identity = VaultSecurityContext.getWorkloadIdentity();
        return identity != null ? identity.serviceName() : "UNKNOWN";
    }
}
