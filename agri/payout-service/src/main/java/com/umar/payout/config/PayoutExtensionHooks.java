package com.umar.payout.config;

import com.umar.integration.ExtensionHookProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PayoutExtensionHooks implements ExtensionHookProvider {

    @Override
    public String serviceName() {
        return "payout-service";
    }

    @Override
    public List<ExtensionHookDescriptor> extensionPoints() {
        return List.of(
                new ExtensionHookDescriptor("payout.disburse", "POST", "1.0", "Protected disbursement control hook", true),
                new ExtensionHookDescriptor("payout.route-select", "PRE", "1.0", "Payout rail selection check", false),
                new ExtensionHookDescriptor("payout.post-reconcile", "POST", "1.0", "Post-reconciliation settlement hook", false)
        );
    }
}
