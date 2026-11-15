package com.umar.policy;



import com.umar.authentication.WorkloadIdentity;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PolicyEngine {

    private final Map<String, DataClassMetadata> schemaRegistry = new ConcurrentHashMap<>();

    public PolicyEngine() {
        seedInitialSchemaRegistry();
    }

    /**
     * Asserts policy rules against the active data class matrix.
     */
    public boolean evaluate(WorkloadIdentity caller, String dataClass, String purpose, String operation) {
        // 1. Fail Fast: Decline processing immediately if the calling context is unmapped
        if (caller == null || dataClass == null || purpose == null || operation == null) {
            return false;
        }

        DataClassMetadata metadata = schemaRegistry.get(dataClass);

        // 2. Default Deny: Reject if data schema doesn't exist or isn't set to active
        if (metadata == null || !"ACTIVE".equalsIgnoreCase(metadata.status())) {
            return false;
        }

        // 3. Operational Vector Check: Verify if data class allows detokenization extraction
        if ("DETOKENIZE".equalsIgnoreCase(operation) && !metadata.detokenizable()) {
            return false;
        }

        // 4. Purpose Bound Check: Validate intent matches policy whitelist profiles
        if (!metadata.allowedPurposes().contains(purpose)) {
            return false;
        }

        // 5. Role Validation: Ensure caller belongs to the verified workload network tier
        if (!caller.roles().contains("WORKLOAD")) {
            return false;
        }

        return true;
    }

    private void seedInitialSchemaRegistry() {
        schemaRegistry.put("farmer.national_id", new DataClassMetadata(
                "farmer.national_id",
                true,
                Set.of("PURPOSE_KYC_VERIFICATION", "PURPOSE_COMPLIANCE_AUDIT"),
                "ACTIVE"
        ));

        schemaRegistry.put("farmer.phone_number", new DataClassMetadata(
                "farmer.phone_number",
                true,
                Set.of("PURPOSE_EMERGENCY_DISBURSEMENT"),
                "ACTIVE"
        ));

        schemaRegistry.put("farmer.biometric_template", new DataClassMetadata(
                "farmer.biometric_template",
                false, // Strictly unresolvable (Match-Only design pattern)
                Set.of("PURPOSE_KYC_VERIFICATION"),
                "ACTIVE"
        ));
    }

    private record DataClassMetadata(
            String name,
            boolean detokenizable,
            Set<String> allowedPurposes,
            String status
    ) {}
}
