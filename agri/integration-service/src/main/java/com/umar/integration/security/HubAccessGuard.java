package com.umar.integration.security;

import com.umar.integration.domain.enums.HubRole;
import com.umar.integration.error.HubException;
import org.springframework.stereotype.Component;

@Component
public class HubAccessGuard {

    public HubIdentity requireRoles(HubRole... allowed) {
        if (com.umar.security.AgreeShieldSecurityContext.current().securityBypassed()) {
            return new HubIdentity("db-security-bypass", null, null, "local-dev",
                    java.util.Set.of(HubRole.ADMIN, HubRole.INTEGRATION_MANAGER), true);
        }
        HubIdentity identity = HubSecurityContext.require();
        if (!identity.hasAnyRole(allowed)) {
            throw HubException.forbidden("Not authorised");
        }
        return identity;
    }

    public boolean isPartnerScopedOnly(HubIdentity identity) {
        return !identity.isInternalStaff()
                && identity.hasAnyRole(HubRole.PARTNER_ADMIN, HubRole.PARTNER_DEVELOPER);
    }

    public String resolveEffectivePartnerId(HubIdentity identity, String requestedPartnerId) {
        if (isPartnerScopedOnly(identity)) {
            if (identity.partnerId() == null || identity.partnerId().isBlank()) {
                throw HubException.forbidden("Not authorised");
            }
            if (requestedPartnerId != null && !requestedPartnerId.isBlank()
                    && !requestedPartnerId.equals(identity.partnerId())) {
                throw HubException.forbidden("Not authorised");
            }
            return identity.partnerId();
        }
        return requestedPartnerId;
    }

    public void assertCanAccessPartnerResource(HubIdentity identity, String resourceOwnerPartnerId, String notFoundCode, String notFoundMessage) {
        if (isPartnerScopedOnly(identity)) {
            if (identity.partnerId() == null || !identity.partnerId().equals(resourceOwnerPartnerId)) {
                // Section 2.17: objects of another partner return 404, the same as unknown ids
                throw HubException.notFound(notFoundCode, notFoundMessage);
            }
        }
    }
}
