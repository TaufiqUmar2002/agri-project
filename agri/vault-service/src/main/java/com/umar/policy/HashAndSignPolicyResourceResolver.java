package com.umar.policy;

import com.umar.payload.request.vault.HashRequest;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;

@Component
public class HashAndSignPolicyResourceResolver implements PolicyResourceResolver {

    @Override
    public boolean supports(Method method) {
        return method.getName().equals("computeHashes");
    }

    @Override
    public List<PolicyResource> resolve(Object[] arguments) {
        for (Object arg : arguments) {
            if (arg instanceof HashRequest req) {
                return List.of(new PolicyResource(req.getDataClass().toUpperCase(), req.getPurpose()));
            }
        }
        return List.of(new PolicyResource("PHONE", "GENERAL"));
    }
}
