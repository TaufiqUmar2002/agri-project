package com.umar.document;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Dual-region encrypted object storage provider (sections 2.8, 4.5, 6).
 * Persists encrypted document ciphertext blobs into primary and replica directories.
 */
@Component
public class ObjectStorageProvider {

    @Value("${vault.storage.primary-dir:./vault-data/objects/primary}")
    private String primaryDir;

    @Value("${vault.storage.replica-dir:./vault-data/objects/secondary}")
    private String replicaDir;

    public record StoredObjectRefs(String primaryRef, String replicaRef) {
    }

    public StoredObjectRefs putEncryptedObject(String dataClass, String token, byte[] encryptedBytes) {
        try {
            String safeName = dataClass.toLowerCase() + "_" + token.replaceAll("[^A-Za-z0-9_.-]", "_") + "_" + UUID.randomUUID() + ".enc";
            Path primaryPath = Path.of(primaryDir).toAbsolutePath().resolve(safeName);
            Path replicaPath = Path.of(replicaDir).toAbsolutePath().resolve(safeName);
            Files.createDirectories(primaryPath.getParent());
            Files.createDirectories(replicaPath.getParent());
            Files.write(primaryPath, encryptedBytes);
            Files.write(replicaPath, encryptedBytes);
            return new StoredObjectRefs(primaryPath.toString(), replicaPath.toString());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write encrypted document to object storage", e);
        }
    }

    public byte[] getEncryptedObject(String primaryRef, String replicaRef) {
        try {
            if (primaryRef != null) {
                Path p = Path.of(primaryRef);
                if (Files.exists(p)) {
                    return Files.readAllBytes(p);
                }
            }
            if (replicaRef != null) {
                Path r = Path.of(replicaRef);
                if (Files.exists(r)) {
                    return Files.readAllBytes(r);
                }
            }
            return null;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read encrypted document from object storage", e);
        }
    }

    public void deleteObjects(String primaryRef, String replicaRef) {
        try {
            if (primaryRef != null) Files.deleteIfExists(Path.of(primaryRef));
            if (replicaRef != null) Files.deleteIfExists(Path.of(replicaRef));
        } catch (IOException ignored) {
        }
    }
}
