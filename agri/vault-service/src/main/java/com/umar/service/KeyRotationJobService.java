package com.umar.service;

import com.umar.approval.ApprovalService;
import com.umar.audit.AuditRecorder;
import com.umar.entity.keyManagement.RotationJob;
import com.umar.events.VaultEventType;
import com.umar.exception.VaultApiException;
import com.umar.payload.request.vault.RotateKeyRequest;
import com.umar.payload.response.vault.ApprovalResponse;
import com.umar.payload.response.vault.RotateKeyResponse;
import com.umar.repository.keyManagement.RotationJobRepository;
import com.umar.rotation.KeyRotationWorkerImpl;
import com.umar.security.VaultRole;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * VLT-04 — Rotate Keys (CLASS_KEK, DATA_KEY, INDEX_KEY, SIGNING_KEY, ROOT).
 */
@Service
@RequiredArgsConstructor
public class KeyRotationJobService {

    private final RotationJobRepository rotationJobRepository;
    private final KeyRotationWorkerImpl keyRotationWorker;
    private final ApprovalService approvalService;
    private final VaultEventEmitter vaultEventEmitter;
    private final AuditRecorder auditRecorder;

    @Transactional
    public RotateKeyResponse rotateKey(RotateKeyRequest request) {
        return rotateKey(request, true);
    }

    @Transactional
    public RotateKeyResponse rotateKey(RotateKeyRequest request, boolean preApproved) {
        String keyType = request.getKeyType().trim().toUpperCase();
        String mode = request.getMode().trim().toUpperCase();

        if ("ROOT".equals(keyType) && !Boolean.TRUE.equals(request.getCeremonyApproved())) {
            throw VaultApiException.unprocessable("CEREMONY_REQUIRED", "Root rotation requires a key ceremony");
        }

        String scopeStr = request.getScope() != null ? request.getScope()
                : (request.getDataClass() != null ? request.getDataClass()
                : (request.getSecretRef() != null ? request.getSecretRef() : "ALL"));

        Optional<RotationJob> running = rotationJobRepository.findFirstByKeyTypeAndScopeAndStatusIn(
                keyType, scopeStr, List.of("RUNNING", "PENDING_APPROVAL"));
        if (running.isPresent()) {
            throw VaultApiException.conflict("ROTATION_IN_PROGRESS", "A rotation is already running",
                    Map.of("jobId", running.get().getId().toString()));
        }

        RotationJob job = new RotationJob();
        job.setId(UUID.randomUUID());
        job.setKeyType(keyType);
        job.setScope(scopeStr);
        job.setDataClass(request.getDataClass() != null ? request.getDataClass() : extractDataClass(scopeStr));
        job.setSecretRef(request.getSecretRef());
        job.setSubjectRef(request.getSubjectRef());
        job.setMode(mode);
        job.setReasonCode(request.getReasonCode());
        job.setNote(request.getNote());
        job.setRevokeOldVersion(Boolean.TRUE.equals(request.getRevokeOldVersion()));
        job.setStartedAt(Instant.now());

        if (!preApproved && ("ON_DEMAND".equals(mode) || "EMERGENCY".equals(mode))) {
            String subjectHash = AuditRecorder.hashIfPresent(keyType + "|" + scopeStr + "|" + mode);
            ApprovalResponse approval = approvalService.requestApproval(
                    "KEY_ACTION",
                    job.getId().toString(),
                    subjectHash,
                    List.of(VaultRole.KEY_CUSTODIAN, VaultRole.KEY_CUSTODIAN),
                    2);
            job.setStatus("PENDING_APPROVAL");
            job.setProgress("0%");
            job.setApprovalId(approval.getId());
            RotationJob saved = rotationJobRepository.save(job);
            auditRecorder.recordStrict("VAULT_KEY_ROTATION_REQUESTED", "ROTATE_KEY", job.getDataClass(),
                    job.getSecretRef(), "GENERAL", saved.getId().toString(), null, 1, "SUCCESS", saved.getId(), null);
            return toResponse(saved, List.of("KEY_CUSTODIAN", "KEY_CUSTODIAN"));
        }

        job.setStatus("RUNNING");
        job.setProgress("0%");
        RotationJob saved = rotationJobRepository.save(job);
        executeRotation(saved);

        RotationJob completed = rotationJobRepository.findById(saved.getId()).orElse(saved);
        return toResponse(completed, List.of());
    }

