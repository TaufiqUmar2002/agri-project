package com.umar.iot.config;

import com.umar.integration.ExtensionHookProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IotExtensionHooks implements ExtensionHookProvider {

    @Override
    public String serviceName() {
        return "iot-service";
    }

    @Override
    public List<ExtensionHookDescriptor> extensionPoints() {
        return List.of(
                new ExtensionHookDescriptor("iot.pre-ingest", "PRE", "1.0", "Pre-ingestion sensor sanity check", false),
                new ExtensionHookDescriptor("iot.qa-transform", "TRANSFORM", "1.0", "Telemetry QA transformation hook", false),
                new ExtensionHookDescriptor("iot.post-calibrate", "POST", "1.0", "Post-calibration notification hook", false)
        );
    }
}
