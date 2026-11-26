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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class CreateLegalHoldRequest {

    private String action; // PLACE (default) | LIFT

    private UUID holdId; // required when action=LIFT

    @NotBlank(message = "Case reference is required")
    private String caseRef;

    @NotBlank(message = "Authority is required")
    private String authority;

    @JsonIgnore
    private String scope;

    private List<String> subjectRefs;

    private List<String> dataClasses;

    private List<String> documentTokens;

    private String reviewDate;

    private String reason;

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
            if (node.has("subjectRefs") && node.get("subjectRefs").isArray()) {
                this.subjectRefs = new ArrayList<>();
                node.get("subjectRefs").forEach(n -> this.subjectRefs.add(n.asString()));
            }
            if (node.has("dataClasses") && node.get("dataClasses").isArray()) {
                this.dataClasses = new ArrayList<>();
                node.get("dataClasses").forEach(n -> this.dataClasses.add(n.asString()));
            }
            if (node.has("documentTokens") && node.get("documentTokens").isArray()) {
                this.documentTokens = new ArrayList<>();
                node.get("documentTokens").forEach(n -> this.documentTokens.add(n.asString()));
            }
        }
    }
}
