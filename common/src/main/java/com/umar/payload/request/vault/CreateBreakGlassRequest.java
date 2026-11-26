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

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class CreateBreakGlassRequest {

    @NotBlank(message = "Incident reference is required")
    private String incidentRef;

    @JsonIgnore
    private String scope;

    private List<String> subjectRefs;

    private List<String> dataClasses;

    private List<String> operations;

    private Integer maxRecords;

    @NotBlank(message = "Investigator reference is required")
    private String investigatorRef;

    @NotBlank(message = "Legal basis is required")
    private String legalBasis;

    private String validUntil;

    private Integer durationMinutes;

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
            if (node.has("operations") && node.get("operations").isArray()) {
                this.operations = new ArrayList<>();
                node.get("operations").forEach(n -> this.operations.add(n.asString()));
            }
            if (node.hasNonNull("maxRecords")) {
                this.maxRecords = node.get("maxRecords").asInt();
            }
        }
    }
}
