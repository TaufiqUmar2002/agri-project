package com.umar.service;

import com.umar.audit.AuditRecorder;
import com.umar.entity.dataProtection.DataClass;
import com.umar.enums.vault.DetokenizeMode;
import com.umar.exception.VaultApiException;
import com.umar.hooks.DataClassDefinition;
import com.umar.hooks.DataClassHookDispatcher;
import com.umar.payload.request.vault.DefineDataClassRequest;
import com.umar.payload.response.vault.DataClassResponse;
import com.umar.repository.dataProtection.DataClassRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Defines new data classes. Runs the {@code vault.data-class} extension point and
 * verifies minimum platform security protections cannot be lowered by hooks (section 4.12, 7).
 */
@Service
@RequiredArgsConstructor
public class DataClassDefinitionService {

    private static final Set<String> NEVER_DETOKENIZABLE_CLASSES = Set.of("KYC_DOCUMENT", "SELFIE");

    private final DataClassHookDispatcher hookDispatcher;
    private final DataClassRepository dataClassRepository;
    private final AuditRecorder auditRecorder;

    @Transactional
    public DataClass defineDataClass(DataClassDefinition definition) {
        String preClassification = definition.getClassification();
        DetokenizeMode preDetokenizable = definition.getDetokenizable();
        var preRetention = definition.getRetentionGroup();

        // Let registered vault.data-class hooks contribute validation and masking rules.
        hookDispatcher.dispatch(definition);

        // Enforce section 7 invariant: hooks may never lower classification, retention, or access protections.
        if (preDetokenizable != null && definition.getDetokenizable() != null
                && detokenizeStrictness(definition.getDetokenizable()) < detokenizeStrictness(preDetokenizable)) {
            throw VaultApiException.unprocessable("EXTENSION_LOWERED_PROTECTION",
                    "An extension tried to lower a protection");
        }
        if (preClassification != null && definition.getClassification() != null
                && classificationRank(definition.getClassification()) < classificationRank(preClassification)) {
            throw VaultApiException.unprocessable("EXTENSION_LOWERED_PROTECTION",
                    "An extension tried to lower a protection");
        }
        if (preRetention != null && definition.getRetentionGroup() != preRetention) {
            throw VaultApiException.unprocessable("EXTENSION_LOWERED_PROTECTION",
                    "An extension tried to lower a protection");
        }
        if (definition.getName() != null
                && NEVER_DETOKENIZABLE_CLASSES.contains(definition.getName().toUpperCase())
                && definition.getDetokenizable() == DetokenizeMode.YES) {
            throw VaultApiException.unprocessable("CLASS_FORBIDS_OPERATION",
                    "This operation is not allowed for this class");
        }

        DataClass dataClass = DataClass.builder()
                .id(UUID.randomUUID())
                .name(definition.getName().toUpperCase())
                .valueKind(definition.getValueKind())
                .validation(definition.getValidation())
                .maskingRules(definition.getMaskingRules())
                .classification(definition.getClassification())
                .retentionGroup(definition.getRetentionGroup())
                .hasLookupHash(definition.getHasLookupHash())
                .blindIndex(definition.getBlindIndex())
                .fpe(definition.getFpe())
                .detokenizable(definition.getDetokenizable())
                .allowedPurposes(definition.getAllowedPurposes())
                .version(definition.getVersion() > 0 ? definition.getVersion() : 1)
                .status(definition.getStatus() != null ? definition.getStatus() : "ACTIVE")
                .build();

        DataClass saved = dataClassRepository.save(dataClass);
        auditRecorder.recordStrict("VAULT_DATA_CLASS_DEFINED", "DEFINE_DATA_CLASS", saved.getName(), null,
                "GENERAL", null, null, 1, "SUCCESS", saved.getId(), null);
        return saved;
    }

    @Transactional
    public DataClassResponse defineDataClass(DefineDataClassRequest request) {
        DataClassDefinition definition = DataClassDefinition.builder()
                .name(request.getName())
                .valueKind(request.getValueKind())
                .classification(request.getClassification())
                .retentionGroup(request.getRetentionGroup())
                .hasLookupHash(request.getHasLookupHash())
                .blindIndex(request.getBlindIndex())
                .fpe(request.getFpe())
                .detokenizable(request.getDetokenizable())
                .allowedPurposes(request.getAllowedPurposes())
                .version(request.getVersion() != null ? request.getVersion() : 1)
                .status(request.getStatus())
                .build();

        DataClass saved = defineDataClass(definition);
        return toResponse(saved);
    }

    private int classificationRank(String classification) {
        if (classification == null) return 0;
        return switch (classification.trim().toUpperCase()) {
            case "RESTRICTED" -> 4;
            case "CONFIDENTIAL" -> 3;
            case "INTERNAL" -> 2;
            case "PUBLIC" -> 1;
            default -> 2;
        };
    }

    private int detokenizeStrictness(DetokenizeMode mode) {
        if (mode == null) return 0;
        return switch (mode) {
            case NO -> 5;
            case MASKED_ONLY -> 4;
            case NAME_MATCH_ONLY -> 3;
            case VIEW_REVIEWERS_ONLY -> 2;
            case YES -> 1;
        };
    }

    private DataClassResponse toResponse(DataClass dataClass) {
        return DataClassResponse.builder()
                .id(dataClass.getId())
                .name(dataClass.getName())
                .valueKind(dataClass.getValueKind())
                .classification(dataClass.getClassification())
                .retentionGroup(dataClass.getRetentionGroup())
                .detokenizable(dataClass.getDetokenizable())
                .allowedPurposes(dataClass.getAllowedPurposes())
                .version(dataClass.getVersion())
                .status(dataClass.getStatus())
                .build();
    }
}
