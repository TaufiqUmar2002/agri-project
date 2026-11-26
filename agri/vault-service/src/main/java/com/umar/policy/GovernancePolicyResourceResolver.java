package com.umar.policy;

import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

/**
 * Resolves the policy resource for governance/administrative operations.
 */
@Component
public class GovernancePolicyResourceResolver implements PolicyResourceResolver {

    private static final String GOVERNANCE_RESOURCE = "GOVERNANCE";
    private static final String MANAGEMENT_PURPOSE = "GENERAL";

    private static final Set<String> GOVERNANCE_METHODS = Set.of(
            "rotateKey",
            "breakGlass",
            "useBreakGlass",
            "closeBreakGlass",
            "eraseSubject",
            "createLegalHold",
            "liftLegalHold",
            "erasureException",
            "getSubjectInventory",
            "getAccessLog",
            "verifyBackup",
            "createPolicy",
            "listPolicies",
            "requestApproval",
            "listApprovals",
            "updateApprovalDecision"
    );

    @Override
    public boolean supports(Method method) {
        return GOVERNANCE_METHODS.contains(method.getName());
    }

    @Override
    public List<PolicyResource> resolve(Object[] arguments) {
        return List.of(new PolicyResource(GOVERNANCE_RESOURCE, MANAGEMENT_PURPOSE));
    }
}
