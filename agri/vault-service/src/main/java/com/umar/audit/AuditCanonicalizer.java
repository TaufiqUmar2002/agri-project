//package com.umar.audit;
//
//
//import org.springframework.stereotype.Component;
//import tools.jackson.databind.MapperFeature;
//import tools.jackson.databind.ObjectMapper;
//import tools.jackson.databind.SerializationFeature;
//
//@Component
//public class AuditCanonicalizer {
//
//    private final ObjectMapper mapper;
//
//    public AuditCanonicalizer(ObjectMapper mapper) {
//        this.mapper = mapper.copy()
//                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
//                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
//    }
//
//    public String canonicalize(AuditEvent event) {
//        try {
//            return mapper.writeValueAsString(event);
//        } catch (JsonProcessingException e) {
//            throw new IllegalStateException("Cannot canonicalize audit event", e);
//        }
//    }
//
//    public byte[] envelopeBytes(AuditEnvelope envelope) {
//        try {
//            return mapper.writeValueAsBytes(envelope);
//        } catch (JsonProcessingException e) {
//            throw new IllegalStateException("Cannot serialize audit envelope", e);
//        }
//    }
//}
