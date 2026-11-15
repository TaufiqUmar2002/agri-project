package com.umar.rotation;


import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record RotationRequest(

        @NotNull
        KeyType keyType,

        @NotNull
        @Valid
        RotationScope scope,

        @NotNull
        RotationMode mode,

        ReasonCode reasonCode,

        @Size(min = 20, max = 1000)
        String note,

        Boolean revokeOldVersion

) {

    public RotationRequest {
        if (revokeOldVersion == null) {
            revokeOldVersion = false;
        }
    }

    /**
     * Validates the scope according to the selected key type.
     */
    public boolean isScopeValid() {

        if (scope == null || keyType == null) {
            return false;
        }

        return switch (keyType) {
            case CLASS_KEK, INDEX_KEY ->
                    hasText(scope.dataClass());

            case DATA_KEY ->
                    hasText(scope.subjectRef());

            case SIGNING_KEY ->
                    hasText(scope.secretRef());

            case ROOT ->
                    true; // Must be handled through the root-key ceremony.
        };
    }

    /**
     * reasonCode and note are required for ON_DEMAND
     * and EMERGENCY requests.
     */
    public boolean isReasonValid() {

        if (mode == null) {
            return false;
        }

        if (mode == RotationMode.SCHEDULED) {
            return true;
        }

        return reasonCode != null
                && hasText(note)
                && note.length() >= 20
                && note.length() <= 1000;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public enum KeyType {
        CLASS_KEK,
        DATA_KEY,
        SIGNING_KEY,
        INDEX_KEY,
        ROOT
    }

    public enum RotationMode {
        SCHEDULED,
        ON_DEMAND,
        EMERGENCY
    }

    public enum ReasonCode {
        POLICY_CHANGE,
        SUSPECTED_COMPROMISE,
        STAFF_CHANGE,
        AUDIT_FINDING,
        OTHER
    }

    public record RotationScope(
            String dataClass,
            String secretRef,
            String subjectRef
    ) {
    }
}
