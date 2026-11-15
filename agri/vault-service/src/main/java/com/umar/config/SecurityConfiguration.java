//package com.umar.config;
//
//
//
//
//import com.umar.authentication.VaultIdentityFilter;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.security.config.Customizer;
//import org.springframework.security.config.annotation.web.builders.HttpSecurity;
//import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
//import org.springframework.security.config.http.SessionCreationPolicy;
//import org.springframework.security.web.SecurityFilterChain;
//import org.springframework.security.web.authentication
//        .AnonymousAuthenticationFilter;
//
//@Configuration
//public class SecurityConfiguration {
//
//    private final VaultIdentityFilter identityFilter;
//
//    public SecurityConfiguration(
//            VaultIdentityFilter identityFilter) {
//        this.identityFilter = identityFilter;
//    }
//
//    @Bean
//    public SecurityFilterChain securityFilterChain(
//            HttpSecurity http) throws Exception {
//
//        return http
//                .csrf(AbstractHttpConfigurer::disable)
//
//                .httpBasic(AbstractHttpConfigurer::disable)
//
//                .formLogin(AbstractHttpConfigurer::disable)
//
//                .logout(AbstractHttpConfigurer::disable)
//
//                .sessionManagement(session ->
//                        session.sessionCreationPolicy(
//                                SessionCreationPolicy.STATELESS))
//
//                .authorizeHttpRequests(auth -> auth
//                        .requestMatchers("/actuator/health")
//                        .authenticated()
//                        .anyRequest()
//                        .authenticated()
//                )
//
//                .addFilterBefore(
//                        identityFilter,
//                        AnonymousAuthenticationFilter.class
//                )
//
//                .build();
//    }
//}
