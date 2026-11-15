package com.umar.keymanagement;

public interface KeyManagementProvider {

    DataKeyMaterial generateDataKey(String keyId);
    byte[] decryptDataKey(String keyId, byte[] encryptedKey);
    String providerName();
}
