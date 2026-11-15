package com.umar.keymanagement.kms;


import com.umar.keymanagement.DataKeyMaterial;
import com.umar.keymanagement.KeyManagementProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "vault.kms.provider",
        havingValue = "cloud"
)
public class KmsKeyManagementProvider implements KeyManagementProvider {

    private final KmsClient kmsClient;

    public KmsKeyManagementProvider(KmsClient kmsClient) {
        this.kmsClient = kmsClient;
    }

    @Override
    public DataKeyMaterial generateDataKey(String keyId) {
        return kmsClient.generateDataKey(keyId, 256);
    }

    @Override
    public byte[] decryptDataKey(String keyId, byte[] encryptedKey) {
        return kmsClient.decryptDataKey(keyId, encryptedKey);
    }

    @Override
    public String providerName() {
        return "CLOUD_KMS";
    }
}
