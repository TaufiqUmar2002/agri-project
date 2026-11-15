package com.umar.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.boot.json.JsonParseException;
import tools.jackson.databind.ObjectMapper;

@Converter(autoApply = true)
public class MaskingRuleConverter implements AttributeConverter<MaskingRule,String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(MaskingRule maskingRule) {
        if(maskingRule==null){
            return null;
        }
        try{
            return objectMapper.writeValueAsString(maskingRule);
        }
        catch (JsonParseException e){
            throw new IllegalArgumentException("Error converting ValidationRule to JSON string", e);
        }
    }

    @Override
    public MaskingRule convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(dbData, MaskingRule.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error converting JSON string to ValidationRule", e);
        }
    }
}
