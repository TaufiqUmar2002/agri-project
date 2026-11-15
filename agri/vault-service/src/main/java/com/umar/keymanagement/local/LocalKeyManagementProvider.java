package com.umar.keymanagement.local;


import com.umar.keymanagement.DataKeyMaterial;
import com.umar.keymanagement.KeyManagementProvider;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.security.SecureRandom;

@Component
@ConditionalOnProperty(
        name = "vault.kms.provider",
        havingValue = "local"
)
public class LocalKeyManagementProvider implements KeyManagementProvider {

    private static final String KEYSTORE_TYPE = "PKCS12";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_SIZE = 256;
    private static final int NONCE_SIZE = 12;
    private static final int TAG_SIZE = 128;
    private static final byte FORMAT_VERSION = 1;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${vault.kms.local.keystore-path}")
    private String keystorePath;

    @Value("${vault.kms.local.keystore-password}")
    private String keystorePassword;

    @Value("${vault.kms.local.kek-alias:vault-local-kek-v1}")
    private String kekAlias;

    private KeyStore keyStore;

    @PostConstruct
    public synchronized void initialize() {
        try {
            if (keystorePassword == null || keystorePassword.isBlank()) {
                throw new IllegalStateException(
                        "Local keystore password is required"
                );
            }

            Path path = Path.of(keystorePath);
            Files.createDirectories(path.toAbsolutePath().getParent());

            keyStore = KeyStore.getInstance(KEYSTORE_TYPE);

            if (Files.exists(path)) {
                try (InputStream input = Files.newInputStream(path)) {
                    keyStore.load(input, keystorePassword.toCharArray());
                }
            } else {
                keyStore.load(null, keystorePassword.toCharArray());
            }

            if (!keyStore.containsAlias(kekAlias)) {
                KeyGenerator generator = KeyGenerator.getInstance("AES");
                generator.init(KEY_SIZE);

                SecretKey kek = generator.generateKey();

                keyStore.setEntry(
                        kekAlias,
                        new KeyStore.SecretKeyEntry(kek),
                        new KeyStore.PasswordProtection(
                                keystorePassword.toCharArray()
                        )
                );

                saveKeystore();
            }

            // Verify that the key can actually be retrieved.
            getKek();

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to initialize local key management", e
            );
        }
    }

    @Override
    public DataKeyMaterial generateDataKey(String keyId) {
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(KEY_SIZE);

            SecretKey dataKey = generator.generateKey();
            byte[] plaintext = dataKey.getEncoded();

            byte[] encrypted = wrapKey(keyId, plaintext);

            return new DataKeyMaterial(plaintext, encrypted);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to generate data key", e
            );
        }
    }

    @Override
    public byte[] decryptDataKey(String keyId, byte[] encryptedKey) {
        try {
            if (encryptedKey == null || encryptedKey.length < 30) {
                throw new IllegalArgumentException(
                        "Invalid encrypted data key"
                );
            }

            ByteBuffer buffer = ByteBuffer.wrap(encryptedKey);

            byte version = buffer.get();

            if (version != FORMAT_VERSION) {
                throw new IllegalArgumentException(
                        "Unsupported encrypted key format"
                );
            }

            byte[] nonce = new byte[NONCE_SIZE];
            buffer.get(nonce);

            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);

            cipher.init(
                    Cipher.DECRYPT_MODE,
                    getKek(),
                    new GCMParameterSpec(TAG_SIZE, nonce)
            );

            cipher.updateAAD(keyId.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            return cipher.doFinal(ciphertext);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to decrypt data key", e
            );
        }
    }

    private byte[] wrapKey(String keyId, byte[] plaintext) throws Exception {

        byte[] nonce = new byte[NONCE_SIZE];
        secureRandom.nextBytes(nonce);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);

        cipher.init(
                Cipher.ENCRYPT_MODE,
                getKek(),
                new GCMParameterSpec(TAG_SIZE, nonce)
        );

        cipher.updateAAD(keyId.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        byte[] ciphertext = cipher.doFinal(plaintext);

        ByteBuffer output = ByteBuffer.allocate(
                1 + NONCE_SIZE + ciphertext.length
        );

        output.put(FORMAT_VERSION);
        output.put(nonce);
        output.put(ciphertext);

        return output.array();
    }

    private synchronized SecretKey getKek() throws Exception {

        KeyStore.Entry entry = keyStore.getEntry(
                kekAlias,
                new KeyStore.PasswordProtection(
                        keystorePassword.toCharArray()
                )
        );

        if (!(entry instanceof KeyStore.SecretKeyEntry secretKeyEntry)) {
            throw new IllegalStateException("KEK not found in local keystore");
        }

        return secretKeyEntry.getSecretKey();
    }

    private synchronized void saveKeystore() throws Exception {

        Path destination = Path.of(keystorePath);
        Path temporary = destination.resolveSibling(
                destination.getFileName() + ".tmp"
        );

        try (OutputStream output = Files.newOutputStream(temporary)) {
            keyStore.store(output, keystorePassword.toCharArray());
        }

        try {
            Files.move(
                    temporary,
                    destination,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(
                    temporary,
                    destination,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    @Override
    public String providerName() {
        return "LOCAL_PKCS12";
    }
}
