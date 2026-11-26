package com.umar.idempotency;



import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Service
public class RequestFingerprintService {

    private final ObjectMapper objectMapper;

    public RequestFingerprintService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public byte[] createFingerprint(
            String callerService,
            String operation,
            Object request) {

        try {
            JsonNode requestNode = objectMapper.valueToTree(request);
            JsonNode canonicalNode = canonicalize(requestNode);

            byte[] caller = callerService.getBytes(StandardCharsets.UTF_8);
            byte[] op = operation.getBytes(StandardCharsets.UTF_8);
            byte[] body = objectMapper.writeValueAsBytes(canonicalNode);

            ByteArrayOutputStream output = new ByteArrayOutputStream();

            writeLengthPrefixed(output, caller);
            writeLengthPrefixed(output, op);
            writeLengthPrefixed(output, body);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            return digest.digest(output.toByteArray());

        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Unable to calculate request fingerprint", ex);
        }
    }

    private JsonNode canonicalize(JsonNode node) {

        if (node.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();

            List<String> names = new ArrayList<>();
            names.addAll(node.propertyNames());
            names.sort(Comparator.naturalOrder());

            for (String name : names) {
                sorted.set(name, canonicalize(node.get(name)));
            }

            return sorted;
        }

        if (node.isArray()) {
            ArrayNode array = objectMapper.createArrayNode();

            for (JsonNode child : node) {
                array.add(canonicalize(child));
            }

            return array;
        }

        return node.deepCopy();
    }

    private void writeLengthPrefixed(
            ByteArrayOutputStream output,
            byte[] value) {

        output.writeBytes(ByteBuffer.allocate(Integer.BYTES)
                .putInt(value.length)
                .array());

        output.writeBytes(value);
    }
}
