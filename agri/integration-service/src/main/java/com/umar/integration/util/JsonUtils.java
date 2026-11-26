package com.umar.integration.util;

import com.umar.integration.error.HubException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JsonUtils {

    private final ObjectMapper objectMapper;

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw HubException.validation("Unable to serialize JSON payload");
        }
    }

    public <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw HubException.validation("Unable to parse JSON payload");
        }
    }

    public <T> T fromJson(String json, TypeReference<T> typeRef) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (Exception e) {
            throw HubException.validation("Unable to parse JSON payload");
        }
    }

    public List<String> toStringList(String json) {
        if (json == null || json.isBlank() || "null".equals(json.trim())) {
            return List.of();
        }
        List<String> list = fromJson(json, new TypeReference<>() {});
        return list == null ? List.of() : list;
    }

    public Map<String, Object> toMap(String json) {
        if (json == null || json.isBlank() || "null".equals(json.trim())) {
            return Map.of();
        }
        Map<String, Object> map = fromJson(json, new TypeReference<>() {});
        return map == null ? Map.of() : map;
    }

    public JsonNode toTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw HubException.validation("Malformed JSON");
        }
    }
}
