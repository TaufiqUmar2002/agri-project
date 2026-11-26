package com.umar.auth.config;

import com.umar.integration.ExtensionHookProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AuthExtensionHooks implements ExtensionHookProvider {

    @Override
    public String serviceName() {
        return "auth-service";
    }

    @Override
    public List<ExtensionHookDescriptor> extensionPoints() {
        return List.of(
                new ExtensionHookDescriptor("auth.pre-register", "PRE", "1.0", "Pre-registration blocklist check", false),
                new ExtensionHookDescriptor("auth.enrich-token", "ENRICH", "1.0", "Enrich issued access token claims", false),
                new ExtensionHookDescriptor("auth.post-login", "POST", "1.0", "Post-login security hook", true)
        );
    }
}
