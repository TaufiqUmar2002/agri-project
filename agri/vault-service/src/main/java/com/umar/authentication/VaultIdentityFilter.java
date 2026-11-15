//package com.umar.authentication;
//
//
//
//
//import jakarta.servlet.FilterChain;
//import jakarta.servlet.ServletException;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//
//import org.springframework.security.authentication
//        .UsernamePasswordAuthenticationToken;
//import org.springframework.security.core.authority.SimpleGrantedAuthority;
//import org.springframework.security.core.context.SecurityContextHolder;
//import org.springframework.stereotype.Component;
//import org.springframework.web.filter.OncePerRequestFilter;
//
//import java.io.IOException;
//import java.security.cert.X509Certificate;
//import java.util.List;
//
//@Component
//public class VaultIdentityFilter extends OncePerRequestFilter {
//
//    private static final String CERT_ATTRIBUTE =
//            "jakarta.servlet.request.X509Certificate";
//
//    private final CertificateIdentityResolver resolver;
//
//    public VaultIdentityFilter(
//            CertificateIdentityResolver resolver) {
//        this.resolver = resolver;
//    }
//
//    @Override
//    protected void doFilterInternal(
//            HttpServletRequest request,
//            HttpServletResponse response,
//            FilterChain chain)
//            throws ServletException, IOException {
//
//        Object certificates =
//                request.getAttribute(CERT_ATTRIBUTE);
//
//        if (!(certificates instanceof X509Certificate[] certs)
//                || certs.length == 0) {
//
//            SecurityContextHolder.clearContext();
//            response.sendError(
//                    HttpServletResponse.SC_UNAUTHORIZED,
//                    "Client certificate required"
//            );
//            return;
//        }
//
//        try {
//            VaultPrincipal principal =
//                    resolver.resolve(certs[0]);
//
//            var authentication =
//                    new UsernamePasswordAuthenticationToken(
//                            principal,
//                            null,
//                            List.of(new SimpleGrantedAuthority(
//                                    "ROLE_VAULT_SERVICE"))
//                    );
//
//            SecurityContextHolder.getContext()
//                    .setAuthentication(authentication);
//
//            chain.doFilter(request, response);
//
//        } catch (IllegalArgumentException ex) {
//            SecurityContextHolder.clearContext();
//
//            response.sendError(
//                    HttpServletResponse.SC_UNAUTHORIZED,
//                    "Invalid service identity"
//            );
//        }
//    }
//}
