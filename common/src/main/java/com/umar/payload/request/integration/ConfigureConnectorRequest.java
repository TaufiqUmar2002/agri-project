package com.umar.payload.request.integration;

import com.umar.enums.integration.ConnectorDirection;
import com.umar.enums.integration.HubEnvironment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ConfigureConnectorRequest {
    private String ownerId;
    @NotBlank @Size(min = 3, max = 80)
    private String name;
    @NotNull
    private ConnectorDirection direction;
    @NotNull
    private Map<String, Object> adapter;
    private Map<String, Object> endpoint;
    @NotNull
    private Map<String, Object> auth;
    @NotNull
    private Map<String, Object> externalSchema;
    @NotNull
    private Map<String, Object> mapping;
    @NotNull
    private Map<String, Object> canonicalTarget;
    private List<String> piiFields;
    @NotNull
    private Map<String, Object> quotas;
    @NotNull
    private HubEnvironment environment;
    @NotNull @Size(min = 3, max = 50)
    private List<Map<String, Object>> sampleData;
}
