package com.umar.payload.request.vault;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class CreateSecretRequest {

    private String ref;

    private String name;

    private String secretClass;

    private String mode; // GENERATE | PROVIDE | DYNAMIC

    private GenerateSpec generate;

    private DynamicSpec dynamic;

    @JsonIgnore
    private String owner;

    private String ownerType;

    private String ownerId;

    @Size(max = 16384, message = "Secret value cannot exceed 16 KB")
    private String value;

    private SecretPolicySpec policy;

    private RotationSpec rotation;

    private String baseRef;

    private Boolean revealOnce;

    @JsonGetter("ownerRaw")
    public String getOwner() {
        return owner;
    }

    @JsonIgnore
    public void setOwner(String owner) {
        this.owner = owner;
    }

    @JsonSetter("owner")
    public void setOwnerFromJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isString()) {
            this.owner = node.asString();
        } else if (node.isObject()) {
            if (node.hasNonNull("ownerType")) {
                this.ownerType = node.get("ownerType").asString();
            }
            if (node.hasNonNull("ownerId")) {
                this.ownerId = node.get("ownerId").asString();
            }
            if (this.owner == null) {
                this.owner = (this.ownerType != null ? this.ownerType + ":" : "")
                        + (this.ownerId != null ? this.ownerId : "");
            }
        }
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    public static class GenerateSpec {
        private String algorithm;
        private Integer keySizeBits;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    public static class DynamicSpec {
        private String providerRef;
        private Integer ttlSeconds;
        private List<String> scopes;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    public static class SecretPolicySpec {
        private List<String> uses;
        private List<String> services;
        private List<String> purposes;
        private Integer overlapHours;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    public static class RotationSpec {
        private Integer intervalDays;
        private Integer notifyDaysBeforeExpiry;
        private String justification;
    }
}
