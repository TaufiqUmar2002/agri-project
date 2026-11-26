package com.umar.billing.config;

import com.umar.integration.ExtensionHookProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BillingExtensionHooks implements ExtensionHookProvider {

    @Override
    public String serviceName() {
        return "premium-billing";
    }

    @Override
    public List<ExtensionHookDescriptor> extensionPoints() {
        return List.of(
                new ExtensionHookDescriptor("billing.settlement-parser", "ADAPTER", "1.0", "Partner settlement file parser", false),
                new ExtensionHookDescriptor("billing.pre-writeoff", "VALIDATE", "1.0", "Pre-writeoff financial control", true),
                new ExtensionHookDescriptor("billing.post-payment", "POST", "1.0", "Post-payment webhook/ledger event", false)
        );
    }
}
