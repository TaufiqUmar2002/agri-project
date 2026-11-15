//package com.umar.authentication;
//
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
//import org.springframework.security.config.annotation.web.builders.HttpSecurity;
//import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
//import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
//import org.springframework.security.core.userdetails.UserDetails;
//import org.springframework.security.core.userdetails.UsernameNotFoundException;
//import org.springframework.security.web.SecurityFilterChain;
//import java.security.cert.X509Certificate;
//import java.util.Collections;
//
//@Configuration
////@EnableWebSecurity
////@EnableMethodSecurity
//public class MtlsAuthenticationConfig {
//
//    private final CertificateIdentityResolver identityResolver;
//
//    public MtlsAuthenticationConfig(CertificateIdentityResolver identityResolver) {
//        this.identityResolver = identityResolver;
//    }
//
//    @Bean
//    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
//        http
//                .csrf(AbstractHttpConfigurer::disable)
//                .authorizeHttpRequests(auth -> auth
//                        .requestMatchers("/api/v1/tokens").hasRole("WORKLOAD")
//                        .anyRequest().denyAll()
//                )
//                .x509(x509 -> x509
//                        .subjectPrincipalRegex("CN=(.*?)(?:,|$)")
//                        .authenticationUserDetailsService(token -> {
//                            X509Certificate[] certs = (X509Certificate[]) token.getCredentials();
//                            if (certs == null || certs.length == 0) {
//                                throw new UsernameNotFoundException("No valid client certificate found.");
//                            }
//
//                            // Trigger your resolver engine pipeline
//                            WorkloadIdentity identity = identityResolver.resolve(certs[0]);
//
//                            // Wrap inside custom user payload
//                            return org.springframework.security.core.userdetails.User
//                                    .withUsername(identity.serviceName())
//                                    .password("") // Stateless mTLS execution
//                                    .authorities("ROLE_WORKLOAD")
//                                    .build();
//                        })
//                );
//
//        return http.build();
//    }
//}
