package com.umar.crypto;


public record EncryptedPayload(byte[] ciphertext, byte[] nonce) {
    public EncryptedPayload {
        if (ciphertext == null || ciphertext.length == 0) {
            throw new IllegalArgumentException("Ciphertext is required");
        }
        if (nonce == null || nonce.length != 12) {
            throw new IllegalArgumentException("AES-GCM nonce must be exactly 12 bytes");
        }
        // Defensive copies
        ciphertext = ciphertext.clone();
        nonce = nonce.clone();
    }

    @Override
    public byte[] ciphertext() {
        return ciphertext.clone();
    }

    @Override
    public byte[] nonce() {
        return nonce.clone();
    }
}
