package com.umar.service;

import com.umar.audit.AuditChainVerifier;
import com.umar.audit.AuditHashService;
import com.umar.audit.AuditRecorder;
import com.umar.authentication.VaultSecurityContext;
import com.umar.authentication.WorkloadIdentity;
import com.umar.entity.auditAndAccess.AccessRecord;
import com.umar.entity.auditAndAccess.DayHash;
import com.umar.exception.VaultApiException;
import com.umar.payload.request.vault.StoreDocumentRequest;
import com.umar.payload.response.vault.AccessLogQueryResponse;
import com.umar.payload.response.vault.AccessRecordResponse;
import com.umar.payload.response.vault.DocumentResponse;
import com.umar.repository.auditAndAccess.AccessRecordRepository;
import com.umar.repository.auditAndAccess.DayHashRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * VLT-09 — Query Access Log (RECORDS, SUMMARY, SUBJECT_SUMMARY, INTEGRITY, CSV_SIGNED).
 */
@Service
@RequiredArgsConstructor
public class AccessLogService {

    private final AccessRecordRepository accessRecordRepository;
    private final DayHashRepository dayHashRepository;
    private final AuditChainVerifier auditChainVerifier;
    private final AuditHashService auditHashService;
    private final AuditRecorder auditRecorder;
    private final AccessSignalEmitter accessSignalEmitter;
    private final DocumentService documentService;

    @Transactional(readOnly = true)
    public List<AccessRecordResponse> getAccessLog() {
        return accessRecordRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AccessLogQueryResponse queryAccessLog(
            String view,
            String from,
            String to,
            String caller,
            String operation,
            String dataClass,
            String purpose,
            String contextRef,
            String subjectRef,
            String outcome,
            String format,
            int page,
            int size) {

        String normalizedView = (view == null || view.isBlank()) ? "RECORDS" : view.trim().toUpperCase();
        WorkloadIdentity identity = VaultSecurityContext.getWorkloadIdentity();
        boolean isAdminOnly = identity != null && identity.roles() != null
                && identity.roles().contains("ADMINISTRATOR")
                && !identity.roles().contains("SECURITY_OFFICER")
                && !identity.roles().contains("COMPLIANCE_OFFICER")
                && !identity.roles().contains("AUDITOR");

        if (isAdminOnly && !"SUMMARY".equals(normalizedView)) {
            throw VaultApiException.forbidden("FORBIDDEN", "Administrators may only view operational summaries");
        }

        Instant fromInstant = parseStart(from);
        Instant toInstant = parseEnd(to);

        if ("RECORDS".equals(normalizedView) && fromInstant != null && toInstant != null) {
            long days = ChronoUnit.DAYS.between(fromInstant, toInstant);
            if (days > 93) {
                throw VaultApiException.unprocessable("RANGE_TOO_LARGE", "Choose a shorter period");
            }
        }

        // Every query of the access log is itself recorded as an access (section 2.10, 4.9)
        String requestId = "vq_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        auditRecorder.recordStrict("VAULT_ACCESS_LOG_QUERIED", "QUERY_ACCESS_LOG:" + normalizedView,
                dataClass, null, "COMPLIANCE_QUERY", null, subjectRef, 1, "SUCCESS", null, null);
        accessSignalEmitter.emit("QUERY_ACCESS_LOG", dataClass, normalizedView, "SUCCESS", 1);

        if ("INTEGRITY".equals(normalizedView)) {
            AuditChainVerifier.VerificationResult journalResult = auditChainVerifier.verify();
            List<DayHash> days = dayHashRepository.findAllByOrderByDateAsc();
            String firstBrokenDay = null;
            boolean intact = journalResult.valid();
            String prev = "0000000000000000000000000000000000000000000000000000000000000000";
            for (DayHash dh : days) {
                if (!dh.getPreviousHash().equals(prev)) {
                    intact = false;
                    firstBrokenDay = dh.getDate();
                    break;
                }
                prev = dh.getHash();
            }
            if (!intact) {
                throw VaultApiException.conflict("INTEGRITY_BREACH",
                        "The access log integrity check failed",
                        Map.of("firstBrokenDay", firstBrokenDay != null ? firstBrokenDay : "journal"));
            }
            return AccessLogQueryResponse.builder()
                    .integrity(Map.of(
                            "daysChecked", days.size(),
                            "recordsChecked", journalResult.recordsChecked(),
                            "intact", true))
                    .requestId(requestId)
                    .build();
        }

        String contextHashFilter = AuditRecorder.hashIfPresent(contextRef);
        String subjectHashFilter = AuditRecorder.hashIfPresent(subjectRef);

        List<AccessRecord> filtered = accessRecordRepository.findAllByOrderByTimestampAsc().stream()
                .filter(r -> fromInstant == null || parseTimestamp(r.getTimestamp()).compareTo(fromInstant) >= 0)
                .filter(r -> toInstant == null || parseTimestamp(r.getTimestamp()).compareTo(toInstant) <= 0)
                .filter(r -> caller == null || caller.isBlank() || caller.equalsIgnoreCase(r.getCaller()))
                .filter(r -> operation == null || operation.isBlank()
                        || (r.getOperation() != null && r.getOperation().toUpperCase().contains(operation.trim().toUpperCase())))
                .filter(r -> dataClass == null || dataClass.isBlank() || dataClass.equalsIgnoreCase(r.getDataClass()))
                .filter(r -> purpose == null || purpose.isBlank() || purpose.equalsIgnoreCase(r.getPurpose()))
                .filter(r -> contextHashFilter == null || contextHashFilter.equals(r.getContextRefHash()))
                .filter(r -> subjectHashFilter == null || subjectHashFilter.equals(r.getSubjectHash()))
                .filter(r -> outcome == null || outcome.isBlank() || outcome.equalsIgnoreCase(r.getOutcome()))
                .toList();

        if ("SUMMARY".equals(normalizedView)) {
            Map<String, Long> byPurpose = new LinkedHashMap<>();
            Map<String, Long> byCaller = new LinkedHashMap<>();
            Map<String, Long> byOperation = new LinkedHashMap<>();
            long denied = 0;
            long throttled = 0;
            for (AccessRecord r : filtered) {
                long c = parseCount(r.getCount());
                if (r.getPurpose() != null) byPurpose.merge(r.getPurpose(), c, Long::sum);
                if (r.getCaller() != null) byCaller.merge(r.getCaller(), c, Long::sum);
                if (r.getOperation() != null) byOperation.merge(r.getOperation(), c, Long::sum);
                if ("DENIED".equalsIgnoreCase(r.getOutcome())) denied += c;
                if ("THROTTLED".equalsIgnoreCase(r.getOutcome())) throttled += c;
            }
            return AccessLogQueryResponse.builder()
                    .summary(Map.of(
                            "total", filtered.stream().mapToLong(r -> parseCount(r.getCount())).sum(),
                            "byPurpose", byPurpose,
                            "byCaller", byCaller,
                            "byOperation", byOperation,
                            "denied", denied,
                            "throttled", throttled))
                    .integrity(Map.of("daysChecked", dayHashRepository.count(), "intact", true))
                    .requestId(requestId)
                    .build();
        }

        if ("SUBJECT_SUMMARY".equals(normalizedView)) {
            if (subjectRef == null || subjectRef.isBlank()) {
                throw VaultApiException.badRequest("VALIDATION_ERROR", "subjectRef is required for SUBJECT_SUMMARY");
            }
            Map<String, Long> byPurpose = new LinkedHashMap<>();
            for (AccessRecord r : filtered) {
                long c = parseCount(r.getCount());
                String p = r.getPurpose() != null ? r.getPurpose() : "GENERAL";
                byPurpose.merge(p, c, Long::sum);
            }
            return AccessLogQueryResponse.builder()
                    .subjectSummary(Map.of(
                            "totalAccesses", filtered.size(),
                            "byPurpose", byPurpose))
                    .requestId(requestId)
                    .build();
        }

        // RECORDS (with optional CSV_SIGNED export)
        int safeSize = Math.min(1000, Math.max(1, size));
        int safePage = Math.max(0, page);
        int fromIdx = Math.min(safePage * safeSize, filtered.size());
        int toIdx = Math.min(fromIdx + safeSize, filtered.size());
        List<AccessRecordResponse> pageItems = filtered.subList(fromIdx, toIdx).stream()
                .map(this::toResponse)
                .toList();

        Map<String, Object> exportMap = null;
        if ("CSV_SIGNED".equalsIgnoreCase(format)) {
            exportMap = buildSignedCsvExport(filtered);
        }

        return AccessLogQueryResponse.builder()
                .records(pageItems)
                .export(exportMap)
                .page(safePage)
                .size(safeSize)
                .totalElements(filtered.size())
                .requestId(requestId)
                .build();
    }

