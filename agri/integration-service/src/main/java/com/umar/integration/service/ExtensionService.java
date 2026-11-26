package com.umar.integration.service;

import com.google.protobuf.ByteString;
import com.umar.grpc.hub.v1.*;
import com.umar.integration.client.DataVaultClient;
import com.umar.integration.client.NotificationClient;
import com.umar.integration.config.HubProperties;
import com.umar.integration.domain.entity.*;
import com.umar.integration.domain.enums.*;
import com.umar.enums.integration.*;
import com.umar.payload.request.integration.EnableExtensionRequest;
import com.umar.payload.request.integration.RegisterExtensionRequest;
import com.umar.payload.request.integration.RolloutExtensionRequest;
import com.umar.integration.error.HubException;
import com.umar.integration.repository.*;
import com.umar.integration.security.HubAccessGuard;
import com.umar.integration.security.HubIdentity;
import com.umar.integration.service.support.*;
import com.umar.integration.util.CryptoUtils;
import com.umar.integration.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ExtensionService {

    private final ExtensionPointRepository pointRepository;
    private final ExtensionVersionRepository extensionVersionRepository;
    private final ExtensionEnablementRepository enablementRepository;
    private final RolloutRunRepository rolloutRunRepository;
    private final InvocationRecordRepository invocationRecordRepository;
    private final ApprovalRepository approvalRepository;
    private final PartnerRepository partnerRepository;
    private final CatalogService catalogService;
    private final DataVaultClient dataVaultClient;
    private final SandboxRuntime sandboxRuntime;
    private final HookRuleEnforcer ruleEnforcer;
    private final NotificationClient notificationClient;
    private final HubAccessGuard accessGuard;
    private final IdempotencyService idempotencyService;
    private final OutboxService outboxService;
    private final RateLimiterService rateLimiterService;
    private final HubProperties properties;
    private final JsonUtils jsonUtils;

    /**
     * INT-06: Register Extension.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> registerExtension(String idempotencyKey, RegisterExtensionRequest request) {
        HubIdentity identity = accessGuard.requireRoles(
                HubRole.PARTNER_ADMIN, HubRole.INTEGRATION_MANAGER, HubRole.ADMIN, HubRole.PLATFORM_PRODUCT);

        Optional<Map> cached = idempotencyService.findExisting("REGISTER_EXTENSION", idempotencyKey, request, Map.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        String ownerId = resolveOwnerId(identity, request.getOwnerId());
        rateLimiterService.checkAndConsume("ext-reg:" + ownerId, 20, Duration.ofDays(1),
                "Extension registration daily quota exceeded");

        ExtensionPointEntity point = pointRepository.findById(request.getExtensionPoint())
                .orElseThrow(() -> HubException.notFound("POINT_NOT_FOUND", "Extension point not found"));

        if (point.isProtectedPoint() && point.getType().isOutcomeChanging()) {
            throw HubException.unprocessable("POINT_PROTECTED", "This point cannot be extended");
        }
        if (point.isProtectedPoint() && !identity.isInternalStaff()) {
            throw HubException.unprocessable("POINT_PROTECTED", "This point cannot be extended");
        }

        if (!catalogService.isContractSupported(point, request.getContractVersion())) {
            List<String> supported = point.getPreviousContractVersion() != null
                    ? List.of(point.getCurrentContractVersion(), point.getPreviousContractVersion())
                    : List.of(point.getCurrentContractVersion());
            throw HubException.unprocessable("CONTRACT_NOT_SUPPORTED",
                    "The contract version is no longer supported", Map.of("supported", supported));
        }

        // Validate owner grant if partner
        PartnerEntity partner = null;
        if (!"PLATFORM".equals(ownerId)) {
            partner = partnerRepository.findById(ownerId)
                    .orElseThrow(() -> HubException.notFound("PARTNER_NOT_FOUND", "Partner not found"));
            if (partner.getStatus() != PartnerStatus.ACTIVE) {
                throw HubException.forbidden("Partner must be ACTIVE to register extensions");
            }
            if (!point.isPartnerVisible()) {
                throw HubException.forbidden("Not authorised");
            }
            List<String> grantedPoints = jsonUtils.toStringList(partner.getExtensionPointsJson());
            if (!grantedPoints.isEmpty() && !grantedPoints.contains(point.getHookId())) {
                throw HubException.forbidden("Not authorised to extend " + point.getHookId());
            }
        }

        // Validate dataScope
        List<String> offeredScopes = jsonUtils.toStringList(point.getDataScopesJson());
        List<String> disallowedScopes = request.getDataScope().stream()
                .filter(s -> !offeredScopes.contains(s))
                .toList();
        if (!disallowedScopes.isEmpty()) {
            throw HubException.unprocessable("DATA_SCOPE_NOT_ALLOWED",
                    "The requested data scope is not allowed", Map.of("scopes", disallowedScopes));
        }

        // Validate limits & failMode
        int timeoutMs = request.getLimits() != null && request.getLimits().getTimeoutMs() != null
                ? request.getLimits().getTimeoutMs() : point.getDefaultTimeoutMs();
        int memoryMb = request.getLimits() != null && request.getLimits().getMemoryMb() != null
                ? request.getLimits().getMemoryMb() : properties.getExtension().getMaxMemoryMb();
        if (timeoutMs < 50 || timeoutMs > point.getMaxTimeoutMs()) {
            throw HubException.validation("timeoutMs must be between 50 and " + point.getMaxTimeoutMs());
        }
        if (memoryMb < 16 || memoryMb > properties.getExtension().getMaxMemoryMb()) {
            throw HubException.validation("memoryMb must be between 16 and " + properties.getExtension().getMaxMemoryMb());
        }

        List<String> allowedModes = jsonUtils.toStringList(point.getAllowedFailModesJson());
        FailMode failMode = request.getFailMode() != null
                ? request.getFailMode()
                : FailMode.valueOf(allowedModes.get(0));
        if (!allowedModes.contains(failMode.name())) {
            throw HubException.validation("failMode " + failMode + " is not allowed by " + point.getHookId());
        }

        RuntimeKind runtimeType = request.resolveRuntimeType();
        if (runtimeType == null) {
            throw HubException.validation("runtime.type must be WASM, CONTAINER, or HTTPS_CALLOUT");
        }

        // Verify secrets in Data Vault
        if (request.getSecrets() != null) {
            for (String secretRef : request.getSecrets()) {
                if (!dataVaultClient.verifySecretOwned(secretRef, ownerId, "EXTENSION_SECRET", "EXTENSION_REGISTRY")) {
                    throw HubException.forbidden("Referenced secret is not owned by caller: " + secretRef);
                }
            }
        }

        // Resolve extensionId and version monotonicity
        String extensionId;
        if (request.getBaseExtensionId() != null && !request.getBaseExtensionId().isBlank()) {
            extensionId = request.getBaseExtensionId().trim();
            List<ExtensionVersionEntity> existingVersions = extensionVersionRepository.findByExtensionIdOrderByCreatedAtDesc(extensionId);
            if (existingVersions.isEmpty()) {
                throw HubException.notFound("EXTENSION_NOT_FOUND", "Base extension not found");
            }
            if (!existingVersions.get(0).getOwnerId().equals(ownerId) && !identity.isInternalStaff()) {
                throw HubException.notFound("EXTENSION_NOT_FOUND", "Base extension not found");
            }
            for (ExtensionVersionEntity ev : existingVersions) {
                if (ev.getVersion().equals(request.getVersion())) {
                    throw HubException.conflict("VERSION_EXISTS", "This version already exists");
                }
                if (compareSemver(request.getVersion(), ev.getVersion()) <= 0) {
                    throw HubException.validation("version must be semantically greater than " + ev.getVersion());
                }
            }
        } else {
            List<ExtensionVersionEntity> sameName = extensionVersionRepository
                    .findByOwnerIdAndNameIgnoreCase(ownerId, request.getName().trim());
            if (!sameName.isEmpty()) {
                throw HubException.conflict("VERSION_EXISTS", "An extension with this name already exists");
            }
            extensionId = "ext_" + CryptoUtils.randomHex(3);
        }

        // Scan artifact
        String artifactToken = request.getRuntime().get("artifactToken") != null
                ? String.valueOf(request.getRuntime().get("artifactToken")) : null;
        SandboxRuntime.ScanResult scan = sandboxRuntime.scanArtifact(runtimeType, request.getRuntime(), null);
        if (!scan.passed()) {
            throw HubException.unprocessable("ARTIFACT_REJECTED",
                    "The artifact did not pass the security scan", Map.of("findings", scan.findings()));
        }

        // Execute test vectors in sandbox
        SandboxRuntime.TestSuiteResult testResult = sandboxRuntime.runTestVectors(
                point.getType(), runtimeType, request.getRuntime(), request.getTestVectors(), timeoutMs, memoryMb);
        if (!testResult.allPassed()) {
            throw HubException.unprocessable("SANDBOX_TEST_FAILED",
                    "The sandbox tests failed",
                    Map.of("failed", testResult.failed(), "deterministic", testResult.deterministic()));
        }

        String artifactHash = CryptoUtils.sha256Hex(jsonUtils.toJson(request.getRuntime()) + ":" + request.getVersion());
        String storedArtifactToken = dataVaultClient.storeDocument("EXTENSION_ARTIFACT", "EXTENSION_REGISTRY",
                ownerId, jsonUtils.toJson(Map.of(
                        "extensionId", extensionId,
                        "version", request.getVersion(),
                        "artifactHash", artifactHash,
                        "scan", scan,
                        "tests", testResult
                )), "application/json");

        Instant now = Instant.now();
        String versionRowId = extensionId + "@" + request.getVersion();
        ExtensionVersionEntity saved = extensionVersionRepository.save(ExtensionVersionEntity.builder()
                .id(versionRowId)
                .extensionId(extensionId)
                .name(request.getName().trim())
                .version(request.getVersion().trim())
                .extensionPoint(point.getHookId())
                .ownerId(ownerId)
                .contractVersion(request.getContractVersion())
                .runtimeType(runtimeType)
                .runtimeConfigJson(jsonUtils.toJson(request.getRuntime()))
                .dataScopeJson(jsonUtils.toJson(request.getDataScope()))
                .timeoutMs(timeoutMs)
                .memoryMb(memoryMb)
                .failMode(failMode)
                .secretsJson(jsonUtils.toJson(request.getSecrets() == null ? List.of() : request.getSecrets()))
                .testVectorsJson(jsonUtils.toJson(request.getTestVectors()))
                .description(request.getDescription().trim())
                .changelog(request.getChangelog().trim())
                .artifactHash(artifactHash)
                .artifactToken(artifactToken != null ? artifactToken : storedArtifactToken)
                .scanResult("PASSED")
                .scanFindingsJson("[]")
                .testsTotal(testResult.total())
                .testsPassed(testResult.passed())
                .testsFailed(testResult.failed())
                .deterministic(testResult.deterministic())
                .maxLatencyMs(testResult.maxLatencyMs())
                .maxMemoryMb(testResult.maxMemoryMb())
                .status(ExtensionStatus.TESTED)
                .etagVersion(1)
                .createdBy(identity.actorId())
                .createdAt(now)
                .updatedAt(now)
                .build());

        long snap = catalogService.bumpSnapshotVersion();
        outboxService.publishIntegrationEvent("extension.registered", extensionId, Map.of(
                "extensionId", extensionId,
                "version", saved.getVersion(),
                "extensionPoint", saved.getExtensionPoint(),
                "ownerId", ownerId,
                "runtime", runtimeType.name(),
                "status", saved.getStatus().name(),
                "artifactHash", artifactHash
        ));
        outboxService.publishIntegrationEvent("extension.catalog.changed", saved.getExtensionPoint(), Map.of(
                "change", "EXTENSION_REGISTERED",
                "hookId", saved.getExtensionPoint(),
                "snapshotVersion", snap
        ));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("extensionId", extensionId);
        response.put("version", saved.getVersion());
        response.put("extensionPoint", saved.getExtensionPoint());
        response.put("status", saved.getStatus().name());
        response.put("artifactHash", artifactHash);
        response.put("scan", Map.of("result", "PASSED", "findings", List.of()));
        response.put("tests", Map.of(
                "total", testResult.total(),
                "passed", testResult.passed(),
                "failed", testResult.failed(),
                "deterministic", testResult.deterministic(),
                "maxLatencyMs", testResult.maxLatencyMs(),
                "maxMemoryMb", testResult.maxMemoryMb()
        ));
        response.put("dataScope", request.getDataScope());
        response.put("limits", Map.of("timeoutMs", timeoutMs, "memoryMb", memoryMb));
        response.put("failMode", failMode.name());
        response.put("nextSteps", point.getType().isOutcomeChanging()
                ? List.of("ROLLOUT_SHADOW", "REQUEST_APPROVAL")
                : List.of("ROLLOUT_SHADOW", "ENABLE"));
        response.put("etagVersion", 1);
        response.put("createdAt", now.toString());

        idempotencyService.save("REGISTER_EXTENSION", idempotencyKey, identity.actorId(),
                request, HttpStatus.CREATED.value(), response, Duration.ofHours(24));
        return response;
    }

    /**
     * INT-12: Roll Out Extension (Shadow and Canary).
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> rollout(String extensionId, String idempotencyKey, RolloutExtensionRequest request) {
        HubIdentity identity = accessGuard.requireRoles(
                HubRole.PARTNER_ADMIN, HubRole.INTEGRATION_MANAGER, HubRole.ADMIN);

        if (accessGuard.isPartnerScopedOnly(identity) && request.getStage() != RolloutStage.SHADOW) {
            throw HubException.forbidden("Not authorised");
        }

        Optional<Map> cached = idempotencyService.findExisting("ROLLOUT:" + extensionId, idempotencyKey, request, Map.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        ExtensionVersionEntity ext = resolveExtensionVersion(extensionId, request.getVersion(), identity);
        if (ext.getStatus() == ExtensionStatus.KILLED || ext.getStatus() == ExtensionStatus.RETIRED
                || ext.getStatus() == ExtensionStatus.REJECTED) {
            throw HubException.conflict("INVALID_STATE", "This extension cannot be rolled out",
                    Map.of("state", ext.getStatus().name()));
        }

        ExtensionPointEntity point = pointRepository.findById(ext.getExtensionPoint())
                .orElseThrow(() -> HubException.notFound("POINT_NOT_FOUND", "Extension point not found"));

        rateLimiterService.checkAndConsume("rollout:" + identity.actorId(), 20, Duration.ofDays(1),
                "Daily rollout limit exceeded");

        Instant now = Instant.now();

        if (request.getStage() == RolloutStage.ROLLBACK) {
            RolloutRunEntity running = rolloutRunRepository
                    .findFirstByExtensionIdAndExtensionVersionAndStatus(ext.getExtensionId(), ext.getVersion(), RolloutStatus.RUNNING)
                    .orElseThrow(() -> HubException.conflict("INVALID_STATE", "No running rollout stage to roll back"));
            running.setStatus(RolloutStatus.ROLLED_BACK);
            running.setVerdict("ROLLED_BACK");
            running.setUpdatedAt(now);
            rolloutRunRepository.save(running);
            long snap = catalogService.bumpSnapshotVersion();
            outboxService.publishIntegrationEvent("extension.catalog.changed", ext.getExtensionPoint(), Map.of(
                    "change", "ENABLEMENT_CHANGED",
                    "hookId", ext.getExtensionPoint(),
                    "snapshotVersion", snap
            ));
            outboxService.publishIntegrationEvent("extension.rollout.changed", ext.getExtensionId(), Map.of(
                    "rolloutId", running.getRolloutId(),
                    "extensionId", ext.getExtensionId(),
                    "version", ext.getVersion(),
                    "stage", running.getStage().name(),
                    "status", RolloutStatus.ROLLED_BACK.name(),
                    "verdict", "ROLLED_BACK"
            ));
            return toRolloutResponse(running, List.of("ROLLOUT_SHADOW"));
        }

        if (request.getStage() == RolloutStage.COMPLETE) {
            RolloutRunEntity running = rolloutRunRepository
                    .findFirstByExtensionIdAndExtensionVersionAndStatus(ext.getExtensionId(), ext.getVersion(), RolloutStatus.RUNNING)
                    .orElseThrow(() -> HubException.conflict("INVALID_STATE", "No running rollout stage to complete"));
            RolloutStatus verdict = evaluateRolloutCriteria(running);
            running.setStatus(verdict);
            running.setVerdict(verdict.name());
            running.setUpdatedAt(now);
            rolloutRunRepository.save(running);
            if (verdict == RolloutStatus.PASSED) {
                ext.setStatus(running.getStage() == RolloutStage.SHADOW ? ExtensionStatus.SHADOW : ExtensionStatus.CANARY);
                ext.setUpdatedAt(now);
                extensionVersionRepository.save(ext);
            }
            long snap = catalogService.bumpSnapshotVersion();
            outboxService.publishIntegrationEvent("extension.catalog.changed", ext.getExtensionPoint(), Map.of(
                    "change", "ENABLEMENT_CHANGED",
                    "hookId", ext.getExtensionPoint(),
                    "snapshotVersion", snap
            ));
            outboxService.publishIntegrationEvent("extension.rollout.changed", ext.getExtensionId(), Map.of(
                    "rolloutId", running.getRolloutId(),
                    "extensionId", ext.getExtensionId(),
                    "version", ext.getVersion(),
                    "stage", running.getStage().name(),
                    "status", verdict.name(),
                    "verdict", verdict.name()
            ));
            List<String> nextSteps = running.getStage() == RolloutStage.SHADOW
                    ? List.of("START_CANARY")
                    : List.of("REQUEST_APPROVAL", "ENABLE");
            return toRolloutResponse(running, nextSteps);
        }

        // Starting SHADOW or CANARY
        if (rolloutRunRepository.findFirstByExtensionIdAndExtensionVersionAndStatus(
                ext.getExtensionId(), ext.getVersion(), RolloutStatus.RUNNING).isPresent()) {
            RolloutRunEntity activeRun = rolloutRunRepository.findFirstByExtensionIdAndExtensionVersionAndStatus(
                    ext.getExtensionId(), ext.getVersion(), RolloutStatus.RUNNING).get();
            throw HubException.conflict("STAGE_IN_PROGRESS", "A stage is already running",
                    Map.of("rolloutId", activeRun.getRolloutId()));
        }

        if (request.getScope() == null) {
            throw HubException.validation("scope is required to start SHADOW or CANARY");
        }
        validateScopeAgainstOwner(ext.getOwnerId(), request.getScope());

        if (request.getStage() == RolloutStage.CANARY) {
            if (request.getCanary() == null || request.getCanary().getPercent() == null) {
                throw HubException.validation("canary.percent (1..50) is required for CANARY");
            }
            boolean shadowPassed = rolloutRunRepository
                    .findByExtensionIdAndExtensionVersionOrderByCreatedAtDesc(ext.getExtensionId(), ext.getVersion())
                    .stream()
                    .anyMatch(r -> r.getStage() == RolloutStage.SHADOW && r.getStatus() == RolloutStatus.PASSED);
            if (point.getType().isOutcomeChanging() && !shadowPassed) {
                throw HubException.conflict("STAGE_REQUIRED", "The shadow stage must pass first");
            }
        }

        int durationHours = request.getDurationHours() != null ? request.getDurationHours() : 24;
        String rolloutId = "rol_" + CryptoUtils.randomHex(3);
        RolloutRunEntity run = rolloutRunRepository.save(RolloutRunEntity.builder()
                .rolloutId(rolloutId)
                .extensionId(ext.getExtensionId())
                .extensionVersion(ext.getVersion())
                .hookId(ext.getExtensionPoint())
                .stage(request.getStage())
                .status(RolloutStatus.RUNNING)
                .scopeJson(jsonUtils.toJson(request.getScope()))
                .canaryJson(jsonUtils.toJson(request.getCanary()))
                .durationHours(durationHours)
                .criteriaJson(jsonUtils.toJson(request.getCriteria() != null
                        ? request.getCriteria() : new RolloutExtensionRequest.CriteriaSpec()))
                .invocations(0L)
                .errors(0L)
                .timeouts(0L)
                .p50Ms(0L)
                .p95Ms(0L)
                .outcomeCountsJson("{}")
                .agreementPercent(100.0)
                .unexpectedHolds(0L)
                .wouldHaveRejected(0L)
                .sampleRefsJson("[]")
                .reason(request.getReason())
                .startedBy(identity.actorId())
                .endsAt(now.plus(Duration.ofHours(durationHours)))
                .createdAt(now)
                .updatedAt(now)
                .build());

        long snap = catalogService.bumpSnapshotVersion();
        outboxService.publishIntegrationEvent("extension.catalog.changed", ext.getExtensionPoint(), Map.of(
                "change", "ENABLEMENT_CHANGED",
                "hookId", ext.getExtensionPoint(),
                "snapshotVersion", snap
        ));
        outboxService.publishIntegrationEvent("extension.rollout.changed", ext.getExtensionId(), Map.of(
                "rolloutId", rolloutId,
                "extensionId", ext.getExtensionId(),
                "version", ext.getVersion(),
                "stage", request.getStage().name(),
                "status", RolloutStatus.RUNNING.name()
        ));

        List<String> next = request.getStage() == RolloutStage.SHADOW
                ? List.of("START_CANARY") : List.of("REQUEST_APPROVAL", "ENABLE");
        Map<String, Object> response = toRolloutResponse(run, next);
        idempotencyService.save("ROLLOUT:" + extensionId, idempotencyKey, identity.actorId(),
                request, HttpStatus.ACCEPTED.value(), response, Duration.ofHours(24));
        return response;
    }

    /**
     * INT-07: Enable, Disable or Kill Extension.
     */
    @Transactional
    public EnableResult enableOrDisableOrKill(String extensionId, String ifMatch, EnableExtensionRequest request) {
        HubIdentity identity = accessGuard.requireRoles(
                HubRole.INTEGRATION_MANAGER, HubRole.ADMIN, HubRole.PARTNER_ADMIN);

        String action = request.getAction().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ENABLE", "DISABLE", "KILL").contains(action)) {
            throw HubException.validation("action must be ENABLE, DISABLE, or KILL");
        }

        if (accessGuard.isPartnerScopedOnly(identity) && !"DISABLE".equals(action)) {
            throw HubException.forbidden("Not authorised");
        }

        ExtensionVersionEntity ext = resolveExtensionVersion(extensionId, request.getVersion(), identity);

        int expectedVersion = parseEtag(ifMatch);
        if (ext.getEtagVersion() != expectedVersion) {
            throw new HubException(HttpStatus.PRECONDITION_FAILED, "VERSION_CONFLICT",
                    "The extension changed. Reload and retry");
        }

        Instant now = Instant.now();

        if ("KILL".equals(action)) {
            if (ext.getStatus() == ExtensionStatus.KILLED) {
                return buildEnableResult(ext, null, "KILLED", now.plusSeconds(properties.getExtension().getKillPropagationSeconds()));
            }
            ext.setStatus(ExtensionStatus.KILLED);
            ext.setEtagVersion(ext.getEtagVersion() + 1);
            ext.setUpdatedAt(now);
            extensionVersionRepository.save(ext);

            for (ExtensionEnablementEntity en : enablementRepository.findByExtensionIdAndActiveTrue(ext.getExtensionId())) {
                en.setActive(false);
                en.setUpdatedAt(now);
                enablementRepository.save(en);
            }
            for (RolloutRunEntity r : rolloutRunRepository.findByExtensionIdAndExtensionVersionOrderByCreatedAtDesc(
                    ext.getExtensionId(), ext.getVersion())) {
                if (r.getStatus() == RolloutStatus.RUNNING) {
                    r.setStatus(RolloutStatus.ROLLED_BACK);
                    r.setUpdatedAt(now);
                    rolloutRunRepository.save(r);
                }
            }

            long snap = catalogService.bumpSnapshotVersion();
            outboxService.publishIntegrationEvent("extension.catalog.changed", ext.getExtensionPoint(), Map.of(
                    "change", "ENABLEMENT_CHANGED",
                    "hookId", ext.getExtensionPoint(),
                    "prioritySignal", true,
                    "snapshotVersion", snap
            ));
            outboxService.publishIntegrationEvent("extension.disabled", ext.getExtensionId(), Map.of(
                    "extensionId", ext.getExtensionId(),
                    "version", ext.getVersion(),
                    "extensionPoint", ext.getExtensionPoint(),
                    "reason", "KILL",
                    "disabledBy", identity.actorId()
            ));
            return buildEnableResult(ext, null, "KILLED", now.plusSeconds(properties.getExtension().getKillPropagationSeconds()));
        }

        if ("DISABLE".equals(action)) {
            if (ext.getStatus() == ExtensionStatus.KILLED) {
                throw HubException.conflict("INVALID_STATE", "This change is not possible in the current state",
                        Map.of("state", "KILLED"));
            }
            for (ExtensionEnablementEntity en : enablementRepository.findByExtensionIdAndExtensionVersionAndActiveTrue(
                    ext.getExtensionId(), ext.getVersion())) {
                en.setActive(false);
                en.setUpdatedAt(now);
                enablementRepository.save(en);
            }
            ext.setStatus(ExtensionStatus.DISABLED);
            ext.setEtagVersion(ext.getEtagVersion() + 1);
            ext.setUpdatedAt(now);
            extensionVersionRepository.save(ext);

            long snap = catalogService.bumpSnapshotVersion();
            outboxService.publishIntegrationEvent("extension.catalog.changed", ext.getExtensionPoint(), Map.of(
                    "change", "ENABLEMENT_CHANGED",
                    "hookId", ext.getExtensionPoint(),
                    "snapshotVersion", snap
            ));
            outboxService.publishIntegrationEvent("extension.disabled", ext.getExtensionId(), Map.of(
                    "extensionId", ext.getExtensionId(),
                    "version", ext.getVersion(),
                    "extensionPoint", ext.getExtensionPoint(),
                    "reason", "MANUAL",
                    "disabledBy", identity.actorId()
            ));
            return buildEnableResult(ext, null, "DISABLED", now.plusSeconds(properties.getExtension().getNormalPropagationSeconds()));
        }

        // ENABLE
        if (ext.getStatus() == ExtensionStatus.KILLED || ext.getStatus() == ExtensionStatus.RETIRED
                || ext.getStatus() == ExtensionStatus.REJECTED || ext.getStatus() == ExtensionStatus.DRAFT) {
            throw HubException.conflict("INVALID_STATE", "This change is not possible in the current state",
                    Map.of("state", ext.getStatus().name()));
        }

        if (request.getScope() == null || ((request.getScope().getTenantIds() == null || request.getScope().getTenantIds().isEmpty())
                && (request.getScope().getProductIds() == null || request.getScope().getProductIds().isEmpty()))) {
            throw HubException.validation("scope must include at least one tenantId or productId");
        }
        if (request.getPriority() == null) {
            throw HubException.validation("priority is required for ENABLE");
        }

        ExtensionPointEntity point = pointRepository.findById(ext.getExtensionPoint())
                .orElseThrow(() -> HubException.notFound("POINT_NOT_FOUND", "Extension point not found"));

        validateScopeAgainstOwner(ext.getOwnerId(), request.getScope());

        FailMode failMode = request.getFailMode() != null ? request.getFailMode() : ext.getFailMode();
        List<String> allowedModes = jsonUtils.toStringList(point.getAllowedFailModesJson());
        if (!allowedModes.contains(failMode.name())) {
            throw HubException.validation("failMode not allowed for " + point.getHookId());
        }
        int timeoutMs = request.getTimeoutMs() != null ? request.getTimeoutMs() : ext.getTimeoutMs();
        if (timeoutMs > point.getMaxTimeoutMs()) {
            throw HubException.validation("timeoutMs exceeds point maximum of " + point.getMaxTimeoutMs());
        }

        // Check rollout & approval gates for decision / money-flow points
        if (point.getType().isOutcomeChanging()) {
            List<RolloutRunEntity> runs = rolloutRunRepository
                    .findByExtensionIdAndExtensionVersionOrderByCreatedAtDesc(ext.getExtensionId(), ext.getVersion());
            boolean shadowOk = runs.stream().anyMatch(r -> r.getStage() == RolloutStage.SHADOW && r.getStatus() == RolloutStatus.PASSED);
            boolean canaryOk = runs.stream().anyMatch(r -> r.getStage() == RolloutStage.CANARY && r.getStatus() == RolloutStatus.PASSED);
            if (!shadowOk || !canaryOk) {
                throw HubException.conflict("ROLLOUT_INCOMPLETE", "The rollout stages are not complete");
            }

            if (ext.getStatus() != ExtensionStatus.APPROVED && ext.getStatus() != ExtensionStatus.ENABLED) {
                String approvalId = ext.getApprovalId();
                if (approvalId == null) {
                    approvalId = "apr_" + CryptoUtils.randomHex(4);
                    String subjectHash = CryptoUtils.sha256Hex(ext.getArtifactHash() + ":" + ext.getVersion());
                    approvalRepository.save(ApprovalEntity.builder()
                            .approvalId(approvalId)
                            .type(ApprovalType.EXTENSION_ENABLE)
                            .subjectId(ext.getId())
                            .subjectVersion(ext.getEtagVersion())
                            .subjectHash(subjectHash)
                            .requiredRolesJson(jsonUtils.toJson(List.of(
                                    HubRole.INTEGRATION_MANAGER.name(), HubRole.COMPLIANCE_OFFICER.name())))
                            .decisionsJson("[]")
                            .status(ApprovalStatus.OPEN)
                            .requestedBy(identity.actorId())
                            .slaDueAt(now.plus(Duration.ofDays(3)))
                            .createdAt(now)
                            .updatedAt(now)
                            .build());
                    ext.setApprovalId(approvalId);
                    extensionVersionRepository.save(ext);
                }
                throw HubException.conflict("APPROVAL_REQUIRED",
                        "Approval is required before enabling", Map.of("approvalId", approvalId));
            }
        }

        // Check priority conflict for overlapping scopes at the same point
        for (ExtensionEnablementEntity existingEn : enablementRepository.findByHookIdAndActiveTrueOrderByPriorityAsc(point.getHookId())) {
            if (existingEn.getExtensionId().equals(ext.getExtensionId())) {
                continue;
            }
            if (Objects.equals(existingEn.getPriority(), request.getPriority())
                    && scopesOverlap(existingEn, request.getScope())) {
                throw HubException.unprocessable("PRIORITY_CONFLICT", "This priority is already in use");
            }
        }

        EnableExtensionRequest.AutoDisableSpec ad = request.getAutoDisable() != null
                ? request.getAutoDisable() : new EnableExtensionRequest.AutoDisableSpec();

        Instant effectiveBy = now.plusSeconds(properties.getExtension().getNormalPropagationSeconds());
        ExtensionEnablementEntity en = enablementRepository.save(ExtensionEnablementEntity.builder()
                .enablementId("enb_" + CryptoUtils.randomHex(4))
                .extensionId(ext.getExtensionId())
                .extensionVersion(ext.getVersion())
                .hookId(ext.getExtensionPoint())
                .tenantIdsJson(jsonUtils.toJson(nullSafe(request.getScope().getTenantIds())))
                .productIdsJson(jsonUtils.toJson(nullSafe(request.getScope().getProductIds())))
                .regionCodesJson(jsonUtils.toJson(nullSafe(request.getScope().getRegionCodes())))
                .priority(request.getPriority())
                .failMode(failMode)
                .timeoutMs(timeoutMs)
                .errorRatePercent(ad.getErrorRatePercent() != null ? ad.getErrorRatePercent() : 20)
                .windowMinutes(ad.getWindowMinutes() != null ? ad.getWindowMinutes() : 5)
                .p95LatencyMs(ad.getP95LatencyMs())
                .consecutiveFailures(ad.getConsecutiveFailures() != null ? ad.getConsecutiveFailures() : 20)
                .active(true)
                .reason(request.getReason())
                .enabledBy(identity.actorId())
                .effectiveBy(effectiveBy)
                .createdAt(now)
                .updatedAt(now)
                .build());

        ext.setStatus(ExtensionStatus.ENABLED);
        ext.setEtagVersion(ext.getEtagVersion() + 1);
        ext.setUpdatedAt(now);
        extensionVersionRepository.save(ext);

        long snap = catalogService.bumpSnapshotVersion();
        outboxService.publishIntegrationEvent("extension.catalog.changed", ext.getExtensionPoint(), Map.of(
                "change", "ENABLEMENT_CHANGED",
                "hookId", ext.getExtensionPoint(),
                "snapshotVersion", snap
        ));
        outboxService.publishIntegrationEvent("extension.enabled", ext.getExtensionId(), Map.of(
                "extensionId", ext.getExtensionId(),
                "version", ext.getVersion(),
                "extensionPoint", ext.getExtensionPoint(),
                "scope", request.getScope(),
                "priority", request.getPriority(),
                "failMode", failMode.name(),
                "timeoutMs", timeoutMs,
                "enabledBy", identity.actorId()
        ));

        return buildEnableResult(ext, en, "ENABLED", effectiveBy);
    }

    /**
     * Requests an approval explicitly for an extension version (after shadow/canary) so INT-15 can approve before INT-07 ENABLE.
     */
    @Transactional
    public Map<String, Object> requestEnableApproval(String extensionId, String version) {
        HubIdentity identity = accessGuard.requireRoles(
                HubRole.PARTNER_ADMIN, HubRole.INTEGRATION_MANAGER, HubRole.ADMIN);
        ExtensionVersionEntity ext = resolveExtensionVersion(extensionId, version, identity);
        ExtensionPointEntity point = pointRepository.findById(ext.getExtensionPoint())
                .orElseThrow(() -> HubException.notFound("POINT_NOT_FOUND", "Extension point not found"));

        Instant now = Instant.now();
        String approvalId = "apr_" + CryptoUtils.randomHex(4);
        String subjectHash = CryptoUtils.sha256Hex(ext.getArtifactHash() + ":" + ext.getVersion());
        List<String> roles = point.getType().isOutcomeChanging()
                ? List.of(HubRole.INTEGRATION_MANAGER.name(), HubRole.COMPLIANCE_OFFICER.name())
                : List.of(HubRole.INTEGRATION_MANAGER.name());

        approvalRepository.save(ApprovalEntity.builder()
                .approvalId(approvalId)
                .type(ApprovalType.EXTENSION_ENABLE)
                .subjectId(ext.getId())
                .subjectVersion(ext.getEtagVersion())
                .subjectHash(subjectHash)
                .requiredRolesJson(jsonUtils.toJson(roles))
                .decisionsJson("[]")
                .status(ApprovalStatus.OPEN)
                .requestedBy(identity.actorId())
                .slaDueAt(now.plus(Duration.ofDays(3)))
                .createdAt(now)
                .updatedAt(now)
                .build());
        ext.setApprovalId(approvalId);
        ext.setUpdatedAt(now);
        extensionVersionRepository.save(ext);

        return Map.of(
                "approvalId", approvalId,
                "extensionId", ext.getExtensionId(),
                "version", ext.getVersion(),
                "subjectHash", subjectHash,
                "requiredRoles", roles
        );
    }

    /**
     * INT-14: Invoke Extension (gRPC) and internal hook runtime helper.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public InvokeExtensionResponse invokeExtension(String authenticatedService, InvokeExtensionRequest request) {
        ExtensionPointEntity point = pointRepository.findById(request.getHookId())
                .orElseThrow(() -> new NoSuchElementException("Hook not declared: " + request.getHookId()));

        if (authenticatedService != null && !authenticatedService.isBlank()
                && !authenticatedService.equalsIgnoreCase(point.getServiceName())) {
            throw new SecurityException("Caller " + authenticatedService + " does not own hook " + point.getHookId());
        }
        if (!catalogService.isContractSupported(point, request.getContractVersion())) {
            throw new IllegalArgumentException("Unsupported contract version: " + request.getContractVersion());
        }
        int deadlineMs = request.getDeadlineMs() > 0 ? request.getDeadlineMs() : point.getDefaultTimeoutMs();
        if (deadlineMs > point.getMaxTimeoutMs()) {
            throw new IllegalArgumentException("deadline_ms exceeds point maximum " + point.getMaxTimeoutMs());
        }

        String rawInputJson = request.getInput().isEmpty()
                ? "{}"
                : request.getInput().toString(StandardCharsets.UTF_8);
        Map<String, Object> fullInput = jsonUtils.toMap(rawInputJson);
        String tenantId = request.getContext().getTenantId();
        String productId = request.getContext().getProductId();
        String regionCode = request.getContext().getRegionCode();

        List<ExtensionEnablementEntity> activeEnablements = enablementRepository
                .findByHookIdAndActiveTrueOrderByPriorityAsc(point.getHookId()).stream()
                .filter(en -> matchesContext(en, tenantId, productId, regionCode))
                .toList();

        List<RolloutRunEntity> activeRollouts = rolloutRunRepository
                .findByHookIdAndStatus(point.getHookId(), RolloutStatus.RUNNING).stream()
                .filter(r -> matchesRolloutContext(r, tenantId, productId, regionCode, request.getSubjectRef()))
                .toList();

        long snapshotVersion = catalogService.currentSnapshotVersion();
        String invocationId = "inv_" + CryptoUtils.randomHex(4);

        if (activeEnablements.isEmpty() && activeRollouts.isEmpty()) {
            return InvokeExtensionResponse.newBuilder()
                    .setStatus(InvokeStatus.NO_EXTENSION)
                    .setFailModeApplied(FailModeApplied.FAIL_MODE_NONE)
                    .setInvocationId(invocationId)
                    .setSnapshotVersion(snapshotVersion)
                    .build();
        }

        List<ExtensionOutcome> outcomes = new ArrayList<>();
        Map<String, Object> mergedPayload = new LinkedHashMap<>();
        List<String> mergedHolds = new ArrayList<>();
        List<String> mergedRejections = new ArrayList<>();
        FailModeApplied worstFailMode = FailModeApplied.FAIL_MODE_NONE;
        boolean anyApplied = false;

        for (ExtensionEnablementEntity en : activeEnablements) {
            ExtensionVersionEntity ext = extensionVersionRepository
                    .findByExtensionIdAndVersion(en.getExtensionId(), en.getExtensionVersion())
                    .orElse(null);
            if (ext == null) continue;

            // Check tenant monthly quota
            if (tenantId != null && !tenantId.isBlank()
                    && !rateLimiterService.tryConsume("ext-inv:" + tenantId, 50000, Duration.ofDays(30))) {
                throw new IllegalStateException("RESOURCE_EXHAUSTED: Tenant invocation quota exceeded");
            }

            Map<String, Object> scopedInput = projectInputToScope(fullInput, jsonUtils.toStringList(ext.getDataScopeJson()));
            Map<String, String> injectedSecrets = dataVaultClient.injectSecrets(
                    jsonUtils.toStringList(ext.getSecretsJson()), ext.getOwnerId());
            int effectiveTimeout = Math.min(en.getTimeoutMs(), deadlineMs);

            SandboxRuntime.ExecutionResult exec = sandboxRuntime.executeLive(
                    ext.getExtensionId(), point.getType(), ext.getRuntimeType(),
                    jsonUtils.toMap(ext.getRuntimeConfigJson()), scopedInput, injectedSecrets, effectiveTimeout);

            ExtensionExecutionResult execEnum = switch (exec.result()) {
                case "OK" -> ExtensionExecutionResult.EXEC_OK;
                case "TIMEOUT" -> ExtensionExecutionResult.TIMEOUT;
                case "RULE_VIOLATION" -> ExtensionExecutionResult.RULE_VIOLATION;
                default -> ExtensionExecutionResult.ERROR;
            };

            outcomes.add(ExtensionOutcome.newBuilder()
                    .setExtensionId(ext.getExtensionId())
                    .setVersion(ext.getVersion())
                    .setResult(execEnum)
                    .setLatencyMs(exec.latencyMs())
                    .setReason(Objects.toString(exec.reason(), ""))
                    .build());

            recordInvocation(invocationId, point.getHookId(), ext, request.getCallerService(), tenantId,
                    request.getSubjectRef(), exec.result(),
                    "OK".equals(exec.result()) ? "NONE" : en.getFailMode().name(),
                    exec.latencyMs(), scopedInput, exec.output(), false);

            if ("OK".equals(exec.result())) {
                anyApplied = true;
                mergeOutput(point.getType(), exec.output(), mergedPayload, mergedHolds, mergedRejections);
            } else {
                worstFailMode = maxFailMode(worstFailMode, toProtoFailMode(en.getFailMode()));
                if (en.getFailMode() == FailMode.HOLD_FOR_REVIEW) {
                    mergedHolds.add("HOLD_FOR_REVIEW:" + ext.getExtensionId());
                } else if (en.getFailMode() == FailMode.FAIL_CLOSED) {
                    mergedRejections.add("FAIL_CLOSED:" + ext.getExtensionId());
                }
                checkAutoDisable(en, ext);
            }
        }

        // Execute active SHADOW and CANARY rollout runs
        for (RolloutRunEntity run : activeRollouts) {
            ExtensionVersionEntity ext = extensionVersionRepository
                    .findByExtensionIdAndVersion(run.getExtensionId(), run.getExtensionVersion())
                    .orElse(null);
            if (ext == null) continue;
            Map<String, Object> scopedInput = projectInputToScope(fullInput, jsonUtils.toStringList(ext.getDataScopeJson()));
            Map<String, String> injectedSecrets = dataVaultClient.injectSecrets(
                    jsonUtils.toStringList(ext.getSecretsJson()), ext.getOwnerId());

            SandboxRuntime.ExecutionResult exec = sandboxRuntime.executeLive(
                    ext.getExtensionId(), point.getType(), ext.getRuntimeType(),
                    jsonUtils.toMap(ext.getRuntimeConfigJson()), scopedInput, injectedSecrets,
                    Math.min(ext.getTimeoutMs(), deadlineMs));

            boolean isShadow = run.getStage() == RolloutStage.SHADOW;
            run.setInvocations(run.getInvocations() + 1);
            if ("TIMEOUT".equals(exec.result())) run.setTimeouts(run.getTimeouts() + 1);
            else if (!"OK".equals(exec.result())) run.setErrors(run.getErrors() + 1);
            run.setP95Ms(Math.max(run.getP95Ms(), exec.latencyMs()));
            run.setP50Ms(run.getP50Ms() == 0 ? exec.latencyMs() : (run.getP50Ms() + exec.latencyMs()) / 2);
            run.setUpdatedAt(Instant.now());
            rolloutRunRepository.save(run);

            outcomes.add(ExtensionOutcome.newBuilder()
                    .setExtensionId(ext.getExtensionId())
                    .setVersion(ext.getVersion())
                    .setResult(isShadow ? ExtensionExecutionResult.SHADOW :
                            ("OK".equals(exec.result()) ? ExtensionExecutionResult.EXEC_OK : ExtensionExecutionResult.ERROR))
                    .setLatencyMs(exec.latencyMs())
                    .setReason(Objects.toString(exec.reason(), ""))
                    .build());

            recordInvocation(invocationId, point.getHookId(), ext, request.getCallerService(), tenantId,
                    request.getSubjectRef(), isShadow ? "SHADOW" : exec.result(),
                    "NONE", exec.latencyMs(), scopedInput, exec.output(), isShadow);

            if (!isShadow && "OK".equals(exec.result())) {
                anyApplied = true;
                mergeOutput(point.getType(), exec.output(), mergedPayload, mergedHolds, mergedRejections);
            }
        }

        InvokeStatus finalStatus;
        if (!mergedRejections.isEmpty()) {
            finalStatus = InvokeStatus.REJECTED;
        } else if (!mergedHolds.isEmpty()) {
            finalStatus = InvokeStatus.HELD;
        } else if (worstFailMode == FailModeApplied.FAIL_OPEN && !anyApplied) {
            finalStatus = InvokeStatus.FAILED_OPEN;
        } else if (worstFailMode != FailModeApplied.FAIL_MODE_NONE && anyApplied) {
            finalStatus = InvokeStatus.PARTIAL;
        } else {
            finalStatus = InvokeStatus.OK;
        }

        byte[] mergedBytes = jsonUtils.toJson(mergedPayload).getBytes(StandardCharsets.UTF_8);
        return InvokeExtensionResponse.newBuilder()
                .setStatus(finalStatus)
                .setMerged(MergedExtensionResult.newBuilder()
                        .setPayloadJson(ByteString.copyFrom(mergedBytes))
                        .addAllHolds(mergedHolds)
                        .addAllRejections(mergedRejections)
                        .build())
                .addAllExtensions(outcomes)
                .setFailModeApplied(worstFailMode)
                .setInvocationId(invocationId)
                .setSnapshotVersion(snapshotVersion)
                .build();
    }

    /**
     * Convenience method for internal Hub services (INT-03, INT-09) to invoke hub.* hooks directly.
     */
    @Transactional
    public Map<String, Object> invokeInternalHubHook(String hookId, String tenantId, String subjectRef, Map<String, Object> input) {
        InvokeExtensionResponse resp = invokeExtension("integration-hub-service", InvokeExtensionRequest.newBuilder()
                .setHookId(hookId)
                .setContractVersion("1.0")
                .setCallerService("integration-hub-service")
                .setCorrelationId(UUID.randomUUID().toString())
                .setContext(InvocationContext.newBuilder().setTenantId(Objects.toString(tenantId, "")).build())
                .setSubjectRef(Objects.toString(subjectRef, ""))
                .setInput(ByteString.copyFromUtf8(jsonUtils.toJson(input)))
                .setDeadlineMs(1500)
                .build());
        if (resp.getStatus() == InvokeStatus.NO_EXTENSION || resp.getMerged().getPayloadJson().isEmpty()) {
            return Map.of();
        }
        return jsonUtils.toMap(resp.getMerged().getPayloadJson().toStringUtf8());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listExtensions() {
        HubIdentity identity = accessGuard.requireRoles(
                HubRole.PARTNER_ADMIN, HubRole.INTEGRATION_MANAGER, HubRole.ADMIN, HubRole.AUDITOR);
        return extensionVersionRepository.findAll().stream()
                .filter(e -> !accessGuard.isPartnerScopedOnly(identity) || e.getOwnerId().equals(identity.partnerId()))
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("extensionId", e.getExtensionId());
                    m.put("name", e.getName());
                    m.put("version", e.getVersion());
                    m.put("extensionPoint", e.getExtensionPoint());
                    m.put("ownerId", e.getOwnerId());
                    m.put("status", e.getStatus().name());
                    m.put("etagVersion", e.getEtagVersion());
                    return m;
                }).toList();
    }

    private void recordInvocation(String invocationId, String hookId, ExtensionVersionEntity ext,
                                  String callerService, String tenantId, String subjectRef,
                                  String result, String failModeApplied, long latencyMs,
                                  Map<String, Object> input, Map<String, Object> output, boolean shadow) {
        String subjectHash = CryptoUtils.sha256Hex(Objects.toString(subjectRef, ""));
        String inputHash = CryptoUtils.sha256Hex(jsonUtils.toJson(input));
        String outputHash = CryptoUtils.sha256Hex(jsonUtils.toJson(output));
        String recordId = invocationId + "_" + CryptoUtils.randomHex(3);
        invocationRecordRepository.save(InvocationRecordEntity.builder()
                .invocationId(recordId)
                .hookId(hookId)
                .extensionId(ext.getExtensionId())
                .extensionVersion(ext.getVersion())
                .callerService(callerService)
                .tenantId(tenantId)
                .subjectRefHash(subjectHash)
                .result(result)
                .failModeApplied(failModeApplied)
                .latencyMs(latencyMs)
                .inputHash(inputHash)
                .outputHash(outputHash)
                .shadowMode(shadow)
                .createdAt(Instant.now())
                .build());

        outboxService.publishIntegrationEvent("extension.invoked", ext.getExtensionId(), Map.of(
                "invocationId", recordId,
                "hookId", hookId,
                "extensionId", ext.getExtensionId(),
                "version", ext.getVersion(),
                "callerService", callerService,
                "subjectRefHash", subjectHash,
                "result", result,
                "latencyMs", latencyMs,
                "failModeApplied", failModeApplied
        ));
    }

    private void checkAutoDisable(ExtensionEnablementEntity en, ExtensionVersionEntity ext) {
        Instant windowStart = Instant.now().minus(Duration.ofMinutes(en.getWindowMinutes()));
        List<InvocationRecordEntity> recent = invocationRecordRepository
                .findByExtensionIdAndCreatedAtBetweenOrderByCreatedAtDesc(ext.getExtensionId(), windowStart, Instant.now());
        if (recent.isEmpty()) return;

        long failures = recent.stream().filter(r -> !"OK".equals(r.getResult()) && !"SHADOW".equals(r.getResult())).count();
        double errorRate = (failures * 100.0) / recent.size();

        int consecutive = 0;
        for (InvocationRecordEntity r : recent) {
            if (!"OK".equals(r.getResult()) && !"SHADOW".equals(r.getResult())) consecutive++;
            else break;
        }

        boolean rateExceeded = recent.size() >= 5 && errorRate >= en.getErrorRatePercent();
        boolean consecutiveExceeded = consecutive >= en.getConsecutiveFailures();
        if (rateExceeded || consecutiveExceeded) {
            en.setActive(false);
            en.setUpdatedAt(Instant.now());
            enablementRepository.save(en);
            ext.setStatus(ExtensionStatus.DISABLED);
            ext.setEtagVersion(ext.getEtagVersion() + 1);
            ext.setUpdatedAt(Instant.now());
            extensionVersionRepository.save(ext);
            catalogService.bumpSnapshotVersion();

            outboxService.publishIntegrationEvent("extension.disabled", ext.getExtensionId(), Map.of(
                    "extensionId", ext.getExtensionId(),
                    "version", ext.getVersion(),
                    "extensionPoint", ext.getExtensionPoint(),
                    "reason", "AUTO_ERROR_RATE",
                    "disabledBy", "SYSTEM"
            ));
            notificationClient.notifyPartner(ext.getOwnerId(), "Extension auto-disabled",
                    "Extension " + ext.getExtensionId() + " exceeded error threshold");
        }
    }

    @SuppressWarnings("unchecked")
    private void mergeOutput(HookType hookType, Map<String, Object> out,
                             Map<String, Object> merged, List<String> holds, List<String> rejections) {
        if (out == null || out.isEmpty() || hookType == HookType.POST || hookType == HookType.SHADOW) {
            return;
        }
        if (Boolean.TRUE.equals(out.get("hold")) || "HOLD".equalsIgnoreCase(String.valueOf(out.get("decision")))) {
            holds.add(Objects.toString(out.get("reasonCode"), "EXTENSION_HOLD"));
        }
        if (out.get("holds") instanceof List<?> list) {
            list.forEach(x -> holds.add(String.valueOf(x)));
        }
        if (Boolean.TRUE.equals(out.get("reject")) || "REJECT".equalsIgnoreCase(String.valueOf(out.get("decision")))) {
            rejections.add(Objects.toString(out.get("reasonCode"), "EXTENSION_REJECTED"));
        }
        if (out.get("rejections") instanceof List<?> list) {
            list.forEach(x -> rejections.add(String.valueOf(x)));
        }
        for (Map.Entry<String, Object> e : out.entrySet()) {
            if ("loadings".equals(e.getKey()) && e.getValue() instanceof List<?> addList) {
                List<Object> existing = (List<Object>) merged.computeIfAbsent("loadings", k -> new ArrayList<>());
                existing.addAll(addList);
            } else if (!Set.of("hold", "holds", "reject", "rejections", "decision").contains(e.getKey())) {
                merged.put(e.getKey(), e.getValue());
            }
        }
    }

    private Map<String, Object> projectInputToScope(Map<String, Object> fullInput, List<String> scopes) {
        if (fullInput == null) return Map.of();
        Map<String, Object> copy = new LinkedHashMap<>(fullInput);
        // Never leak PII keys unless explicitly granted
        for (String pii : PlatformAllowList.PII_FIELD_NAMES) {
            copy.remove(pii);
        }
        return copy;
    }

    private boolean matchesContext(ExtensionEnablementEntity en, String tenantId, String productId, String regionCode) {
        List<String> tenants = jsonUtils.toStringList(en.getTenantIdsJson());
        List<String> products = jsonUtils.toStringList(en.getProductIdsJson());
        List<String> regions = jsonUtils.toStringList(en.getRegionCodesJson());
        if (!tenants.isEmpty() && (tenantId == null || !tenants.contains(tenantId))) return false;
        if (!products.isEmpty() && (productId == null || !products.contains(productId))) return false;
        if (!regions.isEmpty() && (regionCode == null || !regions.contains(regionCode))) return false;
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean matchesRolloutContext(RolloutRunEntity run, String tenantId, String productId, String regionCode, String subjectRef) {
        Map<String, Object> scope = jsonUtils.toMap(run.getScopeJson());
        List<String> tenants = scope.get("tenantIds") instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of();
        List<String> products = scope.get("productIds") instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of();
        List<String> regions = scope.get("regionCodes") instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of();
        if (!tenants.isEmpty() && (tenantId == null || !tenants.contains(tenantId))) return false;
        if (!products.isEmpty() && (productId == null || !products.contains(productId))) return false;
        if (!regions.isEmpty() && (regionCode == null || !regions.contains(regionCode))) return false;
        if (run.getStage() == RolloutStage.CANARY) {
            Map<String, Object> canary = jsonUtils.toMap(run.getCanaryJson());
            int pct = canary.get("percent") instanceof Number n ? n.intValue() : 10;
            String hash = CryptoUtils.sha256Hex(Objects.toString(subjectRef, ""));
            int bucket = Math.abs(hash.substring(0, 8).hashCode()) % 100;
            return bucket < pct;
        }
        return true;
    }

    private RolloutStatus evaluateRolloutCriteria(RolloutRunEntity run) {
        Map<String, Object> criteria = jsonUtils.toMap(run.getCriteriaJson());
        long minInv = criteria.get("minInvocations") instanceof Number n ? n.longValue() : 0L;
        double maxErrPct = criteria.get("maxErrorRatePercent") instanceof Number n ? n.doubleValue() : 5.0;
        double maxToPct = criteria.get("maxTimeoutRatePercent") instanceof Number n ? n.doubleValue() : 5.0;
        long maxP95 = criteria.get("maxP95Ms") instanceof Number n ? n.longValue() : 5000L;

        if (run.getInvocations() < minInv) {
            return RolloutStatus.INSUFFICIENT_DATA;
        }
        double errPct = run.getInvocations() == 0 ? 0.0 : (run.getErrors() * 100.0) / run.getInvocations();
        double toPct = run.getInvocations() == 0 ? 0.0 : (run.getTimeouts() * 100.0) / run.getInvocations();
        if (errPct > maxErrPct || toPct > maxToPct || run.getP95Ms() > maxP95) {
            return RolloutStatus.FAILED;
        }
        return RolloutStatus.PASSED;
    }

    private void validateScopeAgainstOwner(String ownerId, EnableExtensionRequest.ScopeSpec scope) {
        if ("PLATFORM".equals(ownerId)) return;
        PartnerEntity partner = partnerRepository.findById(ownerId)
                .orElseThrow(() -> HubException.notFound("PARTNER_NOT_FOUND", "Partner not found"));
        Map<String, Object> vis = jsonUtils.toMap(partner.getVisibilityJson());
        checkSubset(scope.getTenantIds(), vis.get("tenantIds"));
        checkSubset(scope.getRegionCodes(), vis.get("regionCodes"));
        checkSubset(scope.getProductIds(), vis.get("productIds"));
    }

    private void checkSubset(List<String> requested, Object allowedObj) {
        if (requested == null || requested.isEmpty()) return;
        if (!(allowedObj instanceof List<?> allowed) || allowed.isEmpty()) return;
        Set<String> allowedSet = new HashSet<>();
        allowed.forEach(a -> allowedSet.add(String.valueOf(a)));
        for (String r : requested) {
            if (!allowedSet.contains(r)) {
                throw HubException.unprocessable("SCOPE_NOT_ALLOWED", "The scope is not allowed");
            }
        }
    }

    private boolean scopesOverlap(ExtensionEnablementEntity a, EnableExtensionRequest.ScopeSpec b) {
        return listOverlaps(jsonUtils.toStringList(a.getTenantIdsJson()), b.getTenantIds())
                && listOverlaps(jsonUtils.toStringList(a.getProductIdsJson()), b.getProductIds())
                && listOverlaps(jsonUtils.toStringList(a.getRegionCodesJson()), b.getRegionCodes());
    }

    private boolean listOverlaps(List<String> a, List<String> b) {
        if (a == null || a.isEmpty() || b == null || b.isEmpty()) return true;
        return a.stream().anyMatch(b::contains);
    }

    private ExtensionVersionEntity resolveExtensionVersion(String extensionId, String version, HubIdentity identity) {
        ExtensionVersionEntity ext;
        if (version != null && !version.isBlank()) {
            ext = extensionVersionRepository.findByExtensionIdAndVersion(extensionId, version)
                    .orElseThrow(() -> HubException.notFound("EXTENSION_NOT_FOUND", "Extension not found"));
        } else {
            List<ExtensionVersionEntity> versions = extensionVersionRepository.findByExtensionIdOrderByCreatedAtDesc(extensionId);
            if (versions.isEmpty()) {
                throw HubException.notFound("EXTENSION_NOT_FOUND", "Extension not found");
            }
            ext = versions.get(0);
        }
        accessGuard.assertCanAccessPartnerResource(identity, ext.getOwnerId(), "EXTENSION_NOT_FOUND", "Extension not found");
        return ext;
    }

    private String resolveOwnerId(HubIdentity identity, String requestedOwnerId) {
        if (accessGuard.isPartnerScopedOnly(identity)) {
            return accessGuard.resolveEffectivePartnerId(identity, requestedOwnerId);
        }
        if (requestedOwnerId != null && !requestedOwnerId.isBlank()) {
            return requestedOwnerId.trim();
        }
        return "PLATFORM";
    }

    private int parseEtag(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new HubException(HttpStatus.PRECONDITION_REQUIRED, "PRECONDITION_REQUIRED", "If-Match header is required");
        }
        try {
            return Integer.parseInt(ifMatch.replace("\"", "").trim());
        } catch (NumberFormatException e) {
            throw HubException.validation("Invalid If-Match ETag value");
        }
    }

    private int compareSemver(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int va = i < pa.length ? parseIntSafe(pa[i]) : 0;
            int vb = i < pb.length ? parseIntSafe(pb[i]) : 0;
            if (va != vb) return Integer.compare(va, vb);
        }
        return 0;
    }

    private int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private static List<String> nullSafe(List<String> list) {
        return list == null ? List.of() : list;
    }

    private FailModeApplied toProtoFailMode(FailMode fm) {
        return switch (fm) {
            case FAIL_OPEN -> FailModeApplied.FAIL_OPEN;
            case HOLD_FOR_REVIEW -> FailModeApplied.HOLD_FOR_REVIEW;
            case FAIL_CLOSED -> FailModeApplied.FAIL_CLOSED;
        };
    }

    private FailModeApplied maxFailMode(FailModeApplied a, FailModeApplied b) {
        if (a == FailModeApplied.FAIL_CLOSED || b == FailModeApplied.FAIL_CLOSED) return FailModeApplied.FAIL_CLOSED;
        if (a == FailModeApplied.HOLD_FOR_REVIEW || b == FailModeApplied.HOLD_FOR_REVIEW) return FailModeApplied.HOLD_FOR_REVIEW;
        if (a == FailModeApplied.FAIL_OPEN || b == FailModeApplied.FAIL_OPEN) return FailModeApplied.FAIL_OPEN;
        return FailModeApplied.FAIL_MODE_NONE;
    }

    private Map<String, Object> toRolloutResponse(RolloutRunEntity r, List<String> nextSteps) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("rolloutId", r.getRolloutId());
        res.put("extensionId", r.getExtensionId());
        res.put("version", r.getExtensionVersion());
        res.put("stage", r.getStage().name());
        res.put("status", r.getStatus().name());
        res.put("scope", jsonUtils.toMap(r.getScopeJson()));
        if (r.getCanaryJson() != null && !"null".equals(r.getCanaryJson())) {
            res.put("canary", jsonUtils.toMap(r.getCanaryJson()));
        }
        if (r.getEndsAt() != null) res.put("endsAt", r.getEndsAt().toString());
        long inv = r.getInvocations();
        res.put("metrics", Map.of(
                "invocations", inv,
                "errorRatePercent", inv == 0 ? 0.0 : (r.getErrors() * 100.0) / inv,
                "timeoutRatePercent", inv == 0 ? 0.0 : (r.getTimeouts() * 100.0) / inv,
                "p50Ms", r.getP50Ms(),
                "p95Ms", r.getP95Ms()
        ));
        if (r.getStage() == RolloutStage.SHADOW) {
            res.put("comparison", Map.of(
                    "agreementPercent", r.getAgreementPercent() != null ? r.getAgreementPercent() : 100.0,
                    "unexpectedHolds", r.getUnexpectedHolds() != null ? r.getUnexpectedHolds() : 0L,
                    "wouldHaveRejected", r.getWouldHaveRejected() != null ? r.getWouldHaveRejected() : 0L
            ));
        }
        if (r.getVerdict() != null) res.put("verdict", r.getVerdict());
        res.put("nextSteps", nextSteps);
        return res;
    }

    private EnableResult buildEnableResult(ExtensionVersionEntity ext, ExtensionEnablementEntity en,
                                         String state, Instant effectiveBy) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("extensionId", ext.getExtensionId());
        body.put("version", ext.getVersion());
        body.put("extensionPoint", ext.getExtensionPoint());
        body.put("state", state);
        if (en != null) {
            body.put("scope", Map.of(
                    "tenantIds", jsonUtils.toStringList(en.getTenantIdsJson()),
                    "productIds", jsonUtils.toStringList(en.getProductIdsJson()),
                    "regionCodes", jsonUtils.toStringList(en.getRegionCodesJson())
            ));
            body.put("priority", en.getPriority());
            body.put("failMode", en.getFailMode().name());
            body.put("timeoutMs", en.getTimeoutMs());
            body.put("autoDisable", Map.of(
                    "errorRatePercent", en.getErrorRatePercent(),
                    "windowMinutes", en.getWindowMinutes(),
                    "consecutiveFailures", en.getConsecutiveFailures()
            ));
        }
        body.put("effectiveBy", effectiveBy.toString());
        body.put("versionEtag", ext.getEtagVersion());
        return new EnableResult(body, ext.getEtagVersion());
    }

    public record EnableResult(Map<String, Object> body, int etagVersion) {}
}
