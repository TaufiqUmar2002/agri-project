package com.umar.audit;


import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuditService {
    private final AuditJournalRepository repository;
    private final AuditSink sink;
    private final TransactionTemplate tx;

    public AuditService(
            AuditJournalRepository repository,
            AuditSink sink,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.sink = sink;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /**
     * Returns only after PostgreSQL journal commit and durable sink acknowledgment.
     * If this throws, callers must not release sensitive operation results.
     */
    public AuditReceipt recordAndRequireDurable(AuditEvent event) {
        AuditEnvelope envelope = tx.execute(status -> repository.append(event));
        if (envelope == null) {
            throw new AuditException("Audit journal transaction returned no record", null);
        }

        try {
            AuditReceipt receipt = sink.append(envelope);
            if (receipt == null || !receipt.durable()) {
                throw new IllegalStateException("Sink did not confirm durable acceptance");
            }

            tx.executeWithoutResult(status -> {
                repository.markDelivered(event.id(), receipt.externalReference());
                repository.recordSuccessAttempt(event.id(), receipt.externalReference());
            });
            return receipt;
        } catch (Exception failure) {
            try {
                tx.executeWithoutResult(status ->
                        repository.recordFailure(event.id(),
                                failure.getClass().getSimpleName()));
            } catch (Exception stateFailure) {
                failure.addSuppressed(stateFailure);
            }
            throw new AuditException(
                    "Mandatory audit delivery failed; do not return sensitive data",
                    failure);
        }
    }
}
