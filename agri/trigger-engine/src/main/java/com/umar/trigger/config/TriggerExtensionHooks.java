package com.umar.trigger.config;

import com.umar.integration.ExtensionHookProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TriggerExtensionHooks implements ExtensionHookProvider {

    @Override
    public String serviceName() {
        return "trigger-engine";
    }

    @Override
    public List<ExtensionHookDescriptor> extensionPoints() {
        return List.of(
                new ExtensionHookDescriptor("trigger.data-source", "ADAPTER", "1.0", "Custom index observation adapter", false),
                new ExtensionHookDescriptor("trigger.index-enrich", "ENRICH", "1.0", "Index evaluation enrichment hook", false),
                new ExtensionHookDescriptor("trigger.post-decision", "POST", "1.0", "Post-trigger decision notification", false)
        );
    }
}
