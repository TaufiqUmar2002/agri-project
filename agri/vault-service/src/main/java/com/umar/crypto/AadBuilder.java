package com.umar.crypto;


import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class AadBuilder {

    private static final int FORMAT_VERSION = 1;

    public byte[] build(EncryptionContext context) {
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);
            // AAD format version
            dataOutputStream.writeInt(FORMAT_VERSION);
            // Fixed field order: do not change for existing ciphertext
            writeField(dataOutputStream, context.dataClass());
            writeField(dataOutputStream, context.subjectRef());
            writeField(dataOutputStream, context.token());
            dataOutputStream.flush();
            return byteArrayOutputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to construct AES-GCM AAD", exception);
        }
    }

    private void writeField(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }
}
