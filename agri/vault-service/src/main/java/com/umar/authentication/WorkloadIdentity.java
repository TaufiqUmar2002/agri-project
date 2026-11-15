package com.umar.authentication;


import java.util.Collections;
import java.util.Set;

/**
 * Represents the strongly-typed authenticated machine identity extracted via mTLS.
 */
public record WorkloadIdentity(String serviceName, String environment, Set<String> roles) {
    public WorkloadIdentity {
        if (roles == null) {
            roles = Collections.emptySet();
        } else {
            roles = Set.copyOf(roles);
        }
    }
}
