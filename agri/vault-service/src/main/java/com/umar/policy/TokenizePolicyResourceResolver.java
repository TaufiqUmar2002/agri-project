package com.umar.policy;


import com.umar.payload.request.vault.TokenizeRequest;
import org.springframework.stereotype.Component;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class TokenizePolicyResourceResolver implements PolicyResourceResolver {

    @Override
    public boolean supports(Method method) {
        return method.getName().equals("processTokenizations");
    }

    @Override
    public List<PolicyResource> resolve(Object[] arguments) {
        TokenizeRequest request = null;
        for (Object argument : arguments) {
            if (argument instanceof TokenizeRequest tokenizeRequest) {
                request = tokenizeRequest;
                break;
            }
        }
        if (request == null) {
            throw new IllegalArgumentException("TokenizeRequest not found");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Tokenize items are required");
        }
        List<PolicyResource> list = new ArrayList<>();
        Set<PolicyResource> uniqueValues = new HashSet<>();
        for (TokenizeRequest.Items item : request.getItems()) {
            PolicyResource policyResource = new PolicyResource(item.getDataClass(), request.getPurpose());
            if (uniqueValues.add(policyResource)) {
                list.add(policyResource);
            }
        }
        return list;
    }
}
