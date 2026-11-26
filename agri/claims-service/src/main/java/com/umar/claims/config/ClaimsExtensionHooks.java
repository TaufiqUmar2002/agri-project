package com.umar.claims.config;

import com.umar.integration.ExtensionHookProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ClaimsExtensionHooks implements ExtensionHookProvider {

    @Override
    public String serviceName() {
        return "claims-service";
    }

    @Override
    public List<ExtensionHookDescriptor> extensionPoints() {
        return List.of(
                new ExtensionHookDescriptor("claim.validator", "VALIDATE", "1.0", "Partner validation check on claims", false),
                new ExtensionHookDescriptor("claim.pre-approve", "PRE", "1.0", "Pre-approval fraud/review gate", true),
                new ExtensionHookDescriptor("claim.post-settle", "POST", "1.0", "Post-settlement claims hook", false)
        );
    }
}
