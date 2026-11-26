package com.umar.service;

import com.umar.audit.AuditRecorder;
import com.umar.authentication.VaultSecurityContext;
import com.umar.authentication.WorkloadIdentity;
import com.umar.config.EphemeralSubjectDek;
import com.umar.crypto.CryptoEngineService;
import com.umar.document.DocumentEncryptionService;
import com.umar.document.ObjectStorageProvider;
import com.umar.entity.auditAndAccess.ViewLink;
import com.umar.entity.dataProtection.DataClass;
import com.umar.entity.dataProtection.Document;
import com.umar.entity.dataProtection.RetentionClass;
import com.umar.enums.vault.DetokenizeMode;
import com.umar.events.VaultEventType;
import com.umar.exception.VaultApiException;
import com.umar.idempotency.IdempotencyClaim;
import com.umar.idempotency.IdempotencyControllerAdvice;
import com.umar.idempotency.IdempotencyService;
import com.umar.payload.request.vault.StoreDocumentRequest;
import com.umar.payload.response.vault.DocumentResponse;
import com.umar.payload.response.vault.DownloadDocumentResponse;
import com.umar.policy.PolicyResource;
import com.umar.policy.RateLimitAndAnomalyService;
import com.umar.policy.VaultPolicyService;
import com.umar.repository.auditAndAccess.ViewLinkRepository;
import com.umar.repository.dataProtection.DataClassRepository;
import com.umar.repository.dataProtection.DocumentRepository;
import com.umar.repository.dataProtection.RetentionClassRepository;
import com.umar.repository.keyManagement.DataKeyMaterial;
import com.umar.repository.keyManagement.SubjectKeyServiceImpl;
import com.umar.util.TokenGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

