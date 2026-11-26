package com.umar.policy;

import com.umar.entity.dataProtection.VaultToken;
import com.umar.payload.request.vault.DeTokenizeRequest;
import com.umar.repository.dataProtection.VaultTokenRepository;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/**
 * Resolves the policy resources for {@code processDeTokenization}. Each token's
 * data class is looked up from the token store so the aspect can authorize the
 * DETOKENIZE operation per data class before the service decrypts anything.
 * Tokens that cannot be resolved are skipped here; the service reports them as
 * TOKEN_NOT_FOUND.
 */
@Component
public class DetokenizePolicyResourceResolver implements PolicyResourceResolver {

    private final VaultTokenRepository tokenRepository;

    public DetokenizePolicyResourceResolver(VaultTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    @Override
    public boolean supports(Method method) {
        return method.getName().equals("processDeTokenization");
    }

    @Override
    public List<PolicyResource> resolve(Object[] arguments) {
        for (Object argument : arguments) {
            if (argument instanceof DeTokenizeRequest request) {
                return request.getTokens().stream()
                        .map(tokenRepository::findByToken)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .map(VaultToken::getDataClass)
                        .distinct()
                        .map(dataClass -> new PolicyResource(dataClass, request.getPurpose()))
                        .toList();
            }
        }
        throw new IllegalArgumentException("DeTokenizeRequest not found");
    }
}