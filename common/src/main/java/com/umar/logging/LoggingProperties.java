package com.umar.logging;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "agreeshield.logging")
public class LoggingProperties {
    private boolean enabled = true;
    private boolean preferDb = true;
    private String moduleName;
    private boolean infoEnabled = true;
    private boolean debugEnabled = false;
    private boolean warnEnabled = true;
    private boolean exceptionEnabled = true;
    private boolean requestResponseEnabled = true;
    private boolean securityEnabled = true;
    private boolean externalCallEnabled = true;
}
