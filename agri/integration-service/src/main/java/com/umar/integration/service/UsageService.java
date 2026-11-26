package com.umar.integration.service;

import com.umar.integration.client.DataVaultClient;
import com.umar.integration.domain.entity.*;
import com.umar.integration.domain.enums.DeliveryStatus;
import com.umar.integration.domain.enums.HubRole;
import com.umar.integration.error.HubException;
import com.umar.integration.repository.*;
import com.umar.integration.security.HubAccessGuard;
import com.umar.integration.security.HubIdentity;
import com.umar.integration.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UsageService {

    private final PartnerRepository partnerRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final InboundReceiptRepository receiptRepository;
    private final InvocationRecordRepository invocationRepository;
    private final DataVaultClient dataVaultClient;
    private final HubAccessGuard accessGuard;
    private final JsonUtils jsonUtils;

    /**
     * INT-10: Get Partner Usage.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getPartnerUsage(
            String requestedPartnerId,
            String fromStr,
            String toStr,
            String resolution,
            String sections,
            String subscriptionId,
            String connectorId,
            String extensionId,
            String environment,
            String format) {

        HubIdentity identity = accessGuard.requireRoles(
                HubRole.PARTNER_ADMIN, HubRole.INTEGRATION_MANAGER, HubRole.ADMIN,
                HubRole.COMPLIANCE_OFFICER, HubRole.AUDITOR);

        String partnerId = accessGuard.resolveEffectivePartnerId(identity, requestedPartnerId);
        if (partnerId == null || partnerId.isBlank()) {
            throw HubException.validation("partnerId is required");
        }

        PartnerEntity partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> HubException.notFound("PARTNER_NOT_FOUND", "Partner not found"));

        Instant from = parseInstantOrDate(fromStr, true);
        Instant to = parseInstantOrDate(toStr, false);
        if (to.isBefore(from)) {
            throw HubException.validation("to must be after from");
        }

        String res = resolution == null || resolution.isBlank() ? "DAY" : resolution.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("HOUR", "DAY").contains(res)) {
            throw HubException.validation("resolution must be HOUR or DAY");
        }
        if ("HOUR".equals(res) && Duration.between(from, to).toDays() > 31) {
            throw HubException.unprocessable("RANGE_TOO_LARGE", "Choose a shorter period");
        }
        if (from.isBefore(Instant.now().minus(Duration.ofDays(400)))) {
            throw HubException.unprocessable("RANGE_TOO_LARGE", "Period exceeds 13 months retention");
        }

        Set<String> sectionSet = new HashSet<>();
        for (String s : (sections == null || sections.isBlank() ? "webhooks,inbound,extensions,quotas" : sections).split(",")) {
            sectionSet.add(s.trim().toLowerCase(Locale.ROOT));
        }

        List<WebhookDeliveryEntity> deliveries = deliveryRepository.findByPartnerIdAndCreatedAtBetween(partnerId, from, to)
                .stream()
                .filter(d -> subscriptionId == null || subscriptionId.isBlank() || d.getSubscriptionId().equals(subscriptionId))
                .toList();

        List<InboundReceiptEntity> receipts = receiptRepository.findByOwnerIdAndReceivedAtBetween(partnerId, from, to)
                .stream()
                .filter(r -> connectorId == null || connectorId.isBlank() || r.getConnectorId().equals(connectorId))
                .toList();

        String tenantId = partner.getTenantId();
        List<InvocationRecordEntity> invocations = tenantId != null
                ? invocationRepository.findByTenantIdAndCreatedAtBetween(tenantId, from, to).stream()
                        .filter(i -> extensionId == null || extensionId.isBlank() || i.getExtensionId().equals(extensionId))
                        .toList()
                : List.of();

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("partnerId", partnerId);
        report.put("period", Map.of("from", fromStr, "to", toStr));
        report.put("resolution", res);
        report.put("environment", environment == null || environment.isBlank() ? "PRODUCTION" : environment.toUpperCase(Locale.ROOT));

        if (sectionSet.contains("webhooks")) {
            long total = deliveries.size();
            long delivered = deliveries.stream().filter(d -> d.getStatus() == DeliveryStatus.DELIVERED).count();
            long retries = deliveries.stream().mapToLong(d -> Math.max(0, d.getAttempts() - 1)).sum();
            long dlq = deliveries.stream().filter(d -> d.getStatus() == DeliveryStatus.DEAD_LETTERED).count();
            List<Long> latencies = deliveries.stream().map(WebhookDeliveryEntity::getLatencyMs).filter(Objects::nonNull).sorted().toList();

            Map<String, Object> webhooksSection = new LinkedHashMap<>();
            webhooksSection.put("deliveries", total);
            webhooksSection.put("successRate", total == 0 ? 1.0 : Math.round(((double) delivered / total) * 1000.0) / 1000.0);
            webhooksSection.put("retries", retries);
            webhooksSection.put("deadLettered", dlq);
            webhooksSection.put("p50Ms", WebhookService.percentile(latencies, 0.50));
            webhooksSection.put("p95Ms", WebhookService.percentile(latencies, 0.95));
            webhooksSection.put("p99Ms", WebhookService.percentile(latencies, 0.99));
            report.put("webhooks", webhooksSection);
        }

        if (sectionSet.contains("inbound")) {
            long reqs = receipts.size();
            long accepted = receipts.stream().mapToLong(InboundReceiptEntity::getAcceptedCount).sum();
            long rejected = receipts.stream().mapToLong(InboundReceiptEntity::getRejectedCount).sum();
            long dups = receipts.stream().mapToLong(InboundReceiptEntity::getDuplicateCount).sum();
            report.put("inbound", Map.of(
                    "requests", reqs,
                    "recordsAccepted", accepted,
                    "recordsRejected", rejected,
                    "duplicates", dups
            ));
        }

        if (sectionSet.contains("extensions")) {
            long totalInv = invocations.size();
            long errs = invocations.stream().filter(i -> "ERROR".equals(i.getResult()) || "RULE_VIOLATION".equals(i.getResult())).count();
            long timeouts = invocations.stream().filter(i -> "TIMEOUT".equals(i.getResult())).count();
            long holds = invocations.stream().filter(i -> "HOLD_FOR_REVIEW".equals(i.getFailModeApplied())).count();
            List<Long> lat = invocations.stream().map(InvocationRecordEntity::getLatencyMs).sorted().toList();
            report.put("extensions", Map.of(
                    "invocations", totalInv,
                    "errors", errs,
                    "timeouts", timeouts,
                    "holds", holds,
                    "p95Ms", WebhookService.percentile(lat, 0.95)
            ));
        }

        if (sectionSet.contains("quotas")) {
            Map<String, Object> q = jsonUtils.toMap(partner.getQuotasJson());
            List<Map<String, Object>> quotaList = new ArrayList<>();
            long wbLimit = q.get("webhookDeliveriesPerMonth") instanceof Number n ? n.longValue() : 1_000_000L;
            long wbUsed = deliveries.size();
            quotaList.add(Map.of(
                    "metric", "webhookDeliveries",
                    "used", wbUsed,
                    "limit", wbLimit,
                    "percentUsed", wbLimit == 0 ? 0.0 : Math.round((wbUsed * 1000.0) / wbLimit) / 10.0
            ));
            if (q.get("inboundRecordsPerMonth") instanceof Number n) {
                long inLimit = n.longValue();
                long inUsed = receipts.stream().mapToLong(InboundReceiptEntity::getAcceptedCount).sum();
                quotaList.add(Map.of(
                        "metric", "inboundRecords",
                        "used", inUsed,
                        "limit", inLimit,
                        "percentUsed", inLimit == 0 ? 0.0 : Math.round((inUsed * 1000.0) / inLimit) / 10.0
                ));
            }
            report.put("quotas", quotaList);
        }

        if ("CSV".equalsIgnoreCase(format)) {
            String csv = "metric,value\n"
                    + "webhook_deliveries," + deliveries.size() + "\n"
                    + "inbound_requests," + receipts.size() + "\n"
                    + "extension_invocations," + invocations.size() + "\n";
            String docToken = dataVaultClient.storeDocument("USAGE_EXPORT", "PARTNER_REPORTING", partnerId, csv, "text/csv");
            report.put("export", Map.of(
                    "documentToken", docToken,
                    "expiresAt", Instant.now().plus(Duration.ofHours(1)).toString()
            ));
        }

        report.put("generatedAt", Instant.now().toString());
        return report;
    }

    private Instant parseInstantOrDate(String raw, boolean startOfDay) {
        if (raw == null || raw.isBlank()) {
            throw HubException.validation("from and to query parameters are required");
        }
        try {
            if (raw.length() == 10) {
                LocalDate ld = LocalDate.parse(raw);
                return startOfDay
                        ? ld.atStartOfDay(ZoneOffset.UTC).toInstant()
                        : ld.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusMillis(1);
            }
            return Instant.parse(raw);
        } catch (Exception e) {
            throw HubException.validation("Invalid date/time format: " + raw);
        }
    }
}
