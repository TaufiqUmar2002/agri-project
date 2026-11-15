package com.umar.policy;

public interface PolicyDenialAuditService {

    void recordDenial(String caller, String operation, String dataClass, String purpose, String reason);

}
