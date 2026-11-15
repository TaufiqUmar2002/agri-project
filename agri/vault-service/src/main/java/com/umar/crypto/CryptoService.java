package com.umar.crypto;

import javax.crypto.SecretKey;

public interface CryptoService {

    EncryptedPayload encrypt(String plaintext, SecretKey dataEncryptionKey, EncryptionContext context);
    String decrypt(EncryptedPayload payload, SecretKey dataEncryptionKey, EncryptionContext context);

}
