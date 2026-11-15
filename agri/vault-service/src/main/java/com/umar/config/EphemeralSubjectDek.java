package com.umar.config;

import java.util.Arrays;
import java.util.Objects;

public class EphemeralSubjectDek implements AutoCloseable{


    private final byte[] rawKeyBytes;

    /**
     * Instantiates the container with a defensive clone of the generated or unwrapped key material.
     *
     * @param rawKeyBytes The raw 256-bit AES key bytes.
     */
    public EphemeralSubjectDek(byte[] rawKeyBytes) {
        Objects.requireNonNull(rawKeyBytes, "Raw encryption key bytes cannot be null");
        if (rawKeyBytes.length != 32) { // 256-bit key check
            throw new IllegalArgumentException("Invalid key size. Vault cores require exactly 256-bit keys.");
        }
        this.rawKeyBytes = rawKeyBytes.clone();
    }


    /**
     * Returns a defensive clone of the key bytes to keep internal memory arrays isolated.
     *
     * @return a clone of the raw cryptographic key material.
     */
    public byte[] rawKeyBytes() {
        // Verify container hasn't been scrubbed yet
        if (rawKeyBytes[0] == 0 && rawKeyBytes[31] == 0) {
            throw new IllegalStateException("Cryptographic key context has already been destroyed/shredded in memory.");
        }
        return this.rawKeyBytes.clone();
    }


    /**
     * Crucial Security Control [1.4]: Actively overwrites the underlying key storage row
     * in application memory with zeroes immediately when exiting a try-with-resources scope.
     */
    @Override
    public void close() {
        if (this.rawKeyBytes != null) {
            Arrays.fill(this.rawKeyBytes, (byte) 0);
        }
    }

}
