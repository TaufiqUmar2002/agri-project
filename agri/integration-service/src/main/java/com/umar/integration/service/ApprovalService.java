package com.umar.integration.service;

import com.umar.integration.client.AuthServiceClient;
import com.umar.integration.domain.entity.*;
import com.umar.integration.domain.enums.*;
import com.umar.enums.integration.*;
import com.umar.payload.request.integration.ApprovalDecisionRequest;
import com.umar.integration.error.HubException;
import com.umar.integration.repository.*;
import com.umar.integration.security.HubAccessGuard;
import com.umar.integration.security.HubIdentity;
import com.umar.integration.service.support.OutboxService;
import com.umar.integration.util.CryptoUtils;
import com.umar.integration.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final ApprovalRepository approvalRepository;
    private final PartnerRepository partnerRepository;
    private final WebhookSubscriptionRepository subscriptionRepository;
    private final ExtensionVersionRepository extensionVersionRepository;
    private final ConnectorRepository connectorRepository;
    private final AuthServiceClient authServiceClient;
    private final CatalogService catalogService;
    private final HubAccessGuard accessGuard;
    private final OutboxService outboxService;
    private final JsonUtils jsonUtils;

    /**
     * INT-15: Decide Integration Approval.
     */
    @Transactional
    public Map<String, Object> decide(String approvalId, ApprovalDecisionRequest request) {
        HubIdentity identity = accessGuard.requireRoles(
                HubRole.INTEGRATION_MANAGER, HubRole.COMPLIANCE_OFFICER, HubRole.ADMIN);

        String decision = request.getDecision().trim().toUpperCase(Locale.ROOT);
        if (!"APPROVE".equals(decision) && !"REJECT".equals(decision)) {
            throw HubException.validation("decision must be APPROVE or REJECT");
        }

        ApprovalEntity approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> HubException.notFound("APPROVAL_NOT_FOUND", "Approval not found"));

        if (approval.getStatus() != ApprovalStatus.OPEN) {
            throw HubException.conflict("APPROVAL_NOT_OPEN", "This approval is no longer open");
        }

        if (approval.getRequestedBy().equalsIgnoreCase(identity.actorId())) {
            throw new HubException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "REQUESTER_CANNOT_APPROVE", "You cannot approve your own request");
        }

        if (!approval.getSubjectHash().equals(request.getSubjectHash())) {
            throw HubException.conflict("SUBJECT_CHANGED", "The request changed. Please review again");
        }

        List<String> requiredRoles = jsonUtils.toStringList(approval.getRequiredRolesJson());
        List<Map<String, Object>> decisions = jsonUtils.fromJson(
                approval.getDecisionsJson(), new TypeReference<>() {});
        if (decisions == null) {
            decisions = new ArrayList<>();
        }

        for (Map<String, Object> d : decisions) {
            if (identity.actorId().equalsIgnoreCase(String.valueOf(d.get("approverRef")))) {
                throw HubException.conflict("ALREADY_DECIDED_BY_YOU", "You have already decided this approval");
            }
        }

        Set<String> alreadyDecidedRoles = new HashSet<>();
        for (Map<String, Object> d : decisions) {
            alreadyDecidedRoles.add(String.valueOf(d.get("role")));
        }

        boolean escalatedByAdmin = identity.hasRole(HubRole.ADMIN) && approval.getSlaDueAt().isBefore(Instant.now());
        String actingRole = resolveActingRole(identity, requiredRoles, alreadyDecidedRoles, escalatedByAdmin);
        if (actingRole == null) {
            throw HubException.forbidden("Not authorised");
        }

        if ("APPROVE".equals(decision)) {
            validateChecklist(approval.getType(), request.getChecklist());
        }

        Instant now = Instant.now();
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("role", actingRole);
        entry.put("approverRef", identity.actorId());
        entry.put("decision", decision);
        entry.put("note", request.getNote());
        entry.put("at", now.toString());
        decisions.add(entry);
        alreadyDecidedRoles.add(actingRole);

        List<String> remaining = requiredRoles.stream()
                .filter(r -> !alreadyDecidedRoles.contains(r))
                .toList();

        String subjectStatus;
        if ("REJECT".equals(decision)) {
            approval.setStatus(ApprovalStatus.REJECTED);
            subjectStatus = rejectSubject(approval);
        } else if (remaining.isEmpty()) {
            approval.setStatus(ApprovalStatus.APPROVED);
            subjectStatus = releaseSubject(approval);
        } else {
            subjectStatus = currentSubjectStatus(approval);
        }

        approval.setDecisionsJson(jsonUtils.toJson(decisions));
        approval.setUpdatedAt(now);
        approvalRepository.save(approval);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("approvalId", approval.getApprovalId());
        response.put("type", approval.getType().name());
        response.put("subjectId", approval.getSubjectId());
        response.put("status", approval.getStatus().name());
        response.put("decisions", decisions);
        response.put("remaining", "REJECT".equals(decision) ? List.of() : remaining);
        response.put("subjectStatus", subjectStatus);
        return response;
    }

    private String resolveActingRole(HubIdentity identity, List<String> requiredRoles,
                                     Set<String> alreadyDecidedRoles, boolean escalatedByAdmin) {
        for (String req : requiredRoles) {
            if (!alreadyDecidedRoles.contains(req)) {
                try {
                    HubRole r = HubRole.valueOf(req);
                    if (identity.hasRole(r)) {
                        return req;
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        if (escalatedByAdmin && !requiredRoles.isEmpty()) {
            return requiredRoles.stream().filter(r -> !alreadyDecidedRoles.contains(r)).findFirst().orElse(HubRole.ADMIN.name());
        }
        // Allow ADMIN to act on INTEGRATION_MANAGER approvals if not already decided
        if (identity.hasRole(HubRole.ADMIN)
                && requiredRoles.contains(HubRole.INTEGRATION_MANAGER.name())
                && !alreadyDecidedRoles.contains(HubRole.INTEGRATION_MANAGER.name())) {
            return HubRole.INTEGRATION_MANAGER.name();
        }
        return null;
    }

    private void validateChecklist(ApprovalType type, Map<String, Boolean> checklist) {
        if (checklist == null || checklist.isEmpty()) {
            throw HubException.validation("Checklist is required when approving");
        }
        List<String> requiredKeys = switch (type) {
            case SUBSCRIPTION_PRODUCTION -> List.of("scopeChecked", "endpointChecked", "testsChecked");
            case EXTENSION_ENABLE -> List.of("dataScopeChecked", "rulesChecked", "rolloutReportsChecked", "failModeChecked");
            case CONNECTOR_ACTIVATE -> List.of("schemaChecked", "mappingChecked", "piiChecked", "targetChecked");
            case PARTNER_ONBOARD, PARTNER_SCOPE_CHANGE -> List.of("agreementsChecked", "identityChecked", "quotasChecked");
        };
        List<String> missing = new ArrayList<>();
        for (String key : requiredKeys) {
            if (!Boolean.TRUE.equals(checklist.get(key))) {
                missing.add("Checklist item must be true: " + key);
            }
        }
        if (!missing.isEmpty()) {
            throw HubException.validation(missing);
        }
    }

    private String releaseSubject(ApprovalEntity approval) {
        Instant now = Instant.now();
        return switch (approval.getType()) {
            case SUBSCRIPTION_PRODUCTION -> {
                WebhookSubscriptionEntity sub = subscriptionRepository.findById(approval.getSubjectId())
                        .orElseThrow(() -> HubException.notFound("SUBSCRIPTION_NOT_FOUND", "Subscription not found"));
                sub.setStatus(SubscriptionStatus.ACTIVE);
                sub.setVersion(sub.getVersion() + 1);
                sub.setUpdatedAt(now);
                subscriptionRepository.save(sub);
                outboxService.publishIntegrationEvent("integration.subscription.changed", sub.getSubscriptionId(), Map.of(
                        "subscriptionId", sub.getSubscriptionId(),
                        "partnerId", sub.getPartnerId(),
                        "change", "APPROVED",
                        "environment", sub.getEnvironment().name(),
                        "eventTypes", jsonUtils.toStringList(sub.getEventTypesJson())
                ));
                yield SubscriptionStatus.ACTIVE.name();
            }
            case EXTENSION_ENABLE -> {
                ExtensionVersionEntity ev = extensionVersionRepository.findById(approval.getSubjectId())
                        .orElseThrow(() -> HubException.notFound("EXTENSION_NOT_FOUND", "Extension not found"));
                ev.setStatus(ExtensionStatus.APPROVED);
                ev.setEtagVersion(ev.getEtagVersion() + 1);
                ev.setUpdatedAt(now);
                extensionVersionRepository.save(ev);
                long snap = catalogService.bumpSnapshotVersion();
                outboxService.publishIntegrationEvent("extension.catalog.changed", ev.getExtensionPoint(), Map.of(
                        "change", "EXTENSION_APPROVED",
                        "hookId", ev.getExtensionPoint(),
                        "extensionId", ev.getExtensionId(),
                        "snapshotVersion", snap
                ));
                yield ExtensionStatus.APPROVED.name();
            }
            case CONNECTOR_ACTIVATE -> {
                ConnectorEntity conn = connectorRepository.findById(approval.getSubjectId())
                        .orElseThrow(() -> HubException.notFound("CONNECTOR_NOT_FOUND", "Connector not found"));
                conn.setStatus(ConnectorStatus.ACTIVE);
                conn.setVersion(conn.getVersion() + 1);
                conn.setUpdatedAt(now);
                connectorRepository.save(conn);
                outboxService.publishIntegrationEvent("integration.connector.changed", conn.getConnectorId(), Map.of(
                        "connectorId", conn.getConnectorId(),
                        "ownerId", conn.getOwnerId(),
                        "direction", conn.getDirection().name(),
                        "adapter", conn.getAdapterType(),
                        "environment", conn.getEnvironment().name(),
                        "change", "ACTIVATED",
                        "configHash", conn.getConfigHash()
                ));
                yield ConnectorStatus.ACTIVE.name();
            }
            case PARTNER_ONBOARD, PARTNER_SCOPE_CHANGE -> {
                PartnerEntity partner = partnerRepository.findById(approval.getSubjectId())
                        .orElseThrow(() -> HubException.notFound("PARTNER_NOT_FOUND", "Partner not found"));
                if (partner.getTenantId() == null) {
                    Map<String, Object> vis = jsonUtils.toMap(partner.getVisibilityJson());
                    String configuredTenant = null;
                    if (vis.get("tenantIds") instanceof List<?> tids && !tids.isEmpty()) {
                        configuredTenant = String.valueOf(tids.get(0));
                    }
                    partner.setTenantId(configuredTenant != null ? configuredTenant : "ten_" + CryptoUtils.randomHex(4));
                }
                Map<String, Object> net = jsonUtils.toMap(partner.getNetworkJson());
                boolean mtls = Boolean.TRUE.equals(net.get("mtlsRequired"));
                AuthServiceClient.IssuedPartnerCredentials creds = authServiceClient.issuePartnerCredentials(
                        partner.getPartnerId(), partner.getTenantId(), mtls);
                partner.setOauthClientId(creds.oauthClientId());
                partner.setMtlsProfileId(creds.mtlsProfileId());
                partner.setStatus(PartnerStatus.ACTIVE);
                partner.setUpdatedAt(now);
                partnerRepository.save(partner);
                outboxService.publishIntegrationEvent("partner.onboarded", partner.getPartnerId(), Map.of(
                        "partnerId", partner.getPartnerId(),
                        "tenantId", partner.getTenantId(),
                        "type", partner.getType().name(),
                        "countries", jsonUtils.toStringList(partner.getCountryCodesJson()),
                        "status", PartnerStatus.ACTIVE.name()
                ));
                yield PartnerStatus.ACTIVE.name();
            }
        };
    }

    private String rejectSubject(ApprovalEntity approval) {
        Instant now = Instant.now();
        return switch (approval.getType()) {
            case SUBSCRIPTION_PRODUCTION -> {
                WebhookSubscriptionEntity sub = subscriptionRepository.findById(approval.getSubjectId()).orElse(null);
                if (sub != null) {
                    sub.setStatus(SubscriptionStatus.REJECTED);
                    sub.setUpdatedAt(now);
                    subscriptionRepository.save(sub);
                }
                yield SubscriptionStatus.REJECTED.name();
            }
            case EXTENSION_ENABLE -> {
                ExtensionVersionEntity ev = extensionVersionRepository.findById(approval.getSubjectId()).orElse(null);
                if (ev != null) {
                    ev.setStatus(ExtensionStatus.REJECTED);
                    ev.setUpdatedAt(now);
                    extensionVersionRepository.save(ev);
                }
                yield ExtensionStatus.REJECTED.name();
            }
            case CONNECTOR_ACTIVATE -> {
                ConnectorEntity conn = connectorRepository.findById(approval.getSubjectId()).orElse(null);
                if (conn != null) {
                    conn.setStatus(ConnectorStatus.REJECTED);
                    conn.setUpdatedAt(now);
                    connectorRepository.save(conn);
                }
                yield ConnectorStatus.REJECTED.name();
            }
            case PARTNER_ONBOARD, PARTNER_SCOPE_CHANGE -> {
                PartnerEntity partner = partnerRepository.findById(approval.getSubjectId()).orElse(null);
                if (partner != null) {
                    partner.setStatus(PartnerStatus.REJECTED);
                    partner.setUpdatedAt(now);
                    partnerRepository.save(partner);
                }
                yield PartnerStatus.REJECTED.name();
            }
        };
    }

    private String currentSubjectStatus(ApprovalEntity approval) {
        return switch (approval.getType()) {
            case SUBSCRIPTION_PRODUCTION -> subscriptionRepository.findById(approval.getSubjectId())
                    .map(s -> s.getStatus().name()).orElse("PENDING_APPROVAL");
            case EXTENSION_ENABLE -> extensionVersionRepository.findById(approval.getSubjectId())
                    .map(e -> e.getStatus().name()).orElse("PENDING_APPROVAL");
            case CONNECTOR_ACTIVATE -> connectorRepository.findById(approval.getSubjectId())
                    .map(c -> c.getStatus().name()).orElse("PENDING_APPROVAL");
            case PARTNER_ONBOARD, PARTNER_SCOPE_CHANGE -> partnerRepository.findById(approval.getSubjectId())
                    .map(p -> p.getStatus().name()).orElse("PENDING_APPROVAL");
        };
    }
}
