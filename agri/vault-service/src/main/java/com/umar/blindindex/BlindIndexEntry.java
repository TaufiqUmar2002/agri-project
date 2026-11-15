//package com.umar.blindindex;
//
//
//import jakarta.persistence.*;
//
//import java.time.Instant;
//import java.util.UUID;
//
//@Entity
//@Table(
//        name = "blind_index_entry",
//        uniqueConstraints = {
//                @UniqueConstraint(
//                        name = "uk_blind_index_token_version",
//                        columnNames = {
//                                "token_id",
//                                "index_key_version_id"
//                        }
//                ),
//                @UniqueConstraint(
//                        name = "uk_blind_index_subject_value",
//                        columnNames = {
//                                "data_class",
//                                "subject_ref",
//                                "index_key_version_id",
//                                "index_hash"
//                        }
//                )
//        },
//        indexes = {
//                @Index(
//                        name = "idx_blind_index_lookup",
//                        columnList = "data_class,index_key_version_id,index_hash"
//                ),
//                @Index(
//                        name = "idx_blind_index_subject_lookup",
//                        columnList = "data_class,subject_ref,index_key_version_id,index_hash"
//                )
//        }
//)
//public class BlindIndexEntry {
//
//    @Id
//    private UUID id;
//
//    @Column(name = "data_class", nullable = false, length = 80)
//    private String dataClass;
//
//    @Column(name = "token_id", nullable = false)
//    private UUID tokenId;
//
//    @Column(name = "subject_ref", nullable = false, length = 150)
//    private String subjectRef;
//
//    @Column(
//            name = "index_key_version_id",
//            nullable = false
//    )
//    private UUID indexKeyVersionId;
//
//    @Column(
//            name = "index_hash",
//            nullable = false,
//            columnDefinition = "bytea"
//    )
//    private byte[] indexHash;
//
//    @Column(name = "created_at", nullable = false)
//    private Instant createdAt;
//
//    protected BlindIndexEntry() {
//    }
//
//    public BlindIndexEntry(
//            String dataClass,
//            UUID tokenId,
//            String subjectRef,
//            UUID indexKeyVersionId,
//            byte[] indexHash) {
//
//        this.id = UUID.randomUUID();
//        this.dataClass = dataClass;
//        this.tokenId = tokenId;
//        this.subjectRef = subjectRef;
//        this.indexKeyVersionId = indexKeyVersionId;
//        this.indexHash = indexHash.clone();
//        this.createdAt = Instant.now();
//    }
//
//    public UUID getId() {
//        return id;
//    }
//
//    public String getDataClass() {
//        return dataClass;
//    }
//
//    public UUID getTokenId() {
//        return tokenId;
//    }
//
//    public String getSubjectRef() {
//        return subjectRef;
//    }
//
//    public UUID getIndexKeyVersionId() {
//        return indexKeyVersionId;
//    }
//
//    public byte[] getIndexHash() {
//        return indexHash.clone();
//    }
//
//    public Instant getCreatedAt() {
//        return createdAt;
//    }
//}
