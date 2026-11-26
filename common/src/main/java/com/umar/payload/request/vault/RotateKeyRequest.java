package com.umar.payload.request.vault;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class RotateKeyRequest {

    @NotBlank(message = "Key type is required")
    private String keyType; // CLASS_KEK | DATA_KEY | SIGNING_KEY | INDEX_KEY | ROOT

    @JsonIgnore
    private String scope;

    private String dataClass;

    private String secretRef;

    private String subjectRef;

    @NotBlank(message = "Mode is required")
    private String mode; // SCHEDULED | ON_DEMAND | EMERGENCY

    private String reasonCode;

    private String note;

    private Boolean revokeOldVersion;

    private Boolean ceremonyApproved;

    @JsonIgnore
    public String getScope() {
        return scope;
    }

    @JsonIgnore
    public void setScope(String scope) {
        this.scope = scope;
    }

    @JsonSetter("scope")
    public void setScopeFromJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isString()) {
            this.scope = node.asString();
        } else if (node.isObject()) {
            this.scope = node.toString();
            if (node.hasNonNull("dataClass")) {
                this.dataClass = node.get("dataClass").asString();
            }
            if (node.hasNonNull("secretRef")) {
                this.secretRef = node.get("secretRef").asString();
            }
            if (node.hasNonNull("subjectRef")) {
                this.subjectRef = node.get("subjectRef").asString();
            }
        }
    }
}
