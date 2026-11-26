package com.umar.audit.service;

import com.umar.audit.client.AuditExternalClients;
import com.umar.audit.config.AuditProperties;
import com.umar.audit.domain.*;
import com.umar.audit.repository.*;
import com.umar.audit.service.support.AuditSupportService;
import com.umar.enums.audit.FindingSeverity;
import com.umar.enums.audit.FindingStatus;
import com.umar.enums.audit.RetentionClass;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AuditIntakeAndKeeperService {

    private static final Set<String> REGISTERED_PRODUCERS = Set.of(
            "auth-service", "farmer-profile", "plot-registry", "policy-service",
            "premium-billing", "trigger-engine", "claims-service", "payout-service",
            "riskpool-service", "iot-service", "notification-service", "audit-service", "vault-service"
    );

    private static final Set<String> PII_KEYS = Set.of("msisdn", "phone", "nationalId", "email", "accountNumber", "fullName");

    private final AuditRecordRepository recordRepository;
    private final AuditCheckpointRepository checkpointRepository;
    private final TelemetryBatchRepository telemetryBatchRepository;
    private final AuditFindingRepository findingRepository;
    private final InboxEventRepository inboxRepository;
    private final AuditExternalClients clients;
    private final AuditSupportService support;
    private final AuditProperties properties;

    @Transactional
    public AuditRecordEntity ingestDomainEvent(String eventId, String topic, String producer, String eventType,
                                               Map<String, Object> rawPayload) {
        if (eventId != null && inboxRepository.existsByEventId(eventId)) {
            return null;
        }
        if (eventId != null) {
            inboxRepository.save(InboxEventEntity.builder()
                    .id(UUID.randomUUID()).eventId(eventId).topic(topic).receivedAt(Instant.now()).build());
        }

        Instant now = Instant.now();
        Instant occurredAt = rawPayload.get("occurredAt") != null
                ? Instant.parse(String.valueOf(rawPayload.get("occurredAt")))
                : now;

        List<String> flags = new ArrayList<>();
        if (producer == null || !REGISTERED_PRODUCERS.contains(producer)) {
            flags.add("UNREGISTERED_PRODUCER");
        }
        if (Math.abs(now.getEpochSecond() - occurredAt.getEpochSecond()) > properties.getRules().getClockSkewSeconds()) {
            flags.add("CLOCK_SKEW");
        }

        Map<String, Object> sanitized = new LinkedHashMap<>(rawPayload != null ? rawPayload : Map.of());
        boolean piiRedacted = false;
        for (String key : new ArrayList<>(sanitized.keySet())) {
            if (PII_KEYS.contains(key)) {
                sanitized.put(key, "[REDACTED]");
                piiRedacted = true;
            }
        }
        if (piiRedacted) {
            flags.add("PERSONAL_DATA_DETECTED");
        }

        if ("iot.reading.ingested".equals(eventType)) {
            appendTelemetryLeaf(sanitized);
        }

        RetentionClass rc = classifyRetention(eventType);
        String chainId = support.chainIdFor(rc);
        Optional<AuditRecordEntity> tail = recordRepository.findFirstByChainIdOrderBySeqDesc(chainId);
        long nextSeq = tail.map(r -> r.getSeq() + 1).orElse(1L);
        String prevHash = tail.map(AuditRecordEntity::getRecordHash).orElse("GENESIS_" + chainId);
        String payloadJson = support.toJson(sanitized);
        String recordHash = support.computeRecordHash(chainId, nextSeq, prevHash, payloadJson);

        String claimId = strOrNull(sanitized.get("claimId"));
        String policyId = strOrNull(sanitized.get("policyId"));
        String plotId = strOrNull(sanitized.get("plotId"));
        String subjectId = strOrNull(sanitized.get("subjectToken"));
        if (subjectId == null && sanitized.get("farmerId") != null) {
            subjectId = clients.deriveSubjectToken(String.valueOf(sanitized.get("farmerId")))
                    .orElse(String.valueOf(sanitized.get("farmerId")));
        }

        AuditRecordEntity record = recordRepository.save(AuditRecordEntity.builder()
                .id(UUID.randomUUID())
                .recordId(support.idPrefix("aud_"))
                .chainId(chainId)
                .seq(nextSeq)
                .retentionClass(rc)
                .eventId(eventId != null ? eventId : support.idPrefix("evt_"))
                .eventType(eventType)
                .producerService(producer != null ? producer : "unknown")
                .occurredAt(occurredAt)
                .ingestedAt(now)
                .subjectType(subjectId != null ? "FARMER" : null)
                .subjectId(subjectId)
                .claimId(claimId)
                .policyId(policyId)
                .plotId(plotId)
                .actorId(strOrNull(sanitized.get("actorId")))
                .payloadJson(payloadJson)
                .flagsJson(support.toJson(flags))
                .redacted(piiRedacted)
                .prevHash(prevHash)
                .recordHash(recordHash)
                .build());

        if (claimId != null) {
            if (policyId != null) support.addSubjectEdge("CLAIM", claimId, "POLICY", policyId, record.getRecordId(), "COVERED_BY");
            if (plotId != null) support.addSubjectEdge("CLAIM", claimId, "PLOT", plotId, record.getRecordId(), "AFFECTED_PLOT");
            if (subjectId != null) support.addSubjectEdge("CLAIM", claimId, "FARMER", subjectId, record.getRecordId(), "CLAIMANT");
        }

        evaluateControlRules(record, sanitized, piiRedacted);
        return record;
    }

    private RetentionClass classifyRetention(String eventType) {
        if (eventType == null) return RetentionClass.OPERATIONAL_13M;
        if (eventType.startsWith("claim.") || eventType.startsWith("payout.") || eventType.startsWith("policy.")) {
            return RetentionClass.REGULATED_7Y;
        }
        if (eventType.startsWith("iot.") || eventType.startsWith("trigger.")) {
            return RetentionClass.EVIDENCE_7Y;
        }
        if (eventType.startsWith("auth.") || eventType.contains("access")) {
            return RetentionClass.ACCESS_3Y;
        }
        return RetentionClass.OPERATIONAL_13M;
    }

    private void appendTelemetryLeaf(Map<String, Object> payload) {
        String deviceId = String.valueOf(payload.getOrDefault("deviceId", "dev_default"));
        Instant now = Instant.now();
        TelemetryBatchEntity batch = telemetryBatchRepository
                .findFirstByDeviceIdAndSealedFalseOrderByCreatedAtDesc(deviceId)
                .orElseGet(() -> TelemetryBatchEntity.builder()
                        .id(UUID.randomUUID())
                        .batchId(support.idPrefix("tbat_"))
                        .deviceId(deviceId)
                        .windowStart(now)
                        .windowEnd(now.plus(1, ChronoUnit.HOURS))
                        .readingCount(0)
                        .leavesJson("[]")
                        .sealed(false)
                        .createdAt(now)
                        .build());
        List<String> leaves = support.fromJsonListString(batch.getLeavesJson());
        leaves.add(AuditExternalClients.sha256(support.toJson(payload)));
        batch.setLeavesJson(support.toJson(leaves));
        batch.setReadingCount(leaves.size());
        batch.setMerkleRoot(support.computeMerkleRoot(leaves));
        telemetryBatchRepository.save(batch);
    }

    private void evaluateControlRules(AuditRecordEntity record, Map<String, Object> payload, boolean piiDetected) {
        if (piiDetected) {
            raiseFinding("PERSONAL_DATA_IN_EVENT", FindingSeverity.HIGH, record,
                    "Personal data detected in domain event from " + record.getProducerService());
        }
        if ("payout.completed".equals(record.getEventType()) && payload.get("approvedBy") == null) {
            raiseFinding("PAYOUT_WITHOUT_APPROVAL", FindingSeverity.CRITICAL, record,
                    "Payout completed without recorded approver");
        }
        if ("iot.calibration.completed".equals(record.getEventType()) && payload.get("approvedBy") == null) {
            raiseFinding("CALIBRATION_NO_APPROVER", FindingSeverity.HIGH, record,
                    "Sensor calibration completed without independent verifier");
        }
        if ("notification.template.approved".equals(record.getEventType())
                && Objects.equals(payload.get("approvedBy"), payload.get("lastEditedBy"))) {
            raiseFinding("TEMPLATE_SELF_APPROVED", FindingSeverity.CRITICAL, record,
                    "Template approved by same actor who edited it");
        }
    }

    public AuditFindingEntity raiseFinding(String ruleCode, FindingSeverity severity, AuditRecordEntity record, String summary) {
        Instant now = Instant.now();
        AuditFindingEntity finding = findingRepository.save(AuditFindingEntity.builder()
                .id(UUID.randomUUID())
                .findingId(support.idPrefix("fnd_"))
                .ruleCode(ruleCode)
                .severity(severity)
                .status(FindingStatus.OPEN)
                .subjectType(record.getSubjectType())
                .subjectId(record.getSubjectId())
                .claimId(record.getClaimId())
                .producerService(record.getProducerService())
                .summary(summary)
                .detailsJson(support.toJson(Map.of("recordId", record.getRecordId(), "eventType", record.getEventType())))
                .historyJson(support.toJson(List.of(Map.of("status", "OPEN", "at", now.toString()))))
                .createdAt(now)
                .updatedAt(now)
                .build());
        support.emitOutbox("audit.finding.raised", finding.getFindingId(),
                Map.of("findingId", finding.getFindingId(), "ruleCode", ruleCode, "severity", severity.name()));
        return finding;
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public int runAuditKeeper() {
        int actions = 0;
        for (TelemetryBatchEntity b : telemetryBatchRepository.findBySealedFalse()) {
            b.setSealed(true);
            telemetryBatchRepository.save(b);
            actions++;
        }
        for (String chainId : List.of("REG7", "EVD7", "OPS13M", "ACC3Y")) {
            Optional<AuditRecordEntity> tailOpt = recordRepository.findFirstByChainIdOrderBySeqDesc(chainId);
            if (tailOpt.isEmpty()) continue;
            AuditRecordEntity tail = tailOpt.get();
            Optional<AuditCheckpointEntity> lastCp = checkpointRepository.findFirstByChainIdOrderByToSeqDesc(chainId);
            long cpTo = lastCp.map(AuditCheckpointEntity::getToSeq).orElse(0L);
            if (tail.getSeq() > cpTo) {
                List<AuditRecordEntity> slice = recordRepository.findByChainIdAndSeqBetweenOrderBySeqAsc(chainId, cpTo + 1, tail.getSeq());
                String merkleRoot = support.computeMerkleRoot(slice.stream().map(AuditRecordEntity::getRecordHash).toList());
                String sig = clients.signDigest(properties.getRules().getSigningSecret(), merkleRoot + "|" + tail.getRecordHash());
                AuditCheckpointEntity cp = checkpointRepository.save(AuditCheckpointEntity.builder()
                        .id(UUID.randomUUID())
                        .checkpointId(support.idPrefix("chk_"))
                        .chainId(chainId)
                        .fromSeq(cpTo + 1)
                        .toSeq(tail.getSeq())
                        .merkleRoot(merkleRoot)
                        .headHash(tail.getRecordHash())
                        .signature(sig)
                        .keyId(properties.getRules().getSigningKeyId())
                        .signedAt(Instant.now())
                        .build());
                clients.writeCheckpointToWorm(chainId, tail.getRecordHash());
                for (AuditRecordEntity r : slice) {
                    r.setCheckpointId(cp.getCheckpointId());
                    recordRepository.save(r);
                }
                actions++;
            } else if (lastCp.isPresent()) {
                Optional<String> wormHead = clients.readWormHeadHash(chainId);
                if (wormHead.isPresent() && !wormHead.get().equals(lastCp.get().getHeadHash())) {
                    raiseFinding("HASH_CHAIN_MISMATCH", FindingSeverity.CRITICAL, tail,
                            "WORM head hash mismatch detected on chain " + chainId);
                    support.emitOutbox("audit.integrity.alert", chainId,
                            Map.of("chainId", chainId, "reason", "WORM_HEAD_MISMATCH"));
                }
            }
        }
        return actions;
    }

    private String strOrNull(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
