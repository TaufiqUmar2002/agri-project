package com.umar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PlotRegistryApplication {
    public static void main(String[] args) {
        SpringApplication.run(PlotRegistryApplication.class, args);
    }
}
