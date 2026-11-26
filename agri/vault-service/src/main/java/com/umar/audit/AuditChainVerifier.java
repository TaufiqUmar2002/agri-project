package com.umar.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Recomputes the audit hash chain to prove integrity. Each record's
 * {@code recordHash} must equal {@code SHA256(previousHash || canonicalEvent)},
 * each {@code previousHash} must equal the prior record's {@code recordHash},
 * and sequence numbers must be contiguous from 1.
 */
@Service
@RequiredArgsConstructor
public class AuditChainVerifier {

    private final AuditJournalRepository repository;
    private final AuditHashService hashService;

    public record VerificationResult(
            boolean valid,
            long recordsChecked,
            long firstBadSequence,
            String reason,
            String headHash) {
    }

    @Transactional(readOnly = true)
    public VerificationResult verify() {
        List<AuditEnvelope> envelopes = repository.findAllOrdered();
        if (envelopes.isEmpty()) {
            return new VerificationResult(true, 0, -1, "EMPTY_CHAIN", null);
        }

        String expectedPrev = envelopes.get(0).previousHash();
        long expectedSequence = envelopes.get(0).sequenceNumber();

        for (AuditEnvelope envelope : envelopes) {
            // Sequence must be contiguous.
            if (envelope.sequenceNumber() != expectedSequence) {
                return new VerificationResult(false, envelope.sequenceNumber() - 1,
                        envelope.sequenceNumber(),
                        "SEQUENCE_GAP expected=" + expectedSequence + " actual=" + envelope.sequenceNumber(),
                        envelope.recordHash());
            }

            // previousHash must chain to the prior record's recordHash.
            if (!envelope.previousHash().equals(expectedPrev)) {
                return new VerificationResult(false, envelope.sequenceNumber() - 1,
                        envelope.sequenceNumber(),
                        "CHAIN_BREAK previousHash mismatch at sequence " + envelope.sequenceNumber(),
                        envelope.recordHash());
            }

            // Recompute recordHash from previousHash + canonical event.
            String recomputed = hashService.hash(envelope.previousHash(), envelope.canonicalEvent());
            if (!recomputed.equals(envelope.recordHash())) {
                return new VerificationResult(false, envelope.sequenceNumber() - 1,
                        envelope.sequenceNumber(),
                        "HASH_MISMATCH at sequence " + envelope.sequenceNumber(),
                        envelope.recordHash());
            }

            expectedPrev = envelope.recordHash();
            expectedSequence++;
        }

        return new VerificationResult(true, envelopes.size(), -1, "OK", expectedPrev);
    }
}