    private Map<String, Object> buildSignedCsvExport(List<AccessRecord> records) {
        StringBuilder csv = new StringBuilder("recordId,timestamp,caller,operation,dataClass,purpose,outcome,hash\n");
        for (AccessRecord r : records) {
            csv.append(r.getId()).append(',')
                    .append(safe(r.getTimestamp())).append(',')
                    .append(safe(r.getCaller())).append(',')
                    .append(safe(r.getOperation())).append(',')
                    .append(safe(r.getDataClass())).append(',')
                    .append(safe(r.getPurpose())).append(',')
                    .append(safe(r.getOutcome())).append(',')
                    .append(safe(r.getHash())).append('\n');
        }
        String content = csv.toString();
        String sig = auditHashService.sha256Hex(content.getBytes(StandardCharsets.UTF_8));
        DocumentResponse doc = documentService.storeDocument(StoreDocumentRequest.builder()
                .dataClass("RECONCILIATION_EXPORT")
                .purpose("GENERAL")
                .subjectRef("access-log-export")
                .contentType("text/csv")
                .content(content)
                .build());
        return Map.of(
                "documentToken", doc.getToken(),
                "signature", sig,
                "expiresAt", Instant.now().plus(1, ChronoUnit.HOURS).toString());
    }

    private String safe(String s) {
        return s == null ? "" : s.replace(",", " ");
    }

    private long parseCount(String count) {
        try {
            return count == null ? 1L : Long.parseLong(count);
        } catch (Exception e) {
            return 1L;
        }
    }

    private Instant parseStart(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            return LocalDate.parse(s).atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
        }
    }

    private Instant parseEnd(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            return LocalDate.parse(s).plusDays(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC).minusMillis(1);
        }
    }

    private Instant parseTimestamp(String s) {
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            return Instant.EPOCH;
        }
    }

    private AccessRecordResponse toResponse(AccessRecord record) {
        return AccessRecordResponse.builder()
                .id(record.getId())
                .recordId(record.getId())
                .caller(record.getCaller())
                .operation(record.getOperation())
                .dataClass(record.getDataClass())
                .secretRef(record.getSecretRef())
                .purpose(record.getPurpose())
                .contextRefHash(record.getContextRefHash())
                .subjectHash(record.getSubjectHash())
                .count(record.getCount())
                .outcome(record.getOutcome())
                .reasonCode(record.getReasonCode())
                .timestamp(record.getTimestamp())
                .build();
    }
}
