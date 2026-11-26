package com.umar.policy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PolicyDenialAuditServiceImp implements PolicyDenialAuditService{
    @Override
    public void recordDenial(String caller, String operation, String dataClass, String purpose, String reason) {
        log.warn("Vault policy denial: caller={}, operation={}, dataClass={}, purpose={}, reason={}",
                caller, operation, dataClass, purpose, reason);
    }
}
