package com.umar.farmer.config;

import com.umar.integration.ExtensionHookProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FarmerExtensionHooks implements ExtensionHookProvider {

    @Override
    public String serviceName() {
        return "farmer-profile";
    }

    @Override
    public List<ExtensionHookDescriptor> extensionPoints() {
        return List.of(
                new ExtensionHookDescriptor("farmer.pre-update", "PRE", "1.0", "Validate profile update hook", false),
                new ExtensionHookDescriptor("farmer.enrich-profile", "ENRICH", "1.0", "Enrich farmer profile attributes", false),
                new ExtensionHookDescriptor("farmer.post-enroll", "POST", "1.0", "Post-enrollment notification hook", false)
        );
    }
}
