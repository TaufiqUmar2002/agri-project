package com.umar.keymanagement.kms;

import com.umar.keymanagement.DataKeyMaterial;

public interface KmsClient {

    DataKeyMaterial generateDataKey(String keyId, int keySizeBits);
    byte[] decryptDataKey(String keyId, byte[] encryptedKey);
}
