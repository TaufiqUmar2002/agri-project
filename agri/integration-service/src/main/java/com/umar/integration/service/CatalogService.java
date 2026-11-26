package com.umar.integration.service;

import com.umar.grpc.hub.v1.*;
import com.umar.integration.config.HubProperties;
import com.umar.integration.domain.entity.*;
import com.umar.integration.domain.enums.*;
import com.umar.enums.integration.*;
import com.umar.integration.error.HubException;
import com.umar.integration.repository.*;
import com.umar.integration.security.HubIdentity;
import com.umar.integration.security.HubSecurityContext;
import com.umar.integration.service.support.OutboxService;
import com.umar.integration.util.JsonUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final ExtensionPointRepository pointRepository;
    private final CatalogSnapshotRepository snapshotRepository;
    private final ExtensionEnablementRepository enablementRepository;
    private final ExtensionVersionRepository extensionVersionRepository;
    private final RolloutRunRepository rolloutRunRepository;
    private final OutboxService outboxService;
    private final HubProperties properties;
    private final JsonUtils jsonUtils;

    @PostConstruct
    @Transactional
    public void seedInitialCatalog() {
        if (snapshotRepository.findById(1L).isEmpty()) {
            snapshotRepository.save(CatalogSnapshotEntity.builder()
                    .id(1L)
                    .snapshotVersion(1L)
                    .updatedAt(Instant.now())
                    .build());
        }
        // Always ensure the Hub's own 3 hooks (Section 7.1) and key platform hooks (Section 7.2) exist
        seedPointIfAbsent("hub.connector-adapter", "integration-hub-service", HookType.ADAPTER,
                "Supply a new connector type or protocol behind the adapter interface",
                "1.0", null, List.of("connector.config"), 2000, 5000,
                List.of(FailMode.FAIL_CLOSED, FailMode.FAIL_OPEN), false, true,
                List.of("May supply a new connector type", "May not write directly to core topics or bypass mapping and tokenization"));
        seedPointIfAbsent("hub.mapping-transform", "integration-hub-service", HookType.TRANSFORM,
                "Custom field conversion, validation or code translation during connector mapping",
                "1.0", null, List.of("connector.record"), 2000, 5000,
                List.of(FailMode.FAIL_CLOSED, FailMode.FAIL_OPEN), false, true,
                List.of("May transform or validate canonical fields", "May not add non-canonical fields or skip validation"));
        seedPointIfAbsent("hub.pre-dispatch", "integration-hub-service", HookType.TRANSFORM,
                "Filter, mask or redact webhook payloads per partner before signing",
                "1.0", null, List.of("webhook.payload"), 1000, 2000,
                List.of(FailMode.FAIL_OPEN, FailMode.FAIL_CLOSED), false, true,
                List.of("May remove or mask fields or suppress delivery", "May not add data or bypass the field allow-list"));

        // Seed representative platform-wide declared points (Section 7.2)
        seedPointIfAbsent("policy.pre-quote", "policy-service", HookType.PRE,
                "Add custom loading lines within the product cap",
                "2.1", "2.0", List.of("quote.basic", "quote.extended"), 2000, 5000,
                List.of(FailMode.FAIL_OPEN, FailMode.HOLD_FOR_REVIEW), false, true,
                List.of("May add loading lines within the cap", "May not reduce the price below the floor, change the score or alter eligibility"));
        seedPointIfAbsent("policy.custom-attributes", "policy-service", HookType.ENRICH,
                "Attach partner-owned x_ attributes at policy issue",
                "1.1", "1.0", List.of("policy.basic"), 1500, 3000,
                List.of(FailMode.FAIL_OPEN), false, true,
                List.of("May add attributes under x_ namespace only", "May not alter core policy fields"));
        seedPointIfAbsent("policy.pre-activate-check", "policy-service", HookType.PRE,
                "Pre-activation partner risk and eligibility check (hold-only)",
                "1.0", null, List.of("policy.basic", "quote.basic"), 2000, 5000,
                List.of(FailMode.FAIL_OPEN, FailMode.HOLD_FOR_REVIEW), false, true,
                List.of("May add holds or rejections", "May not release holds or approve protected checks"));
        seedPointIfAbsent("billing.settlement-parser", "premium-billing", HookType.ADAPTER,
                "Parse partner-specific settlement file formats",
                "1.0", null, List.of("settlement.raw"), 2000, 5000,
                List.of(FailMode.FAIL_CLOSED, FailMode.FAIL_OPEN), false, true,
                List.of("May parse external layout to canonical rows", "May not post directly to ledger"));
        seedPointIfAbsent("claim.validator", "claims-service", HookType.VALIDATE,
                "Additional partner validation check on claim submission",
                "1.0", null, List.of("claim.basic"), 2000, 5000,
                List.of(FailMode.FAIL_OPEN, FailMode.HOLD_FOR_REVIEW), false, true,
                List.of("May hold or reject only", "May never release a hold or approve payout"));
        seedPointIfAbsent("payout.disburse", "payout-service", HookType.POST,
                "Protected payout disbursement operation (no outcome-changing hooks)",
                "1.0", null, List.of("payout.summary"), 1000, 2000,
                List.of(FailMode.FAIL_OPEN), true, true,
                List.of("Protected financial control: outcome-changing extensions forbidden"));
        seedPointIfAbsent("plot.enrich", "plot-registry", HookType.ENRICH,
                "Enrich registered plot attributes under x_ namespace",
                "1.0", null, List.of("plot.basic"), 1500, 3000,
                List.of(FailMode.FAIL_OPEN), false, true,
                List.of("May add x_ plot attributes only", "May not access farmer contact details"));
        seedPointIfAbsent("trigger.data-source", "trigger-engine", HookType.ADAPTER,
                "Custom index data source adapter",
                "1.0", null, List.of("observation.basic"), 2000, 5000,
                List.of(FailMode.FAIL_OPEN, FailMode.FAIL_CLOSED), false, true,
                List.of("May supply observations for registered source", "May not alter trigger thresholds"));
        seedPointIfAbsent("internal.audit-enrich", "audit-service", HookType.ENRICH,
                "Internal-only audit enrichment hook",
                "1.0", null, List.of("audit.internal"), 1000, 2000,
                List.of(FailMode.FAIL_OPEN), false, false,
                List.of("Internal point only"));
        seedPointIfAbsent("auth.pre-register", "auth-service", HookType.PRE,
                "Pre-registration validation check",
                "1.0", null, List.of("auth.basic"), 1000, 2000,
                List.of(FailMode.FAIL_CLOSED, FailMode.FAIL_OPEN), false, true,
                List.of("May block registration only"));
        seedPointIfAbsent("farmer.pre-update", "farmer-profile", HookType.PRE,
                "Pre-update farmer profile check",
                "1.0", null, List.of("farmer.basic"), 1000, 2000,
                List.of(FailMode.FAIL_OPEN), false, true,
                List.of("May validate profile fields"));
        seedPointIfAbsent("riskpool.capital-check", "riskpool-service", HookType.VALIDATE,
                "Capital adequacy check",
                "1.0", null, List.of("riskpool.summary"), 1000, 2000,
                List.of(FailMode.FAIL_OPEN), true, false,
                List.of("Protected solvency check"));
        seedPointIfAbsent("iot.qa-transform", "iot-service", HookType.TRANSFORM,
                "Telemetry QA transformation hook",
                "1.0", null, List.of("iot.reading"), 1000, 2000,
                List.of(FailMode.FAIL_OPEN), false, true,
                List.of("May enrich reading quality flags"));
        seedPointIfAbsent("notification.pre-send", "notification-service", HookType.PRE,
                "Pre-dispatch notification check",
                "1.0", null, List.of("notification.basic"), 1000, 2000,
                List.of(FailMode.FAIL_OPEN), false, true,
                List.of("May suppress or enrich advisory messages"));
        seedPointIfAbsent("vault.pre-tokenize", "vault-service", HookType.VALIDATE,
                "Pre-tokenization policy check",
                "1.0", null, List.of("vault.meta"), 1000, 2000,
                List.of(FailMode.FAIL_CLOSED), true, false,
                List.of("Protected security control"));
    }

    private void seedPointIfAbsent(String hookId, String serviceName, HookType type, String description,
                                   String currentContract, String previousContract, List<String> scopes,
                                   int defaultTimeout, int maxTimeout, List<FailMode> failModes,
                                   boolean isProtected, boolean partnerVisible, List<String> rules) {
        if (pointRepository.existsById(hookId)) {
            return;
        }
        Instant now = Instant.now();
        pointRepository.save(ExtensionPointEntity.builder()
                .hookId(hookId)
                .serviceName(serviceName)
                .serviceVersion("1.0.0")
                .sdkVersion("3.2.1")
                .type(type)
                .description(description)
                .currentContractVersion(currentContract)
                .previousContractVersion(previousContract)
                .deprecatesOn(previousContract != null ? "2027-06-30" : null)
                .inputSchemaRef("schema://" + hookId + "/v" + currentContract + "/input.json")
                .outputSchemaRef("schema://" + hookId + "/v" + currentContract + "/output.json")
                .dataScopesJson(jsonUtils.toJson(scopes))
                .defaultTimeoutMs(defaultTimeout)
                .maxTimeoutMs(maxTimeout)
                .allowedFailModesJson(jsonUtils.toJson(failModes.stream().map(Enum::name).toList()))
                .maxExtensionsPerScope(5)
                .protectedPoint(isProtected)
                .partnerVisible(partnerVisible)
                .rulesJson(jsonUtils.toJson(rules))
                .status(PointStatus.ACTIVE)
                .lastRegisteredAt(now)
                .updatedAt(now)
                .build());
    }

    /**
     * INT-11: Browse Extension Point Catalog.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> browseCatalog(
            String service,
            String type,
            Boolean protectedFilter,
            Boolean partnerVisible,
            String q,
            String status,
            String include,
            int page,
            int size) {

        HubIdentity identity = HubSecurityContext.require();
        boolean isPartner = !identity.isInternalStaff();

        if (page < 0 || size < 1 || size > 200) {
            throw HubException.validation("page must be >= 0 and size must be between 1 and 200");
        }
        if (q != null && !q.isBlank() && q.trim().length() < 3) {
            throw HubException.validation("Search query q must be at least 3 characters");
        }

        HookType hookTypeFilter = null;
        if (type != null && !type.isBlank()) {
            try {
                hookTypeFilter = HookType.valueOf(type.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw HubException.validation("Unknown hook type: " + type);
            }
        }

        PointStatus statusFilter = null;
        if (status != null && !status.isBlank()) {
            try {
                statusFilter = PointStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw HubException.validation("Unknown status: " + status);
            }
        }

        boolean effectivePartnerVisible = isPartner ? true : Boolean.TRUE.equals(partnerVisible);
        Set<String> includes = parseInclude(include);

        final HookType ft = hookTypeFilter;
        final PointStatus fs = statusFilter;
        List<ExtensionPointEntity> filtered = pointRepository.findAll().stream()
                .filter(p -> service == null || service.isBlank() || p.getServiceName().equalsIgnoreCase(service.trim()))
                .filter(p -> ft == null || p.getType() == ft)
                .filter(p -> protectedFilter == null || p.isProtectedPoint() == protectedFilter)
                .filter(p -> isPartner ? p.isPartnerVisible() : (partnerVisible == null || p.isPartnerVisible() == effectivePartnerVisible))
                .filter(p -> fs == null || p.getStatus() == fs)
                .filter(p -> q == null || q.isBlank()
                        || p.getHookId().toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT))
                        || p.getDescription().toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT)))
                .sorted(Comparator.comparing(ExtensionPointEntity::getHookId))
                .toList();

        int total = filtered.size();
        int fromIdx = Math.min(page * size, total);
        int toIdx = Math.min(fromIdx + size, total);
        List<ExtensionPointEntity> paged = filtered.subList(fromIdx, toIdx);

        List<Map<String, Object>> items = new ArrayList<>();
        for (ExtensionPointEntity p : paged) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("hookId", p.getHookId());
            item.put("service", p.getServiceName());
            item.put("type", p.getType().name());
            item.put("description", p.getDescription());
            Map<String, Object> cv = new LinkedHashMap<>();
            cv.put("current", p.getCurrentContractVersion());
            if (p.getPreviousContractVersion() != null) {
                cv.put("previous", p.getPreviousContractVersion());
            }
            if (p.getDeprecatesOn() != null) {
                cv.put("deprecatesOn", p.getDeprecatesOn());
            }
            item.put("contractVersions", cv);
            item.put("protected", p.isProtectedPoint());
            item.put("partnerVisible", p.isPartnerVisible());
            item.put("status", p.getStatus().name());
            item.put("dataScopes", jsonUtils.toStringList(p.getDataScopesJson()));
            item.put("rules", jsonUtils.toStringList(p.getRulesJson()));

            if (includes.contains("limits")) {
                item.put("limits", Map.of(
                        "defaultTimeoutMs", p.getDefaultTimeoutMs(),
                        "maxTimeoutMs", p.getMaxTimeoutMs(),
                        "allowedFailModes", jsonUtils.toStringList(p.getAllowedFailModesJson()),
                        "maxExtensionsPerScope", p.getMaxExtensionsPerScope()
                ));
            }
            if (includes.contains("schemas")) {
                item.put("schemas", Map.of(
                        "inputSchemaRef", Objects.toString(p.getInputSchemaRef(), ""),
                        "outputSchemaRef", Objects.toString(p.getOutputSchemaRef(), "")
                ));
            }
            if (includes.contains("enablement") && identity.isInternalStaff()) {
                item.put("enablement", Map.of(
                        "enabledCount", enablementRepository.countByHookIdAndActiveTrue(p.getHookId())
                ));
            }
            if (includes.contains("history")) {
                item.put("lastRegisteredAt", p.getLastRegisteredAt().toString());
            }
            items.add(item);
        }

        long snapshotVer = currentSnapshotVersion();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("page", page);
        result.put("size", size);
        result.put("totalElements", total);
        result.put("totalPages", total == 0 ? 0 : (int) Math.ceil((double) total / size));
        result.put("snapshotVersion", snapshotVer);
        return result;
    }

    /**
     * INT-13: Register Extension Points (gRPC).
     */
    @Transactional
    public RegisterExtensionPointsResponse registerExtensionPoints(
            String authenticatedService,
            RegisterExtensionPointsRequest request) {

        if (request.getServiceName().isBlank() || request.getPointsCount() == 0) {
            throw new IllegalArgumentException("service_name and at least one point are required");
        }
        if (authenticatedService != null && !authenticatedService.isBlank()
                && !authenticatedService.equalsIgnoreCase(request.getServiceName())) {
            throw new SecurityException("Caller service " + authenticatedService
                    + " cannot register points for " + request.getServiceName());
        }

        List<String> accepted = new ArrayList<>();
        List<RejectedExtensionPoint> rejected = new ArrayList<>();
        Set<String> declaredHookIds = new HashSet<>();
        boolean catalogChanged = false;
        Instant now = Instant.now();

        for (DeclaredExtensionPoint pt : request.getPointsList()) {
            String hookId = pt.getHookId().trim();
            declaredHookIds.add(hookId);

            if (!hookId.matches("^[a-z0-9-]+\\.[a-z0-9-]+$")) {
                rejected.add(RejectedExtensionPoint.newBuilder()
                        .setHookId(hookId)
                        .setReason("INVALID_NAME")
                        .setDetail("Hook id must follow service.action naming")
                        .build());
                continue;
            }

            HookType hookType;
            try {
                hookType = HookType.valueOf(pt.getType().trim().toUpperCase(Locale.ROOT));
            } catch (Exception e) {
                rejected.add(RejectedExtensionPoint.newBuilder()
                        .setHookId(hookId)
                        .setReason("INVALID_TYPE")
                        .setDetail("Unknown hook type: " + pt.getType())
                        .build());
                continue;
            }

            if (pt.getProtected() && hookType.isOutcomeChanging()) {
                rejected.add(RejectedExtensionPoint.newBuilder()
                        .setHookId(hookId)
                        .setReason("PROTECTION_CONFLICT")
                        .setDetail("Protected points cannot have outcome-changing type " + hookType)
                        .build());
                continue;
            }

            int maxTimeout = pt.getMaxTimeoutMs() > 0 ? pt.getMaxTimeoutMs() : properties.getExtension().getMaxSyncTimeoutMs();
            int defaultTimeout = pt.getDefaultTimeoutMs() > 0 ? pt.getDefaultTimeoutMs() : properties.getExtension().getDefaultTimeoutMs();
            if (maxTimeout > properties.getExtension().getMaxSyncTimeoutMs() || defaultTimeout > maxTimeout) {
                rejected.add(RejectedExtensionPoint.newBuilder()
                        .setHookId(hookId)
                        .setReason("LIMIT_EXCEEDED")
                        .setDetail("Timeout exceeds platform sync maximum of " + properties.getExtension().getMaxSyncTimeoutMs() + " ms")
                        .build());
                continue;
            }

            Optional<ExtensionPointEntity> existingOpt = pointRepository.findById(hookId);
            if (existingOpt.isPresent()) {
                ExtensionPointEntity existing = existingOpt.get();
                if (!existing.getServiceName().equalsIgnoreCase(request.getServiceName())) {
                    rejected.add(RejectedExtensionPoint.newBuilder()
                            .setHookId(hookId)
                            .setReason("PERMISSION_DENIED")
                            .setDetail("Hook owned by another service")
                            .build());
                    continue;
                }
                if (isBreakingContractChange(existing.getCurrentContractVersion(), pt.getContractVersion())) {
                    rejected.add(RejectedExtensionPoint.newBuilder()
                            .setHookId(hookId)
                            .setReason("CONTRACT_BREAKING_CHANGE")
                            .setDetail("Major contract jump without compatible transition: "
                                    + existing.getCurrentContractVersion() + " -> " + pt.getContractVersion())
                            .build());
                    continue;
                }

                boolean becameProtected = !existing.isProtectedPoint() && pt.getProtected();
                String prevVer = !existing.getCurrentContractVersion().equals(pt.getContractVersion())
                        ? existing.getCurrentContractVersion()
                        : existing.getPreviousContractVersion();

                existing.setServiceVersion(request.getServiceVersion());
                existing.setSdkVersion(request.getSdkVersion());
                existing.setType(hookType);
                existing.setDescription(pt.getDescription().isBlank() ? existing.getDescription() : pt.getDescription());
                existing.setPreviousContractVersion(prevVer);
                existing.setCurrentContractVersion(pt.getContractVersion());
                if (!pt.getDeprecatesOn().isBlank()) {
                    existing.setDeprecatesOn(pt.getDeprecatesOn());
                }
                if (!pt.getInputSchemaRef().isBlank()) existing.setInputSchemaRef(pt.getInputSchemaRef());
                if (!pt.getOutputSchemaRef().isBlank()) existing.setOutputSchemaRef(pt.getOutputSchemaRef());
                if (pt.getDataScopesCount() > 0) existing.setDataScopesJson(jsonUtils.toJson(pt.getDataScopesList()));
                existing.setDefaultTimeoutMs(defaultTimeout);
                existing.setMaxTimeoutMs(maxTimeout);
                if (pt.getAllowedFailModesCount() > 0) {
                    existing.setAllowedFailModesJson(jsonUtils.toJson(pt.getAllowedFailModesList()));
                }
                existing.setProtectedPoint(pt.getProtected());
                existing.setPartnerVisible(pt.getPartnerVisible());
                existing.setStatus(PointStatus.ACTIVE);
                existing.setLastRegisteredAt(now);
                existing.setUpdatedAt(now);
                pointRepository.save(existing);

                if (becameProtected) {
                    disableIncompatibleExtensionsOnProtection(hookId);
                }
                catalogChanged = true;
                outboxService.publishIntegrationEvent("extension.catalog.changed", hookId, Map.of(
                        "change", becameProtected ? "PROTECTION_CHANGED" : "POINT_UPDATED",
                        "hookId", hookId,
                        "service", request.getServiceName(),
                        "snapshotVersion", currentSnapshotVersion() + 1
                ));
            } else {
                List<String> failModes = pt.getAllowedFailModesCount() > 0
                        ? pt.getAllowedFailModesList()
                        : List.of(FailMode.FAIL_OPEN.name());
                List<String> scopes = pt.getDataScopesCount() > 0
                        ? pt.getDataScopesList()
                        : List.of("default");
                pointRepository.save(ExtensionPointEntity.builder()
                        .hookId(hookId)
                        .serviceName(request.getServiceName())
                        .serviceVersion(request.getServiceVersion())
                        .sdkVersion(request.getSdkVersion())
                        .type(hookType)
                        .description(pt.getDescription().isBlank() ? hookId : pt.getDescription())
                        .currentContractVersion(pt.getContractVersion())
                        .deprecatesOn(pt.getDeprecatesOn().isBlank() ? null : pt.getDeprecatesOn())
                        .inputSchemaRef(pt.getInputSchemaRef().isBlank() ? null : pt.getInputSchemaRef())
                        .outputSchemaRef(pt.getOutputSchemaRef().isBlank() ? null : pt.getOutputSchemaRef())
                        .dataScopesJson(jsonUtils.toJson(scopes))
                        .defaultTimeoutMs(defaultTimeout)
                        .maxTimeoutMs(maxTimeout)
                        .allowedFailModesJson(jsonUtils.toJson(failModes))
                        .maxExtensionsPerScope(5)
                        .protectedPoint(pt.getProtected())
                        .partnerVisible(pt.getPartnerVisible())
                        .rulesJson(jsonUtils.toJson(List.of("Hook type: " + hookType.name())))
                        .status(PointStatus.ACTIVE)
                        .lastRegisteredAt(now)
                        .updatedAt(now)
                        .build());
                catalogChanged = true;
                outboxService.publishIntegrationEvent("extension.catalog.changed", hookId, Map.of(
                        "change", "POINT_REGISTERED",
                        "hookId", hookId,
                        "service", request.getServiceName(),
                        "snapshotVersion", currentSnapshotVersion() + 1
                ));
            }
            accepted.add(hookId);
        }

        // Deprecate points previously declared by this service that are no longer in the declaration
        for (ExtensionPointEntity existing : pointRepository.findByServiceName(request.getServiceName())) {
            if (!declaredHookIds.contains(existing.getHookId())
                    && existing.getStatus() == PointStatus.ACTIVE
                    && !existing.getHookId().startsWith("hub.")) {
                existing.setStatus(PointStatus.DEPRECATED);
                existing.setUpdatedAt(now);
                pointRepository.save(existing);
                catalogChanged = true;
                outboxService.publishIntegrationEvent("extension.catalog.changed", existing.getHookId(), Map.of(
                        "change", "POINT_DEPRECATED",
                        "hookId", existing.getHookId(),
                        "service", request.getServiceName(),
                        "snapshotVersion", currentSnapshotVersion() + 1
                ));
            }
        }

        if (accepted.isEmpty() && !rejected.isEmpty()) {
            throw new IllegalStateException("FAILED_PRECONDITION: " + rejected.get(0).getReason()
                    + " - " + rejected.get(0).getDetail());
        }

        long snapshotVersion = catalogChanged ? bumpSnapshotVersion() : currentSnapshotVersion();
        EnablementSnapshot snapshot = buildSnapshotForPoints(declaredHookIds, snapshotVersion);

        return RegisterExtensionPointsResponse.newBuilder()
                .addAllAccepted(accepted)
                .addAllRejected(rejected)
                .setSnapshot(snapshot)
                .setPollAfterSeconds(30)
                .setAsOf(now.toString())
                .build();
    }

    private void disableIncompatibleExtensionsOnProtection(String hookId) {
        List<ExtensionEnablementEntity> enablements = enablementRepository.findByHookIdAndActiveTrueOrderByPriorityAsc(hookId);
        for (ExtensionEnablementEntity en : enablements) {
            en.setActive(false);
            en.setUpdatedAt(Instant.now());
            enablementRepository.save(en);
            extensionVersionRepository.findByExtensionIdAndVersion(en.getExtensionId(), en.getExtensionVersion())
                    .ifPresent(ev -> {
                        ev.setStatus(ExtensionStatus.DISABLED);
                        ev.setUpdatedAt(Instant.now());
                        extensionVersionRepository.save(ev);
                    });
            outboxService.publishIntegrationEvent("extension.disabled", en.getExtensionId(), Map.of(
                    "extensionId", en.getExtensionId(),
                    "version", en.getExtensionVersion(),
                    "extensionPoint", hookId,
                    "reason", "PROTECTION_CHANGED",
                    "disabledBy", "SYSTEM"
            ));
        }
    }

    public EnablementSnapshot buildSnapshotForPoints(Set<String> hookIds, long snapshotVersion) {
        EnablementSnapshot.Builder builder = EnablementSnapshot.newBuilder()
                .setSnapshotVersion(snapshotVersion);
        for (ExtensionEnablementEntity en : enablementRepository.findByActiveTrue()) {
            if (!hookIds.contains(en.getHookId())) {
                continue;
            }
            ExtensionVersionEntity ev = extensionVersionRepository
                    .findByExtensionIdAndVersion(en.getExtensionId(), en.getExtensionVersion())
                    .orElse(null);
            ExtensionScope scope = ExtensionScope.newBuilder()
                    .addAllTenantIds(jsonUtils.toStringList(en.getTenantIdsJson()))
                    .addAllProductIds(jsonUtils.toStringList(en.getProductIdsJson()))
                    .addAllRegionCodes(jsonUtils.toStringList(en.getRegionCodesJson()))
                    .build();
            builder.addEnabled(EnabledExtensionSnapshotEntry.newBuilder()
                    .setHookId(en.getHookId())
                    .setExtensionId(en.getExtensionId())
                    .setVersion(en.getExtensionVersion())
                    .setScope(scope)
                    .setPriority(en.getPriority())
                    .setFailMode(en.getFailMode().name())
                    .setTimeoutMs(en.getTimeoutMs())
                    .setRuntimeKind(ev != null ? ev.getRuntimeType().name() : "WASM")
                    .setRolloutStage("FULL")
                    .build());
        }
        // Include active SHADOW/CANARY rollout runs
        for (String hookId : hookIds) {
            for (RolloutRunEntity run : rolloutRunRepository.findByHookIdAndStatus(hookId, RolloutStatus.RUNNING)) {
                Map<String, Object> scopeMap = jsonUtils.toMap(run.getScopeJson());
                Map<String, Object> canaryMap = jsonUtils.toMap(run.getCanaryJson());
                ExtensionScope.Builder sb = ExtensionScope.newBuilder();
                if (scopeMap.get("tenantIds") instanceof List<?> l) l.forEach(x -> sb.addTenantIds(String.valueOf(x)));
                if (scopeMap.get("productIds") instanceof List<?> l) l.forEach(x -> sb.addProductIds(String.valueOf(x)));
                if (scopeMap.get("regionCodes") instanceof List<?> l) l.forEach(x -> sb.addRegionCodes(String.valueOf(x)));
                int pct = canaryMap.get("percent") instanceof Number n ? n.intValue() : 0;
                builder.addEnabled(EnabledExtensionSnapshotEntry.newBuilder()
                        .setHookId(hookId)
                        .setExtensionId(run.getExtensionId())
                        .setVersion(run.getExtensionVersion())
                        .setScope(sb.build())
                        .setPriority(999)
                        .setFailMode(FailMode.FAIL_OPEN.name())
                        .setTimeoutMs(2000)
                        .setRolloutStage(run.getStage().name())
                        .setCanaryPercent(pct)
                        .build());
            }
        }
        return builder.build();
    }

    @Transactional
    public long bumpSnapshotVersion() {
        CatalogSnapshotEntity snap = snapshotRepository.findById(1L)
                .orElseGet(() -> CatalogSnapshotEntity.builder().id(1L).snapshotVersion(0L).updatedAt(Instant.now()).build());
        snap.setSnapshotVersion(snap.getSnapshotVersion() + 1);
        snap.setUpdatedAt(Instant.now());
        snapshotRepository.save(snap);
        return snap.getSnapshotVersion();
    }

    public long currentSnapshotVersion() {
        return snapshotRepository.findById(1L).map(CatalogSnapshotEntity::getSnapshotVersion).orElse(1L);
    }

    public boolean isContractSupported(ExtensionPointEntity point, String contractVersion) {
        if (contractVersion == null || contractVersion.isBlank()) return false;
        return contractVersion.equals(point.getCurrentContractVersion())
                || contractVersion.equals(point.getPreviousContractVersion());
    }

    private boolean isBreakingContractChange(String current, String proposed) {
        if (current == null || proposed == null) return false;
        try {
            int curMajor = Integer.parseInt(current.split("\\.")[0]);
            int propMajor = Integer.parseInt(proposed.split("\\.")[0]);
            // Major version bump > 1 or downgrade is breaking without migration
            return propMajor < curMajor || propMajor > curMajor + 1;
        } catch (Exception e) {
            return false;
        }
    }

    private Set<String> parseInclude(String include) {
        if (include == null || include.isBlank()) {
            return Set.of("limits");
        }
        Set<String> set = new HashSet<>();
        for (String part : include.split(",")) {
            set.add(part.trim().toLowerCase(Locale.ROOT));
        }
        return set;
    }
}
