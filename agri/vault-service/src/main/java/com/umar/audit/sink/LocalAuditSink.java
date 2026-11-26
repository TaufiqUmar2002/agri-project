package com.umar.audit.sink;


import com.umar.audit.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;

@Component
@ConditionalOnProperty(name = "vault.audit.sink", havingValue = "local", matchIfMissing = true)
public class LocalAuditSink implements AuditSink {
    private final AuditCanonicalizer canonicalizer;
    private final AuditHashService hashService;

    @Value("${vault.audit.local.directory:./vault-data/audit}")
    private String directory;

    public LocalAuditSink(AuditCanonicalizer canonicalizer, AuditHashService hashService) {
        this.canonicalizer = canonicalizer;
        this.hashService = hashService;
    }

    @Override
    public AuditReceipt append(AuditEnvelope envelope) {
        try {
            Path dir = Path.of(directory).toAbsolutePath();
            Files.createDirectories(dir);
            byte[] bytes = canonicalizer.envelopeBytes(envelope);
            String contentHash = hashService.sha256Hex(bytes);
            Path target = dir.resolve(envelope.event().id() + ".json");

            if (Files.exists(target)) {
                verify(target, contentHash);
                return new AuditReceipt(target.toString(), contentHash, Instant.now(), true);
            }

            try (FileChannel channel = FileChannel.open(target, CREATE_NEW, WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            } catch (FileAlreadyExistsException duplicate) {
                verify(target, contentHash);
            }
            return new AuditReceipt(target.toString(), contentHash, Instant.now(), true);
        } catch (Exception e) {
            throw new IllegalStateException("Local audit sink append failed", e);
        }
    }

    private void verify(Path path, String expected) throws Exception {
        String actual = hashService.sha256Hex(Files.readAllBytes(path));
        if (!expected.equals(actual)) {
            throw new SecurityException("Event ID already exists with different content");
        }
    }
}
