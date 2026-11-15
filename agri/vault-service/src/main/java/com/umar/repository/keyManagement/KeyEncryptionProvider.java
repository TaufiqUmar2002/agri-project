package com.umar.repository.keyManagement;


public interface KeyEncryptionProvider {

    GeneratedDataKey generateDataKey(String classKekReference, byte[] encryptionContext);

    byte[] unwrapDataKey(String classKekReference, byte[] wrappedKey, byte[] encryptionContext);

    byte[] rewrapDataKey(String oldKekReference, String newKekReference, byte[] wrappedKey, byte[] encryptionContext);

    record GeneratedDataKey(byte[] plaintextKey, byte[] wrappedKey) {
        public GeneratedDataKey {
            plaintextKey = plaintextKey.clone();
            wrappedKey = wrappedKey.clone();
        }

        @Override
        public byte[] plaintextKey() {
            return plaintextKey.clone();
        }

        @Override
        public byte[] wrappedKey() {
            return wrappedKey.clone();
        }
    }
}
