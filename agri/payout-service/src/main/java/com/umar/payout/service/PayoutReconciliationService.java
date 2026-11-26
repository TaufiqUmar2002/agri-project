package com.umar.payout.service;

import com.umar.enums.auth.AuthRole;
import com.umar.enums.payout.PayoutStatus;
import com.umar.enums.payout.ProviderStatus;
import com.umar.payload.request.payout.ImportPayoutStatementRequest;
import com.umar.payload.request.payout.UpdateProviderStatusRequest;
import com.umar.payload.response.payout.PayoutSettlementResponse;
import com.umar.payload.response.payout.PayoutStatementImportResponse;
import com.umar.payload.response.payout.ProviderStatusResponse;
import com.umar.payout.client.PayoutExternalClients;
import com.umar.payout.config.PayoutProperties;
import com.umar.payout.domain.PayoutInstructionEntity;
import com.umar.payout.domain.PayoutSettlementBatchEntity;
import com.umar.payout.domain.PayoutStatementImportEntity;
import com.umar.payout.domain.ProviderConnectionEntity;
import com.umar.payout.exception.PayoutApiException;
import com.umar.payout.repository.PayoutInstructionRepository;
import com.umar.payout.repository.PayoutSettlementBatchRepository;
import com.umar.payout.repository.PayoutStatementImportRepository;
import com.umar.payout.repository.ProviderConnectionRepository;
import com.umar.payout.security.PayoutIdentity;
import com.umar.payout.security.PayoutIdentityFilter;
import com.umar.payout.service.support.PayoutOutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PayoutReconciliationService {

    private final PayoutInstructionRepository payoutRepository;
    private final PayoutStatementImportRepository importRepository;
    private final PayoutSettlementBatchRepository settlementRepository;
    private final ProviderConnectionRepository connectionRepository;
    private final PayoutExternalClients clients;
    private final PayoutOutboxService outboxService;
    private final PayoutProperties properties;

    @Transactional
    public PayoutStatementImportResponse importStatement(ImportPayoutStatementRequest req) {
        PayoutIdentity actor = requireIdentity();
        if (!actor.isServiceCaller() && !actor.hasAnyRole(AuthRole.FINANCE, AuthRole.ADMIN)) {
            throw new PayoutApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        List<PayoutExternalClients.StatementLine> lines = clients.getStatementLines(req.getFileToken());
        if (lines == null) {
            throw new PayoutApiException(HttpStatus.UNPROCESSABLE_ENTITY, "STATEMENT_INCOMPLETE",
                    "Statement file could not be loaded or is missing trailer");
        }
        Optional<PayoutStatementImportEntity> existing = importRepository.findByConnectionAndStatementDate(
                req.getConnection(), req.getStatementDate());
        if (existing.isPresent()) {
            if (existing.get().getChecksum().equals(req.getChecksum()) && !req.isReplace()) {
                PayoutStatementImportEntity ex = existing.get();
                return PayoutStatementImportResponse.builder()
                        .importId(ex.getImportId()).connection(ex.getConnection())
                        .statementDate(ex.getStatementDate().toString())
                        .lines(Map.of("total", ex.getTotalLines(), "matched", ex.getMatchedLines(), "unmatched", ex.getUnmatchedLines()))
                        .amounts(Map.of("totalMinor", ex.getTotalMinor(), "feesMinor", ex.getFeesMinor()))
                        .breaks(Map.of("count", ex.getBreaksCount()))
                        .importedAt(ex.getImportedAt().toString()).build();
            }
            if (!req.isReplace()) {
                throw new PayoutApiException(HttpStatus.CONFLICT, "STATEMENT_CONFLICT",
                        "Different statement already imported for date; pass replace=true");
            }
            importRepository.delete(existing.get());
        }

        int matched = 0;
        int unmatched = 0;
        long totalMinor = 0;
        long feesMinor = 0;
        Instant now = Instant.now();

        for (PayoutExternalClients.StatementLine l : lines) {
            totalMinor += l.amountMinor();
            feesMinor += l.feeMinor();
            Optional<PayoutInstructionEntity> opt = payoutRepository.findByProviderRef(l.providerRef());
            if (opt.isPresent() && opt.get().getAmountMinor() == l.amountMinor()) {
                PayoutInstructionEntity p = opt.get();
                p.setStatementMatchStatus("MATCHED");
                p.setStatementDate(req.getStatementDate());
                payoutRepository.save(p);
                matched++;
                outboxService.enqueue(properties.getKafka().getPayoutTopic(), "payout.statement.matched", p.getClaimId(), Map.of(
                        "eventType", "payout.statement.matched", "payoutId", p.getPayoutId(), "claimId", p.getClaimId(),
                        "providerRef", l.providerRef(), "statementDate", req.getStatementDate().toString()));
            } else {
                unmatched++;
            }
        }

        String importId = "imp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        importRepository.save(PayoutStatementImportEntity.builder()
                .importId(importId).connection(req.getConnection()).statementDate(req.getStatementDate())
                .checksum(req.getChecksum()).totalLines(lines.size()).matchedLines(matched).unmatchedLines(unmatched)
                .totalMinor(totalMinor).feesMinor(feesMinor).breaksCount(unmatched).importedAt(now).build());

        settlementRepository.save(PayoutSettlementBatchEntity.builder()
                .batchId("stl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                .connection(req.getConnection()).settlementDate(req.getStatementDate())
                .currency(lines.isEmpty() ? "KES" : lines.getFirst().currency())
                .grossMinor(totalMinor).feesMinor(feesMinor).netMinor(totalMinor - feesMinor)
                .status(req.getStatementDate().isBefore(LocalDate.now()) ? "CLOSED" : "OPEN")
                .matchedCount(matched).unmatchedCount(unmatched)
                .checksum(req.getChecksum()).createdAt(now).build());

        outboxService.enqueue(properties.getKafka().getPayoutTopic(), "payout.statement.imported", importId, Map.of(
                "eventType", "payout.statement.imported", "importId", importId, "connection", req.getConnection(),
                "statementDate", req.getStatementDate().toString(), "matched", matched, "unmatched", unmatched));

        return PayoutStatementImportResponse.builder()
                .importId(importId).connection(req.getConnection())
                .statementDate(req.getStatementDate().toString())
                .lines(Map.of("total", lines.size(), "matched", matched, "unmatched", unmatched))
                .amounts(Map.of("totalMinor", totalMinor, "feesMinor", feesMinor))
                .breaks(Map.of("count", unmatched))
                .importedAt(now.toString()).build();
    }

    public PayoutSettlementResponse getSettlements(LocalDate from, LocalDate to, String format) {
        PayoutIdentity actor = requireIdentity();
        if (!actor.hasAnyRole(AuthRole.FINANCE, AuthRole.COMPLIANCE_OFFICER, AuthRole.AUDITOR, AuthRole.ADMIN)) {
            throw new PayoutApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        List<PayoutSettlementBatchEntity> batches = settlementRepository.findBySettlementDateBetween(from, to);
        boolean anyOpen = batches.stream().anyMatch(b -> "OPEN".equals(b.getStatus()));
        if ("CSV".equalsIgnoreCase(format) && anyOpen) {
            throw new PayoutApiException(HttpStatus.CONFLICT, "SETTLEMENT_NOT_CLOSED",
                    "Cannot export settlement while day is still OPEN");
        }
        long gross = batches.stream().mapToLong(PayoutSettlementBatchEntity::getGrossMinor).sum();
        long fees = batches.stream().mapToLong(PayoutSettlementBatchEntity::getFeesMinor).sum();
        int m = batches.stream().mapToInt(PayoutSettlementBatchEntity::getMatchedCount).sum();
        int u = batches.stream().mapToInt(PayoutSettlementBatchEntity::getUnmatchedCount).sum();
        String checksum = clients.sha256(from + "|" + to + "|" + gross + "|" + fees);

        List<Map<String, Object>> batchList = batches.stream()
                .map(b -> Map.<String, Object>of("batchId", b.getBatchId(), "connection", b.getConnection(),
                        "date", b.getSettlementDate().toString(), "grossMinor", b.getGrossMinor(),
                        "feesMinor", b.getFeesMinor(), "status", b.getStatus()))
                .toList();

        return PayoutSettlementResponse.builder()
                .from(from.toString()).to(to.toString()).status(anyOpen ? "OPEN" : "CLOSED")
                .batches(batchList)
                .totals(Map.of("grossMinor", gross, "feesMinor", fees, "netMinor", gross - fees))
                .matchSummary(Map.of("matched", m, "unmatched", u))
                .checksum(checksum)
                .exportToken("CSV".equalsIgnoreCase(format) ? "vt_stl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) : null)
                .build();
    }

    @Transactional
    public ProviderStatusResponse updateProviderStatus(String provider, UpdateProviderStatusRequest req) {
        PayoutIdentity actor = requireIdentity();
        if (!actor.hasAnyRole(AuthRole.FINANCE, AuthRole.ADMIN)) {
            throw new PayoutApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        List<ProviderConnectionEntity> conns = connectionRepository.findByProviderIgnoreCase(provider);
        if (conns.isEmpty()) {
            throw new PayoutApiException(HttpStatus.NOT_FOUND, "PROVIDER_NOT_FOUND", "Unknown provider " + provider);
        }
        ProviderConnectionEntity conn = conns.getFirst();
        if (req.getVersion() != null && !Objects.equals(req.getVersion(), conn.getVersionNum())) {
            throw new PayoutApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "Provider version mismatch");
        }
        if (req.getStatus() == ProviderStatus.ENABLED && !clients.isProbePass()) {
            throw new PayoutApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PROBE_FAILED",
                    "Live provider health probe failed; cannot enable");
        }
        String prev = conn.getStatus().name();
        Instant now = Instant.now();
        conn.setStatus(req.getStatus());
        conn.setLastProbeAt(now);
        conn.setLastProbeOk(clients.isProbePass());
        conn.setUpdatedAt(now);
        conn.setVersionNum(conn.getVersionNum() + 1);
        connectionRepository.save(conn);

        int waitingCount = payoutRepository.findByConnectionAndStatus(conn.getConnectionId(), PayoutStatus.WAITING_PROVIDER).size();
        outboxService.enqueue(properties.getKafka().getPayoutTopic(), "payout.provider.status.changed", provider, Map.of(
                "eventType", "payout.provider.status.changed", "provider", provider,
                "status", req.getStatus().name(), "previousStatus", prev, "reason", req.getReason(),
                "timestamp", now.toString()));

        return ProviderStatusResponse.builder()
                .provider(provider).status(req.getStatus().name()).previousStatus(prev)
                .probe(Map.of("passed", clients.isProbePass(), "at", now.toString()))
                .queuedAffectedCount(waitingCount).updatedAt(now.toString()).version(conn.getVersionNum()).build();
    }

    private PayoutIdentity requireIdentity() {
        PayoutIdentity id = PayoutIdentityFilter.CTX.get();
        if (id == null) throw new PayoutApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Sign in required");
        return id;
    }
}
