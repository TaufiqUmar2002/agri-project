package com.umar.policy.config;

import com.umar.integration.ExtensionHookProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PolicyExtensionHooks implements ExtensionHookProvider {

    @Override
    public String serviceName() {
        return "policy-service";
    }

    @Override
    public List<ExtensionHookDescriptor> extensionPoints() {
        return List.of(
                new ExtensionHookDescriptor("policy.pre-quote", "PRE", "2.1", "Pre-quote pricing/loading hook", false),
                new ExtensionHookDescriptor("policy.custom-attributes", "ENRICH", "1.1", "Custom policy x_ attributes hook", false),
                new ExtensionHookDescriptor("policy.pre-activate-check", "PRE", "1.0", "Pre-activation eligibility check", false)
        );
    }
}
