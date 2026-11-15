//package com.umar.audit.sink;
//
//
//import com.umar.audit.AuditCanonicalizer;
//import com.umar.audit.AuditHashService;
//import com.umar.audit.AuditSink;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
//import org.springframework.stereotype.Component;
//
//import java.time.Instant;
//import java.time.temporal.ChronoUnit;
//import java.util.Map;
//
//@Component
//@ConditionalOnProperty(name = "vault.audit.sink", havingValue = "worm")
//public class WormAuditSink implements AuditSink {
//    private final S3Client s3;
//    private final AuditCanonicalizer canonicalizer;
//    private final AuditHashService hashService;
//
//    @Value("${vault.audit.worm.bucket}") private String bucket;
//    @Value("${vault.audit.worm.prefix:audit}") private String prefix;
//    @Value("${vault.audit.worm.retention-days:2555}") private int retentionDays;
//
//    public WormAuditSink(
//            S3Client s3,
//            AuditCanonicalizer canonicalizer,
//            AuditHashService hashService) {
//        this.s3 = s3;
//        this.canonicalizer = canonicalizer;
//        this.hashService = hashService;
//    }
//
//    @Override
//    public AuditReceipt append(AuditEnvelope envelope) {
//        byte[] body = canonicalizer.envelopeBytes(envelope);
//        String sha256 = hashService.sha256Hex(body);
//        String key = objectKey(envelope);
//        HeadObjectResponse existing = headIfPresent(key);
//
//        if (existing != null) return verifyExisting(key, existing, sha256);
//
//        Instant retainUntil = Instant.now().plus(retentionDays, ChronoUnit.DAYS);
//        try {
//            PutObjectRequest put = PutObjectRequest.builder()
//                    .bucket(bucket)
//                    .key(key)
//                    .contentType("application/json")
//                    .metadata(Map.of(
//                            "event-id", envelope.event().id().toString(),
//                            "sha256", sha256,
//                            "record-hash", envelope.recordHash()))
//                    .objectLockMode(ObjectLockMode.COMPLIANCE)
//                    .objectLockRetainUntilDate(retainUntil)
//                    .build();
//
//            s3.putObject(put, RequestBody.fromBytes(body));
//            HeadObjectResponse stored = s3.headObject(
//                    HeadObjectRequest.builder().bucket(bucket).key(key).build());
//            return verifyExisting(key, stored, sha256);
//
//        } catch (S3Exception writeFailure) {
//            // Handles ambiguous network results and concurrent duplicate writers.
//            HeadObjectResponse afterFailure = headIfPresent(key);
//            if (afterFailure != null) return verifyExisting(key, afterFailure, sha256);
//            throw new IllegalStateException("On-prem WORM write failed", writeFailure);
//        }
//    }
//
//    private AuditReceipt verifyExisting(
//            String key, HeadObjectResponse head, String expectedHash) {
//        String actual = head.metadata() == null ? null : head.metadata().get("sha256");
//        if (!expectedHash.equals(actual)) {
//            throw new SecurityException("WORM key exists with different event content");
//        }
//        if (head.objectLockMode() != ObjectLockMode.COMPLIANCE) {
//            throw new SecurityException("WORM object is not protected in Compliance mode");
//        }
//        if (head.objectLockRetainUntilDate() == null
//                || !head.objectLockRetainUntilDate().isAfter(Instant.now())) {
//            throw new SecurityException("WORM object has no active retention");
//        }
//
//        // Verify object bytes, not metadata alone.
//        var storedBytes = s3.getObjectAsBytes(
//                GetObjectRequest.builder().bucket(bucket).key(key).build());
//        String actualBodyHash = hashService.sha256Hex(storedBytes.asByteArray());
//        if (!expectedHash.equals(actualBodyHash)) {
//            throw new SecurityException("WORM object body does not match expected event");
//        }
//
//        return new AuditReceipt(
//                "s3://" + bucket + "/" + key,
//                expectedHash,
//                Instant.now(),
//                true);
//    }
//
//    private HeadObjectResponse headIfPresent(String key) {
//        try {
//            return s3.headObject(HeadObjectRequest.builder()
//                    .bucket(bucket).key(key).build());
//        } catch (S3Exception e) {
//            if (e.statusCode() == 404) return null;
//            throw e;
//        }
//    }
//
//    private String objectKey(AuditEnvelope envelope) {
//        String date = envelope.event().eventTimestamp().toString().substring(0, 10);
//        return prefix + "/" + date + "/" + envelope.event().id() + ".json";
//    }
//}
