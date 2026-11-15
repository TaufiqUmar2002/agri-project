package com.umar.blindindex;


import java.util.List;
import java.util.UUID;

public interface BlindIndexKeyProvider {

    /**
     * Calculates HMAC-SHA256 inside the configured key provider.
     */
    byte[] calculateHmac(
            UUID keyVersionId,
            byte[] canonicalValue);

    /**
     * Returns the index-key versions that must be searched.
     * Includes active and retiring versions.
     */
    List<IndexKeyVersion> getLookupKeyVersions(String dataClass);

    /**
     * Returns the single version used for new index writes.
     */
    IndexKeyVersion getActiveWriteKey(String dataClass);

    record IndexKeyVersion(
            UUID keyVersionId,
            int version,
            KeyState state) {
    }

    enum KeyState {
        ACTIVE,
        RETIRING,
        RETIRED,
        DESTROYED
    }
}
