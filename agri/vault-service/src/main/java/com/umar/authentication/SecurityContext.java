//package com.umar.authentication;
//
//import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
//import org.springframework.security.core.Authentication;
//import org.springframework.security.core.authority.SimpleGrantedAuthority;
//import org.springframework.security.core.context.SecurityContextHolder;
//import java.util.stream.Collectors;
//
//public final class SecurityContext {
//
//    private SecurityContext() {
//        // Static utility class pattern
//    }
//
//    /**
//     * Helper to read the current validated WorkloadIdentity from the local thread.
//     */
//    public static WorkloadIdentity getWorkloadIdentity() {
//        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
//        if (auth != null && auth.getPrincipal() instanceof WorkloadIdentity identity) {
//            return identity;
//        }
//        return null;
//    }
//
//    /**
//     * Converts a validated WorkloadIdentity into a Spring Security valid Authentication Token.
//     */
//    public static Authentication createAuthentication(WorkloadIdentity identity) {
//        var authorities = identity.roles().stream()
//                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
//                .collect(Collectors.toSet());
//
//        // Ensure default machine client authority is provided if empty
//        if (authorities.isEmpty()) {
//            authorities.add(new SimpleGrantedAuthority("ROLE_WORKLOAD"));
//        }
//
//        return new UsernamePasswordAuthenticationToken(
//                identity,
//                null,
//                authorities
//        );
//    }
//}
