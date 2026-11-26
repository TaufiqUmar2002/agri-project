package com.umar.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({
        SecurityToggleService.class,
        CentralSecurityFilter.class,
        SecurityConfigController.class
})
public class CommonSecurityAutoConfiguration {
}
