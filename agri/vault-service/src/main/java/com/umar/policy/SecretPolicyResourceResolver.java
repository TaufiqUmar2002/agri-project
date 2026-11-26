package com.umar.policy;

import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;

@Component
public class SecretPolicyResourceResolver implements PolicyResourceResolver {

    private static final String SECRET_DATA_CLASS = "SECRET";
    private static final String MANAGEMENT_PURPOSE = "GENERAL";

    @Override
    public boolean supports(Method method) {
        return method.getName().equals("createSecret")
                || method.getName().equals("useSecret")
                || method.getName().equals("useSecretPost")
                || method.getName().equals("getSecretMetadata")
                || method.getName().equals("signPayload");
    }

    @Override
    public List<PolicyResource> resolve(Object[] arguments) {
        return List.of(new PolicyResource(SECRET_DATA_CLASS, MANAGEMENT_PURPOSE));
    }
}
