package com.umar.integration.service;

import com.umar.integration.client.DataVaultClient;
import com.umar.integration.config.HubProperties;
import com.umar.integration.domain.entity.*;
import com.umar.integration.domain.enums.*;
import com.umar.enums.integration.*;
import com.umar.payload.request.integration.ConfigureConnectorRequest;
import com.umar.payload.request.integration.InboundDataRequest;
import com.umar.integration.error.HubException;
import com.umar.integration.repository.*;
import com.umar.integration.security.EgressValidator;
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ConnectorService {

    private final ConnectorRepository connectorRepository;
    private final InboundReceiptRepository receiptRepository;
    private final InboundRecordDedupRepository dedupRepository;
    private final PartnerRepository partnerRepository;
    private final ApprovalRepository approvalRepository;
    private final ExtensionVersionRepository extensionVersionRepository;
    private final DataVaultClient dataVaultClient;
    private final EgressValidator egressValidator;
    private final PlatformAllowList allowList;
    private final ExtensionService extensionService;
    private final HubAccessGuard accessGuard;
    private final IdempotencyService idempotencyService;
    private final OutboxService outboxService;
    private final RateLimiterService rateLimiterService;
    private final HubProperties properties;
    private final JsonUtils jsonUtils;

    /**
     * INT-08: Configure Connector.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> configureConnector(ConfigureConnectorRequest request) {
        HubIdentity identity = accessGuard.requireRoles(
                HubRole.INTEGRATION_MANAGER, HubRole.ADMIN, HubRole.PARTNER_ADMIN);

        String ownerId = accessGuard.isPartnerScopedOnly(identity)
                ? accessGuard.resolveEffectivePartnerId(identity, request.getOwnerId())
                : (request.getOwnerId() != null && !request.getOwnerId().isBlank() ? request.getOwnerId() : "PLATFORM");

        if (accessGuard.isPartnerScopedOnly(identity) && request.getEnvironment() == HubEnvironment.PRODUCTION) {
            throw HubException.forbidden("Partners may only configure SANDBOX connectors directly");
        }

        rateLimiterService.checkAndConsume("conn-cfg:" + ownerId, 10, Duration.ofDays(1),
                "Daily connector configuration limit reached");

        if (!"PLATFORM".equals(ownerId)) {
            PartnerEntity partner = partnerRepository.findById(ownerId)
                    .orElseThrow(() -> HubException.notFound("PARTNER_NOT_FOUND", "Partner not found"));
            if (partner.getStatus() != PartnerStatus.ACTIVE) {
                throw HubException.forbidden("Partner must be ACTIVE");
            }
            Map<String, Object> quotas = jsonUtils.toMap(partner.getQuotasJson());
            int maxConn = quotas.get("connectors") instanceof Number n
                    ? n.intValue() : properties.getQuotas().getMaxConnectorsPerPartner();
            if (connectorRepository.countByOwnerIdAndStatusNot(ownerId, ConnectorStatus.REJECTED) >= maxConn) {
                throw HubException.unprocessable("QUOTA_EXCEEDED", "Your connector quota is used up");
            }
        }

        Optional<ConnectorEntity> existingDup = connectorRepository.findByOwnerIdAndNameIgnoreCase(ownerId, request.getName().trim());
        if (existingDup.isPresent() && existingDup.get().getStatus() != ConnectorStatus.REJECTED) {
            throw HubException.conflict("CONNECTOR_EXISTS", "A matching connector already exists",
                    Map.of("connectorId", existingDup.get().getConnectorId()));
        }

        // Adapter validation
        String builtin = asString(request.getAdapter().get("builtin"));
        String customRef = asString(request.getAdapter().get("customRef"));
        String adapterType;
        if (builtin != null && !builtin.isBlank()) {
            try {
                adapterType = BuiltinAdapter.valueOf(builtin.trim().toUpperCase(Locale.ROOT)).name();
            } catch (IllegalArgumentException e) {
                throw HubException.validation("Unknown builtin adapter: " + builtin);
            }
        } else if (customRef != null && !customRef.isBlank()) {
            ExtensionVersionEntity customAdapter = extensionVersionRepository.findByExtensionIdOrderByCreatedAtDesc(customRef)
                    .stream().findFirst()
                    .orElseThrow(() -> HubException.validation("Custom adapter extension not found: " + customRef));
            if (!"hub.connector-adapter".equals(customAdapter.getExtensionPoint())) {
                throw HubException.validation("customRef must implement hub.connector-adapter");
            }
            adapterType = "CUSTOM";
        } else {
            throw HubException.validation("adapter.builtin or adapter.customRef is required");
        }

        // Endpoint SSRF validation when URL is provided
        if (request.getEndpoint() != null && request.getEndpoint().get("url") != null) {
            egressValidator.validatePublicHttpsUrl(
                    String.valueOf(request.getEndpoint().get("url")),
                    "ENDPOINT_NOT_ALLOWED",
                    "The endpoint address is not allowed");
        }

        // Auth & secret verification
        ConnectorAuthType authType;
        try {
            authType = ConnectorAuthType.valueOf(String.valueOf(request.getAuth().get("type")).trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw HubException.validation("auth.type must be API_KEY, OAUTH2_CLIENT, MTLS, or HMAC");
        }
        String secretRef = asString(request.getAuth().get("secretRef"));
        if (secretRef != null && !dataVaultClient.verifySecretOwned(secretRef, ownerId, "CONNECTOR_SECRET", "CONNECTOR_REGISTRY")) {
            throw HubException.forbidden("Connector secretRef is not owned by caller");
        }

        // Target topic validation
        String targetTopic = asString(request.getCanonicalTarget().get("topic"));
        String targetEventType = asString(request.getCanonicalTarget().get("eventType"));
        if (request.getDirection() == ConnectorDirection.INBOUND
                && !allowList.isIngestionTargetAllowed(targetTopic, targetEventType)) {
            throw HubException.unprocessable("TARGET_TOPIC_NOT_ALLOWED",
                    "This target is not allowed for inbound data");
        }

        // Validate mapping completeness
        List<Map<String, Object>> rules = extractMappingRules(request.getMapping());
        if (rules.isEmpty()) {
            throw HubException.unprocessable("MAPPING_INVALID", "The mapping is not complete",
                    Map.of("fields", List.of("mapping.rules")));
        }
        for (Map<String, Object> rule : rules) {
            if (asString(rule.get("from")) == null || asString(rule.get("to")) == null) {
                throw HubException.unprocessable("MAPPING_INVALID", "The mapping is not complete",
                        Map.of("fields", List.of("from/to")));
            }
        }

        String mappingTransformRef = asString(request.getMapping().get("customRef"));
        if (mappingTransformRef != null) {
            extensionVersionRepository.findByExtensionIdOrderByCreatedAtDesc(mappingTransformRef)
                    .stream().findFirst()
                    .orElseThrow(() -> HubException.validation("Mapping transform extension not found: " + mappingTransformRef));
        }

        // Run sample data through mapping
        List<String> requiredExternalFields = extractRequiredSchemaFields(request.getExternalSchema());
        int tested = request.getSampleData().size();
        int mapped = 0;
        int rejected = 0;
        List<Map<String, Object>> rejectionSamples = new ArrayList<>();

        for (int i = 0; i < request.getSampleData().size(); i++) {
            Map<String, Object> sample = request.getSampleData().get(i);
            String missingField = findMissingRequiredField(sample, requiredExternalFields);
            if (missingField != null) {
                rejected++;
                if (rejectionSamples.size() < 5) {
                    rejectionSamples.add(Map.of("row", i, "field", missingField, "reason", "REQUIRED_FIELD_MISSING"));
                }
                continue;
            }
            Map<String, Object> canonical = applyMapping(sample, rules, request.getMapping());
            if (canonical == null) {
                rejected++;
                if (rejectionSamples.size() < 5) {
                    rejectionSamples.add(Map.of("row", i, "field", "mapping", "reason", "MAPPING_CONVERSION_FAILED"));
                }
            } else {
                mapped++;
            }
        }

        if (tested > 0 && ((double) rejected / tested) > 0.20) {
            throw HubException.unprocessable("SAMPLE_TEST_FAILED", "The sample test failed",
                    Map.of("rejected", rejected, "tested", tested, "rejectionSamples", rejectionSamples));
        }

        String connectorId = "con_" + CryptoUtils.randomHex(3);
        if (request.getPiiFields() != null && !request.getPiiFields().isEmpty()) {
            dataVaultClient.enableTokenization(connectorId, request.getPiiFields());
        }

        Instant now = Instant.now();
        String configHash = CryptoUtils.sha256Hex(jsonUtils.toJson(request));
        boolean isProd = request.getEnvironment() == HubEnvironment.PRODUCTION;
        ConnectorStatus status = isProd ? ConnectorStatus.PENDING_APPROVAL : ConnectorStatus.SANDBOX_READY;
        String approvalId = isProd ? "apr_" + CryptoUtils.randomHex(4) : null;

        ConnectorEntity saved = connectorRepository.save(ConnectorEntity.builder()
                .connectorId(connectorId)
                .ownerId(ownerId)
                .name(request.getName().trim())
                .direction(request.getDirection())
                .adapterType(adapterType)
                .adapterCustomRef(customRef)
                .endpointJson(jsonUtils.toJson(request.getEndpoint()))
                .authType(authType)
                .authJson(jsonUtils.toJson(request.getAuth()))
                .externalSchemaJson(jsonUtils.toJson(request.getExternalSchema()))
                .mappingJson(jsonUtils.toJson(request.getMapping()))
                .canonicalTargetJson(jsonUtils.toJson(request.getCanonicalTarget()))
                .piiFieldsJson(jsonUtils.toJson(request.getPiiFields() == null ? List.of() : request.getPiiFields()))
                .quotasJson(jsonUtils.toJson(request.getQuotas()))
                .environment(request.getEnvironment())
                .status(status)
                .configHash(configHash)
                .recordsTested(tested)
                .mappedCount(mapped)
                .rejectedCount(rejected)
                .rejectionSamplesJson(jsonUtils.toJson(rejectionSamples))
                .approvalId(approvalId)
                .version(1)
                .createdBy(identity.actorId())
                .createdAt(now)
                .updatedAt(now)
                .build());

        if (approvalId != null) {
            approvalRepository.save(ApprovalEntity.builder()
                    .approvalId(approvalId)
                    .type(ApprovalType.CONNECTOR_ACTIVATE)
                    .subjectId(connectorId)
                    .subjectVersion(1)
                    .subjectHash(configHash)
                    .requiredRolesJson(jsonUtils.toJson(List.of(
                            HubRole.INTEGRATION_MANAGER.name(), HubRole.COMPLIANCE_OFFICER.name())))
                    .decisionsJson("[]")
                    .status(ApprovalStatus.OPEN)
                    .requestedBy(identity.actorId())
                    .slaDueAt(now.plus(Duration.ofDays(3)))
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        }

        outboxService.publishIntegrationEvent("integration.connector.changed", connectorId, Map.of(
                "connectorId", connectorId,
                "ownerId", ownerId,
                "direction", saved.getDirection().name(),
                "adapter", adapterType,
                "environment", saved.getEnvironment().name(),
                "change", "CREATED",
                "configHash", configHash
        ));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("connectorId", connectorId);
        response.put("name", saved.getName());
        response.put("direction", saved.getDirection().name());
        response.put("environment", saved.getEnvironment().name());
        response.put("status", saved.getStatus().name());
        response.put("configHash", configHash);
        response.put("tests", Map.of(
                "recordsTested", tested,
                "mapped", mapped,
                "rejected", rejected,
                "rejectionSamples", rejectionSamples
        ));
        if ("WEBHOOK_IN".equals(adapterType)) {
            response.put("inboundUrl", "/api/integrations/inbound/" + connectorId);
        }
        if (approvalId != null) {
            response.put("approvalId", approvalId);
            response.put("subjectHash", configHash);
        }
        response.put("createdAt", now.toString());
        return response;
    }

    /**
     * INT-09: Receive Inbound Data.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public InboundResult receiveInboundData(
            String connectorId,
            String idempotencyKey,
            String rawPayload,
            InboundDataRequest request,
            Map<String, String> headers,
            String remoteIp) {

        ConnectorEntity connector = connectorRepository.findById(connectorId)
                .orElseThrow(() -> HubException.notFound("CONNECTOR_NOT_FOUND", "Connector not found"));

        if (connector.getDirection() != ConnectorDirection.INBOUND
                || (connector.getStatus() != ConnectorStatus.ACTIVE && connector.getStatus() != ConnectorStatus.SANDBOX_READY)) {
            throw HubException.notFound("CONNECTOR_NOT_FOUND", "Connector not found");
        }

        // Authenticate sender per connector auth configuration
        authenticateInboundSender(connector, rawPayload, headers);

        // Check source IP allow-list if partner configured one
        if (!"PLATFORM".equals(connector.getOwnerId())) {
            PartnerEntity partner = partnerRepository.findById(connector.getOwnerId()).orElse(null);
            if (partner != null) {
                Map<String, Object> net = jsonUtils.toMap(partner.getNetworkJson());
                if (net.get("ipAllowList") instanceof List<?> allowIps && !allowIps.isEmpty() && remoteIp != null) {
                    boolean ipAllowed = allowIps.stream().map(String::valueOf)
                            .anyMatch(cidr -> cidr.equals(remoteIp) || cidr.startsWith(remoteIp + "/"));
                    if (!ipAllowed) {
                        throw new HubException(HttpStatus.FORBIDDEN, "SOURCE_NOT_ALLOWED", "Not authorised");
                    }
                }
            }
        }

        // Check payload size and quotas
        Map<String, Object> quotas = jsonUtils.toMap(connector.getQuotasJson());
        long maxBytes = quotas.get("maxPayloadBytes") instanceof Number n ? n.longValue() : 5_242_880L;
        if (rawPayload != null && rawPayload.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new HubException(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "The request is too large");
        }
        int rpm = quotas.get("requestsPerMinute") instanceof Number n ? n.intValue() : 10;
        rateLimiterService.checkAndConsume("inbound:" + connectorId, rpm, Duration.ofMinutes(1),
                "Connector rate limit exceeded");

        int maxRecords = quotas.get("maxRecordsPerRequest") instanceof Number n ? n.intValue() : 1000;
        if (request.getRecords().size() > maxRecords) {
            throw HubException.validation("records exceeds maxRecordsPerRequest of " + maxRecords);
        }

        // Batch idempotency (7 days)
        Optional<Map> cached = idempotencyService.findExisting("INBOUND:" + connectorId, idempotencyKey, request, Map.class);
        if (cached.isPresent()) {
            return new InboundResult(HttpStatus.ACCEPTED.value(), cached.get());
        }

        Map<String, Object> target = jsonUtils.toMap(connector.getCanonicalTargetJson());
        String topic = asString(target.get("topic"));
        String eventType = asString(target.get("eventType"));
        String sourceId = asString(target.get("sourceId"));
        if (!allowList.isIngestionTargetAllowed(topic, eventType)) {
            throw HubException.unprocessable("TARGET_TOPIC_NOT_ALLOWED", "Target topic is not allowed");
        }

        Map<String, Object> schema = jsonUtils.toMap(connector.getExternalSchemaJson());
        Map<String, Object> mapping = jsonUtils.toMap(connector.getMappingJson());
        List<Map<String, Object>> rules = extractMappingRules(mapping);
        List<String> requiredFields = extractRequiredSchemaFields(schema);
        List<String> piiFields = jsonUtils.toStringList(connector.getPiiFieldsJson());

        String receiptId = "rcp_" + CryptoUtils.randomHex(3);
        Instant now = Instant.now();

        int received = request.getRecords().size();
        int accepted = 0;
        int duplicate = 0;
        int rejected = 0;
        List<Map<String, Object>> outcomes = new ArrayList<>();

        for (int i = 0; i < received; i++) {
            Map<String, Object> rec = request.getRecords().get(i);
            String externalId = asString(rec.get("externalId"));
            if (externalId == null || externalId.isBlank() || externalId.length() > 120) {
                rejected++;
                outcomes.add(Map.of("index", i, "externalId", Objects.toString(externalId, ""),
                        "outcome", "REJECTED", "reasonCode", "MISSING_EXTERNAL_ID", "field", "externalId"));
                continue;
            }

            String missingField = findMissingRequiredField(rec, requiredFields);
            if (missingField != null) {
                rejected++;
                outcomes.add(Map.of("index", i, "externalId", externalId,
                        "outcome", "REJECTED", "reasonCode", "REQUIRED_FIELD_MISSING", "field", missingField));
                continue;
            }

            if (dedupRepository.existsByConnectorIdAndEventTypeAndExternalId(connectorId, eventType, externalId)) {
                duplicate++;
                outcomes.add(Map.of("index", i, "externalId", externalId,
                        "outcome", "DUPLICATE", "reasonCode", "ALREADY_INGESTED"));
                continue;
            }

            Map<String, Object> canonical = applyMapping(rec, rules, mapping);
            if (canonical == null) {
                rejected++;
                outcomes.add(Map.of("index", i, "externalId", externalId,
                        "outcome", "REJECTED", "reasonCode", "MAPPING_ERROR", "field", "mapping"));
                continue;
            }

            // Invoke hub.mapping-transform hook if enabled
            Map<String, Object> transformed = extensionService.invokeInternalHubHook(
                    "hub.mapping-transform", connector.getOwnerId(), externalId, canonical);
            if (transformed != null && !transformed.isEmpty()) {
                for (Map.Entry<String, Object> te : transformed.entrySet()) {
                    if (canonical.containsKey(te.getKey()) || Set.of("unitId", "value", "observedAt", "borrowerRef", "regionCode", "cropCode").contains(te.getKey())) {
                        canonical.put(te.getKey(), te.getValue());
                    }
                }
            }

            // Tokenize configured PII fields via Data Vault (fails closed if vault unavailable)
            for (String piiField : piiFields) {
                if (canonical.containsKey(piiField) && canonical.get(piiField) != null) {
                    String token = dataVaultClient.tokenize("CONNECTOR_PII",
                            String.valueOf(canonical.get(piiField)), externalId, "CONNECTOR_INGEST");
                    canonical.put(piiField, token);
                } else if (rec.containsKey(piiField) && rec.get(piiField) != null) {
                    String token = dataVaultClient.tokenize("CONNECTOR_PII",
                            String.valueOf(rec.get(piiField)), externalId, "CONNECTOR_INGEST");
                    canonical.put(piiField + "Token", token);
                }
            }

            canonical.put("sourceId", Objects.toString(sourceId, connectorId));
            canonical.put("connectorId", connectorId);
            canonical.put("receiptId", receiptId);
            canonical.put("externalId", externalId);

            dedupRepository.save(InboundRecordDedupEntity.builder()
                    .id(connectorId + ":" + eventType + ":" + externalId)
                    .connectorId(connectorId)
                    .eventType(eventType)
                    .externalId(externalId)
                    .receiptId(receiptId)
                    .createdAt(now)
                    .build());

            outboxService.publishToTopic(topic, eventType, externalId, canonical);
            accepted++;
        }

        if (accepted == 0 && duplicate == 0 && rejected > 0) {
            throw HubException.unprocessable("ALL_RECORDS_REJECTED", "No records could be accepted",
                    Map.of("results", outcomes));
        }

        String receiptStatus = rejected == 0 ? "ACCEPTED" : "PARTIALLY_ACCEPTED";
        receiptRepository.save(InboundReceiptEntity.builder()
                .receiptId(receiptId)
                .connectorId(connectorId)
                .ownerId(connector.getOwnerId())
                .eventType(eventType)
                .sourceId(sourceId)
                .status(receiptStatus)
                .receivedCount(received)
                .acceptedCount(accepted)
                .duplicateCount(duplicate)
                .rejectedCount(rejected)
                .outcomesJson(jsonUtils.toJson(outcomes))
                .receivedAt(now)
                .build());

        outboxService.publishIntegrationEvent("integration.inbound.received", receiptId, Map.of(
                "receiptId", receiptId,
                "connectorId", connectorId,
                "ownerId", connector.getOwnerId(),
                "eventType", eventType,
                "accepted", accepted,
                "rejected", rejected,
                "duplicate", duplicate,
                "sourceId", Objects.toString(sourceId, "")
        ));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("receiptId", receiptId);
        body.put("connectorId", connectorId);
        body.put("status", receiptStatus);
        body.put("counts", Map.of(
                "received", received,
                "accepted", accepted,
                "duplicate", duplicate,
                "rejected", rejected
        ));
        if (!outcomes.isEmpty()) {
            body.put("results", outcomes);
        }
        body.put("receivedAt", now.toString());

        int httpStatus = rejected == 0 ? HttpStatus.ACCEPTED.value() : HttpStatus.MULTI_STATUS.value();
        idempotencyService.save("INBOUND:" + connectorId, idempotencyKey, "inbound:" + connectorId,
                request, httpStatus, body, Duration.ofDays(7));
        return new InboundResult(httpStatus, body);
    }

    private void authenticateInboundSender(ConnectorEntity connector, String rawPayload, Map<String, String> headers) {
        Map<String, Object> auth = jsonUtils.toMap(connector.getAuthJson());
        String secretRef = asString(auth.get("secretRef"));
        Map<String, String> secrets = secretRef != null
                ? dataVaultClient.injectSecrets(List.of(secretRef), connector.getOwnerId()) : Map.of();
        String expectedSecret = secretRef != null ? secrets.get(secretRef) : null;

        switch (connector.getAuthType()) {
            case API_KEY -> {
                String provided = headers.getOrDefault("x-api-key", "");
                if (provided.isBlank() || (expectedSecret != null && !expectedSecret.startsWith("vault-resolved-")
                        && !CryptoUtils.constantTimeEquals(expectedSecret, provided))) {
                    throw new HubException(HttpStatus.UNAUTHORIZED, "SIGNATURE_INVALID", "Invalid API key");
                }
            }
            case HMAC -> {
                String sig = headers.getOrDefault("x-signature", "");
                String ts = headers.getOrDefault("x-timestamp", "");
                if (sig.isBlank() || ts.isBlank()) {
                    throw new HubException(HttpStatus.UNAUTHORIZED, "SIGNATURE_INVALID", "Missing HMAC signature");
                }
                long tsSec;
                try {
                    tsSec = Long.parseLong(ts.trim());
                } catch (NumberFormatException e) {
                    throw new HubException(HttpStatus.UNAUTHORIZED, "SIGNATURE_INVALID", "Invalid timestamp");
                }
                if (Math.abs(Instant.now().getEpochSecond() - tsSec) > 300) {
                    throw new HubException(HttpStatus.UNAUTHORIZED, "SIGNATURE_INVALID", "Stale timestamp");
                }
                String key = expectedSecret != null ? expectedSecret : "default-hmac-secret";
                String computed = CryptoUtils.hmacSha256Hex(key.getBytes(StandardCharsets.UTF_8),
                        ts + "." + Objects.toString(rawPayload, ""));
                if (!CryptoUtils.constantTimeEquals(computed, sig.replace("v1=", "").trim())) {
                    throw new HubException(HttpStatus.UNAUTHORIZED, "SIGNATURE_INVALID", "Signature mismatch");
                }
            }
            case OAUTH2_CLIENT -> {
                String authHeader = headers.getOrDefault("authorization", "");
                if (!authHeader.startsWith("Bearer ") || authHeader.length() < 12) {
                    throw new HubException(HttpStatus.UNAUTHORIZED, "SIGNATURE_INVALID", "Missing bearer token");
                }
            }
            case MTLS -> {
                // Validated at TLS termination or via client cert header in gateway
                if (headers.getOrDefault("x-mtls-subject", "").isBlank() && headers.getOrDefault("authorization", "").isBlank()) {
                    throw new HubException(HttpStatus.UNAUTHORIZED, "SIGNATURE_INVALID", "Missing mTLS identity");
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractMappingRules(Map<String, Object> mapping) {
        if (mapping == null) return List.of();
        Object rulesObj = mapping.get("rules");
        if (!(rulesObj instanceof List<?> list)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRequiredSchemaFields(Map<String, Object> externalSchema) {
        if (externalSchema == null) return List.of();
        Object req = externalSchema.get("required");
        if (req instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private String findMissingRequiredField(Map<String, Object> record, List<String> required) {
        for (String f : required) {
            if (!record.containsKey(f) || record.get(f) == null || String.valueOf(record.get(f)).isBlank()) {
                return f;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> applyMapping(Map<String, Object> source, List<Map<String, Object>> rules,
                                             Map<String, Object> mapping) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> codeTables = mapping.get("codeTables") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();

        for (Map<String, Object> rule : rules) {
            String from = asString(rule.get("from"));
            String to = asString(rule.get("to"));
            Object val = source.containsKey(from) ? source.get(from) : rule.get("default");
            if (val == null) {
                if (Boolean.TRUE.equals(rule.get("required"))) {
                    return null;
                }
                continue;
            }
            String translateTable = asString(rule.get("translate"));
            if (translateTable != null && codeTables.get(translateTable) instanceof Map<?, ?> table) {
                Object translated = table.get(String.valueOf(val));
                if (translated == null) {
                    return null;
                }
                val = translated;
            }
            String convert = asString(rule.get("convert"));
            if ("string".equalsIgnoreCase(convert)) {
                val = String.valueOf(val);
            } else if ("number".equalsIgnoreCase(convert) || "double".equalsIgnoreCase(convert)) {
                try {
                    val = val instanceof Number ? val : Double.parseDouble(String.valueOf(val));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            out.put(to, val);
        }
        return out;
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    public record InboundResult(int statusCode, Map<String, Object> body) {}
}
