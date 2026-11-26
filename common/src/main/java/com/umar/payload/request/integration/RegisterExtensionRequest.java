package com.umar.payload.request.integration;

import com.umar.enums.integration.FailMode;
import com.umar.enums.integration.RuntimeKind;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class RegisterExtensionRequest {
    private String ownerId;
    @NotBlank @Size(min = 3, max = 80)
    private String name;
    @NotBlank
    private String extensionPoint;
    @NotBlank
    private String version;
    private String baseExtensionId;
    @NotBlank
    private String contractVersion;
    @NotNull
    private Map<String, Object> runtime;
    @NotEmpty
    private List<@NotBlank String> dataScope;
    private LimitsSpec limits;
    private FailMode failMode;
    @Size(max = 5)
    private List<String> secrets;
    @NotNull @Size(min = 5, max = 200)
    private List<Map<String, Object>> testVectors;
    @NotBlank @Size(min = 20, max = 1000)
    private String description;
    @NotBlank @Size(min = 5, max = 1000)
    private String changelog;

    @Data
    public static class LimitsSpec {
        private Integer timeoutMs;
        private Integer memoryMb;
    }

    public RuntimeKind resolveRuntimeType() {
        Object t = runtime == null ? null : runtime.get("type");
        if (t == null) return null;
        return RuntimeKind.valueOf(String.valueOf(t).trim().toUpperCase());
    }
}
