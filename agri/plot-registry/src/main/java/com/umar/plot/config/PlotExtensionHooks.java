package com.umar.plot.config;

import com.umar.integration.ExtensionHookProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PlotExtensionHooks implements ExtensionHookProvider {

    @Override
    public String serviceName() {
        return "plot-registry";
    }

    @Override
    public List<ExtensionHookDescriptor> extensionPoints() {
        return List.of(
                new ExtensionHookDescriptor("plot.enrich", "ENRICH", "1.0", "Enrich registered plot attributes", false),
                new ExtensionHookDescriptor("plot.post-verify", "POST", "1.0", "Post-verification plot hook", false),
                new ExtensionHookDescriptor("plot.pre-transfer", "VALIDATE", "1.0", "Pre-transfer ownership validation", true)
        );
    }
}
