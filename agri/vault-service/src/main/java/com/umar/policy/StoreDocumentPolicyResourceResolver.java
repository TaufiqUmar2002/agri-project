package com.umar.policy;

import com.umar.payload.request.vault.StoreDocumentRequest;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;

@Component
public class StoreDocumentPolicyResourceResolver implements PolicyResourceResolver {

    @Override
    public boolean supports(Method method) {
        return method.getName().equals("storeDocument")
                || method.getName().equals("storeMultipartDocument");
    }

    @Override
    public List<PolicyResource> resolve(Object[] arguments) {
        for (Object argument : arguments) {
            if (argument instanceof StoreDocumentRequest request) {
                return List.of(new PolicyResource(request.getDataClass(), request.getPurpose()));
            }
        }
        throw new IllegalArgumentException("StoreDocumentRequest not found");
    }
}
