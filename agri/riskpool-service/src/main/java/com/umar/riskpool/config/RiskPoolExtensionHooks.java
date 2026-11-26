package com.umar.riskpool.config;

import com.umar.integration.ExtensionHookProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RiskPoolExtensionHooks implements ExtensionHookProvider {

    @Override
    public String serviceName() {
        return "riskpool-service";
    }

    @Override
    public List<ExtensionHookDescriptor> extensionPoints() {
        return List.of(
                new ExtensionHookDescriptor("riskpool.capital-check", "VALIDATE", "1.0", "Capital adequacy solvency check", true),
                new ExtensionHookDescriptor("riskpool.pre-cede", "PRE", "1.0", "Reinsurance cession hook", false),
                new ExtensionHookDescriptor("riskpool.post-close", "POST", "1.0", "Period close ledger hook", false)
        );
    }
}
