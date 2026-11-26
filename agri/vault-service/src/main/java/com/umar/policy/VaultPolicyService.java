package com.umar.policy;

import com.umar.authentication.VaultSecurityContext;
import com.umar.authentication.WorkloadIdentity;
import com.umar.exception.VaultPolicyDeniedException;
import com.umar.repository.dataProtection.AccessPolicyRepository;
import com.umar.security.VaultOperationRoles;
import com.umar.security.VaultRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class VaultPolicyService {

    private static final Set<String> GOVERNANCE_RESOURCES = Set.of("GOVERNANCE", "DATA_CLASS");

    private final AccessPolicyRepository accessPolicyRepository;
    private final PolicyDenialAuditService denialAuditService;

    @Transactional(readOnly = true)
    public void authorize(String operation, List<PolicyResource> resources) {
        WorkloadIdentity identity = VaultSecurityContext.getWorkloadIdentity();
        String caller = identity != null ? identity.serviceName() : "UNKNOWN";
        if (resources == null || resources.isEmpty()) {
            throw new IllegalArgumentException("No policy resources found");
        }

        // Human governance roles (Security Officer, Compliance Officer, Key Custodian, Auditor, SRE)
        // on governance endpoints are authorized by @RequireVaultRole / VaultOperationRoles (section 2.11).
        if (isHumanGovernanceAuthorized(identity, operation, resources)) {
            return;
        }

        // Validate the complete batch before processing anything (default deny).
        for (PolicyResource resource : resources) {
            boolean allowed = accessPolicyRepository.isAllowed(
                    caller,
                    resource.dataClass(),
                    resource.purpose(),
                    operation
            );
            if (!allowed) {
                denialAuditService.recordDenial(
                        caller,
                        operation,
                        resource.dataClass(),
                        resource.purpose(),
                        "POLICY_NOT_FOUND_OR_INACTIVE"
                );

                throw new VaultPolicyDeniedException(
                        "Vault policy denied for caller: "
                                + caller
                                + ", operation: " + operation
                                + ", dataClass: " + resource.dataClass()
                                + ", purpose: " + resource.purpose()
                );
            }
        }
    }

    private boolean isHumanGovernanceAuthorized(WorkloadIdentity identity, String operation, List<PolicyResource> resources) {
        if (identity == null || identity.roles() == null) {
            return false;
        }
        boolean allGovernance = resources.stream().allMatch(r -> GOVERNANCE_RESOURCES.contains(r.dataClass()));
        if (!allGovernance || !VaultOperationRoles.isRoleGated(operation)) {
            return false;
        }
        Set<VaultRole> allowed = VaultOperationRoles.allowedRoles(operation);
        for (String rawRole : identity.roles()) {
            if ("SERVICE".equalsIgnoreCase(rawRole)) {
                continue;
            }
            try {
                VaultRole role = VaultRole.valueOf(rawRole.trim().toUpperCase().replace('-', '_').replace(' ', '_'));
                if (allowed.contains(role)) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return false;
    }
}
