//package com.umar.audit.config;
//
//
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//import java.net.URI;
//
//@Configuration
//public class AuditStorageConfig {
//    @Bean
//    @ConditionalOnProperty(name = "vault.audit.sink", havingValue = "worm")
//    S3Client auditS3Client(
//            @Value("${vault.audit.worm.endpoint}") String endpoint,
//            @Value("${vault.audit.worm.region:us-east-1}") String region,
//            @Value("${vault.audit.worm.access-key}") String accessKey,
//            @Value("${vault.audit.worm.secret-key}") String secretKey,
//            @Value("${vault.audit.worm.path-style:true}") boolean pathStyle) {
//        return S3Client.builder()
//                .endpointOverride(URI.create(endpoint))
//                .region(Region.of(region))
//                .credentialsProvider(StaticCredentialsProvider.create(
//                        AwsBasicCredentials.create(accessKey, secretKey)))
//                .serviceConfiguration(S3Configuration.builder()
//                        .pathStyleAccessEnabled(pathStyle).build())
//                .build();
//    }
//}
