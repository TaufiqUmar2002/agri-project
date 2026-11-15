//package com.umar.audit;
//
//
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.stereotype.Repository;
//import tools.jackson.databind.ObjectMapper;
//
//import java.sql.ResultSet;
//import java.util.List;
//
//@Repository
//public class AuditJournalRepository {
//    private final JdbcTemplate jdbc;
//    private final AuditCanonicalizer canonicalizer;
//    private final AuditHashService hashService;
//    private final ObjectMapper mapper;
//
//    public AuditJournalRepository(
//            JdbcTemplate jdbc,
//            AuditCanonicalizer canonicalizer,
//            AuditHashService hashService,
//            ObjectMapper mapper) {
//        this.jdbc = jdbc;
//        this.canonicalizer = canonicalizer;
//        this.hashService = hashService;
//        this.mapper = mapper;
//    }
//
//    // Invoke inside TransactionTemplate. FOR UPDATE serializes hash-chain appends.
//    public AuditEnvelope append(AuditEvent event) {
//        ChainHead head = jdbc.queryForObject("""
//                SELECT last_sequence, last_hash
//                  FROM vault_audit.audit_chain_head
//                 WHERE chain_name = 'main'
//                 FOR UPDATE
//                """, (rs, rowNum) -> new ChainHead(
//                rs.getLong("last_sequence"), rs.getString("last_hash")));
//
//        if (head == null) throw new IllegalStateException("Audit chain head missing");
//
//        // The chain-head lock serializes appends. Re-check after acquiring it so
//        // concurrent retries using the same event ID return the original row.
//        List<AuditEnvelope> existing = jdbc.query("""
//                SELECT id, sequence_number, canonical_event, previous_hash, record_hash
//                  FROM vault_audit.audit_journal
//                 WHERE id = ?
//                """, (rs, rowNum) -> toEnvelope(rs), event.id());
//        if (!existing.isEmpty()) {
//            AuditEnvelope original = existing.get(0);
//            if (!original.canonicalEvent().equals(canonicalizer.canonicalize(event))) {
//                throw new SecurityException("Audit event ID reused with different payload");
//            }
//            return original;
//        }
//
//        String canonical = canonicalizer.canonicalize(event);
//        long sequence = head.sequence() + 1;
//        String recordHash = hashService.hash(head.hash(), canonical);
//
//        jdbc.update("""
//                INSERT INTO vault_audit.audit_journal
//                    (id, sequence_number, canonical_event, previous_hash, record_hash)
//                VALUES (?, ?, ?, ?, ?)
//                """, event.id(), sequence, canonical, head.hash(), recordHash);
//
//        jdbc.update("""
//                INSERT INTO vault_audit.audit_delivery(audit_id, status)
//                VALUES (?, 'PENDING')
//                """, event.id());
//
//        jdbc.update("""
//                UPDATE vault_audit.audit_chain_head
//                   SET last_sequence = ?, last_hash = ?, updated_at = CURRENT_TIMESTAMP
//                 WHERE chain_name = 'main'
//                """, sequence, recordHash);
//
//        return new AuditEnvelope(event, sequence, head.hash(), recordHash, canonical);
//    }
//
//    public List<AuditEnvelope> findDue(int limit) {
//        return jdbc.query("""
//                SELECT j.id, j.sequence_number, j.canonical_event,
//                       j.previous_hash, j.record_hash
//                  FROM vault_audit.audit_journal j
//                  JOIN vault_audit.audit_delivery d ON d.audit_id = j.id
//                 WHERE d.status IN ('PENDING', 'RETRY')
//                   AND d.next_attempt_at <= CURRENT_TIMESTAMP
//                 ORDER BY j.sequence_number
//                 LIMIT ?
//                """, (rs, rowNum) -> toEnvelope(rs), limit);
//    }
//
//    private AuditEnvelope toEnvelope(ResultSet rs) throws java.sql.SQLException {
//        try {
//            AuditEvent event = mapper.readValue(
//                    rs.getString("canonical_event"), AuditEvent.class);
//            return new AuditEnvelope(
//                    event, rs.getLong("sequence_number"),
//                    rs.getString("previous_hash"), rs.getString("record_hash"),
//                    rs.getString("canonical_event"));
//        } catch (Exception e) {
//            throw new java.sql.SQLException("Cannot deserialize audit event", e);
//        }
//    }
//
//    public void markDelivered(java.util.UUID id, String externalReference) {
//        int updated = jdbc.update("""
//                UPDATE vault_audit.audit_delivery
//                   SET status = 'DELIVERED',
//                       external_reference = ?,
//                       delivered_at = CURRENT_TIMESTAMP,
//                       updated_at = CURRENT_TIMESTAMP
//                 WHERE audit_id = ? AND status <> 'DELIVERED'
//                """, externalReference, id);
//
//        if (updated == 0) {
//            Integer count = jdbc.queryForObject("""
//                    SELECT COUNT(*) FROM vault_audit.audit_delivery
//                     WHERE audit_id = ? AND status = 'DELIVERED'
//                    """, Integer.class, id);
//            if (count == null || count == 0) {
//                throw new IllegalStateException("Audit delivery row not found: " + id);
//            }
//        }
//    }
//
//    public void recordFailure(java.util.UUID id, String errorCode) {
//        jdbc.update("""
//                UPDATE vault_audit.audit_delivery
//                   SET status = 'RETRY',
//                       attempt_count = attempt_count + 1,
//                       last_error_code = ?,
//                       next_attempt_at = CURRENT_TIMESTAMP
//                           + (LEAST(3600, 5 * power(2, LEAST(attempt_count, 9)))
//                              * INTERVAL '1 second'),
//                       updated_at = CURRENT_TIMESTAMP
//                 WHERE audit_id = ? AND status <> 'DELIVERED'
//                """, errorCode, id);
//
//        jdbc.update("""
//                INSERT INTO vault_audit.audit_delivery_attempt(audit_id, result, error_code)
//                VALUES (?, 'FAILED', ?)
//                """, id, errorCode);
//    }
//
//    public void recordSuccessAttempt(java.util.UUID id, String externalReference) {
//        jdbc.update("""
//                INSERT INTO vault_audit.audit_delivery_attempt(audit_id, result, external_ref)
//                VALUES (?, 'SUCCESS', ?)
//                """, id, externalReference);
//    }
//
//    private record ChainHead(long sequence, String hash) {}
//}
