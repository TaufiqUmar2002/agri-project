package com.umar.controller;

import com.umar.annotation.EnforceVaultPolicy;
import com.umar.approval.ApprovalService;
import com.umar.approval.DualControlGuard;
import com.umar.audit.AuditChainVerifier;
import com.umar.payload.request.vault.*;
import com.umar.payload.response.vault.*;
import com.umar.security.DenyVaultRole;
import com.umar.security.RequireVaultRole;
import com.umar.security.VaultRole;
import com.umar.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/vault")
@RequiredArgsConstructor
public class VaultController {

    private final IVaultService tokenizationService;
    private final PolicyManagementService policyManagementService;
    private final ApprovalService approvalService;
    private final LegalHoldService legalHoldService;
    private final ErasureService erasureService;
    private final BreakGlassService breakGlassService;
    private final BackupService backupService;
    private final AccessLogService accessLogService;
    private final SecretService secretService;
    private final KeyRotationJobService keyRotationJobService;
    private final DocumentService documentService;
    private final DataClassDefinitionService dataClassDefinitionService;
    private final DualControlGuard dualControlGuard;
    private final AuditChainVerifier auditChainVerifier;
    private final com.umar.crypto.CryptoEngineService cryptoEngineService;

    @PostMapping("/tokenize")
    @EnforceVaultPolicy(dataClass = "#request.dataClass", purpose = "#request.purpose", operation = "TOKENIZE")
    public ResponseEntity<TokenizeResponse> processTokenization(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody @Valid TokenizeRequest request) {
        try {
            TokenizeResponse response = tokenizationService.processTokenization(idempotencyKey, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } finally {
            if (request.getItems() != null) {
                for (TokenizeRequest.Items item : request.getItems()) {
                    if (item.getValue() != null) {
                        Arrays.fill(item.getValue(), '\0');
                    }
                }
            }
        }
    }

    @PostMapping("/detokenize")
    @EnforceVaultPolicy(operation = "DETOKENIZE")
    @DenyVaultRole(roles = {VaultRole.SECURITY_OFFICER, VaultRole.KEY_CUSTODIAN, VaultRole.AUDITOR, VaultRole.PLATFORM_RELIABILITY_ENGINEER, VaultRole.ADMINISTRATOR})
    public ResponseEntity<DetokenizeResponse> processDeTokenization(
            @RequestHeader(value = "Request-Id", required = false) String requestId,
            @Valid @RequestBody DeTokenizeRequest request) {
        DetokenizeResponse response = this.tokenizationService.processDeTokenization(requestId, request);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @PostMapping("/secrets")
    @EnforceVaultPolicy(operation = "CREATE_SECRET")
    public ResponseEntity<SecretResponse> createSecret(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody @Valid CreateSecretRequest request) {
        SecretResponse response = secretService.createSecret(idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/secrets/{ref}/metadata")
    @EnforceVaultPolicy(operation = "SECRET_METADATA")
    public ResponseEntity<Map<String, Object>> getSecretMetadata(@PathVariable String ref) {
        return ResponseEntity.ok(secretService.getSecretMetadata(ref));
    }

    @PostMapping("/secrets/{ref}/use")
    @EnforceVaultPolicy(operation = "USE_SECRET")
    @DenyVaultRole(roles = {VaultRole.SECURITY_OFFICER, VaultRole.KEY_CUSTODIAN, VaultRole.AUDITOR, VaultRole.PLATFORM_RELIABILITY_ENGINEER, VaultRole.ADMINISTRATOR})
    public ResponseEntity<UseSecretResponse> useSecretPost(
            @PathVariable String ref,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody @Valid UseSecretRequest request) {
        UseSecretResponse response = secretService.useSecret(ref, idempotencyKey, request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    @GetMapping("/secrets/{ref}/use")
    @EnforceVaultPolicy(operation = "USE_SECRET")
    @DenyVaultRole(roles = {VaultRole.SECURITY_OFFICER, VaultRole.KEY_CUSTODIAN, VaultRole.AUDITOR, VaultRole.PLATFORM_RELIABILITY_ENGINEER, VaultRole.ADMINISTRATOR})
    public ResponseEntity<UseSecretResponse> useSecret(@PathVariable String ref) {
        UseSecretResponse response = secretService.useSecret(ref);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    @PostMapping("/keys/rotate")
    @EnforceVaultPolicy(operation = "ROTATE_KEY")
    @RequireVaultRole(operation = "ROTATE_KEY")
    public ResponseEntity<RotateKeyResponse> rotateKey(
            @RequestHeader(value = "X-Approval-Id", required = false) String approvalId,
            @RequestBody @Valid RotateKeyRequest request) {
        boolean preApproved = "SCHEDULED".equalsIgnoreCase(request.getMode());
        if (approvalId != null && !approvalId.isBlank()) {
            dualControlGuard.requireApprovedApproval(approvalId, "KEY_ACTION");
            preApproved = true;
        }
        RotateKeyResponse response = keyRotationJobService.rotateKey(request, preApproved);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/data-classes")
    @EnforceVaultPolicy(operation = "DEFINE_DATA_CLASS")
    @RequireVaultRole(operation = "DEFINE_DATA_CLASS")
    public ResponseEntity<DataClassResponse> defineDataClass(@RequestBody @Valid DefineDataClassRequest request) {
        DataClassResponse response = dataClassDefinitionService.defineDataClass(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(value = "/documents", consumes = MediaType.APPLICATION_JSON_VALUE)
    @EnforceVaultPolicy(operation = "STORE_DOCUMENT")
    public ResponseEntity<DocumentResponse> storeDocument(@RequestBody @Valid StoreDocumentRequest request) {
        DocumentResponse response = documentService.storeDocument(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @EnforceVaultPolicy(operation = "STORE_DOCUMENT")
    public ResponseEntity<DocumentResponse> storeMultipartDocument(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestPart("metadata") @Valid StoreDocumentRequest metadata,
            @RequestPart("file") MultipartFile file) {
        DocumentResponse response = documentService.storeMultipartDocument(idempotencyKey, metadata, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/documents")
    @EnforceVaultPolicy(operation = "LIST_DOCUMENTS")
    public ResponseEntity<List<DocumentResponse>> listDocuments(
            @RequestParam(required = false) String dataClass,
            @RequestParam(required = false) String subjectRef) {
        return ResponseEntity.ok(documentService.listDocuments(dataClass, subjectRef));
    }

    @GetMapping("/documents/{token}")
    public ResponseEntity<?> accessDocument(
            @PathVariable String token,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false, defaultValue = "GENERAL") String purpose,
            @RequestParam(required = false, defaultValue = "direct") String contextRef,
            @RequestParam(required = false) String viewerRef,
            @RequestParam(required = false) String viewerRole,
            @RequestParam(required = false) String subjectRef,
            @RequestParam(required = false) String sha256,
            @RequestParam(required = false) Integer ttlSeconds) {
        if (mode == null || mode.isBlank()) {
            DocumentService.DocumentAccessResult meta = documentService.accessDocument(
                    token, "METADATA", purpose, contextRef, viewerRef, viewerRole, subjectRef, sha256, ttlSeconds);
            return ResponseEntity.ok(meta.jsonResponse());
        }
        DocumentService.DocumentAccessResult result = documentService.accessDocument(
                token, mode, purpose, contextRef, viewerRef, viewerRole, subjectRef, sha256, ttlSeconds);
        if (result.streamBytes() != null) {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .header("X-Document-Sha256", result.sha256() != null ? result.sha256() : "")
                    .header("X-Request-Id", result.requestId())
                    .contentType(MediaType.parseMediaType(result.contentType()))
                    .body(result.streamBytes());
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result.jsonResponse());
    }

    @GetMapping("/viewer/{linkId}")
    public ResponseEntity<Map<String, Object>> redeemViewerLink(
            @PathVariable UUID linkId,
            @RequestParam(required = false) String viewerRef,
            @RequestParam(required = false) String viewerRole) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(documentService.redeemViewLink(linkId, viewerRef, viewerRole));
    }

    @PostMapping("/documents/{token}")
    @EnforceVaultPolicy(operation = "DOWNLOAD_DOCUMENT")
    @DenyVaultRole(roles = {VaultRole.SECURITY_OFFICER, VaultRole.KEY_CUSTODIAN, VaultRole.AUDITOR, VaultRole.PLATFORM_RELIABILITY_ENGINEER, VaultRole.ADMINISTRATOR})
    public ResponseEntity<DownloadDocumentResponse> downloadDocument(@PathVariable String token) {
        DownloadDocumentResponse response = documentService.downloadDocument(token);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    @DeleteMapping("/documents/{token}")
    @EnforceVaultPolicy(operation = "DELETE_DOCUMENT")
    public ResponseEntity<Void> deleteDocument(@PathVariable String token) {
        documentService.deleteDocument(token);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/break-glass")
    @EnforceVaultPolicy(operation = "BREAK_GLASS")
    @RequireVaultRole(operation = "BREAK_GLASS")
    public ResponseEntity<BreakGlassResponse> breakGlass(
            @RequestHeader(value = "X-Approval-Id", required = false) String approvalId,
            @RequestBody @Valid CreateBreakGlassRequest request) {
        boolean preApproved = false;
        if (approvalId != null && !approvalId.isBlank()) {
            dualControlGuard.requireApprovedApproval(approvalId, "BREAK_GLASS");
            preApproved = true;
        }
        BreakGlassResponse response = breakGlassService.createBreakGlassRequest(request, preApproved);
        return ResponseEntity.status(preApproved ? HttpStatus.CREATED : HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/break-glass/{grantId}/use")
    @EnforceVaultPolicy(operation = "USE_BREAK_GLASS")
    @RequireVaultRole(operation = "USE_BREAK_GLASS")
    public ResponseEntity<DetokenizeResponse> useBreakGlass(
            @PathVariable UUID grantId,
            @RequestBody @Valid BreakGlassUseRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(breakGlassService.useBreakGlassGrant(grantId, request));
    }

    @PostMapping("/break-glass/{grantId}/close")
    @EnforceVaultPolicy(operation = "BREAK_GLASS")
    @RequireVaultRole(operation = "BREAK_GLASS")
    public ResponseEntity<BreakGlassResponse> closeBreakGlass(@PathVariable UUID grantId) {
        return ResponseEntity.ok(breakGlassService.closeBreakGlassGrant(grantId));
    }

    @PostMapping("/subjects/{id}/erase")
    @EnforceVaultPolicy(operation = "ERASE_SUBJECT")
    @RequireVaultRole(operation = "ERASE_SUBJECT")
    public ResponseEntity<EraseSubjectResponse> eraseSubject(
            @PathVariable String id,
            @RequestBody(required = false) EraseSubjectRequest request) {
        EraseSubjectResponse response = erasureService.eraseSubject(id, request);
        return ResponseEntity.status(request != null ? HttpStatus.OK : HttpStatus.CREATED).body(response);
    }

    @GetMapping("/subjects/{id}/inventory")
    @EnforceVaultPolicy(operation = "SUBJECT_INVENTORY")
    @RequireVaultRole(operation = "SUBJECT_INVENTORY")
    public ResponseEntity<Map<String, Object>> getSubjectInventory(@PathVariable String id) {
        return ResponseEntity.ok(erasureService.getSubjectInventory(id));
    }

    @PostMapping("/legal-holds")
    @EnforceVaultPolicy(operation = "CREATE_LEGAL_HOLD")
    @RequireVaultRole(operation = "CREATE_LEGAL_HOLD")
    public ResponseEntity<LegalHoldResponse> createLegalHold(@RequestBody @Valid CreateLegalHoldRequest request) {
        LegalHoldResponse response = legalHoldService.createLegalHold(request);
        HttpStatus status = "LIFT_PENDING_APPROVAL".equalsIgnoreCase(response.getStatus())
                ? HttpStatus.ACCEPTED : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    @PostMapping("/legal-holds/{id}/lift")
    @EnforceVaultPolicy(operation = "LIFT_LEGAL_HOLD")
    @RequireVaultRole(operation = "LIFT_LEGAL_HOLD")
    public ResponseEntity<LegalHoldResponse> liftLegalHold(
            @RequestHeader("X-Approval-Id") String approvalId,
            @PathVariable UUID id) {
        dualControlGuard.requireApprovedApproval(approvalId, "LEGAL_HOLD_LIFT");
        return ResponseEntity.ok(legalHoldService.liftLegalHold(id));
    }

    @PostMapping("/subjects/{id}/erasure-exception")
    @EnforceVaultPolicy(operation = "APPROVE_ERASURE_EXCEPTION")
    @RequireVaultRole(operation = "APPROVE_ERASURE_EXCEPTION")
    public ResponseEntity<EraseSubjectResponse> erasureException(
            @RequestHeader("X-Approval-Id") String approvalId,
            @PathVariable String id,
            @RequestParam(required = false) String reason) {
        dualControlGuard.requireApprovedApproval(approvalId, "ERASURE_EXCEPTION");
        return ResponseEntity.status(HttpStatus.CREATED).body(erasureService.recordErasureException(id, reason));
    }

    @GetMapping("/audit/verify")
    @RequireVaultRole(operation = "VERIFY_AUDIT")
    public ResponseEntity<AuditChainVerifier.VerificationResult> verifyAuditChain() {
        return ResponseEntity.ok(auditChainVerifier.verify());
    }

    @GetMapping("/access-log")
    @EnforceVaultPolicy(operation = "ACCESS_LOG")
    @RequireVaultRole(operation = "ACCESS_LOG")
    public ResponseEntity<?> getAccessLog(
            @RequestParam(required = false) String view,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String caller,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String dataClass,
            @RequestParam(required = false) String purpose,
            @RequestParam(required = false) String contextRef,
            @RequestParam(required = false) String subjectRef,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String format,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "100") int size) {
        if (view == null && from == null && to == null && caller == null && operation == null
                && dataClass == null && purpose == null && subjectRef == null && outcome == null) {
            return ResponseEntity.ok(accessLogService.getAccessLog());
        }
        return ResponseEntity.ok(accessLogService.queryAccessLog(
                view, from, to, caller, operation, dataClass, purpose,
                contextRef, subjectRef, outcome, format, page, size));
    }

    @PostMapping("/backups/verify")
    @EnforceVaultPolicy(operation = "VERIFY_BACKUP")
    @RequireVaultRole(operation = "VERIFY_BACKUP")
    public ResponseEntity<BackupRunResponse> verifyBackup(@RequestBody(required = false) VerifyBackupRequest request) {
        BackupRunResponse response = backupService.verifyBackup(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping({"/policies", "/vault/policies"})
    @EnforceVaultPolicy(operation = "CREATE_POLICY")
    @RequireVaultRole(operation = "CREATE_POLICY")
    public ResponseEntity<PolicyResponse> createPolicy(
            @RequestHeader(value = "X-Approval-Id", required = false) String approvalId,
            @RequestBody @Valid CreatePolicyRequest request) {
        boolean preApproved = false;
        if (approvalId != null && !approvalId.isBlank()) {
            dualControlGuard.requireApprovedApproval(approvalId, "POLICY_CHANGE");
            preApproved = true;
        }
        PolicyResponse response = policyManagementService.createPolicy(request, preApproved);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping({"/policies", "/vault/policies"})
    @EnforceVaultPolicy(operation = "LIST_POLICIES")
    public ResponseEntity<List<PolicyResponse>> listPolicies() {
        return ResponseEntity.ok(policyManagementService.listPolicies());
    }

    @PostMapping("/approvals/request")
    @EnforceVaultPolicy(operation = "REQUEST_APPROVAL")
    public ResponseEntity<ApprovalResponse> requestApproval(@RequestBody @Valid ApprovalRequest request) {
        ApprovalResponse response = approvalService.requestApproval(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/approvals")
    @EnforceVaultPolicy(operation = "LIST_APPROVALS")
    @RequireVaultRole(operation = "LIST_APPROVALS")
    public ResponseEntity<List<ApprovalResponse>> listApprovals(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(approvalService.listApprovals(status));
    }

    @PutMapping("/approvals/{approvalId}/decision")
    @EnforceVaultPolicy(operation = "APPROVAL_DECISION")
    public ResponseEntity<ApprovalResponse> updateApprovalDecision(
            @PathVariable UUID approvalId,
            @RequestBody @Valid ApprovalDecisionRequest request) {
        ApprovalResponse response = approvalService.recordDecision(approvalId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/encrypt")
    public ResponseEntity<Map<String, String>> encryptEnvelope(@RequestBody Map<String, String> request) {
        String plaintext = request.get("plaintext");
        String dataClass = request.getOrDefault("dataClass", "PII");
        String ciphertext = cryptoEngineService.encryptEnvelope(plaintext, dataClass);
        return ResponseEntity.ok(Map.of("ciphertext", ciphertext != null ? ciphertext : "", "keyVersion", "1"));
    }

    @PostMapping("/decrypt")
    public ResponseEntity<Map<String, String>> decryptEnvelope(@RequestBody Map<String, String> request) {
        String ciphertext = request.get("ciphertext");
        String dataClass = request.getOrDefault("dataClass", "PII");
        String plaintext = cryptoEngineService.decryptEnvelope(ciphertext, dataClass);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of("plaintext", plaintext != null ? plaintext : ""));
    }
}
