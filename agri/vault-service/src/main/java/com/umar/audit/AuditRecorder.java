package com.umar.audit;

import com.umar.authentication.VaultSecurityContext;
import com.umar.authentication.WorkloadIdentity;
import com.umar.entity.auditAndAccess.AccessRecord;
import com.umar.entity.auditAndAccess.DayHash;
import com.umar.repository.auditAndAccess.AccessRecordRepository;
import com.umar.repository.auditAndAccess.DayHashRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Builds and records {@link AuditEvent}s into BOTH the durable hash-chained audit
 * journal ({@link AuditService} + WORM sink) and the queryable {@link AccessRecord}
 * + {@link DayHash} chain (sections 2.10, 4.9).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditRecorder {

    private final AuditService auditService;
    private final AccessRecordRepository accessRecordRepository;
    private final DayHashRepository dayHashRepository;
    private final AuditHashService auditHashService;

    /** Best-effort audit record; never throws. */
    public void record(String eventType, String operation, String dataClass,
                       String purpose, String outcome, UUID resourceId, String errorCode) {
        record(eventType, operation, dataClass, null, purpose, null, null, 1, outcome, resourceId, errorCode);
    }

    public void record(String eventType, String operation, String dataClass, String secretRef,
                       String purpose, String contextRef, String subjectRef, int count,
                       String outcome, UUID resourceId, String errorCode) {
        try {
            recordInternal(eventType, operation, dataClass, secretRef, purpose,
                    contextRef, subjectRef, count, outcome, resourceId, errorCode);
        } catch (Exception e) {
            log.error("Audit recording failed for operation {}: {}", operation, e.getMessage());
        }
    }

    /** Strict audit record; throws {@link AuditException} on failure. */
    public void recordStrict(String eventType, String operation, String dataClass,
                             String purpose, String outcome, UUID resourceId, String errorCode) {
        recordStrict(eventType, operation, dataClass, null, purpose, null, null, 1, outcome, resourceId, errorCode);
    }

    public void recordStrict(String eventType, String operation, String dataClass, String secretRef,
                             String purpose, String contextRef, String subjectRef, int count,
                             String outcome, UUID resourceId, String errorCode) {
        recordInternal(eventType, operation, dataClass, secretRef, purpose,
                contextRef, subjectRef, count, outcome, resourceId, errorCode);
    }

    private void recordInternal(String eventType, String operation, String dataClass, String secretRef,
                                String purpose, String contextRef, String subjectRef, int count,
                                String outcome, UUID resourceId, String errorCode) {
        Instant now = Instant.now();
        WorkloadIdentity identity = VaultSecurityContext.getWorkloadIdentity();
        String caller = identity != null ? identity.serviceName() : "UNKNOWN";
        String callerIdentity = identity != null ? identity.serviceName() + "/" + identity.environment() : "UNKNOWN";
        String contextHash = hashIfPresent(contextRef);
        String subjectHash = hashIfPresent(subjectRef);
        UUID recordId = UUID.randomUUID();

        String auditOutcome = (outcome != null && outcome.matches("SUCCESS|DENIED|FAILED")) ? outcome : "SUCCESS";
        AuditEvent event = new AuditEvent(
                recordId,
                eventType,
                operation,
                caller,
                callerIdentity,
                purpose != null ? purpose : "GENERAL",
                dataClass,
                secretRef,
                null,
                resourceId,
                contextHash,
                auditOutcome,
                errorCode,
                now);

        AuditReceipt receipt = auditService.recordAndRequireDurable(event);

        // Also persist queryable AccessRecord & update daily hash chain for VLT-09.
        persistAccessRecord(recordId, caller, operation, dataClass, secretRef,
                purpose != null ? purpose : "GENERAL", contextHash, subjectHash,
                Math.max(1, count), auditOutcome, errorCode, now, receipt.contentSha256());
    }

    private synchronized void persistAccessRecord(UUID recordId, String caller, String operation,
                                                  String dataClass, String secretRef, String purpose,
                                                  String contextHash, String subjectHash, int count,
                                                  String outcome, String reasonCode, Instant now,
                                                  String fallbackHash) {
        String prevHash = accessRecordRepository.findFirstByOrderByTimestampDesc()
                .map(AccessRecord::getHash)
                .orElse("0000000000000000000000000000000000000000000000000000000000000000");
        String canonical = recordId + "|" + caller + "|" + operation + "|" + dataClass + "|"
                + secretRef + "|" + purpose + "|" + contextHash + "|" + subjectHash + "|"
                + count + "|" + outcome + "|" + now.toString();
        String recordHash = auditHashService.hash(prevHash, canonical);
        if (recordHash == null) {
            recordHash = fallbackHash;
        }
        String dayDate = LocalDate.ofInstant(now, ZoneOffset.UTC).toString();

        AccessRecord ar = new AccessRecord();
        ar.setId(recordId);
        ar.setCaller(caller);
        ar.setOperation(operation);
        ar.setDataClass(dataClass);
        ar.setSecretRef(secretRef);
        ar.setPurpose(purpose);
        ar.setContextRefHash(contextHash);
        ar.setSubjectHash(subjectHash);
        ar.setCount(String.valueOf(count));
        ar.setOutcome(outcome);
        ar.setReasonCode(reasonCode);
        ar.setPreviousHash(prevHash);
        ar.setHash(recordHash);
        ar.setTimestamp(now.toString());
        ar.setDayDate(dayDate);
        accessRecordRepository.save(ar);

        updateDayHash(dayDate, recordHash);
    }

    private void updateDayHash(String dayDate, String latestRecordHash) {
        DayHash dayHash = dayHashRepository.findByDate(dayDate).orElseGet(() -> {
            String prevDayHash = dayHashRepository.findFirstByOrderByDateDesc()
                    .map(DayHash::getHash)
                    .orElse("0000000000000000000000000000000000000000000000000000000000000000");
            DayHash dh = new DayHash();
            dh.setId(UUID.randomUUID());
            dh.setDate(dayDate);
            dh.setPreviousHash(prevDayHash);
            dh.setHash(prevDayHash);
            return dh;
        });
        String prev = dayHash.getPreviousHash();
        dayHash.setHash(auditHashService.hash(prev, dayDate + ":" + latestRecordHash));
        dayHashRepository.save(dayHash);
    }

    public static String hashIfPresent(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
