package com.umar.policy;

import com.umar.payload.request.vault.DefineDataClassRequest;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Resolves the policy resource for {@code defineDataClass}. Defining a data
 * class is a governance operation over the data-class registry rather than
 * access to a specific data class, so it is gated on the fixed
 * {@code DATA_CLASS} registry resource.
 */
@Component
public class DefineDataClassPolicyResourceResolver implements PolicyResourceResolver {

    private static final String REGISTRY_DATA_CLASS = "DATA_CLASS";
    private static final String MANAGEMENT_PURPOSE = "GENERAL";

    @Override
    public boolean supports(Method method) {
        return method.getName().equals("defineDataClass");
    }

    @Override
    public List<PolicyResource> resolve(Object[] arguments) {
        for (Object argument : arguments) {
            if (argument instanceof DefineDataClassRequest) {
                return List.of(new PolicyResource(REGISTRY_DATA_CLASS, MANAGEMENT_PURPOSE));
            }
        }
        throw new IllegalArgumentException("DefineDataClassRequest not found");
    }
}