    @EventListener
    @Transactional
    public void onApprovalResolved(ApprovalService.ApprovalResolvedEvent event) {
        if (!"KEY_ACTION".equalsIgnoreCase(event.approval().getType())
                && !"KEY_ROTATION".equalsIgnoreCase(event.approval().getType())) {
            return;
        }
        try {
            UUID jobId = UUID.fromString(event.approval().getSubjectId());
            rotationJobRepository.findById(jobId).ifPresent(job -> {
                if ("APPROVED".equalsIgnoreCase(event.approval().getStatus())
                        && "PENDING_APPROVAL".equalsIgnoreCase(job.getStatus())) {
                    job.setStatus("RUNNING");
                    job.setApprovers(String.join(",", event.approval().getDecisions() != null
                            ? event.approval().getDecisions() : List.of()));
                    rotationJobRepository.save(job);
                    executeRotation(job);
                } else if ("REJECTED".equalsIgnoreCase(event.approval().getStatus())) {
                    job.setStatus("FAILED");
                    rotationJobRepository.save(job);
                }
            });
        } catch (Exception ignored) {
        }
    }

    private void executeRotation(RotationJob job) {
        switch (job.getKeyType()) {
            case "CLASS_KEK" -> keyRotationWorker.rotateClassKek(job.getId(), job.getDataClass());
            case "DATA_KEY" -> keyRotationWorker.rotateSubjectDek(job.getId(), job.getSubjectRef(), job.getDataClass());
            case "INDEX_KEY" -> keyRotationWorker.rotateIndexKey(job.getId(),
                    job.getDataClass() != null ? job.getDataClass() : "PHONE");
            case "SIGNING_KEY" -> keyRotationWorker.rotateSigningKey(job.getId(),
                    job.getSecretRef() != null ? job.getSecretRef() : job.getScope());
            case "ROOT" -> {
                job.setStatus("COMPLETED");
                job.setProgress("100%");
                job.setCompletedAt(Instant.now());
                rotationJobRepository.save(job);
            }
            default -> throw VaultApiException.badRequest("VALIDATION_ERROR", "Unsupported keyType: " + job.getKeyType());
        }

        RotationJob updated = rotationJobRepository.findById(job.getId()).orElse(job);
        vaultEventEmitter.emit(VaultEventType.KEY_ROTATED, Map.of(
                "operation", "ROTATE_KEY",
                "keyType", updated.getKeyType(),
                "scope", updated.getScope(),
                "mode", updated.getMode(),
                "oldVersion", updated.getOldVersion() != null ? updated.getOldVersion() : 1,
                "newVersion", updated.getNewVersion() != null ? updated.getNewVersion() : 2,
                "itemsMigrated", updated.getItemsDone() != null ? updated.getItemsDone() : 0,
                "jobId", updated.getId().toString()));
        auditRecorder.recordStrict("VAULT_KEY_ROTATED", "ROTATE_KEY", updated.getDataClass(),
                updated.getSecretRef(), "GENERAL", updated.getId().toString(), null, 1, "SUCCESS", updated.getId(), null);
    }

    private String extractDataClass(String scope) {
        if (scope == null || scope.isBlank()) return null;
        if (!scope.trim().startsWith("{")) return scope.trim();
        return null;
    }

    private RotateKeyResponse toResponse(RotationJob job, List<String> approvalsRequired) {
        Map<String, Object> progressMap = new LinkedHashMap<>();
        progressMap.put("itemsTotal", job.getItemsTotal() != null ? job.getItemsTotal() : 0);
        progressMap.put("itemsDone", job.getItemsDone() != null ? job.getItemsDone() : 0);
        progressMap.put("checkpoint", job.getCheckpoint());

        return RotateKeyResponse.builder()
                .id(job.getId())
                .jobId("rot_" + job.getId().toString().replace("-", "").substring(0, 8))
                .keyType(job.getKeyType())
                .scope(job.getScope())
                .mode(job.getMode())
                .status(job.getStatus())
                .oldVersion(job.getOldVersion())
                .newVersion(job.getNewVersion())
                .progress(progressMap)
                .approvalId(job.getApprovalId() != null ? job.getApprovalId().toString() : null)
                .approvalsRequired(approvalsRequired)
                .requestId("vq_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14))
                .build();
    }
}
