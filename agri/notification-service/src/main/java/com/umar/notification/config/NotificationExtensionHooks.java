package com.umar.notification.config;

import com.umar.integration.ExtensionHookProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NotificationExtensionHooks implements ExtensionHookProvider {

    @Override
    public String serviceName() {
        return "notification-service";
    }

    @Override
    public List<ExtensionHookDescriptor> extensionPoints() {
        return List.of(
                new ExtensionHookDescriptor("notification.pre-send", "PRE", "1.0", "Pre-dispatch suppression/preference check", false),
                new ExtensionHookDescriptor("notification.render-enrich", "ENRICH", "1.0", "Template variable enrichment", false),
                new ExtensionHookDescriptor("notification.post-deliver", "POST", "1.0", "Post-delivery callback hook", false)
        );
    }
}
