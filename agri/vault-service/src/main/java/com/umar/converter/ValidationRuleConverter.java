package com.umar.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.boot.json.JsonParseException;
import tools.jackson.databind.ObjectMapper;

@Converter(autoApply = true)
public class ValidationRuleConverter implements AttributeConverter<ValidationRule,String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(ValidationRule validationRule) {
        if(validationRule==null){
            return null;
        }
        try{
            return objectMapper.writeValueAsString(validationRule);
        }
        catch (JsonParseException e){
            throw new IllegalArgumentException("Error converting ValidationRule to JSON string", e);
        }
    }

    @Override
    public ValidationRule convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(dbData, ValidationRule.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error converting JSON string to ValidationRule", e);
        }
    }
}
