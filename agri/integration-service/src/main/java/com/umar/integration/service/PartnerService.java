package com.umar.integration.service;

import com.umar.integration.client.DataVaultClient;
import com.umar.integration.config.HubProperties;
import com.umar.integration.domain.entity.ApprovalEntity;
import com.umar.integration.domain.entity.ExtensionPointEntity;
import com.umar.integration.domain.entity.PartnerEntity;
import com.umar.integration.domain.entity.PartnerVersionEntity;
import com.umar.integration.domain.enums.*;
import com.umar.enums.integration.*;
import com.umar.payload.request.integration.OnboardPartnerRequest;
import com.umar.integration.error.HubException;
import com.umar.integration.repository.ApprovalRepository;
import com.umar.integration.repository.ExtensionPointRepository;
import com.umar.integration.repository.PartnerRepository;
import com.umar.integration.repository.PartnerVersionRepository;
import com.umar.integration.security.HubAccessGuard;
import com.umar.integration.security.HubIdentity;
import com.umar.integration.service.support.IdempotencyService;
import com.umar.integration.service.support.OutboxService;
import com.umar.integration.service.support.PlatformAllowList;
import com.umar.integration.service.support.RateLimiterService;
import com.umar.integration.util.CryptoUtils;
import com.umar.integration.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PartnerService {

    private final PartnerRepository partnerRepository;
    private final PartnerVersionRepository partnerVersionRepository;
    private final ApprovalRepository approvalRepository;
    private final ExtensionPointRepository extensionPointRepository;
    private final DataVaultClient dataVaultClient;
    private final PlatformAllowList allowList;
    private final HubAccessGuard accessGuard;
    private final IdempotencyService idempotencyService;
    private final OutboxService outboxService;
    private final RateLimiterService rateLimiterService;
    private final HubProperties properties;
    private final JsonUtils jsonUtils;

    /**
     * INT-16: Onboard Partner.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> onboardPartner(String idempotencyKey, OnboardPartnerRequest request) {
        HubIdentity identity = accessGuard.requireRoles(HubRole.INTEGRATION_MANAGER, HubRole.ADMIN);

        Optional<Map> cached = idempotencyService.findExisting("ONBOARD_PARTNER", idempotencyKey, request, Map.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        rateLimiterService.checkAndConsume("onboard:" + identity.actorId(), 10, Duration.ofDays(1),
                "Partner onboarding daily limit reached");

        Optional<PartnerEntity> existingByReg = partnerRepository.findByRegistrationNumberIgnoreCase(request.getRegistrationNumber().trim());
        if (existingByReg.isPresent()) {
            throw HubException.conflict("PARTNER_EXISTS", "This partner already exists",
                    Map.of("partnerId", existingByReg.get().getPartnerId()));
        }
        Optional<PartnerEntity> existingByName = partnerRepository.findByLegalNameIgnoreCase(request.getLegalName().trim());
        if (existingByName.isPresent()) {
            throw HubException.conflict("PARTNER_EXISTS", "This partner already exists",
                    Map.of("partnerId", existingByName.get().getPartnerId()));
        }

        // Validate countries
        for (String country : request.getCountryCodes()) {
            if (!PlatformAllowList.SUPPORTED_COUNTRIES.contains(country.trim().toUpperCase(Locale.ROOT))) {
                throw HubException.unprocessable("GRANT_NOT_ALLOWED",
                        "Country code is not supported: " + country, Map.of("items", List.of(country)));
            }
        }

        // Validate allowed event types
        List<String> disallowedEvents = request.getAllowedEventTypes().stream()
                .filter(e -> !allowList.isEventExportable(e))
                .toList();
        if (!disallowedEvents.isEmpty()) {
            throw HubException.unprocessable("GRANT_NOT_ALLOWED",
                    "A requested grant is not allowed", Map.of("items", disallowedEvents));
        }

        // Validate connector ingestion topics
        if (request.getConnectorRights() != null && request.getConnectorRights().getIngestionTopics() != null) {
            List<String> badTopics = request.getConnectorRights().getIngestionTopics().stream()
                    .filter(t -> !PlatformAllowList.APPROVED_INGESTION_TOPICS.contains(t))
                    .toList();
            if (!badTopics.isEmpty()) {
                throw HubException.unprocessable("GRANT_NOT_ALLOWED",
                        "A requested grant is not allowed", Map.of("items", badTopics));
            }
        }

        // Validate extension points
        if (request.getExtensionPoints() != null) {
            for (String hookId : request.getExtensionPoints()) {
                ExtensionPointEntity point = extensionPointRepository.findById(hookId)
                        .orElseThrow(() -> HubException.unprocessable("GRANT_NOT_ALLOWED",
                                "Extension point not available to partners: " + hookId, Map.of("items", List.of(hookId))));
                if (!point.isPartnerVisible() || point.isProtectedPoint()) {
                    throw HubException.unprocessable("GRANT_NOT_ALLOWED",
                            "A requested grant is not allowed", Map.of("items", List.of(hookId)));
                }
            }
        }

        // Validate quotas against platform maxima
        validateQuotas(request.getQuotas());

        // Validate EXTENDED data scope justification
        if (request.getDataScope() == DataScopeLevel.EXTENDED
                && (request.getDataScopeJustification() == null || request.getDataScopeJustification().isBlank())) {
            throw HubException.validation("EXTENDED dataScope requires dataScopeJustification");
        }

        // Verify agreements (at least 2: contract + DPA) in Data Vault
        if (request.getAgreementTokens().size() < 2) {
            throw HubException.unprocessable("AGREEMENT_REQUIRED", "The required agreements are missing");
        }
        Map<String, String> agreementHashes = dataVaultClient.verifyDocuments(
                request.getAgreementTokens(), "PARTNER_ONBOARDING");
        if (agreementHashes.size() < request.getAgreementTokens().size()) {
            throw HubException.unprocessable("AGREEMENT_REQUIRED", "The required agreements are missing");
        }

        Instant now = Instant.now();
        String slug = request.getDisplayName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        String partnerId = "ptn_" + slug + "_" + CryptoUtils.randomHex(3);
        String partnerHash = CryptoUtils.sha256Hex(jsonUtils.toJson(request));
        String approvalId = "apr_" + CryptoUtils.randomHex(4);

        PartnerEntity partner = PartnerEntity.builder()
                .partnerId(partnerId)
                .legalName(request.getLegalName().trim())
                .displayName(request.getDisplayName().trim())
                .type(request.getType())
                .registrationNumber(request.getRegistrationNumber().trim())
                .countryCodesJson(jsonUtils.toJson(request.getCountryCodes()))
                .contactsJson(jsonUtils.toJson(request.getContacts()))
                .allowedEventTypesJson(jsonUtils.toJson(request.getAllowedEventTypes()))
                .dataScope(request.getDataScope())
                .dataScopeJustification(request.getDataScopeJustification())
                .visibilityJson(jsonUtils.toJson(request.getVisibility()))
                .connectorRightsJson(jsonUtils.toJson(request.getConnectorRights()))
                .extensionPointsJson(jsonUtils.toJson(request.getExtensionPoints() == null ? List.of() : request.getExtensionPoints()))
                .quotasJson(jsonUtils.toJson(request.getQuotas()))
                .networkJson(jsonUtils.toJson(request.getNetwork()))
                .agreementTokensJson(jsonUtils.toJson(request.getAgreementTokens()))
                .agreementHashesJson(jsonUtils.toJson(agreementHashes))
                .status(PartnerStatus.PENDING_APPROVAL)
                .version(1)
                .partnerHash(partnerHash)
                .approvalId(approvalId)
                .createdBy(identity.actorId())
                .createdAt(now)
                .updatedAt(now)
                .build();
        partnerRepository.save(partner);

        partnerVersionRepository.save(PartnerVersionEntity.builder()
                .id(partnerId + "@1")
                .partnerId(partnerId)
                .version(1)
                .partnerHash(partnerHash)
                .status(PartnerStatus.PENDING_APPROVAL)
                .snapshotJson(jsonUtils.toJson(request))
                .createdBy(identity.actorId())
                .createdAt(now)
                .build());

        List<String> requiredRoles = List.of(HubRole.INTEGRATION_MANAGER.name(), HubRole.COMPLIANCE_OFFICER.name());
        approvalRepository.save(ApprovalEntity.builder()
                .approvalId(approvalId)
                .type(ApprovalType.PARTNER_ONBOARD)
                .subjectId(partnerId)
                .subjectVersion(1)
                .subjectHash(partnerHash)
                .requiredRolesJson(jsonUtils.toJson(requiredRoles))
                .decisionsJson("[]")
                .status(ApprovalStatus.OPEN)
                .requestedBy(identity.actorId())
                .slaDueAt(now.plus(Duration.ofDays(3)))
                .createdAt(now)
                .updatedAt(now)
                .build());

        outboxService.publishIntegrationEvent("partner.registered", partnerId, Map.of(
                "partnerId", partnerId,
                "legalName", partner.getLegalName(),
                "type", partner.getType().name(),
                "countryCodes", request.getCountryCodes(),
                "version", 1,
                "status", PartnerStatus.PENDING_APPROVAL.name()
        ));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("partnerId", partnerId);
        response.put("legalName", partner.getLegalName());
        response.put("type", partner.getType().name());
        response.put("status", partner.getStatus().name());
        response.put("allowedEventTypes", request.getAllowedEventTypes());
        response.put("dataScope", partner.getDataScope().name());
        response.put("visibility", request.getVisibility());
        response.put("quotas", request.getQuotas());
        response.put("version", 1);
        response.put("partnerHash", partnerHash);
        response.put("approvalId", approvalId);
        response.put("approvalsRequired", requiredRoles);
        response.put("createdAt", now.toString());

        idempotencyService.save("ONBOARD_PARTNER", idempotencyKey, identity.actorId(),
                request, HttpStatus.CREATED.value(), response, Duration.ofHours(24));
        return response;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPartners() {
        HubIdentity identity = accessGuard.requireRoles(
                HubRole.INTEGRATION_MANAGER, HubRole.ADMIN, HubRole.COMPLIANCE_OFFICER, HubRole.AUDITOR, HubRole.PARTNER_ADMIN);
        return partnerRepository.findAll().stream()
                .filter(p -> !accessGuard.isPartnerScopedOnly(identity) || p.getPartnerId().equals(identity.partnerId()))
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public PartnerEntity requireActivePartner(String partnerId) {
        PartnerEntity partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> HubException.notFound("PARTNER_NOT_FOUND", "Partner not found"));
        if (partner.getStatus() != PartnerStatus.ACTIVE) {
            throw HubException.forbidden("Partner is not active");
        }
        return partner;
    }

    private void validateQuotas(OnboardPartnerRequest.QuotasSpec q) {
        HubProperties.Quotas max = properties.getQuotas();
        if (q.getWebhookDeliveriesPerMonth() != null && q.getWebhookDeliveriesPerMonth() > max.getMaxWebhookDeliveriesPerMonth()) {
            throw HubException.unprocessable("QUOTA_ABOVE_MAX", "A quota is above the allowed maximum");
        }
        if (q.getWebhookRatePerSecond() != null && q.getWebhookRatePerSecond() > max.getMaxWebhookRatePerSecond()) {
            throw HubException.unprocessable("QUOTA_ABOVE_MAX", "A quota is above the allowed maximum");
        }
        if (q.getInboundRecordsPerMonth() != null && q.getInboundRecordsPerMonth() > max.getMaxInboundRecordsPerMonth()) {
            throw HubException.unprocessable("QUOTA_ABOVE_MAX", "A quota is above the allowed maximum");
        }
        if (q.getExtensionInvocationsPerMonth() != null && q.getExtensionInvocationsPerMonth() > max.getMaxExtensionInvocationsPerMonth()) {
            throw HubException.unprocessable("QUOTA_ABOVE_MAX", "A quota is above the allowed maximum");
        }
        if (q.getSubscriptions() != null && q.getSubscriptions() > max.getMaxSubscriptionsPerPartner()) {
            throw HubException.unprocessable("QUOTA_ABOVE_MAX", "A quota is above the allowed maximum");
        }
        if (q.getConnectors() != null && q.getConnectors() > max.getMaxConnectorsPerPartner()) {
            throw HubException.unprocessable("QUOTA_ABOVE_MAX", "A quota is above the allowed maximum");
        }
    }

    private Map<String, Object> toSummary(PartnerEntity p) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("partnerId", p.getPartnerId());
        map.put("tenantId", p.getTenantId());
        map.put("legalName", p.getLegalName());
        map.put("displayName", p.getDisplayName());
        map.put("type", p.getType().name());
        map.put("status", p.getStatus().name());
        map.put("dataScope", p.getDataScope().name());
        map.put("version", p.getVersion());
        return map;
    }
}
