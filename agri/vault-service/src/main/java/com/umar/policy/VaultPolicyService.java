package com.umar.policy;



import com.umar.exception.VaultPolicyDeniedException;
import com.umar.repository.dataProtection.AccessPolicyRepository;
import com.umar.security.ServiceIdentity;
import com.umar.security.ServiceIdentityProvider;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VaultPolicyService {

    private final AccessPolicyRepository accessPolicyRepository;
    private final ServiceIdentityProvider identityProvider;
    private final PolicyDenialAuditService denialAuditService;

    @Transactional(readOnly = true)
    public void authorize(String operation, List<PolicyResource> resources) {
        ServiceIdentity identity = identityProvider.getCurrentIdentity();
        String caller = identity.serviceName();
        if (resources == null || resources.isEmpty()) {
            throw new IllegalArgumentException("No policy resources found");
        }
        // Validate the complete batch before processing anything.
        for (PolicyResource resource : resources) {
            boolean allowed = accessPolicyRepository.isAllowed(caller,
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
}
