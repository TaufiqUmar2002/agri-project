package com.umar.audit;


import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class AuditCanonicalizer {

    private final ObjectMapper mapper;

    public AuditCanonicalizer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String canonicalize(AuditEvent event) {
        try {
            return mapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot canonicalize audit event", e);
        }
    }

    public byte[] envelopeBytes(AuditEnvelope envelope) {
        try {
            return mapper.writeValueAsBytes(envelope);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialize audit envelope", e);
        }
    }
}
