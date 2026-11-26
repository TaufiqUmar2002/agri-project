package com.umar.audit.service.support;

import com.umar.audit.client.AuditExternalClients;
import com.umar.audit.config.AuditProperties;
import com.umar.audit.domain.*;
import com.umar.audit.repository.*;
import com.umar.enums.audit.RetentionClass;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AuditSupportService {

    private final ObjectMapper objectMapper;
    private final OutboxEventRepository outboxRepository;
    private final AuditRecordRepository recordRepository;
    private final AuditSubjectEdgeRepository edgeRepository;
    private final ModelRegisterRepository modelRegisterRepository;
    private final AuditExternalClients clients;
    private final AuditProperties properties;

    @PostConstruct
    public void seedModels() {
        if (modelRegisterRepository.count() == 0) {
            Instant eff = Instant.now().minus(30, ChronoUnit.DAYS);
            modelRegisterRepository.save(ModelRegisterEntity.builder()
                    .id(UUID.randomUUID())
                    .modelId("drought-index-v2")
                    .version("2.1.0")
                    .component("trigger-engine")
                    .approvalStatus("COMPLETE")
                    .ruleHash(AuditExternalClients.sha256("drought-index-v2:2.1.0"))
                    .approvedBy("actuary_lead_1")
                    .governanceNotes("Calibrated on 15y CHIRPS + soil moisture baseline.")
                    .effectiveFrom(eff)
                    .createdAt(eff)
                    .build());
            modelRegisterRepository.save(ModelRegisterEntity.builder()
                    .id(UUID.randomUUID())
                    .modelId("excess-rain-v1")
                    .version("1.4.0")
                    .component("trigger-engine")
                    .approvalStatus("INCOMPLETE")
                    .ruleHash(AuditExternalClients.sha256("excess-rain-v1:1.4.0"))
                    .approvedBy(null)
                    .governanceNotes("Pending actuarial sign-off before activation.")
                    .effectiveFrom(eff)
                    .createdAt(eff)
                    .build());
        }
    }

    public String chainIdFor(RetentionClass rc) {
        return switch (rc) {
            case REGULATED_7Y -> "REG7";
            case EVIDENCE_7Y -> "EVD7";
            case OPERATIONAL_13M -> "OPS13M";
            case ACCESS_3Y -> "ACC3Y";
        };
    }

    public String computeRecordHash(String chainId, long seq, String prevHash, String payloadJson) {
        return AuditExternalClients.sha256(chainId + "|" + seq + "|" + prevHash + "|" + payloadJson);
    }

    public AuditRecordEntity appendAccessRecord(String actorId, String actionType, String targetRef) {
        RetentionClass rc = RetentionClass.ACCESS_3Y;
        String chainId = chainIdFor(rc);
        Optional<AuditRecordEntity> tail = recordRepository.findFirstByChainIdOrderBySeqDesc(chainId);
        long nextSeq = tail.map(r -> r.getSeq() + 1).orElse(1L);
        String prevHash = tail.map(AuditRecordEntity::getRecordHash).orElse("GENESIS_" + chainId);
        String payloadJson = toJson(Map.of("action", actionType, "targetRef", targetRef != null ? targetRef : "", "actorId", actorId));
        String recordHash = computeRecordHash(chainId, nextSeq, prevHash, payloadJson);
        Instant now = Instant.now();
        return recordRepository.save(AuditRecordEntity.builder()
                .id(UUID.randomUUID())
                .recordId(idPrefix("aud_"))
                .chainId(chainId)
                .seq(nextSeq)
                .retentionClass(rc)
                .eventId(idPrefix("acc_"))
                .eventType("audit.access." + actionType.toLowerCase(Locale.ROOT))
                .producerService("audit-service")
                .occurredAt(now)
                .ingestedAt(now)
                .subjectType("ACCESS")
                .subjectId(targetRef)
                .actorId(actorId)
                .payloadJson(payloadJson)
                .flagsJson("[]")
                .redacted(false)
                .prevHash(prevHash)
                .recordHash(recordHash)
                .build());
    }

    public void addSubjectEdge(String subjectType, String subjectId, String linkedType, String linkedId,
                               String recordId, String relation) {
        if (subjectId == null || linkedId == null) return;
        edgeRepository.save(AuditSubjectEdgeEntity.builder()
                .id(UUID.randomUUID())
                .subjectType(subjectType)
                .subjectId(subjectId)
                .linkedType(linkedType)
                .linkedId(linkedId)
                .recordId(recordId)
                .relationType(relation)
                .createdAt(Instant.now())
                .build());
    }

    public String computeMerkleRoot(List<String> leaves) {
        if (leaves == null || leaves.isEmpty()) {
            return AuditExternalClients.sha256("EMPTY_TREE");
        }
        List<String> layer = new ArrayList<>(leaves);
        while (layer.size() > 1) {
            List<String> next = new ArrayList<>();
            for (int i = 0; i < layer.size(); i += 2) {
                String left = layer.get(i);
                String right = (i + 1 < layer.size()) ? layer.get(i + 1) : left;
                next.add(AuditExternalClients.sha256(left + "|" + right));
            }
            layer = next;
        }
        return layer.getFirst();
    }

    public void emitOutbox(String eventType, String aggregateId, Object payloadObj) {
        outboxRepository.save(OutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .eventType(eventType)
                .aggregateId(aggregateId)
                .topic(properties.getKafka().getAuditTopic())
                .payload(toJson(payloadObj))
                .published(false)
                .createdAt(Instant.now())
                .build());
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public Map<String, Object> fromJsonMap(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    public List<String> fromJsonListString(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public List<Map<String, Object>> fromJsonListMap(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public String idPrefix(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
