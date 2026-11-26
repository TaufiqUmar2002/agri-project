package com.umar.iot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "iot")
public class IotProperties {
    private Kafka kafka = new Kafka();
    private Rules rules = new Rules();

    @Data
    public static class Kafka {
        private boolean enabled = false;
        private String observationTopic = "observation.sensor";
        private String iotTopic = "platform.iot.events";
    }

    @Data
    public static class Rules {
        private int maxPayloadBytes = 8192;
        private int maxPerPlotType = 3;
        private double rainGaugeMaxDistanceMeters = 500.0;
        private double rainGaugeWarnDistanceMeters = 150.0;
    }
}
