package com.umar.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter that ensures sensitive entity fields are encrypted via
 * VaultCryptoService (routed through integration-service -> vault-service) before
 * persistence and decrypted transparently when hydrated for service responses.
 */
@Converter
public class VaultEncryptedStringConverter implements AttributeConverter<String, String> {

    private static final VaultCryptoService FALLBACK_SERVICE =
            new VaultCryptoService(null, "local", "AgreeShieldVaultMasterSecretKey2026!");

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        VaultCryptoService svc = VaultCryptoService.instance();
        return (svc != null ? svc : FALLBACK_SERVICE).encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        VaultCryptoService svc = VaultCryptoService.instance();
        return (svc != null ? svc : FALLBACK_SERVICE).decrypt(dbData);
    }
}
