package com.umar.config;

import com.umar.integration.ExtensionHookProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VaultExtensionHooks implements ExtensionHookProvider {

    @Override
    public String serviceName() {
        return "vault-service";
    }

    @Override
    public List<ExtensionHookDescriptor> extensionPoints() {
        return List.of(
                new ExtensionHookDescriptor("vault.pre-tokenize", "VALIDATE", "1.0", "Pre-tokenization classification check", false),
                new ExtensionHookDescriptor("vault.post-detokenize", "POST", "1.0", "Post-detokenization audit hook", true),
                new ExtensionHookDescriptor("vault.pre-erase", "VALIDATE", "1.0", "Legal hold check prior to erasure", true)
        );
    }
}
