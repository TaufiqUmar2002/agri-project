package com.umar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FarmerProfileApplication {
    public static void main(String[] args) {
        SpringApplication.run(FarmerProfileApplication.class, args);
    }
}
