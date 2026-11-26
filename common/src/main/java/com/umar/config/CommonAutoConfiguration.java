package com.umar.config;

import com.umar.crypto.VaultCryptoService;
import com.umar.exception.GlobalExceptionHandler;
import com.umar.integration.ExtensionHookCoordinator;
import com.umar.integration.IntegrationHubFeignClient;
import com.umar.integration.IntegrationPeerClient;
import com.umar.logging.CentralLoggingFilter;
import com.umar.logging.CentralLoggingService;
import com.umar.logging.LoggingConfigController;
import com.umar.logging.LoggingProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@EnableFeignClients(clients = IntegrationHubFeignClient.class)
@EnableConfigurationProperties(LoggingProperties.class)
@Import({
        ValidationConfig.class,
        GlobalExceptionHandler.class,
        VaultCryptoService.class,
        ExtensionHookCoordinator.class,
        IntegrationPeerClient.class,
        CentralLoggingService.class,
        CentralLoggingFilter.class,
        LoggingConfigController.class
})
public class CommonAutoConfiguration {
}
