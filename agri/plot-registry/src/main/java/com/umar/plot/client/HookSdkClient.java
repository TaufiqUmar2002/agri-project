package com.umar.plot.client;

import java.util.Map;

public interface HookSdkClient {
    PreRegisterOutcome preRegister(String farmerId);
    Map<String, Object> enrich(String plotId);
    double overlapThreshold(double platformDefaultPercent);
    void postValidate(String plotId, String outcome);

    enum PreRegisterOutcome { ALLOW, HOLD, REJECT }
}
