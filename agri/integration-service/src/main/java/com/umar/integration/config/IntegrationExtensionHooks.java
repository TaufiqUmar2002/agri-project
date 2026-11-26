package com.umar.integration.config;

import com.umar.integration.ExtensionHookProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IntegrationExtensionHooks implements ExtensionHookProvider {

    @Override
    public String serviceName() {
        return "integration-hub-service";
    }

    @Override
    public List<ExtensionHookDescriptor> extensionPoints() {
        return List.of(
                new ExtensionHookDescriptor("hub.connector-adapter", "ADAPTER", "1.0", "Custom inbound connector adapter", false),
                new ExtensionHookDescriptor("hub.mapping-transform", "TRANSFORM", "1.0", "Canonical record transformation hook", false),
                new ExtensionHookDescriptor("hub.pre-dispatch", "TRANSFORM", "1.0", "Webhook payload redaction hook", false)
        );
    }
}