/**
 * VLT-05 (Store Documents) and VLT-14 (Access a Document: VIEW, READ, VERIFY, METADATA)
 * plus single-use watermarked viewer link redemption.
 */
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final String DOCUMENT_DATA_CLASS = "DOCUMENT";

    private final DocumentRepository documentRepository;
    private final DataClassRepository dataClassRepository;
    private final RetentionClassRepository retentionClassRepository;
    private final ViewLinkRepository viewLinkRepository;
    private final CryptoEngineService cryptoEngineService;
    private final DocumentEncryptionService documentEncryptionService;
    private final ObjectStorageProvider objectStorageProvider;
    private final SubjectKeyServiceImpl subjectKeyService;
    private final LegalHoldService legalHoldService;
    private final VaultPolicyService vaultPolicyService;
    private final RateLimitAndAnomalyService rateLimitAndAnomalyService;
    private final TokenGenerator tokenGenerator;
    private final AccessSignalEmitter accessSignalEmitter;
    private final VaultEventEmitter vaultEventEmitter;
    private final AuditRecorder auditRecorder;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @Value("${vault.viewer.base-url:https://view.vault.agrishield.example/api/vault/viewer}")
    private String viewerBaseUrl;

    public DocumentResponse storeDocument(StoreDocumentRequest request) {
        byte[] bytes = request.getContent() != null
                ? request.getContent().getBytes(StandardCharsets.UTF_8)
                : new byte[0];
        return storeDocumentBytes(null, request, bytes);
    }

    public DocumentResponse storeMultipartDocument(String idempotencyKey, StoreDocumentRequest metadata, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw VaultApiException.badRequest("VALIDATION_ERROR", "Missing file part");
        }
        try {
            return storeDocumentBytes(idempotencyKey, metadata, file.getBytes());
        } catch (VaultApiException e) {
            throw e;
        } catch (Exception e) {
            throw VaultApiException.badRequest("VALIDATION_ERROR", "Unable to read uploaded file");
        }
    }

    public DocumentResponse storeDocumentBytes(String idempotencyKey, StoreDocumentRequest request, byte[] rawBytes) {
        UUID key = parseOptionalUuid(idempotencyKey);
        if (key == null) {
            return doStoreDocument(request, rawBytes);
        }
        String caller = resolveCaller();
        String owner = UUID.randomUUID().toString();
        IdempotencyClaim claim = idempotencyService.claim(caller, "STORE_DOCUMENT", key, request, owner);
        return switch (claim.type()) {
            case REPLAY -> idempotencyService.deserializeReplay(claim.responsePayload(), DocumentResponse.class);
            case IN_PROGRESS -> throw new IdempotencyControllerAdvice.IdempotencyInProgressException(
                    "Document upload with this idempotency key is still in progress");
            case PAYLOAD_CONFLICT -> throw new IdempotencyControllerAdvice.IdempotencyConflictException(
                    "Idempotency key reused with a different request payload");
            case NEW_REQUEST -> {
                try {
                    DocumentResponse resp = doStoreDocument(request, rawBytes);
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
    protected DocumentResponse doStoreDocument(StoreDocumentRequest request, byte[] rawBytes) {
        String dataClass = request.getDataClass().trim().toUpperCase();
        String caller = resolveCaller();
        rateLimitAndAnomalyService.checkAndConsume(caller, dataClass, request.getPurpose(), "STORE_DOCUMENT", 1);

        documentEncryptionService.validateDocumentBytes(rawBytes, request.getContentType(), dataClass, request.getSha256());

        DataClass dcEntity = dataClassRepository.findByNameAndStatus(dataClass, "ACTIVE").orElse(null);
        String retentionGroup = (dcEntity != null && dcEntity.getRetentionGroup() != null)
                ? dcEntity.getRetentionGroup().name()
                : "ERASABLE";

        String retentionClass = (request.getRetentionClass() != null && !request.getRetentionClass().isBlank())
                ? request.getRetentionClass().trim().toUpperCase()
                : (dcEntity != null && dcEntity.getRetentionClassDefault() != null
                ? dcEntity.getRetentionClassDefault() : "STANDARD_7Y");

        RetentionClass rcEntity = retentionClassRepository.findByName(retentionClass).orElse(null);
        int retainDays = (rcEntity != null && rcEntity.getPeriodDays() != null && rcEntity.getPeriodDays() > 0)
                ? rcEntity.getPeriodDays()
                : 2555;
        String retainUntil = LocalDate.now(ZoneOffset.UTC).plusDays(retainDays).toString();

        DataKeyMaterial subjectKeyMaterial = subjectKeyService.getOrCreateActiveKey(
                request.getSubjectRef(), dataClass, retentionGroup);

        String token = tokenGenerator.mintTokenString(dataClass, true);
        String sha256 = documentEncryptionService.sha256Hex(rawBytes);

        DocumentEncryptionService.EncryptedDoc encrypted = documentEncryptionService.encryptBytes(
                rawBytes, subjectKeyMaterial.key(), subjectKeyMaterial.keyVersionId(),
                dataClass, request.getSubjectRef(), token);

        ObjectStorageProvider.StoredObjectRefs objectRefs =
                objectStorageProvider.putEncryptedObject(dataClass, token, encrypted.ciphertext());

        boolean metadataStored = request.getCaptureMeta() != null && !request.getCaptureMeta().isEmpty();
        String captureMetaJson = null;
        if (metadataStored) {
            try {
                captureMetaJson = objectMapper.writeValueAsString(request.getCaptureMeta());
            } catch (Exception ignored) {
            }
        }

        Instant now = Instant.now();
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setToken(token);
        document.setDataClass(dataClass);
        document.setSubjectRef(request.getSubjectRef());
        document.setOwnerRef(request.getOwnerRef());
        document.setSubjectKeyId(subjectKeyMaterial.keyVersionId());
        document.setRetentionClass(retentionClass);
        document.setRetentionGroup(retentionGroup);
        document.setRetainUntil(retainUntil);
        document.setSha256(sha256);
        document.setSizeBytes(String.valueOf(rawBytes.length));
        document.setContentType(request.getContentType() != null ? request.getContentType() : "application/octet-stream");
        document.setObjectRef(objectRefs.primaryRef());
        document.setReplicaObjectRef(objectRefs.replicaRef());
        document.setKeyRef(subjectKeyMaterial.keyVersionId().toString());
        document.setCaptureMetaJson(captureMetaJson);
        document.setCreatedAt(now);
        document.setEncryptedContent(encrypted.ciphertext());
        document.setNonce(encrypted.nonce());
        document.setWrappedDek(encrypted.wrappedDek());

        Document saved = documentRepository.save(document);
        String requestId = "vq_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);

        auditRecorder.recordStrict("VAULT_DOCUMENT_STORED", "STORE_DOCUMENT", dataClass, null,
                request.getPurpose(), request.getContextRef(), request.getSubjectRef(), 1,
                "SUCCESS", saved.getId(), null);
        accessSignalEmitter.emit("STORE_DOCUMENT", dataClass, request.getPurpose(), "SUCCESS", 1);
        vaultEventEmitter.emit(VaultEventType.TOKENIZED, Map.of(
                "operation", "STORE_DOCUMENT",
                "dataClass", dataClass,
                "callerService", caller,
                "purpose", request.getPurpose(),
                "count", 1));
        vaultEventEmitter.emitAccess("STORE_DOCUMENT", dataClass, request.getPurpose(), "SUCCESS", 1);

        DocumentResponse resp = toResponse(saved);
        resp.setMetadataStored(metadataStored);
        resp.setRequestId(requestId);
        return resp;
    }

    /**
     * VLT-14 — Access a Document in mode VIEW | READ | VERIFY | METADATA.
     */
    @Transactional
    public DocumentAccessResult accessDocument(String token, String mode, String purpose, String contextRef,
                                             String viewerRef, String viewerRole, String subjectRef,
                                             String expectedSha256, Integer ttlSeconds) {
        Document document = documentRepository.findByToken(token)
                .orElseThrow(() -> VaultApiException.notFound("DOCUMENT_NOT_FOUND", "Document not found"));

        if (subjectKeyService.isSubjectGroupShredded(document.getSubjectRef(), document.getRetentionGroup())) {
            throw VaultApiException.gone("SUBJECT_ERASED", "This document no longer exists");
        }

        String normalizedMode = mode.trim().toUpperCase();
        String opName = switch (normalizedMode) {
            case "VIEW" -> "VIEW_DOCUMENT";
            case "READ" -> "READ_DOCUMENT";
            case "VERIFY" -> "VERIFY_DOCUMENT";
            case "METADATA" -> "METADATA_DOCUMENT";
            default -> throw VaultApiException.badRequest("VALIDATION_ERROR", "Unsupported document mode: " + mode);
        };

        String caller = resolveCaller();
        if ("READ".equals(normalizedMode)) {
            DataClass dc = dataClassRepository.findByNameAndStatus(document.getDataClass(), "ACTIVE").orElse(null);
            if (dc != null && (dc.getDetokenizable() == DetokenizeMode.VIEW_REVIEWERS_ONLY
                    || dc.getDetokenizable() == DetokenizeMode.NO)) {
                if (!"TEMP-SERVICE".equalsIgnoreCase(caller)) {
                    vaultPolicyService.authorize("VIEW_DOCUMENT",
                            List.of(new PolicyResource(document.getDataClass(), purpose)));
                }
                throw VaultApiException.forbidden("READ_NOT_ALLOWED",
                        "This document can only be viewed by a reviewer");
            }
        }
        if (!"TEMP-SERVICE".equalsIgnoreCase(caller)) {
            vaultPolicyService.authorize(opName, List.of(new PolicyResource(document.getDataClass(), purpose)));
        }
        rateLimitAndAnomalyService.checkAndConsume(caller, document.getDataClass(), purpose, opName, 1);

        String requestId = "vq_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);

        if ("READ".equals(normalizedMode)) {
            auditRecorder.recordStrict("VAULT_DOCUMENT_READ", opName, document.getDataClass(), null,
                    purpose, contextRef, document.getSubjectRef(), 1, "SUCCESS", document.getId(), null);

            byte[] plainBytes = decryptDocumentBytes(document);
            accessSignalEmitter.emit(opName, document.getDataClass(), purpose, "SUCCESS", 1);
            vaultEventEmitter.emitAccess(opName, document.getDataClass(), purpose, "SUCCESS", 1);
            return new DocumentAccessResult(null, plainBytes, document.getContentType(), document.getSha256(), requestId);
        }

        if ("VIEW".equals(normalizedMode)) {
            if (viewerRef == null || viewerRef.isBlank() || viewerRole == null || viewerRole.isBlank()) {
                throw VaultApiException.badRequest("VALIDATION_ERROR", "viewerRef and viewerRole are required for VIEW mode");
            }
            int ttl = ttlSeconds != null ? Math.min(300, Math.max(60, ttlSeconds)) : 300;
            Instant now = Instant.now();
            Instant expiresAt = now.plusSeconds(ttl);
            String viewerHash = AuditRecorder.hashIfPresent(viewerRef);

            auditRecorder.recordStrict("VAULT_DOCUMENT_VIEWED", opName, document.getDataClass(), null,
                    purpose, contextRef, document.getSubjectRef(), 1, "SUCCESS", document.getId(), null);

            UUID linkId = UUID.randomUUID();
            ViewLink link = new ViewLink();
            link.setId(linkId);
            link.setDocumentToken(document.getToken());
            link.setViewerRefHash(viewerHash);
            link.setViewerRole(viewerRole);
            link.setSessionBinding(viewerRef + "|" + viewerRole);
            link.setWatermarkText("VIEWER:" + viewerHash + "|AT:" + now);
            link.setExpiresAt(expiresAt.toString());
            viewLinkRepository.save(link);

            accessSignalEmitter.emit(opName, document.getDataClass(), purpose, "SUCCESS", 1);
            vaultEventEmitter.emitAccess(opName, document.getDataClass(), purpose, "SUCCESS", 1);

            DocumentResponse resp = toResponse(document);
            resp.setViewUrl(viewerBaseUrl + "/" + linkId);
            resp.setExpiresAt(expiresAt.toString());
            resp.setSingleUse(true);
            resp.setWatermark(Map.of("viewerRefHash", viewerHash, "at", now.toString()));
            resp.setRequestId(requestId);
            return new DocumentAccessResult(resp, null, document.getContentType(), document.getSha256(), requestId);
        }

        if ("VERIFY".equals(normalizedMode)) {
            auditRecorder.recordStrict("VAULT_DOCUMENT_VERIFIED", opName, document.getDataClass(), null,
                    purpose, contextRef, document.getSubjectRef(), 1, "SUCCESS", document.getId(), null);

            byte[] plainBytes = decryptDocumentBytes(document);
            boolean intact = document.getSha256() != null
                    && document.getSha256().equalsIgnoreCase(documentEncryptionService.sha256Hex(plainBytes));
            boolean subjectMatches = subjectRef == null || subjectRef.isBlank()
                    || subjectRef.equals(document.getSubjectRef());
            boolean hashMatches = expectedSha256 == null || expectedSha256.isBlank()
                    || expectedSha256.equalsIgnoreCase(document.getSha256());

            accessSignalEmitter.emit(opName, document.getDataClass(), purpose, "SUCCESS", 1);
            vaultEventEmitter.emitAccess(opName, document.getDataClass(), purpose, "SUCCESS", 1);

            DocumentResponse resp = DocumentResponse.builder()
                    .token(document.getToken())
                    .dataClass(document.getDataClass())
                    .exists(true)
                    .intact(intact)
                    .subjectMatches(subjectMatches)
                    .hashMatches(hashMatches)
                    .requestId(requestId)
                    .build();
            return new DocumentAccessResult(resp, null, document.getContentType(), document.getSha256(), requestId);
        }

        // METADATA
        auditRecorder.recordStrict("VAULT_DOCUMENT_METADATA", opName, document.getDataClass(), null,
                purpose, contextRef, document.getSubjectRef(), 1, "SUCCESS", document.getId(), null);
        accessSignalEmitter.emit(opName, document.getDataClass(), purpose, "SUCCESS", 1);

        DocumentResponse resp = toResponse(document);
        resp.setLegalHold(legalHoldService.isDocumentHeld(document.getToken(), document.getSubjectRef(), document.getDataClass()));
        resp.setRequestId(requestId);
        return new DocumentAccessResult(resp, null, document.getContentType(), document.getSha256(), requestId);
    }

    @Transactional
    public Map<String, Object> redeemViewLink(UUID linkId, String viewerRef, String viewerRole) {
        ViewLink link = viewLinkRepository.findById(linkId)
                .orElseThrow(() -> VaultApiException.forbidden("LINK_NOT_VALID", "This link is no longer valid"));

        if (link.getUsedAt() != null) {
            throw VaultApiException.forbidden("LINK_NOT_VALID", "This link is no longer valid");
        }
        if (link.getExpiresAt() != null && Instant.now().isAfter(Instant.parse(link.getExpiresAt()))) {
            throw VaultApiException.forbidden("LINK_NOT_VALID", "This link is no longer valid");
        }
        if (viewerRef != null && link.getViewerRefHash() != null
                && !link.getViewerRefHash().equals(AuditRecorder.hashIfPresent(viewerRef))) {
            throw VaultApiException.forbidden("LINK_NOT_VALID", "This link is no longer valid");
        }

        Document document = documentRepository.findByToken(link.getDocumentToken())
                .orElseThrow(() -> VaultApiException.notFound("DOCUMENT_NOT_FOUND", "Document not found"));
        if (subjectKeyService.isSubjectGroupShredded(document.getSubjectRef(), document.getRetentionGroup())) {
            throw VaultApiException.gone("SUBJECT_ERASED", "This document no longer exists");
        }

        link.setUsedAt(Instant.now().toString());
        viewLinkRepository.save(link);

        byte[] plainBytes = decryptDocumentBytes(document);
        return Map.of(
                "token", document.getToken(),
                "contentType", document.getContentType(),
                "watermark", link.getWatermarkText(),
                "contentBase64", Base64.getEncoder().encodeToString(plainBytes)
        );
    }

    @Transactional(readOnly = true)
    public DownloadDocumentResponse downloadDocument(String token) {
        Document document = documentRepository.findByToken(token)
                .orElseThrow(() -> VaultApiException.notFound("DOCUMENT_NOT_FOUND", "Document not found: " + token));
        if (subjectKeyService.isSubjectGroupShredded(document.getSubjectRef(), document.getRetentionGroup())) {
            throw VaultApiException.gone("SUBJECT_ERASED", "This document no longer exists");
        }
        byte[] plain = decryptDocumentBytes(document);
        accessSignalEmitter.emit("DOWNLOAD_DOCUMENT", document.getDataClass(), null, "SUCCESS", 1);
        vaultEventEmitter.emitAccess("DOWNLOAD_DOCUMENT", document.getDataClass(), null, "SUCCESS", 1);
        auditRecorder.recordStrict("VAULT_DOCUMENT_DOWNLOADED", "DOWNLOAD_DOCUMENT", document.getDataClass(), null,
                "GENERAL", null, document.getSubjectRef(), 1, "SUCCESS", document.getId(), null);
        return DownloadDocumentResponse.builder()
                .token(token)
                .contentType(document.getContentType())
                .content(new String(plain, StandardCharsets.UTF_8))
                .build();
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> listDocuments(String dataClass, String subjectRef) {
        List<Document> documents;
        if (dataClass != null && !dataClass.isBlank() && subjectRef != null && !subjectRef.isBlank()) {
            documents = documentRepository.findByDataClassAndSubjectRef(dataClass, subjectRef);
        } else if (dataClass != null && !dataClass.isBlank()) {
            documents = documentRepository.findByDataClass(dataClass);
        } else if (subjectRef != null && !subjectRef.isBlank()) {
            documents = documentRepository.findBySubjectRef(subjectRef);
        } else {
            documents = documentRepository.findAll();
        }
        return documents.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public DocumentResponse getDocumentMetadata(String token) {
        Document document = documentRepository.findByToken(token)
                .orElseThrow(() -> VaultApiException.notFound("DOCUMENT_NOT_FOUND", "Document not found: " + token));
        return toResponse(document);
    }

    @Transactional
    public void deleteDocument(String token) {
        Document document = documentRepository.findByToken(token)
                .orElseThrow(() -> VaultApiException.notFound("DOCUMENT_NOT_FOUND", "Document not found: " + token));
        if (legalHoldService.isDocumentHeld(token, document.getSubjectRef(), document.getDataClass())) {
            throw VaultApiException.conflict("LEGAL_HOLD_ACTIVE", "Document is protected by an active legal hold");
        }
        objectStorageProvider.deleteObjects(document.getObjectRef(), document.getReplicaObjectRef());
        document.setEncryptedContent(null);
        document.setNonce(null);
        document.setWrappedDek(null);
        documentRepository.save(document);
        documentRepository.delete(document);

        accessSignalEmitter.emit("DELETE_DOCUMENT", document.getDataClass(), null, "SUCCESS", 1);
        auditRecorder.recordStrict("VAULT_DOCUMENT_DELETED", "DELETE_DOCUMENT", document.getDataClass(), null,
                "GENERAL", null, document.getSubjectRef(), 1, "SUCCESS", document.getId(), null);
    }

    private byte[] decryptDocumentBytes(Document document) {
        byte[] ciphertext = objectStorageProvider.getEncryptedObject(document.getObjectRef(), document.getReplicaObjectRef());
        if (ciphertext == null) {
            ciphertext = document.getEncryptedContent();
        }
        if (ciphertext == null || document.getWrappedDek() == null) {
            throw VaultApiException.gone("SUBJECT_ERASED", "This document no longer exists");
        }

        if (document.getSubjectKeyId() != null) {
            DataKeyMaterial skm = subjectKeyService.getKeyByVersion(document.getSubjectKeyId());
            return documentEncryptionService.decryptBytes(
                    ciphertext, document.getNonce(), document.getWrappedDek(),
                    skm.key(), skm.keyVersionId(), document.getDataClass(), document.getSubjectRef(), document.getToken());
        }

        try (EphemeralSubjectDek dek = cryptoEngineService.unwrapDek(document.getWrappedDek())) {
            String content = cryptoEngineService.decryptValue(
                    ciphertext, document.getNonce(), dek,
                    DOCUMENT_DATA_CLASS, document.getSubjectRef(), document.getToken());
            return content.getBytes(StandardCharsets.UTF_8);
        }
    }

    private DocumentResponse toResponse(Document document) {
        return DocumentResponse.builder()
                .id(document.getId())
                .token(document.getToken())
                .dataClass(document.getDataClass())
                .subjectRef(document.getSubjectRef())
                .contentType(document.getContentType())
                .sizeBytes(document.getSizeBytes())
                .sha256(document.getSha256())
                .retentionClass(document.getRetentionClass())
                .retainUntil(document.getRetainUntil())
                .createdAt(document.getCreatedAt() != null ? document.getCreatedAt().toString() : null)
                .build();
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

    public record DocumentAccessResult(
            DocumentResponse jsonResponse,
            byte[] streamBytes,
            String contentType,
            String sha256,
            String requestId) {
    }
}
