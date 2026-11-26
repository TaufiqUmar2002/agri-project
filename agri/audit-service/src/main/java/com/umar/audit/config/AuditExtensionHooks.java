package com.umar.audit.config;

import com.umar.integration.ExtensionHookProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AuditExtensionHooks implements ExtensionHookProvider {

    @Override
    public String serviceName() {
        return "audit-service";
    }

    @Override
    public List<ExtensionHookDescriptor> extensionPoints() {
        return List.of(
                new ExtensionHookDescriptor("internal.audit-enrich", "ENRICH", "1.0", "Internal audit record enrichment", false),
                new ExtensionHookDescriptor("audit.control-rule", "VALIDATE", "1.0", "Custom compliance control rule", true),
                new ExtensionHookDescriptor("audit.post-finding", "POST", "1.0", "Post-finding alert dispatch hook", false)
        );
    }
}
