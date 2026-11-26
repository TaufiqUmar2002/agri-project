package com.umar.cryptography;

import com.umar.audit.AuditRecorder;
import com.umar.blindindex.BlindIndexService;
import com.umar.crypto.ValidationRegistryService;
import com.umar.entity.dataProtection.DataClass;
import com.umar.exception.VaultApiException;
import com.umar.payload.request.vault.HashRequest;
import com.umar.payload.response.vault.HashResponse;
import com.umar.policy.PolicyResource;
import com.umar.policy.VaultPolicyService;
import com.umar.repository.dataProtection.DataClassRepository;
import com.umar.service.AccessSignalEmitter;
import com.umar.service.VaultEventEmitter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * VLT-11 Hash Operation (also exposed via REST HashController).
 * Computes keyed HMAC-SHA256 blind-index hashes for every active/retiring key version
 * so callers can perform duplicate/shared-wallet lookups without holding keys or raw values.
 */
@Service
@RequiredArgsConstructor
public class HashService {

    private final BlindIndexService blindIndexService;
    private final DataClassRepository dataClassRepository;
    private final ValidationRegistryService validationRegistryService;
    private final VaultPolicyService vaultPolicyService;
    private final AuditRecorder auditRecorder;
    private final AccessSignalEmitter accessSignalEmitter;
    private final VaultEventEmitter vaultEventEmitter;

    public HashResponse computeHashes(HashRequest request) {
        if (request == null || request.getValues() == null || request.getValues().isEmpty()) {
            throw VaultApiException.badRequest("VALIDATION_ERROR", "Values are required");
        }
        if (request.getValues().size() > 100) {
            throw VaultApiException.payloadTooLarge("BATCH_TOO_LARGE", "Maximum 100 values allowed", null);
        }

        String dataClass = request.getDataClass().trim().toUpperCase();
        vaultPolicyService.authorize("HASH", List.of(new PolicyResource(dataClass, request.getPurpose())));

        Optional<DataClass> dcOpt = dataClassRepository.findByNameAndStatus(dataClass, "ACTIVE");
        if (dcOpt.isPresent() && Boolean.FALSE.equals(dcOpt.get().getHasLookupHash())) {
            throw VaultApiException.unprocessable("FAILED_PRECONDITION",
                    "Data class " + dataClass + " does not allow lookup hashes");
        }

        List<HashResponse.ValueHashResult> results = new ArrayList<>();
        for (int i = 0; i < request.getValues().size(); i++) {
            String raw = request.getValues().get(i);
            String canonical = validationRegistryService.canonicalize(raw, dataClass);
            List<BlindIndexService.CalculatedIndex> indexes = blindIndexService.generateIndexes(dataClass, canonical);
            List<HashResponse.HashEntry> entries = indexes.stream()
                    .map(idx -> HashResponse.HashEntry.builder()
                            .hash(HexFormat.of().formatHex(idx.hash()))
                            .keyVersion(idx.keyVersion())
                            .build())
                    .toList();
            results.add(HashResponse.ValueHashResult.builder()
                    .valueIndex(i)
                    .entries(entries)
                    .build());
        }

        String requestId = "vq_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        auditRecorder.recordStrict("VAULT_HASHED", "HASH", dataClass, request.getPurpose(),
                "SUCCESS", null, null);
        accessSignalEmitter.emit("HASH", dataClass, request.getPurpose(), "SUCCESS", results.size());
        vaultEventEmitter.emitAccess("HASH", dataClass, request.getPurpose(), "SUCCESS", results.size());

        return HashResponse.builder()
                .hashes(results)
                .requestId(requestId)
                .build();
    }
}
