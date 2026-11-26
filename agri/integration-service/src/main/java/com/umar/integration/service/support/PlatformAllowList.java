package com.umar.integration.service.support;

import com.umar.enums.integration.DataScopeLevel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Platform-wide allow-lists for exportable webhook event types, field projections per data scope,
 * approved inbound ingestion topics, and supported partner operating countries.
 */
@Component
public class PlatformAllowList {

    public static final Set<String> EXPORTABLE_EVENT_TYPES = Set.of(
            "policy.issued",
            "policy.activated",
            "policy.cancelled",
            "policy.expired",
            "premium.paid",
            "premium.refunded",
            "claim.opened",
            "claim.approved",
            "claim.paid",
            "trigger.evaluated",
            "trigger.activated",
            "farmer.group.member.confirmed",
            "plot.validated",
            "product.published"
    );

    public static final Set<String> APPROVED_INGESTION_TOPICS = Set.of(
            "ingest.observation.weather",
            "ingest.observation.satellite",
            "ingest.sponsor.eligibility",
            "ingest.registry.cooperative",
            "ingest.registry.parcel",
            "ingest.settlement.statement"
    );

    public static final Set<String> APPROVED_INGESTION_EVENT_TYPES = Set.of(
            "observation.weather",
            "observation.satellite",
            "sponsor.eligibility",
            "registry.cooperative",
            "registry.parcel",
            "settlement.statement"
    );

    public static final Set<String> SUPPORTED_COUNTRIES = Set.of(
            "KE", "UG", "TZ", "RW", "ET", "GH", "NG", "ZM", "MW", "IN", "PK"
    );

    public static final Set<String> PII_FIELD_NAMES = Set.of(
            "farmerName", "fullName", "firstName", "lastName", "phone", "phoneNumber",
            "msisdn", "walletNumber", "mobileMoneyNumber", "nationalId", "email", "photoUrl", "rawIndexData"
    );

    private static final Map<String, Set<String>> MINIMAL_FIELDS = Map.of(
            "policy.activated", Set.of("policyId", "status", "activatedAt", "productId", "regionCode", "externalRef"),
            "premium.paid", Set.of("policyId", "fullyPaid", "paidAt", "externalRef"),
            "claim.paid", Set.of("claimId", "policyId", "status", "paidAt", "externalRef")
    );

    private static final Map<String, Set<String>> DEFAULT_FIELDS = Map.of(
            "policy.activated", Set.of("policyId", "status", "activatedAt", "productId", "regionCode", "cropCode", "sumInsuredMinor", "currency", "externalRef"),
            "premium.paid", Set.of("policyId", "amountMinor", "currency", "fullyPaid", "paidAt", "externalRef"),
            "claim.paid", Set.of("claimId", "policyId", "amountMinor", "currency", "status", "paidAt", "externalRef")
    );

    public boolean isEventExportable(String eventType) {
        return eventType != null && EXPORTABLE_EVENT_TYPES.contains(eventType);
    }

    public boolean isIngestionTargetAllowed(String topic, String eventType) {
        return topic != null && APPROVED_INGESTION_TOPICS.contains(topic)
                && eventType != null && APPROVED_INGESTION_EVENT_TYPES.contains(eventType);
    }

    public Set<String> allowedFieldsFor(String eventType, DataScopeLevel scope) {
        if (scope == DataScopeLevel.MINIMAL) {
            return MINIMAL_FIELDS.getOrDefault(eventType, Set.of("status", "occurredAt", "externalRef"));
        }
        if (scope == DataScopeLevel.PARTNER_DEFAULT) {
            return DEFAULT_FIELDS.getOrDefault(eventType, Set.of("status", "amountMinor", "currency", "regionCode", "productId", "occurredAt", "externalRef"));
        }
        // EXTENDED still excludes PII fields
        return Set.of("*");
    }

    public List<String> allExportableEventsSorted() {
        return EXPORTABLE_EVENT_TYPES.stream().sorted().toList();
    }
}
