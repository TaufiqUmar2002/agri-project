package com.umar.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Retries delivery of audit envelopes that are PENDING or RETRY in the journal.
 * The journal (PostgreSQL) is the source of truth; the sink is the durable copy.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditDeliveryService {

    private final AuditJournalRepository repository;
    private final AuditSink sink;

    @Value("${vault.audit.delivery.batch-size:50}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${vault.audit.delivery.fixed-delay-ms:5000}",
            initialDelayString = "${vault.audit.delivery.initial-delay-ms:5000}")
    @Transactional
    public void deliverPending() {
        List<AuditEnvelope> due = repository.findDue(batchSize);
        for (AuditEnvelope envelope : due) {
            try {
                AuditReceipt receipt = sink.append(envelope);
                if (receipt != null && receipt.durable()) {
                    repository.markDelivered(envelope.event().id(), receipt.externalReference());
                    repository.recordSuccessAttempt(envelope.event().id(), receipt.externalReference());
                } else {
                    repository.recordFailure(envelope.event().id(), "SINK_NOT_DURABLE");
                }
            } catch (Exception e) {
                log.warn("Audit delivery failed for event {}: {}", envelope.event().id(), e.getMessage());
                repository.recordFailure(envelope.event().id(), e.getClass().getSimpleName());
            }
        }
    }
}
